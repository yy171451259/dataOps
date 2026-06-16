package com.dataops.dms.controller;

import com.dataops.dms.common.result.Result;
import com.dataops.dms.entity.RowControlRule;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/row-controls")
public class RowControlController {

    @Resource
    private IService<RowControlRule> rowControlService;

    @GetMapping
    @Operation(summary = "获取所有行级管控规则")
    public Result<List<RowControlRule>> listAll() {
        return Result.success(rowControlService.list());
    }

    @PostMapping
    @Operation(summary = "创建行级管控规则")
    public Result<RowControlRule> create(@RequestBody RowControlRule rule, HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        if (userId == null) userId = "user_admin";
        rule.setId(UUID.randomUUID().toString().replace("-", ""));
        rule.setCreatedAt(LocalDateTime.now());
        rule.setCreatedBy(userId);
        rule.setIsActive(true);
        rowControlService.save(rule);
        return Result.success("规则创建成功", rule);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新行级管控规则")
    public Result<RowControlRule> update(@PathVariable String id, @RequestBody RowControlRule rule) {
        rule.setId(id);
        rule.setUpdatedAt(LocalDateTime.now());
        rowControlService.updateById(rule);
        return Result.success("规则更新成功", rule);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除行级管控规则")
    public Result<Boolean> delete(@PathVariable String id) {
        rowControlService.removeById(id);
        return Result.success("规则已删除", true);
    }
}
