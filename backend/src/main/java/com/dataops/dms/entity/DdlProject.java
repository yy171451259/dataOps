package com.dataops.dms.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DDL项目工单
 * 对标阿里云DMS的项目工单概念，一个工单关联多张表的变更
 * 流程: 创建工单 → 结构设计 → 环境发布 → 结束
 */
@Data
@TableName("ddl_project")
public class DdlProject {

    /**
     * 主键ID
     */
    private String id;

    /**
     * 项目名称
     */
    private String projectName;

    /**
     * 项目背景/业务描述
     */
    private String businessBackground;

    /**
     * 基准实例ID（一般是开发/测试库）
     */
    private String baseInstanceId;

    /**
     * 基准实例名称
     */
    private String baseInstanceName;

    /**
     * 基准Schema名
     */
    private String baseSchemaName;

    /**
     * 工单状态: DESIGNING, DEV_EXECUTING, DEV_DONE, INTEGRATION_PENDING, INTEGRATION_DONE,
     *           STAGING_PENDING, STAGING_DONE, PRODUCTION_PENDING, PRODUCTION_DONE, PUBLISHED, CLOSED
     */
    private String status;

    /**
     * 当前环境阶段: CREATE, DESIGN, DEV, TEST, INTEGRATION, STAGING, PRODUCTION, FINISH
     */
    private String currentStage;

    /**
     * 负责人
     */
    private String owner;

    /**
     * 变更相关人(JSON数组, 如: ["user1","user2"])
     */
    private String relatedPersons;

    /**
     * 安全规则
     */
    private String securityRule;

    /**
     * 优先级: low, normal, high, urgent
     */
    private String priority;

    /**
     * 变更表数量(冗余, 方便列表展示)
     */
    private Integer tableCount;

    /**
     * 关闭时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime closedAt;

    /**
     * 创建人
     */
    private String createdBy;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
