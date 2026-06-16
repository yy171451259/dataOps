package com.dataops.dms.controller;

import com.dataops.dms.common.result.Result;
import com.dataops.dms.dto.BatchMarkSensitiveDTO;
import com.dataops.dms.dto.MarkSensitiveDTO;
import com.dataops.dms.entity.SensitiveColumn;
import com.dataops.dms.entity.SensitiveMaskRule;
import com.dataops.dms.service.SensitiveColumnService;
import com.dataops.dms.service.SensitiveMaskRuleService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/sensitive")
public class SensitiveDataController {

    @Resource
    private SensitiveColumnService sensitiveColumnService;

    @Resource
    private SensitiveMaskRuleService maskRuleService;

    @GetMapping("/columns")
    @Operation(summary = "查询敏感列列表")
    public Result<List<SensitiveColumn>> listColumns(
            @RequestParam String databaseId,
            @RequestParam(required = false) String databaseName,
            @RequestParam(required = false) String tableName) {
        List<SensitiveColumn> cols;
        if (tableName != null && !tableName.isEmpty()) {
            cols = sensitiveColumnService.getByTable(databaseId, databaseName, tableName);
        } else {
            cols = sensitiveColumnService.getByDatabase(databaseId, databaseName);
        }
        return Result.success(cols);
    }

    @PostMapping("/columns")
    @Operation(summary = "标记单个敏感列")
    public Result<SensitiveColumn> markSensitive(@RequestBody MarkSensitiveDTO dto, HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        if (userId == null) userId = "user_admin";
        SensitiveColumn col = new SensitiveColumn();
        col.setId(UUID.randomUUID().toString().replace("-", ""));
        col.setInstanceId(dto.getInstanceId());
        col.setSchemaName(dto.getSchemaName());
        col.setTableName(dto.getTableName());
        col.setColumnName(dto.getColumnName());
        col.setSensitivityLevel(dto.getSensitivityLevel() != null ? dto.getSensitivityLevel() : "L2");
        col.setCategory(dto.getCategory());
        col.setMaskRuleId(dto.getMaskRuleId());
        col.setDescription(dto.getDescription());
        SensitiveColumn result = sensitiveColumnService.markSensitive(col, userId);
        return Result.success("敏感列标记成功", result);
    }

    @PostMapping("/columns/batch")
    @Operation(summary = "批量标记敏感列")
    public Result<Integer> batchMark(@RequestBody BatchMarkSensitiveDTO dto, HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        if (userId == null) userId = "user_admin";
        List<SensitiveColumn> cols = new ArrayList<>();
        if (dto.getColumns() != null) {
            for (MarkSensitiveDTO md : dto.getColumns()) {
                SensitiveColumn col = new SensitiveColumn();
                col.setId(UUID.randomUUID().toString().replace("-", ""));
                col.setInstanceId(md.getInstanceId() != null ? md.getInstanceId() : dto.getInstanceId());
                col.setSchemaName(md.getSchemaName() != null ? md.getSchemaName() : dto.getSchemaName());
                col.setTableName(md.getTableName() != null ? md.getTableName() : dto.getTableName());
                col.setColumnName(md.getColumnName());
                col.setSensitivityLevel(md.getSensitivityLevel() != null ? md.getSensitivityLevel() : "L2");
                col.setCategory(md.getCategory());
                col.setMaskRuleId(md.getMaskRuleId());
                col.setDescription(md.getDescription());
                cols.add(col);
            }
        }
        int count = sensitiveColumnService.batchMark(cols, userId);
        return Result.success("批量标记完成", count);
    }

    @DeleteMapping("/columns/{id}")
    @Operation(summary = "删除敏感列标记")
    public Result<Boolean> deleteColumn(@PathVariable String id) {
        boolean result = sensitiveColumnService.deleteSensitive(id);
        return Result.success("敏感列标记已删除", result);
    }

    @GetMapping("/mask-rules")
    @Operation(summary = "获取所有脱敏规则")
    public Result<List<SensitiveMaskRule>> listMaskRules() {
        return Result.success(maskRuleService.list());
    }

    @GetMapping("/mask-rules/{id}")
    @Operation(summary = "按ID获取脱敏规则")
    public Result<SensitiveMaskRule> getMaskRuleById(@PathVariable String id) {
        return Result.success(maskRuleService.getById(id));
    }

    @GetMapping("/mask-rules/by-code/{code}")
    @Operation(summary = "按编码获取脱敏规则")
    public Result<SensitiveMaskRule> getByCode(@PathVariable String code) {
        return Result.success(maskRuleService.getByCode(code));
    }

    @PostMapping("/mask-rules")
    @Operation(summary = "创建脱敏规则")
    public Result<SensitiveMaskRule> createMaskRule(@RequestBody SensitiveMaskRule rule) {
        try {
            return Result.success("创建成功", maskRuleService.createRule(rule));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @PutMapping("/mask-rules/{id}")
    @Operation(summary = "更新脱敏规则")
    public Result<SensitiveMaskRule> updateMaskRule(@PathVariable String id, @RequestBody SensitiveMaskRule rule) {
        try {
            rule.setId(id);
            return Result.success("更新成功", maskRuleService.updateRule(rule));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    @DeleteMapping("/mask-rules/{id}")
    @Operation(summary = "删除脱敏规则")
    public Result<Boolean> deleteMaskRule(@PathVariable String id) {
        try {
            return Result.success("删除成功", maskRuleService.deleteRule(id));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}
