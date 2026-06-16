package com.dataops.dms.controller;

import com.dataops.dms.common.result.Result;
import com.dataops.dms.entity.DatabaseInstance;
import com.dataops.dms.service.DatabaseInstanceService;
import com.dataops.dms.service.MetadataAccessControlService;
import com.dataops.dms.sql.SqlAuditEngine;
import com.dataops.dms.sql.SqlExecuteResult;
import com.dataops.dms.sql.SqlExecutor;
import com.dataops.dms.sql.MaskingEngine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL执行控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/sql")
@Tag(name = "SQL执行")
public class SqlController {

    @Resource
    private SqlExecutor sqlExecutor;

    @Resource
    private DatabaseInstanceService databaseInstanceService;

    @Resource
    private SqlAuditEngine sqlAuditEngine;

    @Resource
    private MaskingEngine maskingEngine;

    @Resource
    private MetadataAccessControlService accessControlService;

    /**
     * 执行SQL（支持多条语句，返回多个结果集）
     */
    @PostMapping("/execute")
    @Operation(summary = "执行SQL语句（支持多语句）")
    public Result<List<SqlExecuteResult>> execute(@RequestBody Map<String, Object> request, HttpServletRequest httpRequest) {
        String instanceId = (String) request.get("instanceId");
        if (instanceId == null) instanceId = (String) request.get("databaseId");
        String sql = (String) request.get("sql");
        String schemaName = (String) request.get("schemaName");
        if (schemaName == null) schemaName = (String) request.get("databaseName");
        int offset = getIntParam(request, "offset", 0);
        int limit = getIntParam(request, "limit", SqlExecutor.PAGE_SIZE);

        if (instanceId == null || instanceId.isEmpty()) {
            return Result.error(400, "请选择数据库实例");
        }
        if (sql == null || sql.trim().isEmpty()) {
            return Result.error(400, "SQL语句不能为空");
        }

        DatabaseInstance db = databaseInstanceService.getById(instanceId);
        if (db == null) {
            return Result.error(400, "数据库实例不存在");
        }

        // 获取当前用户ID（从请求头或JWT Token中获取）
        String userId = httpRequest.getHeader("X-User-Id");
        if (userId == null || userId.isEmpty()) {
            userId = (String) httpRequest.getAttribute("userId");
        }
        if (userId == null || userId.isEmpty()) {
            return Result.error(401, "用户未登录");
        }

        // 根据SQL类型判断需要的操作权限
        String requiredAction = determineActionType(sql);
        
        // 权限校验
        if (!accessControlService.canUserAccess(userId, "schema", instanceId, requiredAction)) {
            return Result.error(403, "您没有执行此操作的权限");
        }

        try {
            List<SqlExecuteResult> results = sqlExecutor.executeBatch(db, sql, schemaName, offset, limit);

            // 对SELECT结果集自动应用脱敏（脱敏失败不阻塞查询）
            for (SqlExecuteResult result : results) {
                if (Boolean.TRUE.equals(result.getSuccess()) && result.getData() != null) {
                    String tableName = extractTableName(sql);
                    if (tableName != null) {
                        try {
                            List<Map<String, Object>> masked = maskingEngine.applyMasking(
                                    instanceId, schemaName, tableName, result.getData());
                            result.setData(masked);
                        } catch (Exception maskErr) {
                            log.warn("脱敏跳过: {}.{} - {}", schemaName, tableName, maskErr.getMessage());
                        }
                    }
                }
            }

            return Result.success(results);
        } catch (Exception e) {
            log.error("SQL执行失败: {}", e.getMessage());
            return Result.error("SQL执行失败: " + e.getMessage());
        }
    }

    private int getIntParam(Map<String, Object> request, String key, int defaultValue) {
        Object val = request.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        if (val instanceof String) {
            try { return Integer.parseInt((String) val); } catch (NumberFormatException e) { }
        }
        return defaultValue;
    }

    /**
     * 根据SQL语句判断需要的操作权限类型
     * @param sql SQL语句
     * @return 操作类型: read, write, ddl, export
     */
    private String determineActionType(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return "read";
        }
        String upperSql = sql.trim().toUpperCase();
        
        // DDL操作（结构变更）
        if (upperSql.startsWith("CREATE") || upperSql.startsWith("ALTER") || 
            upperSql.startsWith("DROP") || upperSql.startsWith("TRUNCATE") ||
            upperSql.startsWith("RENAME") || upperSql.startsWith("COMMENT") ||
            upperSql.startsWith("ADD CONSTRAINT")) {
            return "ddl";
        }
        
        // 写入操作（数据变更）
        if (upperSql.startsWith("INSERT") || upperSql.startsWith("UPDATE") || 
            upperSql.startsWith("DELETE") || upperSql.startsWith("MERGE")) {
            return "write";
        }
        
        // 导出相关操作
        if (upperSql.startsWith("SELECT INTO OUTFILE") || upperSql.contains("INTO OUTFILE")) {
            return "export";
        }
        
        // 默认是查询操作
        return "read";
    }

    /**
     * 从SQL语句中提取表名（用于脱敏匹配）
     * 支持: SELECT ... FROM table, SELECT ... FROM db.table, SELECT ... FROM table alias
     */
    private static final Pattern FROM_PATTERN = Pattern.compile(
            "\\bFROM\\s+`?([a-zA-Z_][a-zA-Z0-9_]*)`?\\s*(as\\s+)?\\b",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );

    private String extractTableName(String sql) {
        if (sql == null) return null;
        // 只处理SELECT/查询语句
        String trimmed = sql.trim().toUpperCase();
        if (!(trimmed.startsWith("SELECT") || trimmed.startsWith("SHOW")
                || trimmed.startsWith("DESCRIBE") || trimmed.startsWith("DESC"))) {
            return null;
        }
        // JOIN 等情况暂跳过
        if (trimmed.contains(" JOIN ")) return null;

        Matcher m = FROM_PATTERN.matcher(sql);
        if (m.find()) {
            String name = m.group(1);
            // 排除子查询、保留字
            if (name.equalsIgnoreCase("SELECT") || name.equalsIgnoreCase("DUAL")) {
                return null;
            }
            return name;
        }
        return null;
    }

    /**
     * 获取SQL执行计划（EXPLAIN）
     */
    @PostMapping("/explain")
    @Operation(summary = "获取SQL执行计划")
    public Result<SqlExecuteResult> explain(@RequestBody Map<String, String> request) {
        String instanceId = request.get("instanceId");
        if (instanceId == null) instanceId = request.get("databaseId");
        String sql = request.get("sql");
        String schemaName = request.get("schemaName");
        if (schemaName == null) schemaName = request.get("databaseName");

        if (instanceId == null || instanceId.isEmpty()) {
            return Result.error(400, "请选择数据库实例");
        }
        if (sql == null || sql.trim().isEmpty()) {
            return Result.error(400, "SQL语句不能为空");
        }

        DatabaseInstance db = databaseInstanceService.getById(instanceId);
        if (db == null) {
            return Result.error(400, "数据库实例不存在");
        }

        try {
            long startTime = System.currentTimeMillis();
            SqlExecuteResult result = sqlExecutor.explainQuery(db, sql, schemaName);
            result.setExecutionTime(System.currentTimeMillis() - startTime);
            result.setSuccess(true);
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取执行计划失败: {}", e.getMessage());
            return Result.error("获取执行计划失败: " + e.getMessage());
        }
    }

    /**
     * SQL审核
     */
    @PostMapping("/audit")
    @Operation(summary = "SQL预审核")
    public Result<SqlAuditEngine.SqlAuditResult> audit(@RequestBody Map<String, String> request) {
        String sql = request.get("sql");
        if (sql == null || sql.trim().isEmpty()) {
            return Result.error(400, "SQL语句不能为空");
        }
        SqlAuditEngine.SqlAuditResult result = sqlAuditEngine.audit(sql);
        return Result.success(result);
    }
}
