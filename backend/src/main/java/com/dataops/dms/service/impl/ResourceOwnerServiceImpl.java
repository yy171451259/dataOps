package com.dataops.dms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataops.dms.entity.ResourceOwner;
import com.dataops.dms.mapper.ResourceOwnerMapper;
import com.dataops.dms.service.ResourceOwnerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 资源所有者服务实现
 */
@Slf4j
@Service
public class ResourceOwnerServiceImpl extends ServiceImpl<ResourceOwnerMapper, ResourceOwner> implements ResourceOwnerService {

    @Override
    @Transactional
    public ResourceOwner assignOwner(String resourceType, String resourceId, String ownerUserId, String operatorId) {
        // 检查是否已存在相同的所有者分配
        LambdaQueryWrapper<ResourceOwner> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ResourceOwner::getResourceType, resourceType)
                .eq(ResourceOwner::getResourceId, resourceId)
                .eq(ResourceOwner::getOwnerUserId, ownerUserId);
        ResourceOwner existing = this.getOne(wrapper);
        if (existing != null) {
            log.info("资源所有者已存在: resourceType={}, resourceId={}, ownerUserId={}", resourceType, resourceId, ownerUserId);
            return existing;
        }

        ResourceOwner owner = new ResourceOwner();
        owner.setResourceType(resourceType);
        owner.setResourceId(resourceId);
        owner.setOwnerUserId(ownerUserId);
        owner.setCreatedAt(LocalDateTime.now());
        owner.setCreatedBy(operatorId);
        this.save(owner);
        log.info("分配资源所有者成功: id={}, resourceType={}, resourceId={}, ownerUserId={}", owner.getId(), resourceType, resourceId, ownerUserId);
        return owner;
    }

    @Override
    @Transactional
    public boolean revokeOwner(String id, String operatorId) {
        ResourceOwner owner = this.getById(id);
        if (owner == null) {
            log.warn("资源所有者不存在: id={}", id);
            return false;
        }
        boolean result = this.removeById(id);
        log.info("撤销资源所有者: id={}, operatorId={}", id, operatorId);
        return result;
    }

    @Override
    public List<ResourceOwner> listByResource(String resourceType, String resourceId) {
        LambdaQueryWrapper<ResourceOwner> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ResourceOwner::getResourceType, resourceType)
                .eq(ResourceOwner::getResourceId, resourceId);
        return this.list(wrapper);
    }

    @Override
    public List<ResourceOwner> listByUser(String userId) {
        LambdaQueryWrapper<ResourceOwner> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ResourceOwner::getOwnerUserId, userId);
        return this.list(wrapper);
    }

    @Override
    public boolean checkIsOwner(String userId, String resourceType, String resourceId) {
        LambdaQueryWrapper<ResourceOwner> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ResourceOwner::getOwnerUserId, userId)
                .eq(ResourceOwner::getResourceType, resourceType)
                .eq(ResourceOwner::getResourceId, resourceId);
        return this.count(wrapper) > 0;
    }
}
