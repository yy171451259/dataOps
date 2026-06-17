package com.dataops.dms.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 工单实体
 * 支持数据变更、权限申请等多种工单类型
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ticket")
public class Ticket extends BaseEntity {

    /**
     * 工单类型: data_change, permission_apply, etc.
     */
    private String type;

    /**
     * 工单标题
     */
    private String title;

    /**
     * 工单描述
     */
    private String description;

    /**
     * 状态: pending, approving, approved, rejected, cancelled, executing, done, failed, rolled_back
     */
    private String status;

    /**
     * 优先级: low, normal, high, urgent
     */
    private String priority;

    /**
     * 创建人ID
     */
    private String creatorId;

    /**
     * 当前审批人ID
     */
    private String currentApproverId;

    /**
     * 所有审批人ID（逗号分隔，支持多个）
     */
    private String approverIds;

    /**
     * 所有审批人姓名（逗号分隔，支持多个）
     */
    private String approverNames;

    /**
     * 关联实例ID
     */
    private String instanceId;

    /**
     * 目标Schema名（实例下的具体 schema）
     */
    private String schemaName;

    /**
     * SQL内容（数据变更工单）
     */
    private String sqlContent;

    /**
     * 变更类型: INSERT, UPDATE, DELETE, ALTER, CREATE_INDEX, etc.
     */
    private String changeType;

    // ============ DDL无锁变更（结构变更） ============

    /**
     * 是否使用无锁DDL
     */
    private Boolean useOnlineDdl;

    /**
     * 无锁变更策略: mysql_online, pt_osc, gh_ost
     */
    private String onlineDdlStrategy;

    /**
     * DDL执行进度(0-100)
     */
    private Integer ddlProgress;

    // ============ DML无锁变更（数据变更） ============

    /**
     * 是否使用无锁DML（分批执行）
     */
    private Boolean useLockFreeDml;

    /**
     * DML批次大小（默认1000）
     */
    private Integer dmlBatchSize;

    /**
     * DML批次间隔毫秒（默认100ms）
     */
    private Integer dmlBatchInterval;

    /**
     * DML执行进度（已处理批次）
     */
    private Integer dmlBatchCount;

    /**
     * DML累计影响行数
     */
    private Long dmlTotalAffected;

    /**
     * 执行时间
     */
    private LocalDateTime executeTime;

    /**
     * 回滚时间
     */
    private LocalDateTime rollbackTime;

    /**
     * 失败错误信息
     */
    private String errorMsg;

    /**
     * 工单内容JSON（扩展字段）
     */
    private String content;

    // ============ 对标阿里云DMS新增字段 ============

    /**
     * 预估影响行数（通过EXPLAIN估算）
     */
    private Integer estimateAffectedRows;

    /**
     * 执行超时时间（秒），默认600秒=10分钟，防止长事务锁表
     */
    private Integer executionTimeoutSeconds;

    /**
     * 无锁DML执行状态: running, paused, stopping, stopped, completed
     */
    private String dmlStatus;

    /**
     * 无锁DML预计总批次数
     */
    private Integer dmlTotalBatches;

    /**
     * 无锁DML执行进度百分比(0-100)
     */
    private Integer dmlProgressPercent;

    /**
     * 审批超时时间（创建后N小时未审批自动拒绝）
     */
    private LocalDateTime approvalDeadline;

    /**
     * 已审批级数
     */
    private Integer approvedLevel;

    /**
     * 执行方式: auto（审批通过后自动执行）, manual（审批通过后由提交者手动执行）
     */
    private String execMode;

    /**
     * 原因类型: config_fix, init_data, bug_fix, no_backend_feature, data_cleanup, test, misoperation, other
     */
    private String reasonType;
}