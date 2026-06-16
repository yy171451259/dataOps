package com.dataops.dms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 权限操作审计日志
 * 记录所有权限授予、撤销、角色分配等操作
 */
@Data
@TableName("sys_permission_audit_log")
public class PermissionAuditLog {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /** 操作类型: GRANT, REVOKE, ROLE_ASSIGN, ROLE_REVOKE, CLEANUP */
    private String operation;

    /** 目标类型: USER, ROLE */
    private String targetType;

    /** 目标ID（用户ID或角色ID） */
    private String targetId;

    /** 资源类型 */
    private String resourceType;

    /** 资源ID */
    private String resourceId;

    /** 授予的操作 */
    private String action;

    /** 操作详情JSON */
    private String detail;

    /** 操作人ID */
    private String operatorId;

    /** 操作人名称 */
    private String operatorName;

    /** IP地址 */
    private String ipAddress;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
