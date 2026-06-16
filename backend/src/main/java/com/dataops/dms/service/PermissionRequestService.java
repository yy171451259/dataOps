package com.dataops.dms.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dataops.dms.entity.PermissionRequest;

import java.util.List;

public interface PermissionRequestService extends IService<PermissionRequest> {

    PermissionRequest submitRequest(PermissionRequest request);

    boolean approveRequest(String requestId, String approverId, String approverName, boolean approved, String comment);

    List<PermissionRequest> getPendingRequests(String approverId);

    List<PermissionRequest> getMyRequests(String applicantId);

    boolean cancelRequest(String requestId, String userId);
}
