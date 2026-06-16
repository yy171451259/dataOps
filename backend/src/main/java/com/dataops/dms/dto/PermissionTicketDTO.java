package com.dataops.dms.dto;

import lombok.Data;

import java.util.List;

/**
 * 权限工单DTO - 支持多资源批量申请（对标阿里云DMS权限申请工单）
 */
@Data
public class PermissionTicketDTO {
    /** 工单标题 */
    private String title;
    /** 背景描述/原因 */
    private String reason;
    /** 工单类型: owner / database / table */
    private String ticketType;
    /** 选择的资源列表 [{instanceId, schemaName, instanceId, instanceName}] */
    private List<ResourceItem> resources;
    /** 申请的权限类型: query(查询), export(导出), update(变更), ddl(结构变更) */
    private List<String> permissionTypes;
    /** 过期时间(天) 或具体日期 */
    private Integer expireDays;
    /** 过期时间(精确到秒) - 可选，优先级高于expireDays */
    private java.time.LocalDateTime expireTime;

    @Data
    public static class ResourceItem {
        private String instanceId;
        private String schemaName;
        private String instanceName;
        /** 表级别: 可选，表名列表 */
        private List<String> tableNames;
    }
}
