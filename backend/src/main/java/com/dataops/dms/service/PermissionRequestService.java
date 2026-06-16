package com.dataops.dms.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dataops.dms.entity.PermissionRequest;

import java.util.List;

public interface PermissionRequestService extends IService<PermissionRequest> {

    PermissionRequest submitRequest(PermissionRequest request);

    boolean approveRequest(String requestId, String approverId, String approverName, boolean approved, String comment);

    List<PermissionRequest> getPendingRequests(String approverId);

    List<PermissionRequest> getUnassignedPendingRequests();

    List<PermissionRequest> getMyRequests(String applicantId);

    boolean cancelRequest(String requestId, String userId);

    /**
     * 检查当前用户是否有权审批该工单
     * 规则：当前用户是工单的 approver，或是管理员，或当前用户是资源的 Owner
     */
    boolean canApprove(String requestId, String userId, boolean isAdmin);
}
