package com.dataops.dms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataops.dms.common.result.Result;
import com.dataops.dms.entity.Permission;
import com.dataops.dms.entity.Role;
import com.dataops.dms.mapper.PermissionMapper;
import com.dataops.dms.mapper.RoleMapper;
import com.dataops.dms.service.RoleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;

@Slf4j
@Service
public class RoleServiceImpl implements RoleService {

    @Resource
    private RoleMapper roleMapper;

    @Resource
    private PermissionMapper permissionMapper;

    @Override
    public Result<Page<Role>> list(Integer page, Integer size, String keyword) {
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w
                    .like(Role::getName, keyword)
                    .or()
                    .like(Role::getCode, keyword)
                    .or()
                    .like(Role::getDescription, keyword));
        }
        wrapper.orderByAsc(Role::getCreateTime);
        return Result.success(roleMapper.selectPage(new Page<>(page, size), wrapper));
    }

    @Override
    public Result<List<Role>> listAll() {
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(Role::getCreateTime);
        return Result.success(roleMapper.selectList(wrapper));
    }

    @Override
    public Result<Role> getById(String id) {
        return Result.success(roleMapper.selectById(id));
    }

    @Override
    @Transactional
    public Result<Role> create(Role role) {
        // 检查编码唯一性
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Role::getCode, role.getCode());
        if (roleMapper.selectCount(wrapper) > 0) {
            return Result.error("角色编码已存在");
        }
        role.setIsSystem(role.getIsSystem() != null ? role.getIsSystem() : false);
        roleMapper.insert(role);
        log.info("创建角色: {} (code={})", role.getName(), role.getCode());
        return Result.success("创建成功", role);
    }

    @Override
    public Result<Role> update(Role role) {
        Role existing = roleMapper.selectById(role.getId());
        if (existing == null) {
            return Result.error("角色不存在");
        }
        roleMapper.updateById(role);
        log.info("更新角色: {}", role.getName());
        return Result.success("更新成功", role);
    }

    @Override
    @Transactional
    public Result<Void> delete(String id) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            return Result.error("角色不存在");
        }
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            return Result.error("系统内置角色不可删除");
        }
        // 删除角色权限关联
        roleMapper.deleteRolePermissions(id);
        // 删除用户角色关联
        roleMapper.deleteUserRoles(id);
        // 删除角色
        roleMapper.deleteById(id);
        log.info("删除角色: {} (code={})", role.getName(), role.getCode());
        return Result.success("删除成功");
    }

    @Override
    @Transactional
    public Result<Void> assignPermissions(String roleId, List<String> permissionIds, String operatorId) {
        Role role = roleMapper.selectById(roleId);
        if (role == null) {
            return Result.error("角色不存在");
        }

        // 先清除该角色的所有权限关联
        roleMapper.deleteRolePermissions(roleId);

        // 插入新的权限关联
        if (permissionIds != null && !permissionIds.isEmpty()) {
            for (String permissionId : permissionIds) {
                String recordId = UUID.randomUUID().toString().replace("-", "");
                roleMapper.insertRolePermission(recordId, roleId, permissionId);
            }
        }

        log.info("角色[{}]权限分配完成，共 {} 个权限点，操作人: {}", role.getName(), permissionIds != null ? permissionIds.size() : 0, operatorId);
        return Result.success("权限分配成功");
    }

    @Override
    public Result<List<String>> getRolePermissions(String roleId) {
        List<String> codes = roleMapper.findPermissionCodesByRoleId(roleId);
        return Result.success(codes);
    }

    @Override
    public Result<List<?>> getRolePermissionDetails(String roleId) {
        List<String> permIds = roleMapper.findPermissionIdsByRoleId(roleId);
        if (permIds.isEmpty()) {
            return Result.success(new ArrayList<>());
        }
        List<Permission> permissions = permissionMapper.selectBatchIds(permIds);
        return Result.success(permissions != null ? permissions : new ArrayList<>());
    }

    @Override
    @Transactional
    public Result<Void> removePermission(String roleId, String permissionId) {
        int deleted = roleMapper.deleteRolePermission(roleId, permissionId);
        if (deleted > 0) {
            log.info("移除角色[{}]的权限[{}]", roleId, permissionId);
            return Result.success("权限移除成功");
        }
        return Result.error("未找到该权限关联");
    }

    @Override
    public Result<List<String>> getUsersByRole(String roleId) {
        List<String> userIds = roleMapper.findUserIdsByRoleId(roleId);
        return Result.success(userIds);
    }
}
