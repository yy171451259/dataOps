package com.dataops.dms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataops.dms.entity.Permission;
import com.dataops.dms.entity.PermissionRequest;
import com.dataops.dms.mapper.PermissionMapper;
import com.dataops.dms.mapper.PermissionRequestMapper;
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
    private PermissionMapper permissionMapper;

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
     */
    private void autoGrantPermissions(PermissionRequest request) {
        try {
            String applicantId = request.getApplicantId();
            String resourceId = request.getResourceId();
            String resourceType = request.getResourceType();
            // requestedPermissions 格式: ["query","export"] 或 "query,export"
            String perms = request.getRequestedPermissions();
            
            if (perms == null || perms.isEmpty()) return;

            // 解析权限
            String[] permArray = perms.replace("[", "").replace("]", "").replace("\"", "").split(",");
            LocalDateTime expireTime = request.getExpiredAt();

            for (String p : permArray) {
                String action = p.trim();
                if (action.isEmpty()) continue;

                // 检查是否已存在相同权限（避免重复）
                LambdaQueryWrapper<Permission> checkWrapper = new LambdaQueryWrapper<>();
                checkWrapper.eq(Permission::getRoleId, applicantId)
                           .eq(Permission::getResourceType, resourceType)
                           .eq(Permission::getResourceId, resourceId)
                           .eq(Permission::getAction, action);
                Long existCount = permissionMapper.selectCount(checkWrapper);

                if (existCount > 0) continue; // 已存在则跳过

                Permission permission = new Permission();
                permission.setRoleId(applicantId);
                permission.setResourceType(resourceType != null ? resourceType : "schema");
                permission.setResourceId(resourceId);
                permission.setResourceName(request.getResourceName() != null ? request.getResourceName() : resourceId);
                permission.setAction(action);  // query / export / update / ddl
                permission.setFieldList(null); // 全部字段
                permission.setExpireTime(expireTime);
                permission.setCreateTime(LocalDateTime.now());

                permissionMapper.insert(permission);
                log.info("自动授权: 用户={}, 资源={}, 权限={}", applicantId, resourceId, action);
            }

            log.info("审批通过: 用户 {} 已获得资源 {} 的访问权限", applicantId, resourceId);
        } catch (Exception e) {
            log.error("自动授权失败（不影响审批结果）: {}", e.getMessage(), e);
        }
    }

    @Override
    public List<PermissionRequest> getPendingRequests(String approverId) {
        LambdaQueryWrapper<PermissionRequest> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PermissionRequest::getStatus, "pending")
               .orderByDesc(PermissionRequest::getCreatedAt);
        return this.list(wrapper);
    }

    @Override
    public List<PermissionRequest> getMyRequests(String applicantId) {
        LambdaQueryWrapper<PermissionRequest> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PermissionRequest::getApplicantId, applicantId)
               .orderByDesc(PermissionRequest::getCreatedAt);
        return this.list(wrapper);
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
