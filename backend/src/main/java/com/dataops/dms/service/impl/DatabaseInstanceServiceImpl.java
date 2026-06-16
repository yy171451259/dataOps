package com.dataops.dms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataops.dms.entity.DatabaseInstance;
import com.dataops.dms.mapper.DatabaseInstanceMapper;
import com.dataops.dms.service.DatabaseInstanceService;
import com.dataops.dms.sql.SqlDialect;
import com.dataops.dms.sql.SqlExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 数据库实例服务实现 — 支持 MySQL / Oracle / PostgreSQL
 */
@Slf4j
@Service
public class DatabaseInstanceServiceImpl
    extends ServiceImpl<DatabaseInstanceMapper, DatabaseInstance>
    implements DatabaseInstanceService {

    @Resource
    private SqlExecutor sqlExecutor;

    @Override
    public boolean testConnection(DatabaseInstance db) {
        return sqlExecutor.testConnection(db);
    }

    @Override
    public boolean testConnectionById(String id) {
        DatabaseInstance db = this.getById(id);
        if (db == null) return false;
        boolean success = testConnection(db);
        db.setLastHeartbeat(LocalDateTime.now());
        db.setStatus(success ? "active" : "error");
        this.updateById(db);
        return success;
    }

    @Override
    public List<DatabaseInstance> getActiveInstances() {
        LambdaQueryWrapper<DatabaseInstance> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DatabaseInstance::getStatus, "active");
        wrapper.orderByDesc(DatabaseInstance::getCreateTime);
        return this.list(wrapper);
    }

    @Override
    public boolean saveAndTest(DatabaseInstance db) {
        boolean success = testConnection(db);
        if (success) {
            db.setStatus("active");
            db.setLastHeartbeat(LocalDateTime.now());
        } else {
            db.setStatus("error");
        }
        db.setCreateTime(LocalDateTime.now());
        return this.save(db);
    }

    // ==================== 表名 ====================

    @Override
    public List<String> getTableNames(String id) throws Exception {
        return getTableNames(id, null);
    }

    @Override
    public List<String> getTableNames(String id, String databaseName) throws Exception {
        DatabaseInstance db = this.getById(id);
        if (db == null) throw new RuntimeException("数据库实例不存在");
        String schema = resolveSchema(db, databaseName);
        String dbType = SqlDialect.normalizeType(db.getDbType());

        Connection conn = null; Statement stmt = null; ResultSet rs = null;
        try {
            conn = getConnection(db, databaseName);
            stmt = conn.createStatement();
            rs = stmt.executeQuery(SqlDialect.listTablesAndViewsSql(dbType, schema));
            List<String> tables = new ArrayList<>();
            while (rs.next()) {
                String rawType = rs.getString(2);
                if ("TABLE".equals(SqlDialect.mapObjectType(dbType, rawType))) {
                    tables.add(rs.getString(1));
                }
            }
            return tables;
        } finally { closeQuietly(rs, stmt, conn); }
    }

    // ==================== 建表语句 ====================

    @Override
    public String getCreateTableSql(String id, String tableName) throws Exception {
        return getCreateTableSql(id, null, tableName);
    }

    @Override
    public String getCreateTableSql(String id, String databaseName, String tableName) throws Exception {
        DatabaseInstance db = this.getById(id);
        if (db == null) throw new RuntimeException("数据库实例不存在");
        String dbType = SqlDialect.normalizeType(db.getDbType());

        Connection conn = null; Statement stmt = null; ResultSet rs = null;
        try {
            conn = getConnection(db, databaseName);
            stmt = conn.createStatement();
            rs = stmt.executeQuery(SqlDialect.getCreateTableSql(dbType, tableName));
            if (rs.next()) {
                return SqlDialect.extractCreateTableDdl(dbType, rs);
            }
            throw new RuntimeException("未找到表: " + tableName);
        } finally { closeQuietly(rs, stmt, conn); }
    }

    @Override
    public Map<String, String> batchGetCreateTableSql(String id, List<String> tableNames) throws Exception {
        return batchGetCreateTableSql(id, null, tableNames);
    }

    @Override
    public Map<String, String> batchGetCreateTableSql(String id, String databaseName, List<String> tableNames) throws Exception {
        Map<String, String> result = new LinkedHashMap<>();
        for (String tableName : tableNames) {
            try {
                result.put(tableName, getCreateTableSql(id, databaseName, tableName));
            } catch (Exception e) {
                log.warn("获取表 {} 建表语句失败: {}", tableName, e.getMessage());
            }
        }
        return result;
    }

    // ==================== Schema 补全 ====================

    @Override
    public List<Map<String, Object>> getSchemaForCompletion(String id) throws Exception {
        return getSchemaForCompletion(id, null);
    }

    @Override
    public List<Map<String, Object>> getSchemaForCompletion(String id, String databaseName) throws Exception {
        DatabaseInstance db = this.getById(id);
        if (db == null) throw new RuntimeException("数据库实例不存在");
        String schema = resolveSchema(db, databaseName);
        String dbType = SqlDialect.normalizeType(db.getDbType());

        Connection conn = null;
        try {
            conn = getConnection(db, databaseName);
            return fetchTablesAndViews(conn, dbType, schema);
        } finally { closeQuietly(conn); }
    }

    // ==================== 数据库列表 ====================

    @Override
    public List<String> getSchemaNames(String id) throws Exception {
        DatabaseInstance db = this.getById(id);
        if (db == null) throw new RuntimeException("数据库实例不存在");
        String dbType = SqlDialect.normalizeType(db.getDbType());

        Connection conn = null; Statement stmt = null; ResultSet rs = null;
        try {
            conn = getConnectionNoDb(db);
            stmt = conn.createStatement();
            rs = stmt.executeQuery(SqlDialect.listSchemasSql(dbType));
            List<String> schemas = new ArrayList<>();
            while (rs.next()) {
                String name = rs.getString(1);
                if (!SqlDialect.isSystemSchema(dbType, name)) {
                    schemas.add(name);
                }
            }
            return schemas;
        } finally { closeQuietly(rs, stmt, conn); }
    }

    // ==================== 综合浏览器 Schema（新增） ====================

    @Override
    public Map<String, Object> getBrowserSchema(String id, String databaseName) throws Exception {
        DatabaseInstance db = this.getById(id);
        if (db == null) throw new RuntimeException("数据库实例不存在");
        String schema = resolveSchema(db, databaseName);
        String dbType = SqlDialect.normalizeType(db.getDbType());

        Map<String, Object> result = new LinkedHashMap<>();

        Connection conn = null;
        try {
            conn = getConnection(db, databaseName);

            // 表 + 视图（含列信息）
            List<Map<String, Object>> allItems = fetchTablesAndViews(conn, dbType, schema);
            List<Map<String, Object>> tables = new ArrayList<>();
            List<Map<String, Object>> views = new ArrayList<>();
            for (Map<String, Object> item : allItems) {
                if ("VIEW".equals(item.get("type"))) views.add(item);
                else tables.add(item);
            }
            result.put("tables", tables);
            result.put("views", views);

            // 存储过程
            result.put("procedures", fetchNameList(conn, SqlDialect.listProceduresSql(dbType, schema)));

            // 函数
            result.put("functions", fetchNameList(conn, SqlDialect.listFunctionsSql(dbType, schema)));

            // 触发器
            result.put("triggers", fetchNameList(conn, SqlDialect.listTriggersSql(dbType, schema)));

            // 事件（部分数据库不支持）
            String eventSql = SqlDialect.listEventsSql(dbType, schema);
            result.put("events", eventSql != null ? fetchNameList(conn, eventSql) : Collections.emptyList());

        } finally { closeQuietly(conn); }
        return result;
    }

    // ==================== 表详细信息 ====================

    @Override
    public Map<String, Object> getTableDetail(String id, String databaseName, String tableName) throws Exception {
        DatabaseInstance db = this.getById(id);
        if (db == null) throw new RuntimeException("数据库实例不存在");
        String dbType = SqlDialect.normalizeType(db.getDbType());
        String schema = resolveSchema(db, databaseName);

        Map<String, Object> result = new LinkedHashMap<>();
        Connection conn = null;
        try {
            conn = getConnection(db, databaseName);

            // 索引
            Map<String, Map<String, Object>> mergedIndexes = new LinkedHashMap<>();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(SqlDialect.listIndexesSql(dbType, schema, tableName))) {
                while (rs.next()) {
                    Map<String, String> raw = SqlDialect.parseIndex(dbType, rs);
                    String name = raw.get("name");
                    if (name == null) continue;
                    if (!mergedIndexes.containsKey(name)) {
                        Map<String, Object> idx = new LinkedHashMap<>();
                        idx.put("name", name);
                        idx.put("unique", raw.get("unique"));
                        idx.put("type", raw.getOrDefault("type", ""));
                        idx.put("columns", new ArrayList<String>());
                        mergedIndexes.put(name, idx);
                    }
                    @SuppressWarnings("unchecked")
                    List<String> cols = (List<String>) mergedIndexes.get(name).get("columns");
                    String col = raw.get("column");
                    if (col != null) cols.add(col);
                }
            } catch (Exception e) { /* 部分数据库不支持 */ }
            result.put("indexes", new ArrayList<>(mergedIndexes.values()));

            // 外键
            List<Map<String, Object>> foreignKeys = new ArrayList<>();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(SqlDialect.listForeignKeysSql(dbType, schema, tableName))) {
                ResultSetMetaData meta = rs.getMetaData();
                while (rs.next()) {
                    Map<String, Object> fk = new LinkedHashMap<>();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        fk.put(meta.getColumnName(i).toLowerCase(), rs.getObject(i));
                    }
                    foreignKeys.add(fk);
                }
            } catch (Exception e) { /* 部分数据库不支持 */ }
            result.put("foreignKeys", foreignKeys);

            // 约束
            List<Map<String, Object>> constraints = new ArrayList<>();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(SqlDialect.listConstraintsSql(dbType, schema, tableName))) {
                while (rs.next()) {
                    Map<String, Object> c = new LinkedHashMap<>();
                    String cName = rs.getString(1);
                    String cType = rs.getString(2);
                    c.put("name", cName);
                    c.put("type", SqlDialect.mapConstraintType(dbType, cType));
                    constraints.add(c);
                }
            } catch (Exception e) { /* 部分数据库不支持 */ }
            result.put("constraints", constraints);

            // 触发器（表级）
            List<String> triggers = new ArrayList<>();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(SqlDialect.listTableTriggersSql(dbType, schema, tableName))) {
                while (rs.next()) {
                    triggers.add(rs.getString(1));
                }
            } catch (Exception e) { /* 部分数据库不支持 */ }
            result.put("triggers", triggers);

            // DDL
            String ddl = null;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(SqlDialect.getCreateTableSql(dbType, tableName))) {
                if (rs.next()) {
                    ddl = SqlDialect.extractCreateTableDdl(dbType, rs);
                }
            } catch (Exception e) { /* 部分数据库不支持 */ }
            result.put("ddl", ddl);

        } finally { closeQuietly(conn); }
        return result;
    }

    // ==================== JDBC 连接 ====================

    @Override
    public Connection getConnection(DatabaseInstance db) throws Exception {
        return getConnection(db, (String) null);
    }

    private Connection getConnection(DatabaseInstance db, String databaseName) throws Exception {
        String url = SqlDialect.buildJdbcUrl(db, databaseName);
        Properties props = new Properties();
        props.put("user", db.getUsername());
        props.put("password", db.getPassword());
        props.put("connectTimeout", "5000");
        return DriverManager.getConnection(url, props);
    }

    private Connection getConnectionNoDb(DatabaseInstance db) throws Exception {
        String url = SqlDialect.buildJdbcUrlNoDb(db);
        Properties props = new Properties();
        props.put("user", db.getUsername());
        props.put("password", db.getPassword());
        props.put("connectTimeout", "5000");
        return DriverManager.getConnection(url, props);
    }

    // ==================== 内部工具方法 ====================

    /** 解析 schema 名：优先用传入的 databaseName，其次用实例默认库 */
    private String resolveSchema(DatabaseInstance db, String databaseName) {
        String dbType = SqlDialect.normalizeType(db.getDbType());
        String schema = (databaseName != null && !databaseName.isEmpty()) ? databaseName : db.getDefaultSchemaName();
        // Oracle 用大写 schema 名
        if ("oracle".equals(dbType) && schema != null) {
            schema = schema.toUpperCase();
        }
        return schema;
    }

    /** 获取表+视图列表（含列信息） */
    private List<Map<String, Object>> fetchTablesAndViews(Connection conn, String dbType, String schema) throws Exception {
        List<Map<String, Object>> result = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(SqlDialect.listTablesAndViewsSql(dbType, schema))) {
            List<Map.Entry<String, String>> items = new ArrayList<>();
            while (rs.next()) {
                String name = rs.getString(1);
                String rawType = rs.getString(2);
                items.add(new AbstractMap.SimpleEntry<>(name, SqlDialect.mapObjectType(dbType, rawType)));
            }

            for (Map.Entry<String, String> entry : items) {
                String tableName = entry.getKey();
                String tableType = entry.getValue();
                Map<String, Object> tableInfo = new LinkedHashMap<>();
                tableInfo.put("name", tableName);
                tableInfo.put("type", tableType);
                List<Map<String, String>> columns = new ArrayList<>();

                try (Statement colStmt = conn.createStatement();
                     ResultSet colRs = colStmt.executeQuery(SqlDialect.listColumnsSql(dbType, schema, tableName))) {
                    while (colRs.next()) {
                        columns.add(SqlDialect.parseColumn(dbType, colRs));
                    }
                }
                tableInfo.put("columns", columns);
                result.add(tableInfo);
            }
        }
        return result;
    }

    /** 执行单列查询，返回名称列表 */
    private List<String> fetchNameList(Connection conn, String sql) throws Exception {
        List<String> names = new ArrayList<>();
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                names.add(rs.getString(1));
            }
        }
        return names;
    }

    /** 静默关闭资源 */
    private void closeQuietly(AutoCloseable... resources) {
        for (AutoCloseable r : resources) {
            if (r != null) {
                try { r.close(); } catch (Exception ignored) {}
            }
        }
    }
}
