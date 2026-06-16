package com.dataops.dms.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataops.dms.common.result.Result;
import com.dataops.dms.entity.DatabaseInstance;
import com.dataops.dms.service.DatabaseInstanceService;
import com.dataops.dms.service.MetadataAccessControlService;
import com.dataops.dms.service.ResourceOwnerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 数据库实例管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/instances")
@Tag(name = "实例管理")
public class DatabaseController {

    @Resource
    private DatabaseInstanceService databaseInstanceService;

    @Resource
    private MetadataAccessControlService accessControlService;

    @Resource
    private ResourceOwnerService resourceOwnerService;

    /**
     * 获取所有数据库实例列表（含访问控制过滤）
     */
    @GetMapping
    @Operation(summary = "获取实例列表")
    public Result<List<DatabaseInstance>> list(HttpServletRequest request) {
        LambdaQueryWrapper<DatabaseInstance> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(DatabaseInstance::getCreateTime);
        List<DatabaseInstance> allInstances = databaseInstanceService.list(wrapper);

        // 访问控制过滤：受限实例只对 Owner 和已授权用户可见
        String userId = (String) request.getAttribute("userId");
        if (userId != null) {
            allInstances = allInstances.stream()
                    .filter(inst -> accessControlService.canUserAccess(userId, "instance", inst.getId()))
                    .collect(Collectors.toList());
        }
        return Result.success(allInstances);
    }

    /**
     * 获取单个数据库实例详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "获取实例详情")
    public Result<DatabaseInstance> getById(@PathVariable String id) {
        return Result.success(databaseInstanceService.getById(id));
    }

    /**
     * 创建数据库实例（自动赋予创建者 Owner 权限）
     */
    @PostMapping
    @Operation(summary = "创建实例")
    public Result<DatabaseInstance> create(@RequestBody DatabaseInstance db, HttpServletRequest request) {
        boolean success = databaseInstanceService.saveAndTest(db);
        if (success) {
            // 创建者自动成为该实例的 Owner
            String userId = (String) request.getAttribute("userId");
            if (userId != null && db.getId() != null) {
                try {
                    resourceOwnerService.assignOwner("instance", db.getId(), userId, userId);
                    log.info("创建者[{}]自动成为实例[{}]的Owner", userId, db.getId());
                } catch (Exception e) {
                    log.warn("自动分配Owner失败: userId={}, instanceId={}, error={}", userId, db.getId(), e.getMessage());
                }
            }
            return Result.success("创建成功", db);
        } else {
            return Result.error(500, "数据库连接测试失败，请检查配置");
        }
    }

    /**
     * 更新数据库实例
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新实例")
    public Result<DatabaseInstance> update(@PathVariable String id, @RequestBody DatabaseInstance db) {
        db.setId(id);
        databaseInstanceService.updateById(db);
        return Result.success("更新成功", db);
    }

    /**
     * 删除数据库实例
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除实例")
    public Result<Void> delete(@PathVariable String id) {
        databaseInstanceService.removeById(id);
        return Result.success("删除成功");
    }

    /**
     * 测试数据库连接
     */
    @PostMapping("/{id}/test")
    @Operation(summary = "测试连接")
    public Result<Boolean> testConnection(@PathVariable String id) {
        boolean success = databaseInstanceService.testConnectionById(id);
        return Result.success(success ? "连接成功" : "连接失败", success);
    }

    /**
     * 获取数据库实例下的所有表名
     */
    @GetMapping("/{id}/tables")
    @Operation(summary = "获取表列表")
    public Result<List<String>> getTableNames(
            @PathVariable String id,
            @RequestParam(required = false) String schemaName) {
        try {
            List<String> tables;
            if (schemaName != null && !schemaName.isEmpty()) {
                tables = databaseInstanceService.getTableNames(id, schemaName);
            } else {
                tables = databaseInstanceService.getTableNames(id);
            }
            return Result.success(tables);
        } catch (Exception e) {
            return Result.error("获取表列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取指定表的建表语句
     */
    @GetMapping("/{id}/tables/{tableName}/create-sql")
    @Operation(summary = "获取表建表语句")
    public Result<String> getCreateTableSql(
            @PathVariable String id,
            @PathVariable String tableName,
            @RequestParam(required = false) String schemaName) {
        try {
            String sql;
            if (schemaName != null && !schemaName.isEmpty()) {
                sql = databaseInstanceService.getCreateTableSql(id, schemaName, tableName);
            } else {
                sql = databaseInstanceService.getCreateTableSql(id, tableName);
            }
            return Result.success("操作成功", sql);
        } catch (Exception e) {
            return Result.error("获取建表语句失败: " + e.getMessage());
        }
    }

    /**
     * 获取实例下的所有Schema列表（含访问控制过滤）
     */
    @GetMapping("/{id}/schemas")
    @Operation(summary = "获取Schema列表（SHOW DATABASES）")
    public Result<List<String>> getSchemas(@PathVariable String id, HttpServletRequest request) {
        try {
            List<String> schemas = databaseInstanceService.getSchemaNames(id);
            // 访问控制过滤：受限Schema只对 Owner 和已授权用户可见
            String userId = (String) request.getAttribute("userId");
            if (userId != null) {
                schemas = schemas.stream()
                        .filter(schema -> accessControlService.canUserAccess(userId, "database", schema))
                        .collect(Collectors.toList());
            }
            return Result.success(schemas);
        } catch (Exception e) {
            return Result.error("获取Schema列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取Schema信息（用于SQL智能补全）
     */
    @GetMapping("/{id}/schema")
    @Operation(summary = "获取Schema（补全用）")
    public Result<List<Map<String, Object>>> getSchemaForCompletion(
            @PathVariable String id,
            @RequestParam(required = false) String schemaName) {
        try {
            List<Map<String, Object>> schema;
            if (schemaName != null && !schemaName.isEmpty()) {
                schema = databaseInstanceService.getSchemaForCompletion(id, schemaName);
            } else {
                schema = databaseInstanceService.getSchemaForCompletion(id);
            }
            return Result.success(schema);
        } catch (Exception e) {
            return Result.error("获取Schema失败: " + e.getMessage());
        }
    }

    /**
     * 批量获取多张表的建表语句
     */
    @PostMapping("/{id}/tables/batch-create-sql")
    @Operation(summary = "批量获取建表语句")
    public Result<Map<String, String>> batchGetCreateTableSql(@PathVariable String id, @RequestBody List<String> tableNames) {
        try {
            Map<String, String> result = databaseInstanceService.batchGetCreateTableSql(id, tableNames);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error("批量获取建表语句失败: " + e.getMessage());
        }
    }

    /**
     * 获取浏览器综合Schema（表、视图、存储过程、函数、触发器、事件）
     */
    @GetMapping("/{id}/browser-schema")
    @Operation(summary = "获取浏览器综合Schema")
    public Result<Map<String, Object>> getBrowserSchema(
            @PathVariable String id,
            @RequestParam(required = false) String schemaName) {
        try {
            Map<String, Object> schema = databaseInstanceService.getBrowserSchema(id, schemaName);
            return Result.success(schema);
        } catch (Exception e) {
            return Result.error("获取浏览器Schema失败: " + e.getMessage());
        }
    }

    @GetMapping("/{id}/tables/{tableName}/detail")
    @Operation(summary = "获取表详细信息（索引/外键/约束/触发器/DDL）")
    public Result<Map<String, Object>> getTableDetail(
            @PathVariable String id,
            @PathVariable String tableName,
            @RequestParam(required = false) String schemaName) {
        try {
            Map<String, Object> detail = databaseInstanceService.getTableDetail(id, schemaName, tableName);
            return Result.success(detail);
        } catch (Exception e) {
            return Result.error("获取表详细信息失败: " + e.getMessage());
        }
    }
}