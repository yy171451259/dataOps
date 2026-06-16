package com.dataops.dms.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dataops.dms.entity.MetadataAccessControl;

/**
 * 元数据访问控制服务接口
 */
public interface MetadataAccessControlService extends IService<MetadataAccessControl> {

    /**
     * 启用资源访问控制
     */
    MetadataAccessControl enableControl(String resourceType, String resourceId, String resourceName, String userId);

    /**
     * 禁用资源访问控制
     */
    boolean disableControl(String id, String userId);

    /**
     * 禁用资源访问控制
     * @param parentResourceId 父级资源ID（Schema禁用时需校验父级Instance是否受限）
     */
    boolean disableControl(String id, String userId, String parentResourceId);

    /**
     * 检查资源是否受访问控制（含父级继承）
     * @param parentResourceId Schema的父级实例ID，仅database类型需要
     */
    boolean isAccessControlled(String resourceType, String resourceId, String parentResourceId);

    /**
     * 检查资源是否受访问控制
     */
    boolean isAccessControlled(String resourceType, String resourceId);

    /**
     * 检查用户是否有权访问指定资源
     */
    boolean canUserAccess(String userId, String resourceType, String resourceId);

    /**
     * 检查用户是否有权对指定资源执行指定操作
     * @param userId 用户ID
     * @param resourceType 资源类型
     * @param resourceId 资源ID
     * @param action 操作类型: read, write, export, ddl, *
     * @return 是否有权限
     */
    boolean canUserAccess(String userId, String resourceType, String resourceId, String action);
}
