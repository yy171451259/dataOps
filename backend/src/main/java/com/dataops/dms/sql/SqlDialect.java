package com.dataops.dms.sql;

import com.dataops.dms.entity.DatabaseInstance;

import java.sql.*;
import java.util.*;

/**
 * SQL 方言工具 — 根据 dbType 返回正确的 JDBC URL 和查询语句
 * 支持 MySQL / Oracle / PostgreSQL
 */
public class SqlDialect {

    // ===================== JDBC URL =====================

    public static String buildJdbcUrl(DatabaseInstance db, String databaseName) {
        String type = normalizeType(db.getDbType());
        String host = db.getHost();
        int port = defaultPort(type, db.getPort());
        String dbName = (databaseName != null && !databaseName.isEmpty()) ? databaseName : db.getDefaultSchemaName();

        switch (type) {
            case "postgresql":
                return String.format("jdbc:postgresql://%s:%d/%s", host, port, dbName != null ? dbName : "");
            case "oracle":
                // Oracle URL: jdbc:oracle:thin:@host:port:serviceName
                return String.format("jdbc:oracle:thin:@%s:%d:%s", host, port, dbName != null ? dbName : "ORCL");
            default: // mysql
                return String.format(
                    "jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowMultiQueries=true&zeroDateTimeBehavior=convertToNull",
                    host, port, dbName != null ? dbName : "");
        }
    }

    /** 无数据库名的连接 URL（用于获取数据库列表） */
    public static String buildJdbcUrlNoDb(DatabaseInstance db) {
        String type = normalizeType(db.getDbType());
        String host = db.getHost();
        int port = defaultPort(type, db.getPort());
        switch (type) {
            case "postgresql":
                return String.format("jdbc:postgresql://%s:%d/postgres", host, port);
            case "oracle":
                return String.format("jdbc:oracle:thin:@%s:%d:%s", host, port,
                    db.getDefaultSchemaName() != null ? db.getDefaultSchemaName() : "ORCL");
            default:
                return String.format(
                    "jdbc:mysql://%s:%d?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&zeroDateTimeBehavior=convertToNull",
                    host, port);
        }
    }

    public static int defaultPort(String type) {
        switch (normalizeType(type)) {
            case "postgresql": return 5432;
            case "oracle": return 1521;
            default: return 3306;
        }
    }

    public static String normalizeType(String dbType) {
        if (dbType == null) return "mysql";
        return dbType.toLowerCase().replaceAll("[^a-z]", "");
    }

    // ===================== Schema 列表 =====================

    public static boolean isSystemSchema(String type, String name) {
        if (name == null) return true;
        String t = normalizeType(type);
        String n = name.toLowerCase();
        switch (t) {
            case "oracle":
                return n.startsWith("sys") || n.startsWith("system") || "xs$null".equals(n)
                    || "outln".equals(n) || "ordsys".equals(n) || "ordschema".equals(n)
                    || "ctxsys".equals(n) || "dbsnmp".equals(n) || "xdb".equals(n)
                    || "anonymous".equals(n) || "mdsys".equals(n) || "wmsys".equals(n);
            case "postgresql":
                return "template0".equals(n) || "template1".equals(n)
                    || n.startsWith("pg_");
            default: // mysql
                return "information_schema".equals(n) || "performance_schema".equals(n)
                    || "mysql".equals(n) || "sys".equals(n) || "innodb".equals(n);
        }
    }

    /** SHOW DATABASES / SELECT username FROM all_users / SELECT datname FROM pg_database */
    public static String listSchemasSql(String type) {
        switch (normalizeType(type)) {
            case "oracle":
                return "SELECT USERNAME FROM ALL_USERS ORDER BY USERNAME";
            case "postgresql":
                return "SELECT datname FROM pg_database WHERE datistemplate = false ORDER BY datname";
            default:
                return "SHOW DATABASES";
        }
    }

    // ===================== 表/视图列表 =====================

    /** 获取表+视图名和类型 */
    public static String listTablesAndViewsSql(String type, String schema) {
        switch (normalizeType(type)) {
            case "oracle":
                return "SELECT OBJECT_NAME, OBJECT_TYPE FROM ALL_OBJECTS WHERE OWNER='"
                    + schema.toUpperCase() + "' AND OBJECT_TYPE IN ('TABLE','VIEW') ORDER BY OBJECT_TYPE, OBJECT_NAME";
            case "postgresql":
                return "SELECT table_name, table_type FROM information_schema.tables WHERE table_schema='"
                    + schema + "' ORDER BY table_type, table_name";
            default:
                return "SELECT TABLE_NAME, TABLE_TYPE FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='"
                    + schema + "' ORDER BY TABLE_TYPE, TABLE_NAME";
        }
    }

    /** 获取列信息 */
    public static String listColumnsSql(String type, String schema, String tableName) {
        switch (normalizeType(type)) {
            case "oracle":
                return "SELECT COLUMN_NAME, DATA_TYPE, NULLABLE, DATA_DEFAULT FROM ALL_TAB_COLUMNS WHERE OWNER='"
                    + schema.toUpperCase() + "' AND TABLE_NAME='" + tableName.toUpperCase()
                    + "' ORDER BY COLUMN_ID";
            case "postgresql":
                return "SELECT column_name, data_type, is_nullable, column_default FROM information_schema.columns "
                    + "WHERE table_schema='" + schema + "' AND table_name='" + tableName
                    + "' ORDER BY ordinal_position";
            default:
                return "SELECT COLUMN_NAME, DATA_TYPE, COLUMN_KEY, IS_NULLABLE, COLUMN_DEFAULT, COLUMN_COMMENT "
                    + "FROM INFORMATION_SCHEMA.COLUMNS "
                    + "WHERE TABLE_SCHEMA='" + schema + "' AND TABLE_NAME='" + tableName
                    + "' ORDER BY ORDINAL_POSITION";
        }
    }

    // ===================== 存储过程/函数 =====================

    public static String listProceduresSql(String type, String schema) {
        switch (normalizeType(type)) {
            case "oracle":
                return "SELECT DISTINCT OBJECT_NAME FROM ALL_PROCEDURES WHERE OWNER='"
                    + schema.toUpperCase() + "' AND OBJECT_TYPE='PROCEDURE' ORDER BY OBJECT_NAME";
            case "postgresql":
                return "SELECT proname FROM pg_proc p JOIN pg_namespace n ON p.pronamespace = n.oid "
                    + "WHERE n.nspname='" + schema + "' AND p.prokind='p' ORDER BY proname";
            default:
                return "SELECT ROUTINE_NAME FROM INFORMATION_SCHEMA.ROUTINES WHERE ROUTINE_SCHEMA='"
                    + schema + "' AND ROUTINE_TYPE='PROCEDURE' ORDER BY ROUTINE_NAME";
        }
    }

    public static String listFunctionsSql(String type, String schema) {
        switch (normalizeType(type)) {
            case "oracle":
                return "SELECT DISTINCT OBJECT_NAME FROM ALL_PROCEDURES WHERE OWNER='"
                    + schema.toUpperCase() + "' AND OBJECT_TYPE='FUNCTION' ORDER BY OBJECT_NAME";
            case "postgresql":
                return "SELECT proname FROM pg_proc p JOIN pg_namespace n ON p.pronamespace = n.oid "
                    + "WHERE n.nspname='" + schema + "' AND p.prokind='f' ORDER BY proname";
            default:
                return "SELECT ROUTINE_NAME FROM INFORMATION_SCHEMA.ROUTINES WHERE ROUTINE_SCHEMA='"
                    + schema + "' AND ROUTINE_TYPE='FUNCTION' ORDER BY ROUTINE_NAME";
        }
    }

    // ===================== 触发器 =====================

    public static String listTriggersSql(String type, String schema) {
        switch (normalizeType(type)) {
            case "oracle":
                return "SELECT TRIGGER_NAME FROM ALL_TRIGGERS WHERE OWNER='"
                    + schema.toUpperCase() + "' ORDER BY TRIGGER_NAME";
            case "postgresql":
                return "SELECT trigger_name FROM information_schema.triggers WHERE trigger_schema='"
                    + schema + "' ORDER BY trigger_name";
            default:
                return "SELECT TRIGGER_NAME FROM INFORMATION_SCHEMA.TRIGGERS WHERE TRIGGER_SCHEMA='"
                    + schema + "' ORDER BY TRIGGER_NAME";
        }
    }

    // ===================== 事件（仅 MySQL） =====================

    public static String listEventsSql(String type, String schema) {
        String t = normalizeType(type);
        if ("mysql".equals(t)) {
            return "SHOW EVENTS FROM `" + schema + "`";
        }
        // Oracle/PostgreSQL 没有直接等价物（Oracle: DBMS_SCHEDULER, PG: pg_cron）
        return null;
    }

    // ===================== 表级索引 =====================

    public static String listIndexesSql(String type, String schema, String tableName) {
        switch (normalizeType(type)) {
            case "oracle":
                return "SELECT INDEX_NAME, COLUMN_NAME, UNIQUENESS FROM ALL_IND_COLUMNS ic "
                    + "JOIN ALL_INDEXES i ON ic.INDEX_NAME = i.INDEX_NAME AND ic.INDEX_OWNER = i.OWNER "
                    + "WHERE ic.INDEX_OWNER='" + schema.toUpperCase() + "' AND ic.TABLE_NAME='" + tableName.toUpperCase()
                    + "' ORDER BY ic.INDEX_NAME, ic.COLUMN_POSITION";
            case "postgresql":
                return "SELECT indexname, indexdef FROM pg_indexes "
                    + "WHERE schemaname='" + schema + "' AND tablename='" + tableName + "' ORDER BY indexname";
            default:
                return "SELECT INDEX_NAME, COLUMN_NAME, NON_UNIQUE, INDEX_TYPE FROM INFORMATION_SCHEMA.STATISTICS "
                    + "WHERE TABLE_SCHEMA='" + schema + "' AND TABLE_NAME='" + tableName
                    + "' ORDER BY INDEX_NAME, SEQ_IN_INDEX";
        }
    }

    /** 解析索引行 */
    public static Map<String, String> parseIndex(String dbType, ResultSet rs) throws SQLException {
        Map<String, String> idx = new LinkedHashMap<>();
        switch (normalizeType(dbType)) {
            case "oracle":
                idx.put("name", rs.getString("INDEX_NAME"));
                idx.put("column", rs.getString("COLUMN_NAME"));
                idx.put("unique", "UNIQUE".equalsIgnoreCase(rs.getString("UNIQUENESS")) ? "Y" : "N");
                break;
            case "postgresql":
                idx.put("name", rs.getString("indexname"));
                idx.put("definition", rs.getString("indexdef"));
                break;
            default:
                idx.put("name", rs.getString("INDEX_NAME"));
                idx.put("column", rs.getString("COLUMN_NAME"));
                idx.put("unique", rs.getInt("NON_UNIQUE") == 0 ? "Y" : "N");
                try { idx.put("type", rs.getString("INDEX_TYPE")); } catch (SQLException e) { idx.put("type", ""); }
                break;
        }
        return idx;
    }

    // ===================== 外键 =====================

    public static String listForeignKeysSql(String type, String schema, String tableName) {
        switch (normalizeType(type)) {
            case "oracle":
                return "SELECT a.CONSTRAINT_NAME, a.COLUMN_NAME, "
                    + "c_pk.TABLE_NAME AS REFERENCED_TABLE, b.COLUMN_NAME AS REFERENCED_COLUMN "
                    + "FROM ALL_CONS_COLUMNS a "
                    + "JOIN ALL_CONSTRAINTS c ON a.CONSTRAINT_NAME = c.CONSTRAINT_NAME AND a.OWNER = c.OWNER "
                    + "JOIN ALL_CONSTRAINTS c_pk ON c.R_CONSTRAINT_NAME = c_pk.CONSTRAINT_NAME AND c.R_OWNER = c_pk.OWNER "
                    + "JOIN ALL_CONS_COLUMNS b ON c_pk.CONSTRAINT_NAME = b.CONSTRAINT_NAME AND c_pk.OWNER = b.OWNER "
                    + "WHERE a.OWNER='" + schema.toUpperCase() + "' AND c.TABLE_NAME='" + tableName.toUpperCase()
                    + "' AND c.CONSTRAINT_TYPE='R'";
            case "postgresql":
                return "SELECT tc.constraint_name, kcu.column_name, "
                    + "ccu.table_schema AS referenced_schema, ccu.table_name AS referenced_table, ccu.column_name AS referenced_column "
                    + "FROM information_schema.table_constraints tc "
                    + "JOIN information_schema.key_column_usage kcu ON tc.constraint_name = kcu.constraint_name "
                    + "JOIN information_schema.constraint_column_usage ccu ON tc.constraint_name = ccu.constraint_name "
                    + "WHERE tc.constraint_type = 'FOREIGN KEY' AND tc.table_schema='" + schema + "' AND tc.table_name='" + tableName + "'";
            default:
                return "SELECT CONSTRAINT_NAME, COLUMN_NAME, "
                    + "REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME "
                    + "FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE "
                    + "WHERE TABLE_SCHEMA='" + schema + "' AND TABLE_NAME='" + tableName
                    + "' AND REFERENCED_TABLE_NAME IS NOT NULL";
        }
    }

    // ===================== 约束 =====================

    public static String listConstraintsSql(String type, String schema, String tableName) {
        switch (normalizeType(type)) {
            case "oracle":
                return "SELECT CONSTRAINT_NAME, CONSTRAINT_TYPE FROM ALL_CONSTRAINTS "
                    + "WHERE OWNER='" + schema.toUpperCase() + "' AND TABLE_NAME='" + tableName.toUpperCase() + "'";
            case "postgresql":
                return "SELECT constraint_name, constraint_type FROM information_schema.table_constraints "
                    + "WHERE table_schema='" + schema + "' AND table_name='" + tableName + "'";
            default:
                return "SELECT CONSTRAINT_NAME, CONSTRAINT_TYPE FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS "
                    + "WHERE TABLE_SCHEMA='" + schema + "' AND TABLE_NAME='" + tableName + "'";
        }
    }

    /** 约束类型映射为可读名称 */
    public static String mapConstraintType(String dbType, String type) {
        if (type == null) return "UNKNOWN";
        if ("oracle".equals(normalizeType(dbType))) {
            switch (type.toUpperCase()) {
                case "P": return "PRIMARY KEY";
                case "R": return "FOREIGN KEY";
                case "U": return "UNIQUE";
                case "C": return "CHECK";
                default: return type;
            }
        } else {
            switch (type.toUpperCase()) {
                case "PRIMARY KEY": return "PRIMARY KEY";
                case "FOREIGN KEY": return "FOREIGN KEY";
                case "UNIQUE": return "UNIQUE";
                default: return type;
            }
        }
    }

    // ===================== 表级触发器 =====================

    public static String listTableTriggersSql(String type, String schema, String tableName) {
        switch (normalizeType(type)) {
            case "oracle":
                return "SELECT TRIGGER_NAME FROM ALL_TRIGGERS WHERE OWNER='"
                    + schema.toUpperCase() + "' AND TABLE_NAME='" + tableName.toUpperCase()
                    + "' ORDER BY TRIGGER_NAME";
            case "postgresql":
                return "SELECT trigger_name FROM information_schema.triggers "
                    + "WHERE trigger_schema='" + schema + "' AND event_object_table='" + tableName
                    + "' ORDER BY trigger_name";
            default:
                return "SELECT TRIGGER_NAME FROM INFORMATION_SCHEMA.TRIGGERS "
                    + "WHERE TRIGGER_SCHEMA='" + schema + "' AND EVENT_OBJECT_TABLE='" + tableName
                    + "' ORDER BY TRIGGER_NAME";
        }
    }

    // ===================== 建表语句 =====================

    public static String getCreateTableSql(String type, String tableName) {
        switch (normalizeType(type)) {
            case "oracle":
                return "SELECT DBMS_METADATA.GET_DDL('TABLE','" + tableName.toUpperCase() + "') FROM DUAL";
            case "postgresql":
                // PostgreSQL 没有直接的单行 DDL 函数，这里返回占位符
                return "SELECT '-- DDL not available via SQL for PostgreSQL'";
            default:
                return "SHOW CREATE TABLE `" + tableName + "`";
        }
    }

    public static String extractCreateTableDdl(String type, ResultSet rs) throws SQLException {
        switch (normalizeType(type)) {
            case "oracle":
                // DBMS_METADATA.GET_DDL returns CLOB
                Object val = rs.getObject(1);
                if (val instanceof java.sql.Clob) {
                    java.sql.Clob clob = (java.sql.Clob) val;
                    return clob.getSubString(1, (int) clob.length());
                }
                return val != null ? val.toString() : "";
            case "postgresql":
                return rs.getString(1);
            default:
                return rs.getString(2); // SHOW CREATE TABLE returns 2 columns
        }
    }

    /** 将数据库类型信息映射为 OBJECT_TYPE 值 */
    public static String mapObjectType(String dbType, String rawType) {
        String t = normalizeType(dbType);
        if ("oracle".equals(t)) {
            if ("TABLE".equalsIgnoreCase(rawType)) return "TABLE";
            if ("VIEW".equalsIgnoreCase(rawType)) return "VIEW";
            return rawType;
        }
        // MySQL: TABLE_TYPE = 'BASE TABLE' or 'VIEW'
        if ("BASE TABLE".equalsIgnoreCase(rawType)) return "TABLE";
        if ("VIEW".equalsIgnoreCase(rawType)) return "VIEW";
        if ("TABLE".equalsIgnoreCase(rawType)) return "TABLE";
        return rawType;
    }

    /** 列信息解析为 Map */
    public static Map<String, String> parseColumn(String dbType, ResultSet rs) throws SQLException {
        String t = normalizeType(dbType);
        Map<String, String> col = new LinkedHashMap<>();
        col.put("name", rs.getString("COLUMN_NAME"));
        col.put("type", rs.getString("DATA_TYPE"));
        if ("oracle".equals(t)) {
            col.put("key", "");
            try { col.put("default", rs.getString("DATA_DEFAULT")); } catch (SQLException e) { col.put("default", ""); }
        } else {
            try { col.put("key", rs.getString("COLUMN_KEY")); } catch (SQLException e) { col.put("key", ""); }
            try { col.put("default", rs.getString("COLUMN_DEFAULT")); } catch (SQLException e) { col.put("default", ""); }
        }
        try { col.put("comment", rs.getString("COLUMN_COMMENT")); } catch (SQLException e) {
            try { col.put("comment", rs.getString("REMARKS")); } catch (SQLException e2) { col.put("comment", ""); }
        }
        return col;
    }

    public static int defaultPort(String type, Integer configuredPort) {
        return (configuredPort != null && configuredPort > 0) ? configuredPort : defaultPort(type);
    }
}
