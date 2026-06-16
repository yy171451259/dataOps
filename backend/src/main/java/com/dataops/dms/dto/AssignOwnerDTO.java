package com.dataops.dms.dto;

import lombok.Data;

@Data
public class AssignOwnerDTO {
    private String resourceType;
    private String resourceId;
    private String resourceName;
    private String ownerUserId;
    private String ownerUsername;
    private String parentResourceType;
    private String parentResourceId;
}
