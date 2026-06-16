package com.dataops.dms.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataops.dms.common.result.Result;
import com.dataops.dms.entity.DataMaskingRule;
import com.dataops.dms.service.DataMaskingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/v1/masking")
@Tag(name = "数据脱敏管理")
public class DataMaskingController {

    @Resource
    private DataMaskingService maskingService;

    @GetMapping("/rules")
    @Operation(summary = "获取脱敏规则列表")
    public Result<Page<DataMaskingRule>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String databaseId) {
        return maskingService.listRules(page, size, databaseId);
    }

    @GetMapping("/rules/{id}")
    @Operation(summary = "获取规则详情")
    public Result<DataMaskingRule> getById(@PathVariable String id) {
        return maskingService.getRuleById(id);
    }

    @PostMapping("/rules")
    @Operation(summary = "创建脱敏规则")
    public Result<DataMaskingRule> create(@RequestBody DataMaskingRule rule) {
        return maskingService.createRule(rule);
    }

    @PutMapping("/rules/{id}")
    @Operation(summary = "更新脱敏规则")
    public Result<DataMaskingRule> update(@PathVariable String id, @RequestBody DataMaskingRule rule) {
        rule.setId(id);
        return maskingService.updateRule(rule);
    }

    @DeleteMapping("/rules/{id}")
    @Operation(summary = "删除脱敏规则")
    public Result<Void> delete(@PathVariable String id) {
        return maskingService.deleteRule(id);
    }

    @PostMapping("/rules/{id}/toggle")
    @Operation(summary = "启用/禁用规则")
    public Result<Void> toggle(@PathVariable String id, @RequestParam Boolean enabled) {
        return maskingService.toggleRule(id, enabled);
    }

    @PostMapping("/apply")
    @Operation(summary = "对数据应用脱敏")
    public Result<Object> apply(@RequestParam String databaseId, @RequestParam String tableName, @RequestBody Object data) {
        return maskingService.applyMasking(databaseId, tableName, data);
    }
}
