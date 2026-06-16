package com.dataops.dms.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 权限实体 - 支持库/表/字段级细粒度权限
 */
@Data
@TableName("sys_permission")
public class Permission {
    private String id;
    private String name;
    private String code;
    private String resource;
    private String action;
    private String description;

    /**
     * 角色ID或用户ID
     */
    @TableField("role_id")
    private String roleId;

    /**
     * 资源类型: schema, table, column
     */
    @TableField("resource_type")
    private String resourceType;

    /**
     * 资源ID（数据库实例ID/表ID等）
     */
    @TableField("resource_id")
    private String resourceId;

    /**
     * 资源名称（便于展示）
     */
    @TableField("resource_name")
    private String resourceName;

    /**
     * 字段级权限（逗号分隔的字段列表，null表示所有字段）
     */
    @TableField("field_list")
    private String fieldList;

    /**
     * 权限过期时间（null表示永不过期）
     */
    @TableField("expire_time")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private java.time.LocalDateTime createTime;
}
