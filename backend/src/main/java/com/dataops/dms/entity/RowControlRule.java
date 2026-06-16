package com.dataops.dms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 行级控制规则实体
 * 控制用户对表中特定行的访问权限
 */
@Data
@TableName("sys_row_control_rule")
public class RowControlRule {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 规则名称
     */
    private String name;

    /**
     * 实例ID
     */
    private String instanceId;

    /**
     * 数据库名称
     */
    private String schemaName;

    /**
     * 表名
     */
    private String tableName;

    /**
     * 过滤条件（SQL WHERE子句）
     */
    private String filterCondition;

    /**
     * 过滤条件描述
     */
    private String filterDescription;

    /**
     * 目标用户ID列表（逗号分隔）
     */
    private String targetUserIds;

    /**
     * 目标角色ID列表（逗号分隔）
     */
    private String targetRoleIds;

    /**
     * 是否启用
     */
    private Boolean isActive;

    /**
     * 优先级（数值越小优先级越高）
     */
    private Integer priority;

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

    /**
     * 创建人
     */
    private String createdBy;
}
