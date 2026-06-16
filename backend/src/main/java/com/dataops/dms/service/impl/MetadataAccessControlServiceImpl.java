package com.dataops.dms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataops.dms.entity.MetadataAccessControl;
import com.dataops.dms.entity.Permission;
import com.dataops.dms.mapper.MetadataAccessControlMapper;
import com.dataops.dms.mapper.PermissionMapper;
import com.dataops.dms.mapper.RoleMapper;
import com.dataops.dms.service.MetadataAccessControlService;
import com.dataops.dms.service.ResourceOwnerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 元数据访问控制服务实现
 */
@Slf4j
@Service
public class MetadataAccessControlServiceImpl extends ServiceImpl<MetadataAccessControlMapper, MetadataAccessControl> implements MetadataAccessControlService {

    @Resource
    private ResourceOwnerService resourceOwnerService;

    @Resource
    private PermissionMapper permissionMapper;

    @Resource
    private RoleMapper roleMapper;

    /** 检查用户是否为超级管理员，拥有所有资源权限 */
    private boolean isAdmin(String userId) {
        if (userId == null) return false;
        List<String> roleIds = roleMapper.findRoleIdsByUserId(userId);
        return roleIds.contains("role_admin");
    }

    @Override
    @Transactional
    public MetadataAccessControl enableControl(String resourceType, String resourceId, String resourceName, String userId) {
        // 检查是否已存在
        LambdaQueryWrapper<MetadataAccessControl> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MetadataAccessControl::getResourceType, resourceType)
                .eq(MetadataAccessControl::getResourceId, resourceId);
        MetadataAccessControl existing = this.getOne(wrapper);
        if (existing != null) {
            existing.setEnabled(true);
            existing.setResourceName(resourceName);
            this.updateById(existing);
            log.info("更新访问控制: resourceType={}, resourceId={}, enabled=true", resourceType, resourceId);
            return existing;
        }

        MetadataAccessControl control = new MetadataAccessControl();
        control.setResourceType(resourceType);
        control.setResourceId(resourceId);
        control.setResourceName(resourceName);
        control.setEnabled(true);
        this.save(control);
        log.info("启用访问控制: id={}, resourceType={}, resourceId={}", control.getId(), resourceType, resourceId);
        return control;
    }

    @Override
    @Transactional
    public boolean disableControl(String id, String userId) {
        return disableControl(id, userId, null);
    }

    @Override
    @Transactional
    public boolean disableControl(String id, String userId, String parentResourceId) {
        MetadataAccessControl control = this.getById(id);
        if (control == null) {
            log.warn("访问控制记录不存在: id={}", id);
            return false;
        }
        // 层级校验：Schema 的父级 Instance 如果受限，不允许将 Schema 设为公开
        if ("database".equals(control.getResourceType()) && parentResourceId != null && !parentResourceId.isEmpty()) {
            if (isAccessControlled("instance", parentResourceId)) {
                log.warn("禁止关闭Schema访问控制: Schema={}, 父级Instance={} 已开启访问控制", 
                         control.getResourceId(), parentResourceId);
                throw new RuntimeException("父级实例已开启访问控制，该Schema不能设为公开访问");
            }
        }
        control.setEnabled(false);
        boolean result = this.updateById(control);
        log.info("禁用访问控制: id={}, userId={}", id, userId);
        return result;
    }

    @Override
    public boolean isAccessControlled(String resourceType, String resourceId) {
        LambdaQueryWrapper<MetadataAccessControl> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MetadataAccessControl::getResourceType, resourceType)
                .eq(MetadataAccessControl::getResourceId, resourceId)
                .eq(MetadataAccessControl::getEnabled, true);
        return this.count(wrapper) > 0;
    }

    @Override
    public boolean isAccessControlled(String resourceType, String resourceId, String parentResourceId) {
        // 先检查资源自身
        if (isAccessControlled(resourceType, resourceId)) {
            return true;
        }
        // 层级继承：如果父级 Instance 受限，Schema 也视为受限
        if ("database".equals(resourceType) && parentResourceId != null && !parentResourceId.isEmpty()) {
            return isAccessControlled("instance", parentResourceId);
        }
        return false;
    }

    @Override
    public boolean canUserAccess(String userId, String resourceType, String resourceId) {
        // 超级管理员拥有所有资源权限
        if (isAdmin(userId)) return true;
        // 首先检查资源是否受访问控制
        if (!isAccessControlled(resourceType, resourceId)) {
            return true;
        }
        // 检查1: 用户是否为资源 Owner
        if (resourceOwnerService.checkIsOwner(userId, resourceType, resourceId)) {
            return true;
        }
        // 检查2: 用户是否有未过期的 Permission 记录（任一操作类型）
        LambdaQueryWrapper<Permission> permWrapper = new LambdaQueryWrapper<>();
        permWrapper.eq(Permission::getRoleId, userId)
                .eq(Permission::getResourceType, resourceType)
                .eq(Permission::getResourceId, resourceId);
        java.util.List<Permission> permissions = permissionMapper.selectList(permWrapper);
        LocalDateTime now = LocalDateTime.now();
        for (Permission p : permissions) {
            if (p.getExpireTime() == null || p.getExpireTime().isAfter(now)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canUserAccess(String userId, String resourceType, String resourceId, String action) {
        // 超级管理员拥有所有资源权限
        if (isAdmin(userId)) return true;
        // 1. 资源未开启访问控制 → 所有人可访问
        if (!isAccessControlled(resourceType, resourceId)) {
            return true;
        }
        // 2. 用户是资源Owner → 拥有全部权限
        if (resourceOwnerService.checkIsOwner(userId, resourceType, resourceId)) {
            return true;
        }
        // 3. 检查用户是否有指定操作类型的有效Permission
        LambdaQueryWrapper<Permission> permWrapper = new LambdaQueryWrapper<>();
        permWrapper.eq(Permission::getRoleId, userId)
                .eq(Permission::getResourceType, resourceType)
                .eq(Permission::getResourceId, resourceId)
                .and(w -> w.eq(Permission::getAction, action)
                        .or()
                        .eq(Permission::getAction, "*")); // 支持"全部权限"
        
        java.util.List<Permission> permissions = permissionMapper.selectList(permWrapper);
        LocalDateTime now = LocalDateTime.now();
        for (Permission p : permissions) {
            // expireTime 为 null 表示永不过期，否则检查是否未过期
            if (p.getExpireTime() == null || p.getExpireTime().isAfter(now)) {
                return true;
            }
        }
        return false;
    }
}
