package com.dataops.dms.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataops.dms.common.result.Result;
import com.dataops.dms.entity.AuditLog;
import com.dataops.dms.mapper.AuditLogMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * 审计日志控制器
 */
@RestController
@RequestMapping("/api/v1/audit")
@Tag(name = "审计日志")
public class AuditController {

    @Resource
    private AuditLogMapper auditLogMapper;

    /**
     * 获取审计日志列表
     */
    @GetMapping
    @Operation(summary = "获取审计日志列表")
    public Result<List<AuditLog>> list(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String action) {
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();
        if (userId != null && !userId.isEmpty()) {
            wrapper.eq(AuditLog::getUserId, userId);
        }
        if (riskLevel != null && !riskLevel.isEmpty()) {
            wrapper.eq(AuditLog::getRiskLevel, riskLevel);
        }
        if (action != null && !action.isEmpty()) {
            wrapper.like(AuditLog::getAction, action);
        }
        wrapper.orderByDesc(AuditLog::getCreateTime);
        wrapper.last("LIMIT 100");
        return Result.success(auditLogMapper.selectList(wrapper));
    }
}
