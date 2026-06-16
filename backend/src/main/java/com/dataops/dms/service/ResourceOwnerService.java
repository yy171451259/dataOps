package com.dataops.dms.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dataops.dms.entity.ResourceOwner;

import java.util.List;

/**
 * 资源所有者服务接口
 */
public interface ResourceOwnerService extends IService<ResourceOwner> {

    /**
     * 分配资源所有者
     */
    ResourceOwner assignOwner(String resourceType, String resourceId, String ownerUserId, String operatorId);

    /**
     * 撤销资源所有者
     */
    boolean revokeOwner(String id, String operatorId);

    /**
     * 根据资源类型和ID查询所有者列表
     */
    List<ResourceOwner> listByResource(String resourceType, String resourceId);

    /**
     * 根据用户ID查询其拥有的资源
     */
    List<ResourceOwner> listByUser(String userId);

    /**
     * 检查用户是否为指定资源的所有者
     */
    boolean checkIsOwner(String userId, String resourceType, String resourceId);
}
