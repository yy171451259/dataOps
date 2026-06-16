package com.dataops.dms.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DDL变更流转任务
 * 记录每个环境的DDL变更执行状态，支持 DEV → INTEGRATION → PRODUCTION 流转
 */
@Data
@TableName("ddl_change_task")
public class DdlChangeTask {

    /**
     * 主键ID
     */
    private String id;

    /**
     * 变更标题
     */
    private String title;

    /**
     * 环境: DEV, INTEGRATION, PRODUCTION
     */
    private String environment;

    /**
     * 状态: PENDING, EXECUTING, SUCCESS, FAILED, SKIPPED, ROLLED_BACK, ROLLING_BACK
     */
    private String status;

    /**
     * 目标实例ID
     */
    private String instanceId;

    /**
     * 目标实例名称（冗余，方便前端展示）
     */
    private String instanceName;

    /**
     * 目标Schema名
     */
    private String schemaName;

    /**
     * DDL变更SQL
     */
    private String sqlContent;

    /**
     * 来源任务ID（上一个环境的任务ID）
     */
    private String sourceTaskId;

    /**
     * 执行人
     */
    private String executedBy;

    /**
     * 执行时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime executedAt;

    /**
     * 执行耗时（秒）
     */
    private Integer durationSeconds;

    /**
     * 执行结果消息
     */
    private String resultMessage;

    /**
     * 自动生成的回滚SQL
     */
    private String rollbackSql;

    /**
     * 是否为回退到来源环境而产生的任务
     */
    private Boolean rollbackToSource;

    /**
     * 回退时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime rolledBackAt;

    /**
     * 回退人
     */
    private String rolledBackBy;

    /**
     * 上游环境原始SQL（方便追溯）
     */
    private String sourceSqlContent;

    /**
     * 上游环境执行人
     */
    private String sourceExecutedBy;

    /**
     * 上游环境执行时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime sourceExecutedAt;

    /**
     * 创建人
     */
    private String createdBy;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;
}
