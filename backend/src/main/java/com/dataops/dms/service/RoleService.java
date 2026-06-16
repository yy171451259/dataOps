package com.dataops.dms.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataops.dms.common.result.Result;
import com.dataops.dms.entity.Role;

import java.util.List;

/**
 * 角色管理服务
 * 支持角色CRUD、角色权限分配、用户角色关联
 */
public interface RoleService {

    /** 获取角色列表 */
    Result<Page<Role>> list(Integer page, Integer size, String keyword);

    /** 获取所有角色（不分页） */
    Result<List<Role>> listAll();

    /** 获取角色详情 */
    Result<Role> getById(String id);

    /** 创建角色 */
    Result<Role> create(Role role);

    /** 更新角色 */
    Result<Role> update(Role role);

    /** 删除角色（系统内置角色不可删除） */
    Result<Void> delete(String id);

    /** 为角色分配权限码 */
    Result<Void> assignPermissions(String roleId, List<String> permissionIds, String operatorId);

    /** 获取角色的权限码列表 */
    Result<List<String>> getRolePermissions(String roleId);

    /** 获取角色的完整权限信息 */
    Result<List<?>> getRolePermissionDetails(String roleId);

    /** 移除角色的某个权限 */
    Result<Void> removePermission(String roleId, String permissionId);

    /** 获取拥有某个角色的用户列表 */
    Result<List<String>> getUsersByRole(String roleId);
}
