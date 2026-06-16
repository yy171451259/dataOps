package com.dataops.dms.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataops.dms.common.result.Result;
import com.dataops.dms.dto.ApproveRequestDTO;
import com.dataops.dms.dto.PermissionTicketDTO;
import com.dataops.dms.dto.PermissionRequestDTO;
import com.dataops.dms.entity.PermissionRequest;
import com.dataops.dms.service.PermissionRequestService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.UUID;

/**
 * 权限申请工单控制器
 * 对标阿里云DMS权限申请工单功能，支持：
 * - 多资源批量申请（实例/库/表）
 * - 审批通过后自动授权到 sys_permission 表
 * - 工单列表/详情/创建/审批/取消 全流程
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/permission-requests")
public class PermissionRequestController {

    @Resource
    private PermissionRequestService requestService;

    @Resource
    private com.dataops.dms.service.ResourceOwnerService resourceOwnerService;

    @Resource
    private com.dataops.dms.mapper.UserMapper userMapper;

    // ==================== 工单列表 ====================

    @GetMapping("/tickets")
    @Operation(summary = "获取所有权限工单（完整列表）")
    public Result<List<PermissionRequest>> listTickets(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        LambdaQueryWrapper<PermissionRequest> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            wrapper.eq(PermissionRequest::getStatus, status);
        }
        if (type != null && !type.isEmpty()) {
            wrapper.eq(PermissionRequest::getResourceType, type);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.like(PermissionRequest::getResourceName, keyword)
                   .or().like(PermissionRequest::getReason, keyword)
                   .or().like(PermissionRequest::getApplicantName, keyword);
        }
        wrapper.orderByDesc(PermissionRequest::getCreatedAt);
        List<PermissionRequest> list = requestService.list(wrapper);
        return Result.success(list);
    }

    @GetMapping
    @Operation(summary = "获取权限申请列表")
    public Result<List<PermissionRequest>> listAll(@RequestParam(required = false) String status) {
        List<PermissionRequest> list;
        if (status != null && !status.isEmpty()) {
            list = requestService.lambdaQuery().eq(PermissionRequest::getStatus, status)
                    .orderByDesc(PermissionRequest::getCreatedAt).list();
        } else {
            list = requestService.list();
        }
        return Result.success(list);
    }

    // ==================== 我的 / 待审批 ====================

    @GetMapping("/pending")
    @Operation(summary = "获取待审批列表（仅显示当前用户作为审批人的工单）")
    public Result<List<PermissionRequest>> getPending(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        Boolean isAdmin = (Boolean) request.getAttribute("isAdmin");
        if (userId == null) userId = "user_admin";

        // 管理员可以看到所有待审批工单，普通用户只看到自己作为审批人的工单
        List<PermissionRequest> list = requestService.getPendingRequests(userId);
        // 若当前用户是资源 Owner 但未在 approver 中，也应该能看到（通过 Service 层已处理）
        // 同时管理员可额外看到未指定审批人的工单
        if (Boolean.TRUE.equals(isAdmin)) {
            List<PermissionRequest> unassigned = requestService.getUnassignedPendingRequests();
            if (unassigned != null && !unassigned.isEmpty()) {
                java.util.Set<String> seenIds = new java.util.HashSet<>();
                java.util.List<PermissionRequest> merged = new java.util.ArrayList<>();
                for (PermissionRequest r : list) {
                    if (seenIds.add(r.getId())) merged.add(r);
                }
                for (PermissionRequest r : unassigned) {
                    if (seenIds.add(r.getId())) merged.add(r);
                }
                // 按创建时间倒序
                merged.sort((a, b) -> {
                    if (a.getCreatedAt() == null) return 1;
                    if (b.getCreatedAt() == null) return -1;
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                });
                list = merged;
            }
        }
        return Result.success(list);
    }

    @GetMapping("/my")
    @Operation(summary = "获取我的申请")
    public Result<List<PermissionRequest>> getMy(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        if (userId == null) userId = "user_admin";
        return Result.success(requestService.getMyRequests(userId));
    }

    // ==================== 工单详情 ====================

    @GetMapping("/{id}")
    @Operation(summary = "获取工单详情")
    public Result<PermissionRequest> getById(@PathVariable String id) {
        PermissionRequest req = requestService.getById(id);
        if (req == null) return Result.error(404, "工单不存在");
        return Result.success(req);
    }

    // ==================== 创建工单 ====================

    /**
     * 简单提交（兼容原有接口）
     */
    @PostMapping
    @Operation(summary = "提交权限申请")
    public Result<PermissionRequest> submit(@RequestBody PermissionRequestDTO dto, HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        String username = (String) request.getAttribute("username");
        if (userId == null) userId = "user_admin";
        if (username == null) username = "admin";

        PermissionRequest req = new PermissionRequest();
        req.setId(UUID.randomUUID().toString().replace("-", ""));
        req.setApplicantId(userId);
        req.setApplicantName(username);
        req.setResourceType(dto.getResourceType());
        req.setResourceId(dto.getResourceId());
        req.setResourceName(dto.getResourceName());
        req.setRequestedPermissions(dto.getRequestedPermissions());
        req.setReason(dto.getReason());
        req.setApprovalLevel(dto.getApprovalLevel() != null ? dto.getApprovalLevel() : 1);
        req.setCurrentApprovalStep(0);

        // 根据资源查找审批人
        String[] approver = resolveApprover(dto.getResourceType(), dto.getResourceId(), null, null);
        req.setApproverId(approver[0]);
        req.setApproverName(approver[1]);
        // 支持过期时间
        if (dto.getExpireTime() != null) {
            req.setExpiredAt(dto.getExpireTime());
        }
        PermissionRequest result = requestService.submitRequest(req);
        return Result.success("权限申请已提交", result);
    }

    /**
     * 批量资源权限工单（对标阿里云DMS）
     * 支持一次选择多个库/表，批量申请权限
     */
    @PostMapping("/ticket")
    @Operation(summary = "提交权限工单（多资源）")
    public Result<List<PermissionRequest>> submitTicket(@RequestBody PermissionTicketDTO dto, HttpServletRequest httpRequest) {
        String userId = (String) httpRequest.getAttribute("userId");
        String username = (String) httpRequest.getAttribute("username");
        if (userId == null) userId = "user_admin";
        if (username == null) username = "admin";

        List<PermissionTicketDTO.ResourceItem> resources = dto.getResources();
        if (resources == null || resources.isEmpty()) {
            return Result.error(400, "请至少选择一个数据库或表");
        }
        if (dto.getPermissionTypes() == null || dto.getPermissionTypes().isEmpty()) {
            return Result.error(400, "请选择要申请的权限类型");
        }

        // 计算过期时间
        java.time.LocalDateTime expireAt = dto.getExpireTime();
        if (expireAt == null && dto.getExpireDays() != null && dto.getExpireDays() > 0) {
            expireAt = java.time.LocalDateTime.now().plusDays(dto.getExpireDays());
        }

        // 拼接权限字符串
        String permsStr = String.join(",", dto.getPermissionTypes());

        // 为每个选中的资源创建一条工单记录
        List<PermissionRequest> createdList = new java.util.ArrayList<>();
        for (PermissionTicketDTO.ResourceItem resource : resources) {
            PermissionRequest req = new PermissionRequest();
            req.setId(UUID.randomUUID().toString().replace("-", ""));
            req.setApplicantId(userId);
            req.setApplicantName(username);

            // 工单标题
            String title = dto.getTitle();
            if (title == null || title.isEmpty()) {
                title = String.format("【%s】申请%s的%s",
                        resolveTypeLabel(dto.getTicketType()),
                        resource.getSchemaName() != null ? resource.getSchemaName() : resource.getInstanceName(),
                        formatPermissions(dto.getPermissionTypes()));
            }
            req.setResourceName(title);

            // 按资源类型设置 resourceType 和 resourceId
            String rType = resource.getResourceType();
            if (rType == null) {
                rType = resolveResourceType(dto.getTicketType(), resource);
            }
            req.setResourceType(rType);
            if ("instance".equals(rType)) {
                req.setResourceId(resource.getInstanceId());
            } else if ("table".equals(rType)) {
                req.setResourceId(resource.getInstanceId() + ":" +
                        (resource.getSchemaName() != null ? resource.getSchemaName() : ""));
            } else {
                req.setResourceId(resource.getInstanceId() + ":" +
                        (resource.getSchemaName() != null ? resource.getSchemaName() : ""));
            }

            // 根据资源查找审批人（支持逐级回退：schema/table -> instance）
            String parentResourceType = null;
            String parentResourceId = null;
            String ownerResourceType = rType;
            String ownerResourceId = req.getResourceId();
            if (!"instance".equals(rType)) {
                parentResourceType = "instance";
                parentResourceId = resource.getInstanceId();
                // schema 资源：Owner 可能匹配 schema 名（仅 schema 部分），尝试两种
            }
            String[] approver = resolveApprover(ownerResourceType, ownerResourceId, parentResourceType, parentResourceId);
            // schema 情况下如果 Owner 未匹配 "instanceId:schemaName"，尝试仅匹配 schemaName
            if (approver[0] == null && !"instance".equals(rType) && resource.getSchemaName() != null) {
                String[] fallback = resolveApprover(rType, resource.getSchemaName(), "instance", resource.getInstanceId());
                if (fallback[0] != null) {
                    approver = fallback;
                }
            }
            req.setApproverId(approver[0]);
            req.setApproverName(approver[1]);

            req.setRequestedPermissions(permsStr);
            req.setReason(dto.getReason());
            req.setStatus("pending");
            req.setApprovalLevel(1);
            req.setCurrentApprovalStep(0);
            req.setExpiredAt(expireAt);
            req.setCreatedAt(java.time.LocalDateTime.now());

            // 将额外信息存入 reason 前面（JSON格式存储资源明细）
            String extraInfo = buildExtraInfo(dto, resource);
            if (extraInfo != null) {
                req.setReason(extraInfo + "\n" + (dto.getReason() != null ? dto.getReason() : ""));
            }

            requestService.submitRequest(req);
            createdList.add(req);
        }

        log.info("权限工单批量提交: {} 个资源, 申请人: {}", createdList.size(), username);
        return Result.success(String.format("已提交 %d 条权限工单申请", createdList.size()), createdList);
    }

    // ==================== 审批操作 ====================

    @PostMapping("/{id}/approve")
    @Operation(summary = "审批通过（自动授权）")
    public Result<Boolean> approve(@PathVariable String id, @RequestBody ApproveRequestDTO dto, HttpServletRequest request) {
        String approverId = (String) request.getAttribute("userId");
        String approverName = (String) request.getAttribute("username");
        Boolean isAdmin = (Boolean) request.getAttribute("isAdmin");
        if (approverId == null) approverId = "user_admin";
        if (approverName == null) approverName = "admin";

        if (!requestService.canApprove(id, approverId, Boolean.TRUE.equals(isAdmin))) {
            return Result.error(403, "无权审批该工单，非该资源的 Owner 或管理员");
        }

        boolean result = requestService.approveRequest(id, approverId, approverName, true, dto.getComment());
        return Result.success("审批通过并自动授权", result);
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "审批拒绝")
    public Result<Boolean> reject(@PathVariable String id, @RequestBody ApproveRequestDTO dto, HttpServletRequest request) {
        String approverId = (String) request.getAttribute("userId");
        String approverName = (String) request.getAttribute("username");
        Boolean isAdmin = (Boolean) request.getAttribute("isAdmin");
        if (approverId == null) approverId = "user_admin";
        if (approverName == null) approverName = "admin";

        if (!requestService.canApprove(id, approverId, Boolean.TRUE.equals(isAdmin))) {
            return Result.error(403, "无权审批该工单，非该资源的 Owner 或管理员");
        }

        boolean result = requestService.approveRequest(id, approverId, approverName, false, dto.getComment());
        return Result.success("审批已拒绝", result);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "取消申请")
    public Result<Boolean> cancel(@PathVariable String id, HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        if (userId == null) userId = "user_admin";
        boolean result = requestService.cancelRequest(id, userId);
        return Result.success("申请已取消", result);
    }

    // ==================== 统计 ====================

    @GetMapping("/stats")
    @Operation(summary = "工单统计")
    public Result<Object> stats(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        if (userId == null) userId = "user_admin";

        long pendingCount = requestService.lambdaQuery()
                .eq(PermissionRequest::getStatus, "pending").count();
        long myPendingCount = requestService.lambdaQuery()
                .eq(PermissionRequest::getApplicantId, userId)
                .eq(PermissionRequest::getStatus, "pending").count();
        long myApprovedCount = requestService.lambdaQuery()
                .eq(PermissionRequest::getApplicantId, userId)
                .eq(PermissionRequest::getStatus, "approved").count();

        java.util.Map<String, Object> stats = new java.util.LinkedHashMap<>();
        stats.put("pending", pendingCount);
        stats.put("myPending", myPendingCount);
        stats.put("myApproved", myApprovedCount);
        return Result.success(stats);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 查找资源的审批人
     * 1) 优先匹配当前资源类型在 sys_resource_owner 中的 Owner
     * 2) 若未找到，回退到父级资源（schema -> instance）
     * 3) 仍未找到，回退到管理员（isAdmin=true 的用户，取第一个）
     */
    private String[] resolveApprover(String resourceType, String resourceId,
                                     String parentResourceType, String parentResourceId) {
        // 1) 当前资源 Owner
        java.util.List<com.dataops.dms.entity.ResourceOwner> owners =
                resourceOwnerService.listByResource(resourceType, resourceId);
        if (owners != null && !owners.isEmpty()) {
            com.dataops.dms.entity.ResourceOwner owner = owners.get(0);
            return new String[] { owner.getOwnerUserId(), owner.getOwnerUsername() };
        }

        // 2) 父级资源 Owner
        if (parentResourceType != null && parentResourceId != null) {
            java.util.List<com.dataops.dms.entity.ResourceOwner> parentOwners =
                    resourceOwnerService.listByResource(parentResourceType, parentResourceId);
            if (parentOwners != null && !parentOwners.isEmpty()) {
                com.dataops.dms.entity.ResourceOwner owner = parentOwners.get(0);
                return new String[] { owner.getOwnerUserId(), owner.getOwnerUsername() };
            }
        }

        // 3) 管理员兜底
        try {
            com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.dataops.dms.entity.User> wrapper =
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
            wrapper.eq(com.dataops.dms.entity.User::getIsAdmin, true).last("LIMIT 1");
            com.dataops.dms.entity.User admin = userMapper.selectOne(wrapper);
            if (admin != null) {
                return new String[] { admin.getId(), admin.getUsername() };
            }
        } catch (Exception ignored) { }

        return new String[] { null, null };
    }

    private String resolveTypeLabel(String ticketType) {
        if (ticketType == null) return "库权限";
        switch (ticketType.toLowerCase()) {
            case "owner": return "Owner";
            case "table": return "表权限";
            default: return "库权限";
        }
    }

    private String resolveResourceType(String ticketType, PermissionTicketDTO.ResourceItem resource) {
        if ("table".equals(ticketType)) return "table";
        if ("owner".equals(ticketType)) return "instance";
        return "schema";
    }

    private String formatPermissions(List<String> perms) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < perms.size(); i++) {
            if (i > 0) sb.append("/");
            String p = perms.get(i);
            switch (p) {
                case "query": sb.append("查询"); break;
                case "export": sb.append("导出"); break;
                case "update": sb.append("变更"); break;
                case "ddl": sb.append("结构变更"); break;
                default: sb.append(p); break;
            }
        }
        return sb.toString();
    }

    private String buildExtraInfo(PermissionTicketDTO dto, PermissionTicketDTO.ResourceItem resource) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.Map<String, Object> info = new java.util.LinkedHashMap<>();
            info.put("databaseId", resource.getInstanceId());
            info.put("databaseName", resource.getSchemaName());
            info.put("instanceId", resource.getInstanceId());
            info.put("instanceName", resource.getInstanceName());
            info.put("tables", resource.getTableNames());
            info.put("permissionTypes", dto.getPermissionTypes());
            info.put("ticketType", dto.getTicketType());
            if (dto.getExpireDays() != null) info.put("expireDays", dto.getExpireDays());
            if (dto.getExpireTime() != null) info.put("expireTime", dto.getExpireTime());
            return "[JSON]" + mapper.writeValueAsString(info);
        } catch (Exception e) {
            return null;
        }
    }
}
