package com.dataops.dms.sql;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * SQL审核引擎
 * 检查SQL语法、性能风险、规范合规性
 */
@Slf4j
@Component
public class SqlAuditEngine {

    // 危险SQL模式
    private static final Pattern DROP_PATTERN = Pattern.compile("\\bDROP\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TRUNCATE_PATTERN = Pattern.compile("\\bTRUNCATE\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern DELETE_WITHOUT_WHERE = Pattern.compile(
            "^\\s*DELETE\\s+(FROM\\s+)?[\\w.]+\\s*(;\\s*)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern UPDATE_WITHOUT_WHERE = Pattern.compile(
            "^\\s*UPDATE\\s+[\\w.]+\\s+SET\\s+(?!.*\\bWHERE\\b)[^;]+(;\\s*)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern SELECT_FULL_TABLE = Pattern.compile(
            "^\\s*SELECT\\s+\\*\\s+FROM\\s+[\\w.]+\\s*(LIMIT|WHERE|;|$)", Pattern.CASE_INSENSITIVE);

    // 索引相关模式
    private static final Pattern LIKE_PREFIX_WILDCARD = Pattern.compile(
            "LIKE\\s+['\"]%", Pattern.CASE_INSENSITIVE);
    private static final Pattern OR_CONDITION = Pattern.compile("\\bOR\\b", Pattern.CASE_INSENSITIVE);

    /**
     * 审核SQL
     */
    public SqlAuditResult audit(String sql) {
        SqlAuditResult result = new SqlAuditResult();
        result.setSql(sql);
        
        List<AuditIssue> issues = new ArrayList<>();

        // 检查SQL语法（优先执行，语法错误直接阻止提交）
        checkSyntax(sql, issues);

        // 检查危险操作
        checkDangerousOperations(sql, issues);

        // 检查性能问题
        checkPerformanceIssues(sql, issues);

        // 检查规范问题
        checkSpecificationIssues(sql, issues);

        // 索引优化建议
        checkIndexSuggestions(sql, issues);

        // SQL改写建议
        checkRewriteSuggestions(sql, issues);

        result.setIssues(issues);
        
        // 计算风险等级
        result.setRiskLevel(calculateRiskLevel(issues));
        result.setPassed(issues.stream().noneMatch(i -> "error".equals(i.getLevel())));

        // 计算优化评分
        result.setOptimizationScore(calculateOptimizationScore(issues));
        result.setOptimizationLevel(determineOptimizationLevel(result.getOptimizationScore()));

        return result;
    }

    /**
     * 检查SQL语法正确性（使用JSqlParser进行语法解析）
     */
    private void checkSyntax(String sql, List<AuditIssue> issues) {
        if (sql == null || sql.trim().isEmpty()) {
            issues.add(new AuditIssue(
                    "error",
                    "SQL_SYNTAX_ERROR",
                    "SQL语句不能为空",
                    "请输入有效的SQL语句"
            ));
            return;
        }

        try {
            // 去除末尾分号后解析
            String cleanSql = sql.trim();
            // 支持多条SQL语句（用分号分隔），逐条解析
            String[] statements = cleanSql.split(";");
            for (String stmt : statements) {
                String trimmed = stmt.trim();
                if (trimmed.isEmpty()) continue;
                CCJSqlParserUtil.parse(trimmed);
            }
            log.debug("SQL syntax check passed");
        } catch (JSQLParserException e) {
            String errorMsg = e.getMessage();
            // 提取更友好的错误信息
            String shortMsg = errorMsg;
            if (errorMsg != null && errorMsg.contains("Was expecting")) {
                int idx = errorMsg.indexOf("Was expecting");
                shortMsg = errorMsg.substring(0, Math.min(errorMsg.length(), idx + 80));
            }
            log.warn("SQL syntax error: {}", shortMsg);
            issues.add(new AuditIssue(
                    "error",
                    "SQL_SYNTAX_ERROR",
                    "SQL语法错误: " + (shortMsg != null ? shortMsg : "无法解析SQL语句"),
                    "请检查SQL语法，确保关键字、表名、列名拼写正确，标点符号使用规范"
            ));
        } catch (Exception e) {
            log.warn("SQL syntax check exception: {}", e.getMessage());
            issues.add(new AuditIssue(
                    "error",
                    "SQL_SYNTAX_ERROR",
                    "SQL语法解析异常: " + e.getMessage(),
                    "请检查SQL语句格式是否正确"
            ));
        }
    }

    /**
     * 检查危险操作
     */
    private void checkDangerousOperations(String sql, List<AuditIssue> issues) {
        // DROP TABLE
        if (DROP_PATTERN.matcher(sql).find()) {
            issues.add(new AuditIssue(
                    "error",
                    "DANGEROUS_DROP",
                    "检测到 DROP 操作，这将永久删除表数据和结构，请确认是否必要",
                    "建议：在生产环境执行 DROP 前必须备份数据，建议重命名表而非直接删除"
            ));
        }

        // TRUNCATE TABLE
        if (TRUNCATE_PATTERN.matcher(sql).find()) {
            issues.add(new AuditIssue(
                    "error",
                    "DANGEROUS_TRUNCATE",
                    "检测到 TRUNCATE 操作，这将清空表且无法回滚",
                    "建议：使用 DELETE 替代，或在运维窗口执行并确认备份"
            ));
        }

        // DELETE without WHERE
        if (DELETE_WITHOUT_WHERE.matcher(sql).find()) {
            issues.add(new AuditIssue(
                    "error",
                    "DELETE_NO_WHERE",
                    "DELETE 语句缺少 WHERE 条件，将删除全表数据",
                    "建议：添加 WHERE 条件，如需清空表请使用 TRUNCATE（并确认风险）"
            ));
        }

        // UPDATE without WHERE
        if (UPDATE_WITHOUT_WHERE.matcher(sql).find()) {
            issues.add(new AuditIssue(
                    "error",
                    "UPDATE_NO_WHERE",
                    "UPDATE 语句缺少 WHERE 条件，将更新全表数据",
                    "建议：添加 WHERE 条件，或确认全表更新是否必要"
            ));
        }
    }

    /**
     * 检查性能问题
     */
    private void checkPerformanceIssues(String sql, List<AuditIssue> issues) {
        // SELECT *
        if (SELECT_FULL_TABLE.matcher(sql).find()) {
            issues.add(new AuditIssue(
                    "warning",
                    "SELECT_ALL_COLUMNS",
                    "使用 SELECT * 查询所有列，可能导致不必要的IO和网络开销",
                    "建议：明确列出需要的列名"
            ));
        }

        // LIKE %xxx 前缀通配符（无法使用索引）
        if (LIKE_PREFIX_WILDCARD.matcher(sql).find()) {
            issues.add(new AuditIssue(
                    "warning",
                    "LIKE_PREFIX_WILDCARD",
                    "LIKE 查询使用前缀通配符（%xxx），无法使用索引",
                    "建议：使用后缀通配符（xxx%）或使用全文索引"
            ));
        }

        // OR 条件（可能导致索引失效）
        if (OR_CONDITION.matcher(sql).find()) {
            issues.add(new AuditIssue(
                    "notice",
                    "OR_CONDITION",
                    "使用 OR 条件，可能导致索引失效",
                    "建议：考虑使用 UNION ALL 替代 OR，或检查索引覆盖情况"
            ));
        }

        // 检查LIMIT
        if (!sql.toUpperCase().contains("LIMIT") && sql.toUpperCase().startsWith("SELECT")) {
            issues.add(new AuditIssue(
                    "notice",
                    "NO_LIMIT",
                    "SELECT 语句未设置 LIMIT 限制返回行数",
                    "建议：添加 LIMIT 限制，避免大结果集导致内存溢出"
            ));
        }
    }

    /**
     * 检查规范问题
     */
    private void checkSpecificationIssues(String sql, List<AuditIssue> issues) {
        // 检查是否以分号结尾
        if (!sql.trim().endsWith(";")) {
            issues.add(new AuditIssue(
                    "notice",
                    "MISSING_SEMICOLON",
                    "SQL 语句建议以分号结尾",
                    "建议：添加分号，养成良好编码习惯"
            ));
        }

        // 检查SQL长度（超长SQL）
        if (sql.length() > 5000) {
            issues.add(new AuditIssue(
                    "warning",
                    "SQL_TOO_LONG",
                    "SQL 语句过长（超过5000字符），建议拆分",
                    "建议：将大SQL拆分为多个小SQL，分批执行"
            ));
        }
    }

    /**
     * 检查索引优化建议
     */
    private void checkIndexSuggestions(String sql, List<AuditIssue> issues) {
        String upperSql = sql.toUpperCase();

        // WHERE子句中的列建议加索引
        if (upperSql.contains("WHERE")) {
            // 检查 BETWEEN 范围查询
            if (upperSql.contains("BETWEEN")) {
                issues.add(new AuditIssue(
                        "notice",
                        "INDEX_RANGE_SUGGESTION",
                        "使用BETWEEN范围查询，建议在相关列上建立索引以加速范围扫描",
                        "建议：对BETWEEN查询的列创建索引，如 CREATE INDEX idx_col ON table(col)"
                ));
            }

            // 检查 IN 子句
            if (upperSql.contains(" IN (") || upperSql.contains(" IN(")) {
                issues.add(new AuditIssue(
                        "notice",
                        "INDEX_IN_SUGGESTION",
                        "使用IN条件查询，当IN列表较大时索引效率可能下降",
                        "建议：对IN查询列建立索引；如IN列表>1000项，考虑使用临时表JOIN"
                ));
            }

            // 检查 ORDER BY 与 WHERE 组合
            if (upperSql.contains("ORDER BY") && upperSql.contains("WHERE")) {
                issues.add(new AuditIssue(
                        "notice",
                        "INDEX_ORDER_SUGGESTION",
                        "WHERE+ORDER BY组合查询，建议使用联合索引覆盖过滤和排序列",
                        "建议：创建联合索引 idx(WHERE列, ORDER BY列) 避免filesort"
                ));
            }

            // 检查 GROUP BY
            if (upperSql.contains("GROUP BY")) {
                issues.add(new AuditIssue(
                        "notice",
                        "INDEX_GROUP_SUGGESTION",
                        "GROUP BY聚合查询，建议在分组列和聚合列上建立索引",
                        "建议：创建覆盖索引包含GROUP BY列，可避免临时表和filesort"
                ));
            }
        }

        // JOIN查询索引建议
        if (upperSql.contains(" JOIN ")) {
            issues.add(new AuditIssue(
                    "notice",
                    "INDEX_JOIN_SUGGESTION",
                    "JOIN关联查询，建议在关联列上建立索引",
                    "建议：对被驱动表的关联列创建索引，如 CREATE INDEX idx_fk ON child(parent_id)"
            ));

            // 检查是否有多个JOIN
            long joinCount = countOccurrences(upperSql, " JOIN ");
            if (joinCount >= 3) {
                issues.add(new AuditIssue(
                        "warning",
                        "TOO_MANY_JOINS",
                        String.format("JOIN数量过多（%d个），可能导致性能问题", joinCount),
                        "建议：考虑拆分查询或使用子查询、临时表减少JOIN层数"
                ));
            }
        }

        // DISTINCT使用检查
        if (upperSql.contains("SELECT DISTINCT") || upperSql.contains("SELECT\nDISTINCT")) {
            issues.add(new AuditIssue(
                    "notice",
                    "DISTINCT_SUGGESTION",
                    "使用DISTINCT去重，可能意味着数据冗余或查询逻辑可优化",
                    "建议：检查是否可通过EXISTS替代DISTINCT，或检查表设计是否合理"
            ));
        }
    }

    /**
     * 检查SQL改写建议
     */
    private void checkRewriteSuggestions(String sql, List<AuditIssue> issues) {
        String upperSql = sql.toUpperCase();

        // NOT IN -> NOT EXISTS 改写建议
        if (upperSql.contains("NOT IN") && upperSql.contains("SELECT")) {
            issues.add(new AuditIssue(
                    "notice",
                    "REWRITE_NOT_IN",
                    "NOT IN (子查询) 可改写为 NOT EXISTS，性能更优",
                    "改写示例：WHERE NOT EXISTS (SELECT 1 FROM sub_table WHERE sub_table.id = main_table.id)"
            ));
        }

        // COUNT(*) vs COUNT(1) vs COUNT(col)
        if (upperSql.contains("COUNT(*)")) {
            issues.add(new AuditIssue(
                    "notice",
                    "REWRITE_COUNT",
                    "COUNT(*) 在MySQL中已被优化，与COUNT(1)性能一致，无需刻意修改",
                    "说明：MySQL优化器会自动优化COUNT(*)，无需改写"
            ));
        }

        // 子查询改JOIN建议
        if (upperSql.contains("WHERE") && upperSql.indexOf("SELECT", upperSql.indexOf("WHERE")) > 0
                && !upperSql.contains("EXISTS")) {
            issues.add(new AuditIssue(
                    "notice",
                    "REWRITE_SUBQUERY",
                    "WHERE子查询可能可改写为JOIN，提升查询性能",
                    "建议：将 WHERE col IN (SELECT ...) 改写为 INNER JOIN 或 LEFT JOIN"
            ));
        }

        // UNION -> UNION ALL 建议
        if (upperSql.contains("UNION") && !upperSql.contains("UNION ALL")) {
            issues.add(new AuditIssue(
                    "notice",
                    "REWRITE_UNION",
                    "UNION会去重排序，如不需要去重请使用UNION ALL",
                    "建议：UNION ALL性能优于UNION，确认数据无重复时使用UNION ALL"
            ));
        }

        // IS NULL / IS NOT NULL
        if (upperSql.contains("IS NULL") || upperSql.contains("IS NOT NULL")) {
            issues.add(new AuditIssue(
                    "notice",
                    "NULL_CHECK_SUGGESTION",
                    "NULL值判断可能导致索引失效",
                    "建议：为列设置默认值避免NULL，或使用DEFAULT值替代NULL判断"
            ));
        }

        // 函数索引失效
        if (upperSql.contains("WHERE") && (upperSql.contains("DATE(") || upperSql.contains("YEAR(")
                || upperSql.contains("MONTH(") || upperSql.contains("UPPER(") || upperSql.contains("LOWER("))) {
            issues.add(new AuditIssue(
                    "warning",
                    "FUNCTION_INDEX_INVALID",
                    "WHERE条件中对列使用函数，将导致索引失效进行全表扫描",
                    "建议：避免对索引列使用函数，如 WHERE DATE(create_time)='2024-01-01' 改为 WHERE create_time >= '2024-01-01' AND create_time < '2024-01-02'"
            ));
        }
    }

    /**
     * 计算优化评分（0-100分）
     */
    private int calculateOptimizationScore(List<AuditIssue> issues) {
        int score = 100;
        for (AuditIssue issue : issues) {
            switch (issue.getLevel()) {
                case "error": score -= 30; break;
                case "warning": score -= 15; break;
                case "notice": score -= 5; break;
            }
        }
        return Math.max(0, Math.min(100, score));
    }

    /**
     * 确定优化等级
     */
    private String determineOptimizationLevel(int score) {
        if (score >= 90) return "excellent";
        if (score >= 75) return "good";
        if (score >= 60) return "fair";
        if (score >= 40) return "poor";
        return "critical";
    }

    private long countOccurrences(String str, String sub) {
        long count = 0;
        int idx = 0;
        while ((idx = str.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }

    /**
     * 计算风险等级
     */
    private String calculateRiskLevel(List<AuditIssue> issues) {
        long errorCount = issues.stream().filter(i -> "error".equals(i.getLevel())).count();
        long warningCount = issues.stream().filter(i -> "warning".equals(i.getLevel())).count();

        if (errorCount > 0) {
            return "high";
        } else if (warningCount >= 2) {
            return "medium";
        } else if (warningCount > 0) {
            return "low";
        } else {
            return "none";
        }
    }

    /**
     * SQL审核结果
     */
    @Data
    public static class SqlAuditResult {
        private String sql;
        private List<AuditIssue> issues;
        private String riskLevel;
        private boolean passed;
        private int optimizationScore;   // 0-100优化评分
        private String optimizationLevel; // excellent/good/fair/poor/critical
    }

    /**
     * 审核问题项
     */
    @Data
    public static class AuditIssue {
        private String level;      // error, warning, notice
        private String code;       // 问题编码
        private String message;    // 问题描述
        private String suggestion; // 优化建议

        public AuditIssue(String level, String code, String message, String suggestion) {
            this.level = level;
            this.code = code;
            this.message = message;
            this.suggestion = suggestion;
        }
    }
}
