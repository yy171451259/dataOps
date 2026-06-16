package com.dataops.dms.dto;

import lombok.Data;

@Data
public class AccessControlDTO {
    private String resourceType;
    private String resourceId;
    private String resourceName;
    private Boolean enabled;
    /** Schema层级需要知道父级实例ID，用于校验实例是否已限制访问 */
    private String parentResourceId;
}
