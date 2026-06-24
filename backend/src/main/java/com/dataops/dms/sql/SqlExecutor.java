package com.dataops.dms.sql;

import com.dataops.dms.entity.DatabaseInstance;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.sql.*;
import java.util.*;

/**
 * SQL执行器
 */
@Slf4j
@Component
public class SqlExecutor {

    public SqlExecuteResult executeQuery(DatabaseInstance db, String sql) throws Exception {
        return executeQuery(db, sql, null);
    }

    public SqlExecuteResult executeQuery(DatabaseInstance db, String sql, String databaseName) throws Exception {
        return executeRaw(db, sql, databaseName, false);
    }

    public static final int PAGE_SIZE = 200;

    /**
     * 批量执行SQL（支持多条语句），返回多个结果
     */
    public List<SqlExecuteResult> executeBatch(DatabaseInstance db, String sql, String databaseName) throws Exception {
        return executeBatch(db, sql, databaseName, 0, 0);
    }

    /**
     * 批量执行SQL，支持分页（offset/limit）
     * 仅对单个SELECT语句生效，多语句或非SELECT直接执行
     */
    public List<SqlExecuteResult> executeBatch(DatabaseInstance db, String sql, String databaseName, int offset, int limit) throws Exception {
        String trimmedSql = sql.trim();
        // 去掉末尾分号
        while (trimmedSql.endsWith(";")) trimmedSql = trimmedSql.substring(0, trimmedSql.length() - 1).trim();
        String upperSql = trimmedSql.toUpperCase();

        // 判断是否可分页：单条SELECT语句、无LIMIT、limit>0
        boolean isSingleSelect = (upperSql.startsWith("SELECT") || upperSql.startsWith("SHOW")
            || upperSql.startsWith("DESCRIBE") || upperSql.startsWith("DESC"))
            && !trimmedSql.contains(";");
        boolean canPaginate = limit > 0 && isSingleSelect && !hasLimitClause(trimmedSql);

        String execSql = trimmedSql;
        if (canPaginate) {
            // 多查一行用于判断 hasMore
            execSql = "SELECT * FROM (" + trimmedSql + ") _dms_page LIMIT " + (limit + 1) + " OFFSET " + offset;
        }

        List<SqlExecuteResult> results = executeRawBatch(db, execSql, databaseName);

        // 裁剪多余行并标记 hasMore
        if (canPaginate) {
            for (SqlExecuteResult r : results) {
                if (r.getData() != null && r.getData().size() > limit) {
                    r.setHasMore(true);
                    r.setData(r.getData().subList(0, limit));
                    r.setTotalRows(limit);
                } else {
                    r.setHasMore(false);
                }
            }
        }

        return results;
    }

    private List<SqlExecuteResult> executeRawBatch(DatabaseInstance db, String sql, String databaseName) throws Exception {
        Connection conn = null;
        Statement stmt = null;
        long startTime = System.currentTimeMillis();
        List<SqlExecuteResult> results = new ArrayList<>();

        try {
            conn = getConnection(db, databaseName);
            stmt = conn.createStatement();
            stmt.setMaxRows(10000);

            boolean hasResultSet = stmt.execute(sql);
            results.add(buildSingleResult(stmt, hasResultSet));

            while (true) {
                boolean more = stmt.getMoreResults();
                int updateCount = stmt.getUpdateCount();
                if (!more && updateCount == -1) break;
                if (more) {
                    results.add(buildSingleResult(stmt, true));
                } else if (updateCount >= 0) {
                    results.add(buildUpdateResult(updateCount));
                }
            }

            long elapsed = System.currentTimeMillis() - startTime;
            for (SqlExecuteResult r : results) {
                r.setExecutionTime(elapsed);
            }
            return results;
        } finally {
            closeResources(null, stmt, conn);
        }
    }

    /**
     * 判断SQL是否已包含LIMIT子句
     */
    private boolean hasLimitClause(String sql) {
        return sql.toUpperCase().matches("(?s).*\\bLIMIT\\s+\\d+.*");
    }

    private SqlExecuteResult buildSingleResult(Statement stmt, boolean hasResultSet) throws SQLException {
        if (hasResultSet) {
            try (ResultSet rs = stmt.getResultSet()) {
                return buildResult(rs);
            }
        }
        int count = stmt.getUpdateCount();
        return buildUpdateResult(count);
    }

    private SqlExecuteResult buildUpdateResult(int affectRows) {
        SqlExecuteResult r = new SqlExecuteResult();
        r.setSuccess(true);
        r.setAffectRows(affectRows);
        r.setColumns(Collections.singletonList("result"));
        r.setData(Collections.singletonList(
            Collections.singletonMap("result", "执行成功，影响行数：" + affectRows)
        ));
        return r;
    }

    /**
     * 执行EXPLAIN获取执行计划
     */
    public SqlExecuteResult explainQuery(DatabaseInstance db, String sql, String databaseName) throws Exception {
        return executeRaw(db, "EXPLAIN " + sql, databaseName, true);
    }

    private SqlExecuteResult executeRaw(DatabaseInstance db, String sql, String databaseName, boolean isExplain) throws Exception {
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        
        long startTime = System.currentTimeMillis();
        
        try {
            conn = getConnection(db, databaseName);
            stmt = conn.createStatement();
            if (!isExplain) {
                // 默认最多返回10000行，防止大结果集撑爆内存
                stmt.setMaxRows(10000);
            }
            
            boolean isSelect = isSelectStatement(sql);
            
            if (isSelect) {
                rs = stmt.executeQuery(sql);
                return buildResult(rs);
            } else {
                int affectRows = stmt.executeUpdate(sql);
                SqlExecuteResult result = new SqlExecuteResult();
                result.setSuccess(true);
                result.setAffectRows(affectRows);
                result.setColumns(Collections.singletonList("result"));
                result.setData(Collections.singletonList(
                    Collections.singletonMap("result", "执行成功，影响行数：" + affectRows)
                ));
                result.setExecutionTime(System.currentTimeMillis() - startTime);
                return result;
            }
        } finally {
            closeResources(rs, stmt, conn);
        }
    }

    public boolean testConnection(DatabaseInstance db) {
        Connection conn = null;
        try {
            conn = getConnection(db);
            return conn != null && !conn.isClosed();
        } catch (Exception e) {
            log.error("数据库连接测试失败: {}", e.getMessage());
            return false;
        } finally {
            closeResources(null, null, conn);
        }
    }

    private Connection getConnection(DatabaseInstance db) throws Exception {
        return getConnection(db, null);
    }

    private Connection getConnection(DatabaseInstance db, String databaseName) throws Exception {
        String jdbcUrl = buildJdbcUrl(db, databaseName);
        Properties props = new Properties();
        props.put("user", db.getUsername());
        props.put("password", db.getPassword());
        props.put("connectTimeout", "10000");
        props.put("socketTimeout", "30000");
        return DriverManager.getConnection(jdbcUrl, props);
    }

    private String buildJdbcUrl(DatabaseInstance db) {
        return buildJdbcUrl(db, null);
    }

    private String buildJdbcUrl(DatabaseInstance db, String databaseName) {
        String type = db.getDbType() != null ? db.getDbType().toLowerCase() : "mysql";
        String host = db.getHost();
        Integer port = db.getPort();
        String dbName = databaseName != null && !databaseName.isEmpty() ? databaseName : db.getDefaultSchemaName();
        
        if ("mysql".equals(type)) {
            if (dbName != null && !dbName.isEmpty()) {
                return String.format("jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowMultiQueries=true&zeroDateTimeBehavior=convertToNull",
                    host, port != null ? port : 3306, dbName);
            }
            return String.format("jdbc:mysql://%s:%d?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowMultiQueries=true&zeroDateTimeBehavior=convertToNull",
                host, port != null ? port : 3306);
        } else if ("postgresql".equals(type)) {
            if (dbName != null && !dbName.isEmpty()) {
                return String.format("jdbc:postgresql://%s:%d/%s",
                    host, port != null ? port : 5432, dbName);
            }
            return String.format("jdbc:postgresql://%s:%d", host, port != null ? port : 5432);
        } else if ("oracle".equals(type)) {
            if (dbName != null && !dbName.isEmpty()) {
                return String.format("jdbc:oracle:thin:@%s:%d:%s",
                    host, port != null ? port : 1521, dbName);
            }
            return String.format("jdbc:oracle:thin:@%s:%d", host, port != null ? port : 1521);
        }
        if (dbName != null && !dbName.isEmpty()) {
            return String.format("jdbc:mysql://%s:%d/%s?allowMultiQueries=true", host, port != null ? port : 3306, dbName);
        }
        return String.format("jdbc:mysql://%s:%d?allowMultiQueries=true", host, port != null ? port : 3306);
    }

    private SqlExecuteResult buildResult(ResultSet rs) throws SQLException {
        SqlExecuteResult result = new SqlExecuteResult();
        result.setSuccess(true);
        
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        
        List<String> columns = new ArrayList<>();
        for (int i = 1; i <= columnCount; i++) {
            columns.add(metaData.getColumnLabel(i));
        }
        result.setColumns(columns);
        
        List<Map<String, Object>> dataList = new ArrayList<>();
        while (rs.next()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                String colName = metaData.getColumnLabel(i);
                Object value = rs.getObject(i);
                if (value instanceof java.sql.Clob) {
                    java.sql.Clob clob = (java.sql.Clob) value;
                    value = clob.getSubString(1, (int) clob.length());
                }
                row.put(colName, value);
            }
            dataList.add(row);
        }
        
        result.setData(dataList);
        result.setTotalRows(dataList.size());
        return result;
    }

    private boolean isSelectStatement(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return false;
        }
        String trimmed = sql.trim().toUpperCase();
        return trimmed.startsWith("SELECT") || trimmed.startsWith("SHOW") 
            || trimmed.startsWith("DESCRIBE") || trimmed.startsWith("DESC")
            || trimmed.startsWith("EXPLAIN");
    }

    private void closeResources(ResultSet rs, Statement stmt, Connection conn) {
        try {
            if (rs != null) rs.close();
        } catch (Exception e) {
            log.warn("关闭ResultSet失败: {}", e.getMessage());
        }
        try {
            if (stmt != null) stmt.close();
        } catch (Exception e) {
            log.warn("关闭Statement失败: {}", e.getMessage());
        }
        try {
            if (conn != null && !conn.isClosed()) conn.close();
        } catch (Exception e) {
            log.warn("关闭Connection失败: {}", e.getMessage());
        }
    }
}