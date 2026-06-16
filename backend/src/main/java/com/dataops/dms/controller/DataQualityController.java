package com.dataops.dms.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataops.dms.common.result.Result;
import com.dataops.dms.entity.DataQualityResult;
import com.dataops.dms.entity.DataQualityRule;
import com.dataops.dms.service.DataQualityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/v1/quality")
@Tag(name = "数据质量管理")
public class DataQualityController {

    @Resource
    private DataQualityService qualityService;

    @GetMapping("/rules")
    @Operation(summary = "获取质量规则列表")
    public Result<Page<DataQualityRule>> listRules(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String databaseId) {
        return qualityService.listRules(page, size, databaseId);
    }

    @GetMapping("/rules/{id}")
    @Operation(summary = "获取规则详情")
    public Result<DataQualityRule> getRule(@PathVariable String id) {
        return qualityService.getRuleById(id);
    }

    @PostMapping("/rules")
    @Operation(summary = "创建质量规则")
    public Result<DataQualityRule> createRule(@RequestBody DataQualityRule rule) {
        return qualityService.createRule(rule);
    }

    @PutMapping("/rules/{id}")
    @Operation(summary = "更新质量规则")
    public Result<DataQualityRule> updateRule(@PathVariable String id, @RequestBody DataQualityRule rule) {
        rule.setId(id);
        return qualityService.updateRule(rule);
    }

    @DeleteMapping("/rules/{id}")
    @Operation(summary = "删除质量规则")
    public Result<Void> deleteRule(@PathVariable String id) {
        return qualityService.deleteRule(id);
    }

    @PostMapping("/rules/{id}/toggle")
    @Operation(summary = "启用/禁用规则")
    public Result<Void> toggleRule(@PathVariable String id, @RequestParam Boolean enabled) {
        return qualityService.toggleRule(id, enabled);
    }

    @PostMapping("/rules/{id}/execute")
    @Operation(summary = "执行单个规则")
    public Result<DataQualityResult> executeRule(@PathVariable String id) {
        return qualityService.executeRule(id);
    }

    @PostMapping("/execute/{databaseId}")
    @Operation(summary = "执行数据库所有规则")
    public Result<List<DataQualityResult>> executeAll(@PathVariable String databaseId) {
        return qualityService.executeAllRules(databaseId);
    }

    @GetMapping("/results")
    @Operation(summary = "获取检查结果")
    public Result<Page<DataQualityResult>> listResults(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String ruleId,
            @RequestParam(required = false) String databaseId) {
        return qualityService.listResults(page, size, ruleId, databaseId);
    }
}
