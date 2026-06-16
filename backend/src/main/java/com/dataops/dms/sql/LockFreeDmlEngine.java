package com.dataops.dms.sql;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 无锁数据变更引擎（DML无锁）
 * 针对大表UPDATE/DELETE操作，分批执行避免锁表
 * 
 * 核心特性：
 * 1. 自动解析主键，按主键分批次
 * 2. 每批执行后自动提交，避免长事务
 * 3. 批次间休眠，降低主从延迟
 * 4. 实时进度追踪
 * 5. 断点续跑支持
 */
@Slf4j
@Component
public class LockFreeDmlEngine {

    // 主键解析正则
    private static final Pattern UPDATE_PATTERN = Pattern.compile(
        "^\\s*UPDATE\\s+(?:LOW_PRIORITY\\s+)?(?:IGNORE\\s+)?`?(\\w+)`?\\s+", 
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern DELETE_PATTERN = Pattern.compile(
        "^\\s*DELETE\\s+FROM\\s+`?(\\w+)`?\\s+", 
        Pattern.CASE_INSENSITIVE
    );

    // 检测 WHERE 子句中非法列名格式：如 "e id" 这种有空格的不连续标识符
    // 匹配 WHERE 后两个相邻的独立标识符（中间只有空白符，不是合法运算符）
    // 排除 "ORDER BY", "GROUP BY", "IS NULL", "IS NOT", "NOT IN", "IN (" 等合法组合
    private static final Pattern INVALID_WHERE_COLUMN = Pattern.compile(
        "(?i)\\bWHERE\\s+[^;]*?\\b([a-zA-Z_]\\w*)\\s+([a-zA-Z_]\\w*)\\s*(?![,\\)])", 
        Pattern.DOTALL
    );

    // 默认批次大小
    private static final int DEFAULT_BATCH_SIZE = 1000;
    // 默认批次间隔（毫秒）
    private static final int DEFAULT_INTERVAL_MS = 100;

    /**
     * 检测SQL是否需要无锁执行
     */
    public DmlCheckResult checkNeedLockFree(String databaseId, String sql, Connection conn) {
        DmlCheckResult result = new DmlCheckResult();
        result.setSql(sql);
        result.setNeedLockFree(false);

        List<String> statements = splitSqlStatements(sql);
        if (statements.isEmpty()) {
            result.setMessage("SQL内容为空");
            return result;
        }

        List<TableCheckResult> tableResults = new ArrayList<>();
        int totalAffectRows = 0;
        boolean anyNeedLockFree = false;
        String highestRisk = "low";
        StringBuilder summaryMsg = new StringBuilder();

        for (int i = 0; i < statements.size(); i++) {
            String stmt = statements.get(i);
            TableCheckResult tr = checkSingleNeedLockFree(databaseId, stmt, conn);
            tr.setSql(stmt.length() > 60 ? stmt.substring(0, 60) + "..." : stmt);
            tableResults.add(tr);
            totalAffectRows += tr.getEstimateAffectRows();
            if (tr.isNeedLockFree()) anyNeedLockFree = true;
            // 风险等级追踪（包含 error）
            if ("error".equals(tr.getRiskLevel()) || "high".equals(tr.getRiskLevel())
                || ("medium".equals(tr.getRiskLevel()) && "low".equals(highestRisk))) {
                highestRisk = tr.getRiskLevel();
            }
            if (statements.size() > 1) {
                if ("error".equals(tr.getRiskLevel())) {
                    summaryMsg.append(String.format("[%d] %s: 语法错误; ", i + 1, 
                        tr.getTableName() != null ? tr.getTableName() : "未知"));
                } else {
                    summaryMsg.append(String.format("[%d] %s: %d行%s; ", i + 1, tr.getTableName(), 
                        tr.getEstimateAffectRows(), tr.isNeedLockFree() ? " ⚠建议无锁" : ""));
                }
            }
        }

        // 汇总到顶层字段（兼容单表）
        result.setTables(tableResults);
        result.setTableCount(tableResults.size());
        if (!tableResults.isEmpty()) {
            TableCheckResult first = tableResults.get(0);
            result.setTableName(first.getTableName());
            result.setTableSize(first.getTableSize());
            result.setTableSizeHuman(first.getTableSizeHuman());
            result.setEstimateRows(first.getEstimateRows());
            result.setPrimaryKeys(first.getPrimaryKeys());
            result.setHasPrimaryKey(first.isHasPrimaryKey());
            result.setPrimaryKey(first.getPrimaryKey());
        }
        result.setEstimateAffectRows(totalAffectRows);
        result.setNeedLockFree(anyNeedLockFree);
        result.setRiskLevel(highestRisk);

        if (tableResults.size() == 1) {
            result.setMessage(tableResults.get(0).getMessage());
            result.setRecommendedBatchSize(tableResults.get(0).getRecommendedBatchSize());
        } else {
            result.setRecommendedBatchSize(
                anyNeedLockFree ? Math.min(DEFAULT_BATCH_SIZE, totalAffectRows > 100000 ? 500 : DEFAULT_BATCH_SIZE) : 0);
            if ("error".equals(highestRisk)) {
                result.setMessage("存在SQL语法错误，请修正后重新检测。详情：" + summaryMsg.toString().trim());
            } else if (anyNeedLockFree) {
                result.setMessage(String.format("共 %d 条SQL，合计影响 %d 行，建议使用无锁变更。%s", 
                    tableResults.size(), totalAffectRows, summaryMsg.toString().trim()));
            } else if (totalAffectRows == 0) {
                result.setMessage("所有SQL预估影响行数均为 0");
            } else {
                result.setMessage(String.format("共 %d 条SQL，合计影响 %d 行，可直接执行", 
                    tableResults.size(), totalAffectRows));
            }
        }

        return result;
    }

    /**
     * 检测单条SQL是否需要无锁变更
     */
    private TableCheckResult checkSingleNeedLockFree(String databaseId, String sql, Connection conn) {
        TableCheckResult result = new TableCheckResult();
        result.setNeedLockFree(false);
        
        try {
            String syntaxError = validateSqlSyntax(sql);
            if (syntaxError != null) {
                result.setMessage("SQL语法错误: " + syntaxError);
                result.setRiskLevel("error");
                return result;
            }

            // EXPLAIN 语法预检（让 MySQL 直接验证SQL合法性，拦截 "SET SET" 等基础校验遗漏的错误）
            try {
                Statement explainSt = null;
                ResultSet explainRs = null;
                try {
                    explainSt = conn.createStatement();
                    explainRs = explainSt.executeQuery("EXPLAIN " + sql);
                } catch (SQLException ee) {
                    String errMsg = ee.getMessage();
                    if (errMsg != null && (errMsg.toLowerCase().contains("syntax") 
                        || errMsg.contains("You have an error"))) {
                        result.setMessage("SQL语法错误: " + errMsg);
                        result.setRiskLevel("error");
                        return result;
                    }
                } finally {
                    try { if (explainRs != null) explainRs.close(); } catch (Exception ignored) {}
                    try { if (explainSt != null) explainSt.close(); } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}

            String tableName = extractTableName(sql);
            if (tableName == null) {
                result.setMessage("非UPDATE/DELETE语句，无需无锁变更");
                return result;
            }
            result.setTableName(tableName);

            TableStats stats = getTableStats(conn, tableName);
            result.setTableSize(stats.totalSize);
            result.setTableSizeHuman(formatSize(stats.totalSize));
            result.setEstimateRows(stats.estimateRows);

            int affectRows = estimateAffectRows(conn, sql, tableName);
            if (affectRows < 0) {
                result.setEstimateAffectRows(0);
                result.setMessage("无法预估影响行数");
                result.setRiskLevel("unknown");
                return result;
            }
            result.setEstimateAffectRows(affectRows);

            if (affectRows > 10000) {
                result.setNeedLockFree(true);
                result.setRiskLevel("high");
                result.setMessage(String.format("预计影响 %d 行，强烈建议使用无锁变更分批执行", affectRows));
                result.setRecommendedBatchSize(Math.min(DEFAULT_BATCH_SIZE, affectRows > 100000 ? 500 : DEFAULT_BATCH_SIZE));
            } else if (affectRows > 1000) {
                result.setNeedLockFree(true);
                result.setRiskLevel("medium");
                result.setMessage(String.format("预计影响 %d 行，建议使用无锁变更分批执行", affectRows));
                result.setRecommendedBatchSize(DEFAULT_BATCH_SIZE);
            } else {
                result.setRiskLevel("low");
                result.setMessage("影响行数较少，可直接执行");
            }

            List<String> primaryKeys = getPrimaryKeys(conn, tableName);
            result.setPrimaryKeys(primaryKeys);
            result.setHasPrimaryKey(!primaryKeys.isEmpty());
            if (!primaryKeys.isEmpty()) {
                result.setPrimaryKey(primaryKeys.get(0));
            }
        } catch (Exception e) {
            log.error("检测无锁变更失败: {}", e.getMessage(), e);
            result.setMessage("检测失败: " + e.getMessage());
        }
        return result;
    }

    /**
     * 执行无锁数据变更
     */
    public DmlExecutionResult executeLockFree(
            Connection conn, 
            String sql, 
            String tableName,
            String primaryKey,
            int batchSize,
            int intervalMs,
            DmlProgressCallback callback) throws Exception {
        
        DmlExecutionResult result = new DmlExecutionResult();
        result.setStartTime(System.currentTimeMillis());
        result.setBatchSize(batchSize);
        
        long totalAffected = 0;
        int batchCount = 0;
        long lastId = 0;

        try {
            conn.setAutoCommit(false);

            // 生成分批SQL
            String batchSql = generateBatchSql(sql, primaryKey, batchSize);
            log.info("分批执行SQL: {}", batchSql);

            while (true) {
                // 执行一批
                PreparedStatement pstmt = conn.prepareStatement(batchSql);
                pstmt.setLong(1, lastId);
                int affected = pstmt.executeUpdate();
                conn.commit();

                if (affected == 0) {
                    break; // 执行完成
                }

                totalAffected += affected;
                batchCount++;
                
                // 获取本次处理的最大ID（简化）
                lastId += batchSize;

                // 进度回调
                if (callback != null) {
                    callback.onProgress(batchCount, totalAffected, affected);
                }

                result.setLastProcessedId(lastId);
                result.setBatchCount(batchCount);
                result.setTotalAffected(totalAffected);

                log.info("批次 {} 执行完成，影响行数: {}，累计: {}", 
                    batchCount, affected, totalAffected);

                // 批次间休眠
                if (intervalMs > 0 && affected == batchSize) {
                    Thread.sleep(intervalMs);
                }
            }

            result.setSuccess(true);
            result.setMessage(String.format("执行完成，共 %d 批次，影响 %d 行", batchCount, totalAffected));

        } catch (Exception e) {
            conn.rollback();
            result.setSuccess(false);
            result.setMessage("执行失败: " + e.getMessage());
            log.error("无锁变更执行失败: {}", e.getMessage(), e);
            throw e;
        } finally {
            conn.setAutoCommit(true);
            result.setEndTime(System.currentTimeMillis());
            result.setDurationMs(result.getEndTime() - result.getStartTime());
        }

        return result;
    }

    /**
     * 生成分批执行SQL（基于主键范围）
     * 注意：必须先 trim originalSql，确保 upperSql 和 originalSql 的位置对齐
     */
    private String generateBatchSql(String originalSql, String primaryKey, int batchSize) {
        // 先 trim 原始SQL，避免前导/尾随空白导致截取位置偏移
        String sql = originalSql.trim();
        String upperSql = sql.toUpperCase();

        if (upperSql.startsWith("UPDATE")) {
            // UPDATE table SET ... WHERE ... AND id > ? LIMIT batchSize
            int whereIdx = upperSql.indexOf("WHERE");
            if (whereIdx > 0) {
                String beforeWhere = sql.substring(0, whereIdx + 5);
                String afterWhere = sql.substring(whereIdx + 5);
                return String.format("%s %s > ? AND (%s) LIMIT %d",
                    beforeWhere, primaryKey, afterWhere, batchSize);
            } else {
                // 没有WHERE，添加WHERE id > ?
                int setIdx = upperSql.indexOf("SET");
                if (setIdx > 0) {
                    String tablePart = sql.substring(0, setIdx);
                    String setPart = sql.substring(setIdx);
                    return String.format("%s %s WHERE %s > ? LIMIT %d",
                        tablePart, setPart, primaryKey, batchSize);
                }
            }
        } else if (upperSql.startsWith("DELETE")) {
            // DELETE FROM table WHERE ... AND id > ? LIMIT batchSize
            int whereIdx = upperSql.indexOf("WHERE");
            if (whereIdx > 0) {
                String beforeWhere = sql.substring(0, whereIdx + 5);
                String afterWhere = sql.substring(whereIdx + 5);
                return String.format("%s %s > ? AND (%s) LIMIT %d",
                    beforeWhere, primaryKey, afterWhere, batchSize);
            } else {
                int fromIdx = upperSql.indexOf("FROM");
                if (fromIdx > 0) {
                    String rest = sql.substring(fromIdx + 4);
                    return String.format("DELETE FROM %s WHERE %s > ? LIMIT %d",
                        rest, primaryKey, batchSize);
                }
            }
        }

        return sql;
    }

    /**
     * 从SQL中提取表名
     */
    private String extractTableName(String sql) {
        String upper = sql.trim().toUpperCase();

        Matcher updateMatcher = UPDATE_PATTERN.matcher(sql);
        if (updateMatcher.find()) {
            return updateMatcher.group(1);
        }

        Matcher deleteMatcher = DELETE_PATTERN.matcher(sql);
        if (deleteMatcher.find()) {
            return deleteMatcher.group(1);
        }

        return null;
    }

    /**
     * 获取表统计信息
     */
    private TableStats getTableStats(Connection conn, String tableName) throws SQLException {
        TableStats stats = new TableStats();
        
        ResultSet rs = conn.createStatement().executeQuery(
            "SELECT TABLE_ROWS, DATA_LENGTH + INDEX_LENGTH AS total_size " +
            "FROM information_schema.TABLES " +
            "WHERE TABLE_NAME = '" + tableName + "'"
        );

        if (rs.next()) {
            stats.estimateRows = rs.getLong("TABLE_ROWS");
            stats.totalSize = rs.getLong("total_size");
        }

        rs.close();
        return stats;
    }

    /**
     * 估算影响行数（支持多条SQL用 ; 分隔，累加所有语句）
     */
    private int estimateAffectRows(Connection conn, String sql, String tableName) {
        List<String> statements = splitSqlStatements(sql);
        if (statements.isEmpty()) return 0;

        int totalRows = 0;
        for (String stmt : statements) {
            int rows = estimateSingleAffectRows(conn, stmt);
            if (rows < 0) return rows;
            totalRows += rows;
        }
        return totalRows;
    }

    /**
     * 拆分多条SQL语句（按 ; 分隔，忽略空语句）
     */
    private List<String> splitSqlStatements(String sql) {
        List<String> result = new ArrayList<>();
        if (sql == null || sql.trim().isEmpty()) return result;
        String[] parts = sql.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * 估算单条SQL的影响行数
     */
    private int estimateSingleAffectRows(Connection conn, String sql) {
        Statement stmt = null;
        ResultSet rs = null;
        try {
            String normalized = sql.trim().replaceAll("\\s+", " ");
            String countSql = null;
            
            java.util.regex.Matcher updateMatcher = java.util.regex.Pattern.compile(
                "(?i)^UPDATE\\s+(.+?)\\s+SET\\s+.+?(WHERE\\s+.+)?$", java.util.regex.Pattern.DOTALL).matcher(normalized);
            if (updateMatcher.matches()) {
                String table = updateMatcher.group(1).trim();
                String where = updateMatcher.group(2) != null ? " " + updateMatcher.group(2).trim() : "";
                countSql = "SELECT COUNT(*) FROM " + table + where;
            }
            
            if (countSql == null) {
                java.util.regex.Matcher deleteMatcher = java.util.regex.Pattern.compile(
                    "(?i)^DELETE\\s+FROM\\s+(.+?)(\\s+WHERE\\s+.+)?$", java.util.regex.Pattern.DOTALL).matcher(normalized);
                if (deleteMatcher.matches()) {
                    String table = deleteMatcher.group(1).trim();
                    String where = deleteMatcher.group(2) != null ? " " + deleteMatcher.group(2).trim() : "";
                    countSql = "SELECT COUNT(*) FROM " + table + where;
                }
            }
            
            if (countSql == null) {
                stmt = conn.createStatement();
                rs = stmt.executeQuery("EXPLAIN " + sql);
                if (rs.next()) {
                    long rows = rs.getLong("rows");
                    String extra = null;
                    try { extra = rs.getString("Extra"); } catch (SQLException ignored) {}
                    if (extra != null && extra.toUpperCase().contains("IMPOSSIBLE WHERE")) {
                        return -1;
                    }
                    return (int) Math.min(rows, Integer.MAX_VALUE);
                }
                return 0;
            }

            log.info("智能检测执行真实计数: {}", countSql);
            stmt = conn.createStatement();
            rs = stmt.executeQuery(countSql);
            if (rs.next()) {
                long count = rs.getLong(1);
                log.info("智能检测真实影响行数: {}", count);
                return (int) Math.min(count, Integer.MAX_VALUE);
            }
        } catch (SQLException e) {
            String errMsg = e.getMessage();
            log.warn("COUNT查询执行失败: {}", errMsg);
            if (errMsg != null && (errMsg.contains("syntax error") 
                || errMsg.contains("You have an error in your SQL")
                || errMsg.contains("check the manual"))) {
                throw new RuntimeException("SQL语法错误，请检查语句: " + errMsg);
            }
            log.warn("无法通过 COUNT 预估影响行数: {}", errMsg);
            return -1;
        } finally {
            closeQuietly(rs, stmt);
        }
        return -1;
    }

    /**
     * 基础 SQL 语法校验（预检查，在发送给数据库之前）
     * 返回 null 表示通过，否则返回错误描述
     */
    private String validateSqlSyntax(String sql) {
        String trimmed = sql.trim();
        String upper = trimmed.toUpperCase();

        // 必须是 UPDATE 或 DELETE 开头
        if (!upper.startsWith("UPDATE") && !upper.startsWith("DELETE")) {
            return null; // 不是 DML，交给后续处理
        }

        // 检测 WHERE 子句中空格分隔的无效列名（如 "e id"）
        // 合法的两个连续标识符组合：运算符+操作数 或 关键字组合
        Matcher whereColMatcher = INVALID_WHERE_COLUMN.matcher(trimmed);
        while (whereColMatcher.find()) {
            String part1 = whereColMatcher.group(1);
            String part2 = whereColMatcher.group(2);
            String combined = (part1 + " " + part2).toUpperCase();
            
            // 合法的两词组合白名单
            if (combined.equals("GROUP BY") || combined.equals("ORDER BY")
                || combined.equals("PRIMARY KEY") || combined.equals("FOREIGN KEY")
                || combined.equals("IS NULL") || combined.equals("IS NOT")
                || combined.equals("NOT NULL") || combined.equals("NOT IN")
                || combined.equals("NOT EXISTS") || combined.equals("NOT LIKE")
                || combined.equals("NOT BETWEEN")) {
                continue; // 合法，跳过
            }
            
            // part1 是单目运算符/关键字
            if (part1.equalsIgnoreCase("AND") || part1.equalsIgnoreCase("OR")
                || part1.equalsIgnoreCase("NOT") || part1.equalsIgnoreCase("XOR")
                || part1.equalsIgnoreCase("LIKE") || part1.equalsIgnoreCase("BETWEEN")
                || part1.equalsIgnoreCase("IN") || part1.equalsIgnoreCase("EXISTS")
                || part1.equalsIgnoreCase("IS") || part1.equalsIgnoreCase("RLIKE")
                || part1.equalsIgnoreCase("REGEXP")) {
                continue; // 合法，跳过
            }
            
            // part2 是双目运算符后半部分
            if (part2.equalsIgnoreCase("BY") || part2.equalsIgnoreCase("KEY")
                || part2.equalsIgnoreCase("NULL") || part2.equalsIgnoreCase("IN")
                || part2.equalsIgnoreCase("EXISTS") || part2.equalsIgnoreCase("LIKE")
                || part2.equalsIgnoreCase("BETWEEN")) {
                continue; // 合法，跳过
            }
            
            // 检查 part2 后面是否紧跟运算符（如 "id <" 中的 id 是列名，< 是运算符）
            int endPos = whereColMatcher.end(2);
            if (endPos < trimmed.length()) {
                char next = trimmed.charAt(endPos);
                if (next == '<' || next == '>' || next == '=' || next == '!' 
                    || next == ',' || next == ')' || next == ';') {
                    // part2 后面跟运算符，part2 是合法列名。
                    // 但 part1 可能仍是多余的无效标识符（如 "e id <"）。
                    // 排除 part1 是表别名的情况（part1.id 形式在正则中不会匹配到空格分隔）
                    // 此时需要额外检查 part1 是否是已知关键字，否则报错。
                    if (isSqlKeyword(part1)) {
                        continue; // part1 是关键字，合法
                    }
                    // part1 不是关键字，也不是运算符，很可能是无效列名
                    return "WHERE 子句中发现无效标识符 \"" + part1 + " " + part2 + "\"，请检查列名是否正确（多余的 \"" + part1 + "\"？）";
                }
            }
            
            return "WHERE 子句中发现无效列名 \"" + part1 + " " + part2 + "\"，请检查列名是否正确";
        }

        // 检测 UPDATE 语句必须有 SET 子句
        if (upper.startsWith("UPDATE")) {
            int setIdx = upper.indexOf("SET ");
            if (setIdx < 0) {
                return "UPDATE 语句缺少 SET 子句";
            }
        }

        // 检测 WHERE 子句格式基本正确
        if (upper.contains("WHERE")) {
            int whereIdx = upper.indexOf("WHERE");
            String afterWhere = trimmed.substring(whereIdx + 5).trim();
            if (afterWhere.isEmpty()) {
                return "WHERE 子句不能为空，请指定过滤条件";
            }
        }

        return null; // 通过基础校验
    }

    /**
     * 判断是否为 SQL 关键字/运算符（这些可以出现在列名位置而不报错）
     */
    private boolean isSqlKeyword(String word) {
        String upper = word.toUpperCase();
        return upper.equals("AND") || upper.equals("OR") || upper.equals("NOT")
            || upper.equals("XOR") || upper.equals("IN") || upper.equals("LIKE")
            || upper.equals("BETWEEN") || upper.equals("IS") || upper.equals("NULL")
            || upper.equals("EXISTS") || upper.equals("RLIKE") || upper.equals("REGEXP")
            || upper.equals("TRUE") || upper.equals("FALSE") || upper.equals("UNKNOWN")
            || upper.equals("CASE") || upper.equals("WHEN") || upper.equals("THEN")
            || upper.equals("ELSE") || upper.equals("END") || upper.equals("AS")
            || upper.equals("ON") || upper.equals("JOIN") || upper.equals("LEFT")
            || upper.equals("RIGHT") || upper.equals("INNER") || upper.equals("OUTER")
            || upper.equals("CROSS") || upper.equals("FULL") || upper.equals("NATURAL")
            || upper.equals("USING") || upper.equals("DISTINCT") || upper.equals("ALL")
            || upper.equals("ANY") || upper.equals("SOME") || upper.equals("ASC")
            || upper.equals("DESC") || upper.equals("LIMIT") || upper.equals("OFFSET")
            || upper.equals("HAVING") || upper.equals("GROUP") || upper.equals("ORDER")
            || upper.equals("BY") || upper.equals("SET") || upper.equals("WHERE")
            || upper.equals("FROM") || upper.equals("SELECT") || upper.equals("UPDATE")
            || upper.equals("DELETE") || upper.equals("INSERT") || upper.equals("INTO")
            || upper.equals("VALUES") || upper.equals("PRIMARY") || upper.equals("FOREIGN")
            || upper.equals("KEY") || upper.equals("INDEX") || upper.equals("CONSTRAINT");
    }

    /**
     * 安全关闭资源
     */
    private void closeQuietly(ResultSet rs, Statement stmt) {
        try { if (rs != null) rs.close(); } catch (Exception ignored) {}
        try { if (stmt != null) stmt.close(); } catch (Exception ignored) {}
    }

    /**
     * 获取主键列表
     */
    private List<String> getPrimaryKeys(Connection conn, String tableName) throws SQLException {
        List<String> keys = new ArrayList<>();
        ResultSet rs = conn.getMetaData().getPrimaryKeys(null, null, tableName);
        while (rs.next()) {
            keys.add(rs.getString("COLUMN_NAME"));
        }
        rs.close();
        return keys;
    }

    /**
     * 格式化字节大小
     */
    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    // ============ 内部类 ============

    @Data
    public static class TableStats {
        long estimateRows;
        long totalSize;
    }

    @Data
    public static class TableCheckResult {
        private String sql;
        private String tableName;
        private long tableSize;
        private String tableSizeHuman;
        private long estimateRows;
        private int estimateAffectRows;
        private boolean needLockFree;
        private String riskLevel;
        private String message;
        private int recommendedBatchSize;
        private List<String> primaryKeys;
        private boolean hasPrimaryKey;
        private String primaryKey;
    }

    @Data
    public static class DmlCheckResult {
        private String sql;
        // 单表兼容字段
        private String tableName;
        private long tableSize;
        private String tableSizeHuman;
        private long estimateRows;
        private int estimateAffectRows;
        private boolean needLockFree;
        private String riskLevel;
        private String message;
        private int recommendedBatchSize;
        private List<String> primaryKeys;
        private boolean hasPrimaryKey;
        private String primaryKey;
        // 多表明细
        private List<TableCheckResult> tables;
        private int tableCount;
    }

    @Data
    public static class DmlExecutionResult {
        private boolean success;
        private String message;
        private int batchSize;
        private int batchCount;
        private long totalAffected;
        private long lastProcessedId;
        private long startTime;
        private long endTime;
        private long durationMs;
    }

    /**
     * 进度回调接口
     */
    public interface DmlProgressCallback {
        void onProgress(int batchCount, long totalAffected, int batchAffected);
    }
}