package com.dataops.dms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 权限申请实体
 * 用户申请数据资源访问权限的工单记录
 */
@Data
@TableName("sys_permission_request")
public class PermissionRequest {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 申请人ID
     */
    private String applicantId;

    /**
     * 申请人姓名
     */
    private String applicantName;

    /**
     * 资源类型: schema, table, column
     */
    private String resourceType;

    /**
     * 资源ID
     */
    private String resourceId;

    /**
     * 资源名称
     */
    private String resourceName;

    /**
     * 申请的权限（逗号分隔）: SELECT, INSERT, UPDATE, DELETE
     */
    private String requestedPermissions;

    /**
     * 申请原因
     */
    private String reason;

    /**
     * 状态: pending, approved, rejected, cancelled, expired
     */
    private String status;

    /**
     * 审批人ID
     */
    private String approverId;

    /**
     * 审批人姓名
     */
    private String approverName;

    /**
     * 审批意见
     */
    private String approvalComment;

    /**
     * 审批级别
     */
    private Integer approvalLevel;

    /**
     * 当前审批步骤
     */
    private Integer currentApprovalStep;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * 审批时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime approvedAt;

    /**
     * 过期时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expiredAt;
}
