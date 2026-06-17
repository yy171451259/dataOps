package com.dataops.dms.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dataops.dms.common.result.PageResult;
import com.dataops.dms.entity.PermissionRequest;

import java.util.List;

public interface PermissionRequestService extends IService<PermissionRequest> {

    PermissionRequest submitRequest(PermissionRequest request);

    boolean approveRequest(String requestId, String approverId, String approverName, boolean approved, String comment);

    List<PermissionRequest> getPendingRequests(String approverId);

    /**
     * 分页获取待审批列表
     */
    PageResult<PermissionRequest> getPendingRequestsPage(String approverId, Integer page, Integer size);

    List<PermissionRequest> getUnassignedPendingRequests();

    List<PermissionRequest> getMyRequests(String applicantId);

    /**
     * 分页获取我的申请列表
     *
     * @param applicantId 申请人ID
     * @param status      状态筛选（可选）
     * @param page        页码，从1开始
     * @param size        每页条数
     */
    PageResult<PermissionRequest> getMyRequestsPage(String applicantId, String status, Integer page, Integer size);

    boolean cancelRequest(String requestId, String userId);

    /**
     * 检查当前用户是否有权审批该工单
     * 规则：当前用户是工单的 approver，或是管理员，或当前用户是资源的 Owner
     */
    boolean canApprove(String requestId, String userId, boolean isAdmin);
}
