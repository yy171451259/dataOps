package com.dataops.dms.controller;

import com.dataops.dms.common.result.Result;
import com.dataops.dms.entity.DatabaseInstance;
import com.dataops.dms.service.DatabaseInstanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.sql.*;
import java.util.*;

/**
 * 数据库性能监控诊断控制器
 * 实时捕获慢SQL、连接数、QPS、锁等待、资源使用率
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/db-monitor")
@Tag(name = "数据库性能监控")
public class DatabaseMonitorController {

    @Resource
    private DatabaseInstanceService databaseInstanceService;

    /**
     * 数据库实时状态概览（连接数、QPS、线程等）
     */
    @GetMapping("/{instanceId}/status")
    @Operation(summary = "数据库实时状态概览")
    public Result<Map<String, Object>> getDatabaseStatus(
            @PathVariable String instanceId,
            @RequestParam(required = false) String schemaName) {
        DatabaseInstance db = databaseInstanceService.getById(instanceId);
        if (db == null) return Result.error(400, "数据库实例不存在");

        Map<String, Object> status = new LinkedHashMap<>();
        Connection conn = null;
        try {
            conn = getConnection(db, schemaName);

            // 全局状态变量
            Map<String, String> globalStatus = queryGlobalStatus(conn);

            // 连接数
            status.put("maxConnections", parseInt(globalStatus.get("max_connections")));
            status.put("currentConnections", parseInt(globalStatus.get("Threads_connected")));
            status.put("runningThreads", parseInt(globalStatus.get("Threads_running")));
            status.put("abortedConnects", parseInt(globalStatus.get("Aborted_connects")));

            // QPS/TPS
            status.put("questions", parseLong(globalStatus.get("Questions")));
            status.put("queries", parseLong(globalStatus.get("Queries")));
            status.put("comSelect", parseLong(globalStatus.get("Com_select")));
            status.put("comInsert", parseLong(globalStatus.get("Com_insert")));
            status.put("comUpdate", parseLong(globalStatus.get("Com_update")));
            status.put("comDelete", parseLong(globalStatus.get("Com_delete")));
            status.put("uptime", parseLong(globalStatus.get("Uptime")));

            // 计算平均QPS
            long uptime = parseLong(globalStatus.get("Uptime"));
            long questions = parseLong(globalStatus.get("Questions"));
            status.put("avgQps", uptime > 0 ? String.format("%.2f", (double) questions / uptime) : "0");

            // InnoDB状态
            status.put("innodbRowsRead", parseLong(globalStatus.get("Innodb_rows_read")));
            status.put("innodbRowsInserted", parseLong(globalStatus.get("Innodb_rows_inserted")));
            status.put("innodbRowsUpdated", parseLong(globalStatus.get("Innodb_rows_updated")));
            status.put("innodbRowsDeleted", parseLong(globalStatus.get("Innodb_rows_deleted")));
            status.put("innodbBufferPoolReadRequests", parseLong(globalStatus.get("Innodb_buffer_pool_read_requests")));
            status.put("innodbBufferPoolReads", parseLong(globalStatus.get("Innodb_buffer_pool_reads")));

            // 计算缓冲命中率
            long readReqs = parseLong(globalStatus.get("Innodb_buffer_pool_read_requests"));
            long reads = parseLong(globalStatus.get("Innodb_buffer_pool_reads"));
            if (readReqs > 0) {
                status.put("bufferHitRate", String.format("%.4f", (1 - (double) reads / readReqs) * 100) + "%");
            } else {
                status.put("bufferHitRate", "N/A");
            }

            // 慢查询统计
            status.put("slowQueries", parseLong(globalStatus.get("Slow_queries")));
            status.put("longQueryTime", queryVariable(conn, "long_query_time"));

            status.put("status", "healthy");
        } catch (Exception e) {
            log.error("获取数据库状态失败: {}", e.getMessage());
            status.put("status", "error");
            status.put("error", e.getMessage());
        } finally {
            closeConnection(conn);
        }
        return Result.success(status);
    }

    /**
     * 慢SQL列表（从 performance_schema 或 processlist 获取）
     */
    @GetMapping("/{instanceId}/slow-queries")
    @Operation(summary = "慢SQL列表")
    public Result<List<Map<String, Object>>> getSlowQueries(
            @PathVariable String instanceId,
            @RequestParam(required = false) String schemaName,
            @RequestParam(defaultValue = "20") int limit) {
        DatabaseInstance db = databaseInstanceService.getById(instanceId);
        if (db == null) return Result.error(400, "数据库实例不存在");

        List<Map<String, Object>> slowQueries = new ArrayList<>();
        Connection conn = null;
        try {
            conn = getConnection(db, schemaName);

            // 先尝试 performance_schema（MySQL 5.7+）
            try {
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                    "SELECT DIGEST_TEXT, COUNT_STAR, SUM_TIMER_WAIT/1000000000 AS total_time_ms, " +
                    "AVG_TIMER_WAIT/1000000000 AS avg_time_ms, SUM_ROWS_EXAMINED, SUM_ROWS_SENT, " +
                    "FIRST_SEEN, LAST_SEEN " +
                    "FROM performance_schema.events_statements_summary_by_digest " +
                    "WHERE DIGEST_TEXT IS NOT NULL " +
                    "ORDER BY SUM_TIMER_WAIT DESC LIMIT " + limit
                );
                while (rs.next()) {
                    Map<String, Object> query = new LinkedHashMap<>();
                    query.put("sql", rs.getString("DIGEST_TEXT"));
                    query.put("execCount", rs.getLong("COUNT_STAR"));
                    query.put("totalTimeMs", rs.getDouble("total_time_ms"));
                    query.put("avgTimeMs", rs.getDouble("avg_time_ms"));
                    query.put("rowsExamined", rs.getLong("SUM_ROWS_EXAMINED"));
                    query.put("rowsSent", rs.getLong("SUM_ROWS_SENT"));
                    query.put("firstSeen", rs.getString("FIRST_SEEN"));
                    query.put("lastSeen", rs.getString("LAST_SEEN"));
                    slowQueries.add(query);
                }
                rs.close();
                stmt.close();
            } catch (SQLException e) {
                // performance_schema 不可用，回退到 SHOW PROCESSLIST
                log.debug("performance_schema不可用，使用processlist: {}", e.getMessage());
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                    "SELECT ID, USER, HOST, DB, COMMAND, TIME, STATE, INFO " +
                    "FROM information_schema.PROCESSLIST " +
                    "WHERE COMMAND != 'Sleep' AND TIME > 1 " +
                    "ORDER BY TIME DESC LIMIT " + limit
                );
                while (rs.next()) {
                    Map<String, Object> query = new LinkedHashMap<>();
                    query.put("processId", rs.getLong("ID"));
                    query.put("user", rs.getString("USER"));
                    query.put("host", rs.getString("HOST"));
                    query.put("schema", rs.getString("DB"));
                    query.put("command", rs.getString("COMMAND"));
                    query.put("time", rs.getLong("TIME"));
                    query.put("state", rs.getString("STATE"));
                    query.put("sql", rs.getString("INFO"));
                    slowQueries.add(query);
                }
                rs.close();
                stmt.close();
            }
        } catch (Exception e) {
            log.error("获取慢SQL失败: {}", e.getMessage());
            return Result.error("获取慢SQL失败: " + e.getMessage());
        } finally {
            closeConnection(conn);
        }
        return Result.success(slowQueries);
    }

    /**
     * 锁等待与死锁检测
     */
    @GetMapping("/{instanceId}/locks")
    @Operation(summary = "锁等待与死锁检测")
    public Result<Map<String, Object>> getLockInfo(
            @PathVariable String instanceId,
            @RequestParam(required = false) String schemaName) {
        DatabaseInstance db = databaseInstanceService.getById(instanceId);
        if (db == null) return Result.error(400, "数据库实例不存在");

        Map<String, Object> lockInfo = new LinkedHashMap<>();
        Connection conn = null;
        try {
            conn = getConnection(db, schemaName);

            // 当前锁等待（MySQL 8.0+ 使用 performance_schema.data_locks）
            List<Map<String, Object>> lockWaits = new ArrayList<>();
            try {
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                    "SELECT r.trx_id waiting_trx_id, r.trx_mysql_thread_id waiting_thread, " +
                    "r.trx_query waiting_query, b.trx_id blocking_trx_id, " +
                    "b.trx_mysql_thread_id blocking_thread, b.trx_query blocking_query " +
                    "FROM information_schema.innodb_lock_waits w " +
                    "INNER JOIN information_schema.innodb_trx b ON b.trx_id = w.blocking_trx_id " +
                    "INNER JOIN information_schema.innodb_trx r ON r.trx_id = w.requesting_trx_id"
                );
                while (rs.next()) {
                    Map<String, Object> wait = new LinkedHashMap<>();
                    wait.put("waitingTrxId", rs.getString("waiting_trx_id"));
                    wait.put("waitingThread", rs.getLong("waiting_thread"));
                    wait.put("waitingQuery", rs.getString("waiting_query"));
                    wait.put("blockingTrxId", rs.getString("blocking_trx_id"));
                    wait.put("blockingThread", rs.getLong("blocking_thread"));
                    wait.put("blockingQuery", rs.getString("blocking_query"));
                    lockWaits.add(wait);
                }
                rs.close();
                stmt.close();
            } catch (SQLException e) {
                log.debug("获取锁等待信息失败: {}", e.getMessage());
            }

            lockInfo.put("lockWaits", lockWaits);
            lockInfo.put("lockWaitCount", lockWaits.size());

            // 长事务（运行超过10秒）
            List<Map<String, Object>> longTransactions = new ArrayList<>();
            try {
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                    "SELECT trx_id, trx_state, trx_started, trx_mysql_thread_id, " +
                    "TIMESTAMPDIFF(SECOND, trx_started, NOW()) as duration_sec, " +
                    "trx_rows_locked, trx_rows_modified, trx_query " +
                    "FROM information_schema.innodb_trx " +
                    "WHERE TIMESTAMPDIFF(SECOND, trx_started, NOW()) > 10 " +
                    "ORDER BY trx_started"
                );
                while (rs.next()) {
                    Map<String, Object> trx = new LinkedHashMap<>();
                    trx.put("trxId", rs.getString("trx_id"));
                    trx.put("state", rs.getString("trx_state"));
                    trx.put("startedAt", rs.getString("trx_started"));
                    trx.put("durationSec", rs.getLong("duration_sec"));
                    trx.put("rowsLocked", rs.getLong("trx_rows_locked"));
                    trx.put("rowsModified", rs.getLong("trx_rows_modified"));
                    trx.put("query", rs.getString("trx_query"));
                    longTransactions.add(trx);
                }
                rs.close();
                stmt.close();
            } catch (SQLException e) {
                log.debug("获取长事务信息失败: {}", e.getMessage());
            }

            lockInfo.put("longTransactions", longTransactions);
            lockInfo.put("longTransactionCount", longTransactions.size());

        } catch (Exception e) {
            log.error("获取锁信息失败: {}", e.getMessage());
            return Result.error("获取锁信息失败: " + e.getMessage());
        } finally {
            closeConnection(conn);
        }
        return Result.success(lockInfo);
    }

    /**
     * 表空间使用统计
     */
    @GetMapping("/{instanceId}/table-stats")
    @Operation(summary = "表空间使用统计")
    public Result<List<Map<String, Object>>> getTableStats(
            @PathVariable String instanceId,
            @RequestParam(required = false) String schemaName,
            @RequestParam(required = false) String searchKeyword,
            @RequestParam(required = false, defaultValue = "totalSizeMb") String sortBy,
            @RequestParam(required = false, defaultValue = "desc") String sortOrder) {
        DatabaseInstance db = databaseInstanceService.getById(instanceId);
        if (db == null) return Result.error(400, "数据库实例不存在");

        String dbName = schemaName != null && !schemaName.isEmpty() ? schemaName : db.getDefaultSchemaName();
        List<Map<String, Object>> stats = new ArrayList<>();
        Connection conn = null;
        try {
            conn = getConnection(db, schemaName);
            
            StringBuilder sql = new StringBuilder();
            sql.append("SELECT table_name, table_rows, ");
            sql.append("ROUND(data_length/1024/1024, 2) AS data_size_mb, ");
            sql.append("ROUND(index_length/1024/1024, 2) AS index_size_mb, ");
            sql.append("ROUND((data_length + index_length)/1024/1024, 2) AS total_size_mb, ");
            sql.append("auto_increment, table_comment, engine, table_collation ");
            sql.append("FROM information_schema.tables ");
            sql.append("WHERE table_schema = '" + dbName + "' ");
            
            if (searchKeyword != null && !searchKeyword.isEmpty()) {
                sql.append("AND (table_name LIKE '%" + searchKeyword + "%' OR table_comment LIKE '%" + searchKeyword + "%') ");
            }
            
            String orderColumn;
            switch (sortBy) {
                case "tableRows":
                    orderColumn = "table_rows";
                    break;
                case "dataSizeMb":
                    orderColumn = "data_size_mb";
                    break;
                case "indexSizeMb":
                    orderColumn = "index_size_mb";
                    break;
                case "totalSizeMb":
                default:
                    orderColumn = "total_size_mb";
                    break;
            }
            sql.append("ORDER BY " + orderColumn + " " + (sortOrder.equalsIgnoreCase("asc") ? "ASC" : "DESC"));
            sql.append(" LIMIT 200");

            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql.toString());
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("tableName", rs.getString("table_name"));
                row.put("tableRows", rs.getLong("table_rows"));
                row.put("dataSizeMb", rs.getDouble("data_size_mb"));
                row.put("indexSizeMb", rs.getDouble("index_size_mb"));
                row.put("totalSizeMb", rs.getDouble("total_size_mb"));
                row.put("autoIncrement", rs.getString("auto_increment"));
                row.put("comment", rs.getString("table_comment"));
                row.put("engine", rs.getString("engine"));
                row.put("collation", rs.getString("table_collation"));
                stats.add(row);
            }
            rs.close();
            stmt.close();
        } catch (Exception e) {
            log.error("获取表统计信息失败: {}", e.getMessage());
            return Result.error("获取表统计失败: " + e.getMessage());
        } finally {
            closeConnection(conn);
        }
        return Result.success(stats);
    }

    /**
     * 性能诊断报告（综合诊断）
     */
    @GetMapping("/{instanceId}/diagnosis")
    @Operation(summary = "数据库性能诊断报告")
    public Result<Map<String, Object>> diagnose(
            @PathVariable String instanceId,
            @RequestParam(required = false) String schemaName) {
        DatabaseInstance db = databaseInstanceService.getById(instanceId);
        if (db == null) return Result.error(400, "数据库实例不存在");

        Map<String, Object> report = new LinkedHashMap<>();
        Connection conn = null;
        try {
            conn = getConnection(db, schemaName);
            Map<String, String> globalStatus = queryGlobalStatus(conn);
            Map<String, String> globalVars = queryGlobalVariables(conn);

            List<Map<String, Object>> issues = new ArrayList<>();
            int healthScore = 100;

            // 检查1: 连接数使用率
            int maxConn = parseInt(globalVars.get("max_connections"));
            int currentConn = parseInt(globalStatus.get("Threads_connected"));
            if (maxConn > 0) {
                double connUsage = (double) currentConn / maxConn * 100;
                if (connUsage > 80) {
                    issues.add(buildIssue("critical", "连接数使用率过高", String.format("当前%d/%d（%.1f%%），建议增大max_connections或优化连接池", currentConn, maxConn, connUsage)));
                    healthScore -= 20;
                } else if (connUsage > 60) {
                    issues.add(buildIssue("warning", "连接数使用率较高", String.format("当前%d/%d（%.1f%%）", currentConn, maxConn, connUsage)));
                    healthScore -= 10;
                }
            }

            // 检查2: 慢查询
            long slowQueries = parseLong(globalStatus.get("Slow_queries"));
            long questions = parseLong(globalStatus.get("Questions"));
            if (slowQueries > 0 && questions > 0) {
                double slowRate = (double) slowQueries / questions * 100;
                if (slowRate > 1) {
                    issues.add(buildIssue("warning", "慢查询比例偏高", String.format("慢查询%d次，占比%.2f%%，建议优化SQL或增加索引", slowQueries, slowRate)));
                    healthScore -= 15;
                }
            }

            // 检查3: Buffer Pool命中率
            long readReqs = parseLong(globalStatus.get("Innodb_buffer_pool_read_requests"));
            long reads = parseLong(globalStatus.get("Innodb_buffer_pool_reads"));
            if (readReqs > 0) {
                double hitRate = (1 - (double) reads / readReqs) * 100;
                if (hitRate < 95) {
                    issues.add(buildIssue("warning", "InnoDB缓冲命中率偏低", String.format("命中率%.2f%%，建议增大innodb_buffer_pool_size", hitRate)));
                    healthScore -= 15;
                }
            }

            // 检查4: 临时表使用
            long tmpDiskTables = parseLong(globalStatus.get("Created_tmp_disk_tables"));
            long tmpTables = parseLong(globalStatus.get("Created_tmp_tables"));
            if (tmpTables > 0) {
                double diskRate = (double) tmpDiskTables / tmpTables * 100;
                if (diskRate > 25) {
                    issues.add(buildIssue("warning", "磁盘临时表比例过高", String.format("磁盘临时表%d/%d（%.1f%%），建议增大tmp_table_size和max_heap_table_size", tmpDiskTables, tmpTables, diskRate)));
                    healthScore -= 10;
                }
            }

            // 检查5: 表锁等待
            long tableLocksWaited = parseLong(globalStatus.get("Table_locks_waited"));
            if (tableLocksWaited > 100) {
                issues.add(buildIssue("warning", "表锁等待次数较多", "累计" + tableLocksWaited + "次，建议检查是否使用了MyISAM表或存在锁竞争"));
                healthScore -= 10;
            }

            // 检查6: Aborted连接
            long aborted = parseLong(globalStatus.get("Aborted_connects"));
            if (aborted > 50) {
                issues.add(buildIssue("notice", "异常断开连接较多", "累计" + aborted + "次，可能是网络问题或客户端未正常关闭连接"));
                healthScore -= 5;
            }

            healthScore = Math.max(0, healthScore);
            report.put("healthScore", healthScore);
            report.put("healthLevel", healthScore >= 80 ? "healthy" : healthScore >= 60 ? "warning" : "critical");
            report.put("issues", issues);
            report.put("summary", String.format("数据库健康评分 %d/100，发现 %d 个问题", healthScore, issues.size()));

            // 关键配置建议
            Map<String, String> recommendations = new LinkedHashMap<>();
            recommendations.put("innodb_buffer_pool_size", globalVars.getOrDefault("innodb_buffer_pool_size", "N/A"));
            recommendations.put("max_connections", globalVars.getOrDefault("max_connections", "N/A"));
            recommendations.put("long_query_time", globalVars.getOrDefault("long_query_time", "N/A"));
            recommendations.put("tmp_table_size", globalVars.getOrDefault("tmp_table_size", "N/A"));
            recommendations.put("innodb_log_file_size", globalVars.getOrDefault("innodb_log_file_size", "N/A"));
            report.put("keyVariables", recommendations);

        } catch (Exception e) {
            log.error("性能诊断失败: {}", e.getMessage());
            return Result.error("性能诊断失败: " + e.getMessage());
        } finally {
            closeConnection(conn);
        }
        return Result.success(report);
    }

    /**
     * Kill长查询
     */
    @PostMapping("/{instanceId}/kill-process")
    @Operation(summary = "Kill数据库进程")
    public Result<Void> killProcess(
            @PathVariable String instanceId,
            @RequestBody Map<String, Object> request) {
        DatabaseInstance db = databaseInstanceService.getById(instanceId);
        if (db == null) return Result.error(400, "数据库实例不存在");

        int processId = request.get("processId") != null ? ((Number) request.get("processId")).intValue() : 0;
        if (processId <= 0) return Result.error(400, "无效的进程ID");

        Connection conn = null;
        try {
            conn = getConnection(db, (String) request.get("schemaName"));
            Statement stmt = conn.createStatement();
            stmt.execute("KILL " + processId);
            stmt.close();
            log.info("已Kill进程: {}", processId);
        } catch (Exception e) {
            log.error("Kill进程失败: {}", e.getMessage());
            return Result.error("Kill进程失败: " + e.getMessage());
        } finally {
            closeConnection(conn);
        }
        return Result.success("进程已终止");
    }

    // ========== 辅助方法 ==========

    private Map<String, String> queryGlobalStatus(Connection conn) throws SQLException {
        Map<String, String> status = new HashMap<>();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SHOW GLOBAL STATUS");
        while (rs.next()) {
            status.put(rs.getString(1), rs.getString(2));
        }
        rs.close();
        stmt.close();
        return status;
    }

    private Map<String, String> queryGlobalVariables(Connection conn) throws SQLException {
        Map<String, String> vars = new HashMap<>();
        Statement stmt = conn.createStatement();
        ResultSet rs = stmt.executeQuery("SHOW GLOBAL VARIABLES");
        while (rs.next()) {
            vars.put(rs.getString(1), rs.getString(2));
        }
        rs.close();
        stmt.close();
        return vars;
    }

    private String queryVariable(Connection conn, String name) {
        try {
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery("SHOW GLOBAL VARIABLES LIKE '" + name + "'");
            if (rs.next()) return rs.getString(2);
            rs.close();
            stmt.close();
        } catch (Exception ignored) {}
        return "N/A";
    }

    private Connection getConnection(DatabaseInstance db, String schemaName) throws Exception {
        String dbName = (schemaName != null && !schemaName.isEmpty()) ? schemaName : db.getDefaultSchemaName();
        String url = String.format("jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false",
                db.getHost(), db.getPort() != null ? db.getPort() : 3306, dbName != null ? dbName : "");
        Properties props = new Properties();
        props.put("user", db.getUsername());
        props.put("password", db.getPassword());
        return DriverManager.getConnection(url, props);
    }

    private void closeConnection(Connection conn) {
        if (conn != null) try { conn.close(); } catch (Exception ignored) {}
    }

    private int parseInt(String val) {
        try { return val != null ? Integer.parseInt(val) : 0; } catch (Exception e) { return 0; }
    }

    private long parseLong(String val) {
        try { return val != null ? Long.parseLong(val) : 0; } catch (Exception e) { return 0; }
    }

    private Map<String, Object> buildIssue(String level, String title, String detail) {
        Map<String, Object> issue = new LinkedHashMap<>();
        issue.put("level", level);
        issue.put("title", title);
        issue.put("detail", detail);
        return issue;
    }
}
