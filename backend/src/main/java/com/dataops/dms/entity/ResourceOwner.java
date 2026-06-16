package com.dataops.dms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 资源所有者实体
 * 记录数据资源（库/表等）的所有者信息
 */
@Data
@TableName("sys_resource_owner")
public class ResourceOwner {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 资源类型: schema, table
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
     * 父资源类型
     */
    private String parentResourceType;

    /**
     * 父资源ID
     */
    private String parentResourceId;

    /**
     * 所有者用户ID
     */
    private String ownerUserId;

    /**
     * 所有者用户名
     */
    private String ownerUsername;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * 创建人
     */
    private String createdBy;
}
