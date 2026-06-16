package com.dataops.dms.dto;

import lombok.Data;

@Data
public class PermissionRequestDTO {
    private String resourceType;
    private String resourceId;
    private String resourceName;
    private String requestedPermissions;
    private String reason;
    private Integer approvalLevel;
    /** 权限过期时间 */
    @com.fasterxml.jackson.annotation.JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private java.time.LocalDateTime expireTime;
}
