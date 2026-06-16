package com.dataops.dms.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 审计日志实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("audit_log")
public class AuditLog extends BaseEntity {

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 操作类型: query, insert, update, delete, login, etc.
     */
    private String action;

    /**
     * 资源类型: schema, table, ticket, system
     */
    private String resourceType;

    /**
     * 资源ID
     */
    private String resourceId;

    /**
     * 操作详情JSON
     */
    private String detail;

    /**
     * 客户端IP
     */
    private String clientIp;

    /**
     * User-Agent
     */
    private String userAgent;

    /**
     * 风险等级: low, medium, high
     */
    private String riskLevel;

    /**
     * 操作时间
     */
    private LocalDateTime createTime;
}
