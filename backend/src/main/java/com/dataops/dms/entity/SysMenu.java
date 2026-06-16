package com.dataops.dms.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统菜单实体 - 支持菜单树和按钮操作管理
 */
@Data
@TableName("sys_menu")
public class SysMenu {
    private String id;
    private String parentId;
    private String name;
    private String type;          // menu | button
    private String path;          // 前端路由路径
    private String component;     // 前端组件路径（预留）
    private String icon;          // Ant Design 图标名称
    private String permissionCode; // 关联的功能权限码
    private Integer sortOrder;
    private Integer visible;      // 1可见 0隐藏
    private String status;        // active | inactive
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updateTime;
}
