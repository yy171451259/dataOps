package com.dataops.dms.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 元数据访问控制实体
 * 控制对元数据资源的访问权限
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_metadata_access_control")
public class MetadataAccessControl extends BaseEntity {

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
     * 是否启用访问控制
     */
    private Boolean enabled;

    /**
     * 配置JSON（扩展配置）
     */
    private String configJson;
}
