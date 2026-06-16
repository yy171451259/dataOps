package com.dataops.dms.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 功能权限定义实体
 *
 * 设计定位：仅管理菜单/按钮级别的功能权限定义（如 menu:manage、ticket:approve）。
 * 该表中的权限定义通过 sys_role_permission 授予给角色，而不是直接授予用户。
 *
 * 与数据权限的严格分离：
 * - sys_permission：功能权限定义（菜单/按钮级别，静态配置）
 * - sys_user_permission：数据资源权限（数据库实例/Schema 级，用户申请后动态授予）
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

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
}
