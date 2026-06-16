package com.dataops.dms.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

/**
 * 创建工单DTO
 */
@Data
public class TicketCreateDTO {

    /**
     * 工单标题
     */
    @NotBlank(message = "工单标题不能为空")
    private String title;

    /**
     * 工单描述
     */
    private String description;

    /**
     * 优先级: low, normal, high, urgent
     */
    private String priority = "normal";

    /**
     * 数据库实例ID
     */
    @NotBlank(message = "数据库实例不能为空")
    private String instanceId;

    /**
     * 目标Schema名（实例下的具体 schema）
     */
    @NotBlank(message = "Schema名不能为空")
    private String schemaName;

    /**
     * SQL内容
     */
    @NotBlank(message = "SQL内容不能为空")
    private String sqlContent;

    /**
     * 回滚SQL
     */
    private String rollbackSql;

    /**
     * 变更类型: ddl, dml, data_export
     */
    @NotBlank(message = "变更类型不能为空")
    private String changeType;

    // ============ DDL无锁变更（结构变更） ============

    /**
     * 是否使用无锁变更（Online DDL）
     */
    private Boolean useOnlineDdl = false;

    /**
     * 无锁变更策略: mysql_online, pt_osc, gh_ost
     */
    private String onlineDdlStrategy;

    // ============ DML无锁变更（数据变更） ============

    /**
     * 是否使用无锁DML（分批执行）
     */
    private Boolean useLockFreeDml = false;

    /**
     * DML批次大小（默认1000）
     */
    private Integer dmlBatchSize = 1000;

    /**
     * DML批次间隔毫秒（默认100ms）
     */
    private Integer dmlBatchInterval = 100;

    /**
     * 项目经理审批人ID
     */
    private String managerId;

    /**
     * DBA审批人ID
     */
    private String dbaId;

    /**
     * 定时执行时间（可选）
     */
    private String scheduledAt;

    /**
     * 执行超时时间（秒），默认600秒=10分钟
     */
    private Integer executionTimeoutSeconds = 600;

    /**
     * 审批超时小时数（创建后N小时未审批自动拒绝），0表示不超时
     */
    private Integer approvalTimeoutHours = 0;
}