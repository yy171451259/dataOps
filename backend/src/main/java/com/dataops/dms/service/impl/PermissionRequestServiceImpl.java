package com.dataops.dms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataops.dms.common.result.PageResult;
import com.dataops.dms.entity.PermissionRequest;
import com.dataops.dms.entity.UserPermission;
import com.dataops.dms.mapper.PermissionRequestMapper;
import com.dataops.dms.mapper.UserPermissionMapper;
import com.dataops.dms.service.PermissionRequestService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class PermissionRequestServiceImpl extends ServiceImpl<PermissionRequestMapper, PermissionRequest> implements PermissionRequestService {

    @javax.annotation.Resource
    private UserPermissionMapper userPermissionMapper;

    @javax.annotation.Resource
    private com.dataops.dms.service.ResourceOwnerService resourceOwnerService;

    @Override
    @Transactional
    public PermissionRequest submitRequest(PermissionRequest request) {
        request.setStatus("pending");
        request.setCreatedAt(LocalDateTime.now());
        this.save(request);
        log.info("权限工单提交: {}, 标题: {}, 申请人: {}", request.getId(), request.getResourceName(), request.getApplicantName());
        return request;
    }

    @Override
    @Transactional
    public boolean approveRequest(String requestId, String approverId, String approverName, boolean approved, String comment) {
        PermissionRequest request = this.getById(requestId);
        if (request == null) throw new RuntimeException("申请不存在");
        if (!"pending".equals(request.getStatus())) throw new RuntimeException("该申请已处理，无法重复审批");

        request.setApproverId(approverId);
        request.setApproverName(approverName);
        request.setApprovalComment(comment);
        request.setApprovedAt(LocalDateTime.now());

        if (approved) {
            request.setStatus("approved");
            log.info("权限工单审批通过: {}, 审批人: {}", requestId, approverName);

            // ★ 核心：审批通过后自动授权到 sys_permission 表
            autoGrantPermissions(request);
        } else {
            request.setStatus("rejected");
            log.info("权限工单被拒绝: {}, 审批人: {}", requestId, approverName);
        }
        return this.updateById(request);
    }

    /**
     * 审批通过后自动授予资源级权限
     * <p>
     * 注意：申请提交时的 resourceId 格式为 "instanceId" 或 "instanceId:schemaName"，
     * 但权限检查时使用不同的格式：
     * - 左侧树 Schema 可见性检查：resourceType="database", resourceId=schemaName
     * - SQL 执行权限检查：resourceType="schema", resourceId=instanceId
     * - 实例级权限检查：resourceType="instance", resourceId=instanceId
     */
    private void autoGrantPermissions(PermissionRequest request) {
        try {
            String applicantId = request.getApplicantId();
            String resourceId = request.getResourceId();
            String resourceType = request.getResourceType();
            String perms = request.getRequestedPermissions();
            
            if (perms == null || perms.isEmpty()) return;
            if (resourceId == null || resourceId.isEmpty()) return;

            // 解析 resourceId：可能是 instanceId 或 instanceId:schemaName
            String instanceIdPart = resourceId;
            String schemaNamePart = null;
            int colonIdx = resourceId.indexOf(':');
            if (colonIdx > 0) {
                instanceIdPart = resourceId.substring(0, colonIdx);
                schemaNamePart = resourceId.substring(colonIdx + 1);
                if (schemaNamePart != null && schemaNamePart.isEmpty()) {
                    schemaNamePart = null;
                }
            }

            // 解析权限操作类型
            String[] permArray = perms.replace("[", "").replace("]", "").replace("\"", "").split(",");
            LocalDateTime expireTime = request.getExpiredAt();

            // 根据资源类型确定需要写入的权限条目
            // 每个条目是 (写入的 resourceType, 写入的 resourceId)
            java.util.List<java.util.Map.Entry<String, String>> permEntries = new java.util.ArrayList<>();

            if ("instance".equals(resourceType)) {
                // 实例级权限：只写 instance -> instance (保持与检查逻辑一致)
                permEntries.add(new java.util.AbstractMap.SimpleEntry<>("instance", instanceIdPart));
            } else if (schemaNamePart != null) {
                // Schema 级权限：同时写 2 条，确保可见性和SQL执行都能匹配
                // 1) database + schemaName —— 左侧树 Schema 列表可见性检查 (DatabaseController)
                // 2) schema + instanceId —— SQL 执行权限检查 (SqlController)
                permEntries.add(new java.util.AbstractMap.SimpleEntry<>("database", schemaNamePart));
                permEntries.add(new java.util.AbstractMap.SimpleEntry<>("schema", instanceIdPart));
            } else {
                // 回退：未知格式，按原始值写入
                permEntries.add(new java.util.AbstractMap.SimpleEntry<>(resourceType != null ? resourceType : "schema", resourceId));
            }

            for (String p : permArray) {
                String action = p.trim();
                if (action.isEmpty()) continue;

                for (java.util.Map.Entry<String, String> entry : permEntries) {
                    String writeType = entry.getKey();
                    String writeId = entry.getValue();

                    // 检查是否已存在相同权限（避免重复）
                    LambdaQueryWrapper<UserPermission> checkWrapper = new LambdaQueryWrapper<>();
                    checkWrapper.eq(UserPermission::getUserId, applicantId)
                               .eq(UserPermission::getResourceType, writeType)
                               .eq(UserPermission::getResourceId, writeId)
                               .eq(UserPermission::getAction, action);
                    Long existCount = userPermissionMapper.selectCount(checkWrapper);

                    if (existCount > 0) continue; // 已存在则跳过

                    UserPermission up = new UserPermission();
                    up.setUserId(applicantId);
                    up.setResourceType(writeType);
                    up.setResourceId(writeId);
                    up.setResourceName(request.getResourceName() != null ? request.getResourceName() : resourceId);
                    up.setAction(action);  // query / export / update / ddl
                    up.setFieldList(null); // 全部字段
                    up.setExpireTime(expireTime);
                    up.setGrantedBy(request.getApproverId());
                    up.setGrantedAt(LocalDateTime.now());

                    userPermissionMapper.insert(up);
                    log.info("自动授权: 用户={}, 资源类型={}, 资源ID={}, 权限={}", applicantId, writeType, writeId, action);
                }
            }

            log.info("审批通过: 用户 {} 已获得资源 {} 的访问权限", applicantId, resourceId);
        } catch (Exception e) {
            log.error("自动授权失败（不影响审批结果）: {}", e.getMessage(), e);
        }
    }

    @Override
    public List<PermissionRequest> getPendingRequests(String approverId) {
        // approverId 为空时返回所有 pending（兼容旧代码/管理员兜底查询），否则按审批人过滤
        LambdaQueryWrapper<PermissionRequest> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PermissionRequest::getStatus, "pending");
        if (approverId != null && !approverId.isEmpty()) {
            wrapper.eq(PermissionRequest::getApproverId, approverId);
        }
        wrapper.orderByDesc(PermissionRequest::getCreatedAt);
        return this.list(wrapper);
    }

    @Override
    public List<PermissionRequest> getUnassignedPendingRequests() {
        LambdaQueryWrapper<PermissionRequest> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PermissionRequest::getStatus, "pending")
               .isNull(PermissionRequest::getApproverId)
               .orderByDesc(PermissionRequest::getCreatedAt);
        return this.list(wrapper);
    }

    @Override
    public boolean canApprove(String requestId, String userId, boolean isAdmin) {
        if (isAdmin) return true;
        if (userId == null || userId.isEmpty()) return false;
        PermissionRequest request = this.getById(requestId);
        if (request == null) return false;
        if (userId.equals(request.getApproverId())) return true;
        // 若是资源 Owner，也可以审批
        if (resourceOwnerService != null && request.getResourceType() != null && request.getResourceId() != null) {
            if (resourceOwnerService.checkIsOwner(userId, request.getResourceType(), request.getResourceId())) {
                return true;
            }
            // 尝试将 resourceId 拆分为 instanceId:schemaName，匹配 schema 级 Owner
            int idx = request.getResourceId().indexOf(':');
            if (idx > 0 && !"instance".equals(request.getResourceType())) {
                String schemaPart = request.getResourceId().substring(idx + 1);
                String instancePart = request.getResourceId().substring(0, idx);
                if (resourceOwnerService.checkIsOwner(userId, request.getResourceType(), schemaPart)) {
                    return true;
                }
                if (resourceOwnerService.checkIsOwner(userId, "instance", instancePart)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public List<PermissionRequest> getMyRequests(String applicantId) {
        LambdaQueryWrapper<PermissionRequest> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PermissionRequest::getApplicantId, applicantId)
               .orderByDesc(PermissionRequest::getCreatedAt);
        return this.list(wrapper);
    }

    @Override
    public PageResult<PermissionRequest> getMyRequestsPage(String applicantId, String status, Integer page, Integer size) {
        Integer pageNum = page == null || page <= 0 ? 1 : page;
        Integer pageSize = size == null || size <= 0 ? 15 : (size > 200 ? 200 : size);

        LambdaQueryWrapper<PermissionRequest> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PermissionRequest::getApplicantId, applicantId);
        if (status != null && !status.isEmpty()) {
            wrapper.eq(PermissionRequest::getStatus, status);
        }
        wrapper.orderByDesc(PermissionRequest::getCreatedAt);

        Page<PermissionRequest> query = new Page<>(pageNum, pageSize);
        IPage<PermissionRequest> result = this.page(query, wrapper);

        return PageResult.of(pageNum, pageSize, result.getTotal(), result.getRecords());
    }

    @Override
    public PageResult<PermissionRequest> getPendingRequestsPage(String approverId, Integer page, Integer size) {
        Integer pageNum = page == null || page <= 0 ? 1 : page;
        Integer pageSize = size == null || size <= 0 ? 15 : (size > 200 ? 200 : size);

        LambdaQueryWrapper<PermissionRequest> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PermissionRequest::getStatus, "pending");
        
        if (approverId != null && !approverId.isEmpty()) {
            wrapper.and(w -> {
                w.eq(PermissionRequest::getApproverId, approverId);
                w.or().apply("FIND_IN_SET({0}, approver_ids)", approverId);
                w.or().apply("CONCAT(',', approver_ids, ',') LIKE {0}", "%," + approverId + ",%");
            });
        }
        
        wrapper.orderByDesc(PermissionRequest::getCreatedAt);

        Page<PermissionRequest> query = new Page<>(pageNum, pageSize);
        IPage<PermissionRequest> result = this.page(query, wrapper);

        return PageResult.of(pageNum, pageSize, result.getTotal(), result.getRecords());
    }

    @Override
    @Transactional
    public boolean cancelRequest(String requestId, String userId) {
        PermissionRequest request = this.getById(requestId);
        if (request == null) throw new RuntimeException("申请不存在");
        if (!request.getApplicantId().equals(userId)) throw new RuntimeException("只能取消自己的申请");
        if (!"pending".equals(request.getStatus())) throw new RuntimeException("只能取消待审批的申请");

        request.setStatus("cancelled");
        request.setApprovedAt(LocalDateTime.now());
        return this.updateById(request);
    }
}
