package com.dataops.dms.controller;

import com.dataops.dms.common.result.PageResult;
import com.dataops.dms.common.result.Result;
import com.dataops.dms.dto.TicketCreateDTO;
import com.dataops.dms.entity.Ticket;
import com.dataops.dms.service.TicketService;
import com.dataops.dms.sql.LockFreeDmlEngine;
import com.dataops.dms.sql.SqlAuditEngine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * 工单管理控制器
 * 支持数据变更工单、审批流程、无锁DML变更
 */
@RestController
@RequestMapping("/api/v1/tickets")
@Tag(name = "工单管理")
public class TicketController {

    @Resource
    private TicketService ticketService;

    @Resource
    private SqlAuditEngine sqlAuditEngine;

    /**
     * SQL预审核
     */
    @PostMapping("/audit-sql")
    @Operation(summary = "SQL预审核")
    public Result<SqlAuditEngine.SqlAuditResult> auditSql(@RequestBody Map<String, String> request) {
        String sql = request.get("sql");
        SqlAuditEngine.SqlAuditResult result = sqlAuditEngine.audit(sql);
        return Result.success(result);
    }

    /**
     * 检测是否需要无锁数据变更（DML）
     */
    @PostMapping("/check-lock-free-dml")
    @Operation(summary = "检测无锁数据变更需求")
    public Result<LockFreeDmlEngine.DmlCheckResult> checkLockFreeDml(@RequestBody Map<String, String> request) {
        String instanceId = request.get("instanceId");
        String schemaName = request.get("schemaName");
        String sql = request.get("sql");
        try {
            LockFreeDmlEngine.DmlCheckResult result = ticketService.checkLockFreeDml(instanceId, schemaName, sql);
            return Result.success(result);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 创建数据变更工单
     */
    @PostMapping
    @Operation(summary = "创建工单")
    public Result<Ticket> createTicket(
            @Validated @RequestBody TicketCreateDTO dto,
            HttpServletRequest request) {
        String creatorId = (String) request.getAttribute("userId");
        if (creatorId == null) {
            creatorId = "user_admin";
        }
        Ticket ticket = ticketService.createDataChangeTicket(dto, creatorId);
        return Result.success("工单创建成功", ticket);
    }

    /**
     * 更新工单基本信息（影响行数、相关人员等）
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新工单信息")
    public Result<?> updateTicket(@PathVariable String id, @RequestBody Map<String, Object> body) {
        ticketService.updateTicketInfo(id, body);
        return Result.success("更新成功");
    }

    /**
     * 获取所有工单（分页）
     */
    @GetMapping
    @Operation(summary = "所有工单列表（支持筛选）")
    public Result<PageResult<Ticket>> getAllTickets(
            @RequestParam(required = false) String changeType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String databaseId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "15") Integer size) {
        PageResult<Ticket> pageResult = ticketService.queryTicketsPage(changeType, status, keyword, databaseId, page, size);
        return Result.success(pageResult);
    }

    /**
     * 获取我的待审批工单（分页）
     * 管理员可查看所有待审批工单，普通用户仅看自己为审批人的工单
     */
    @GetMapping("/pending")
    @Operation(summary = "我的待审批")
    public Result<PageResult<Ticket>> getMyPendingTickets(HttpServletRequest request,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "15") Integer size) {
        String userId = (String) request.getAttribute("userId");
        Boolean isAdmin = (Boolean) request.getAttribute("isAdmin");
        if (userId == null) {
            userId = "user_admin";
        }
        // 管理员看全部 pending，普通用户只看自己是审批人的工单
        PageResult<Ticket> pageResult = ticketService.getMyPendingTicketsPage(
                Boolean.TRUE.equals(isAdmin) ? null : userId, page, size);
        return Result.success(pageResult);
    }

    /**
     * 获取我创建的工单（分页）
     */
    @GetMapping("/my")
    @Operation(summary = "我创建的工单")
    public Result<PageResult<Ticket>> getMyCreatedTickets(HttpServletRequest request,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "15") Integer size) {
        String creatorId = (String) request.getAttribute("userId");
        if (creatorId == null) {
            creatorId = "user_admin";
        }
        PageResult<Ticket> pageResult = ticketService.getMyCreatedTicketsPage(creatorId, status, page, size);
        return Result.success(pageResult);
    }

    /**
     * 获取工单详情
     */
    @GetMapping("/{id}")
    @Operation(summary = "工单详情")
    public Result<Ticket> getTicketDetail(@PathVariable String id) {
        Ticket ticket = ticketService.getTicketDetail(id);
        return Result.success(ticket);
    }

    /**
     * 审批工单（通过）
     */
    @PostMapping("/{id}/approve")
    @Operation(summary = "审批通过")
    public Result<Void> approveTicket(
            @PathVariable String id,
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {
        String approverId = (String) httpRequest.getAttribute("userId");
        if (approverId == null) {
            approverId = "user_admin";
        }
        String comment = request.get("comment");
        ticketService.approveTicket(id, approverId, true, comment);
        return Result.success("审批通过");
    }

    /**
     * 审批工单（拒绝）
     */
    @PostMapping("/{id}/reject")
    @Operation(summary = "审批拒绝")
    public Result<Void> rejectTicket(
            @PathVariable String id,
            @RequestBody Map<String, String> request,
            HttpServletRequest httpRequest) {
        String approverId = (String) httpRequest.getAttribute("userId");
        if (approverId == null) {
            approverId = "user_admin";
        }
        String comment = request.get("comment");
        ticketService.approveTicket(id, approverId, false, comment);
        return Result.success("已拒绝");
    }

    /**
     * 回滚工单
     */
    @PostMapping("/{id}/rollback")
    @Operation(summary = "回滚工单")
    public Result<Void> rollbackTicket(
            @PathVariable String id,
            HttpServletRequest httpRequest) throws Exception {
        String operatorId = (String) httpRequest.getAttribute("userId");
        if (operatorId == null) {
            operatorId = "user_admin";
        }
        ticketService.rollbackTicket(id, operatorId);
        return Result.success("回滚成功");
    }

    /**
     * 取消工单
     */
    @PostMapping("/{id}/cancel")
    @Operation(summary = "取消工单")
    public Result<Void> cancelTicket(
            @PathVariable String id,
            HttpServletRequest httpRequest) {
        String userId = (String) httpRequest.getAttribute("userId");
        if (userId == null) {
            userId = "user_admin";
        }
        ticketService.cancelTicket(id, userId);
        return Result.success("取消成功");
    }

    /**
     * 手动执行工单（审批通过后由提交者触发）
     */
    @PostMapping("/{id}/execute")
    @Operation(summary = "手动执行工单")
    public Result<Void> executeTicket(
            @PathVariable String id,
            HttpServletRequest httpRequest) {
        String operatorId = (String) httpRequest.getAttribute("userId");
        if (operatorId == null) {
            operatorId = "user_admin";
        }
        ticketService.executeTicket(id, operatorId);
        return Result.success("执行成功");
    }

    // ============ 对标阿里云DMS：无锁DML运行态控制 ============

    /**
     * 暂停无锁DML执行
     */
    @PostMapping("/{id}/dml/pause")
    @Operation(summary = "暂停无锁DML")
    public Result<Void> pauseDml(@PathVariable String id, HttpServletRequest httpRequest) {
        String userId = (String) httpRequest.getAttribute("userId");
        if (userId == null) userId = "user_admin";
        try {
            ticketService.pauseDmlExecution(id, userId);
            return Result.success("已暂停无锁DML执行");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 恢复无锁DML执行
     */
    @PostMapping("/{id}/dml/resume")
    @Operation(summary = "恢复无锁DML")
    public Result<Void> resumeDml(@PathVariable String id, HttpServletRequest httpRequest) {
        String userId = (String) httpRequest.getAttribute("userId");
        if (userId == null) userId = "user_admin";
        try {
            ticketService.resumeDmlExecution(id, userId);
            return Result.success("已恢复无锁DML执行");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 终止无锁DML执行
     */
    @PostMapping("/{id}/dml/stop")
    @Operation(summary = "终止无锁DML")
    public Result<Void> stopDml(@PathVariable String id, HttpServletRequest httpRequest) {
        String userId = (String) httpRequest.getAttribute("userId");
        if (userId == null) userId = "user_admin";
        try {
            ticketService.stopDmlExecution(id, userId);
            return Result.success("已终止无锁DML执行");
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 查询无锁DML执行进度
     */
    @GetMapping("/{id}/dml/progress")
    @Operation(summary = "查询DML进度")
    public Result<java.util.Map<String, Object>> getDmlProgress(@PathVariable String id) {
        try {
            java.util.Map<String, Object> progress = ticketService.getDmlProgress(id);
            return Result.success(progress);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 处理审批超时（定时任务手动触发）
     */
    @PostMapping("/process-timeout")
    @Operation(summary = "处理审批超时工单")
    public Result<java.util.Map<String, Object>> processTimeout() {
        int count = ticketService.processApprovalTimeout();
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("processedCount", count);
        return Result.success("审批超时处理完成", result);
    }

    /**
     * 获取工单审批记录
     */
    @GetMapping("/{id}/approvals")
    @Operation(summary = "工单审批记录")
    public Result<java.util.List<java.util.Map<String, Object>>> getApprovalRecords(@PathVariable String id) {
        try {
            java.util.List<java.util.Map<String, Object>> records = ticketService.getApprovalRecords(id);
            return Result.success(records);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }
}