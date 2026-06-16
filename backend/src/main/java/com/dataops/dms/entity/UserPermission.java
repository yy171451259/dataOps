package com.dataops.dms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户-数据资源权限实体
 *
 * 设计定位：专门管理用户级别的数据资源访问权限，与 sys_permission（功能权限定义）严格分离。
 *
 * 权限体系分层：
 * - sys_permission：功能权限定义（菜单/按钮级，如 menu:manage、ticket:approve），通过 sys_role_permission 授予角色
 * - sys_user_permission：数据资源权限（数据库实例/Schema/表/字段），直接授予用户
 *
 * resourceType/resourceId 的约定：
 * - instance / instanceId：整个数据库实例
 * - database / schemaName：特定 Schema（左侧树可见性检查时用这个组合）
 * - schema / instanceId：特定实例的 Schema 级执行权限（SQL 执行权限检查时用）
 * - table / tableName：特定表
 * - column / columnName：特定字段
 */
@Data
@TableName("sys_user_permission")
public class UserPermission {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    private String userId;

    /**
     * 资源类型: instance/schema/table/column
     */
    private String resourceType;

    /**
     * 资源ID（数据库实例ID/Schema名/表名等）
     */
    private String resourceId;

    /**
     * 资源名称（便于展示）
     */
    private String resourceName;

    /**
     * 权限操作: query/export/update/ddl/*
     */
    private String action;

    /**
     * 字段级权限（逗号分隔的字段列表，null=全部字段）
     */
    private String fieldList;

    /**
     * 权限过期时间（null=永不过期）
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime expireTime;

    private String grantedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime grantedAt;
}
