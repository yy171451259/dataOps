package com.dataops.dms.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataops.dms.common.result.Result;
import com.dataops.dms.entity.Role;
import com.dataops.dms.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * 角色管理控制器
 * 支持角色CRUD、权限分配、用户角色关联
 */
@RestController
@RequestMapping("/api/v1/roles")
@Tag(name = "角色管理")
public class RoleController {

    @Resource
    private RoleService roleService;

    @GetMapping
    @Operation(summary = "获取角色列表")
    public Result<Page<Role>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String keyword) {
        return roleService.list(page, size, keyword);
    }

    @GetMapping("/all")
    @Operation(summary = "获取所有角色（不分页）")
    public Result<List<Role>> listAll() {
        return roleService.listAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取角色详情")
    public Result<Role> getById(@PathVariable String id) {
        return roleService.getById(id);
    }

    @PostMapping
    @Operation(summary = "创建角色")
    public Result<Role> create(@RequestBody Role role) {
        return roleService.create(role);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新角色")
    public Result<Role> update(@PathVariable String id, @RequestBody Role role) {
        role.setId(id);
        return roleService.update(role);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除角色")
    public Result<Void> delete(@PathVariable String id) {
        return roleService.delete(id);
    }

    @PostMapping("/{id}/permissions")
    @Operation(summary = "为角色分配权限")
    public Result<Void> assignPermissions(
            @PathVariable String id,
            @RequestBody List<String> permissionIds,
            HttpServletRequest request) {
        String operatorId = (String) request.getAttribute("userId");
        if (operatorId == null) operatorId = "user_admin";
        return roleService.assignPermissions(id, permissionIds, operatorId);
    }

    @GetMapping("/{id}/permissions")
    @Operation(summary = "获取角色权限码列表")
    public Result<List<String>> getRolePermissions(@PathVariable String id) {
        return roleService.getRolePermissions(id);
    }

    @GetMapping("/{id}/permissions/details")
    @Operation(summary = "获取角色权限详情")
    public Result<List<?>> getRolePermissionDetails(@PathVariable String id) {
        return roleService.getRolePermissionDetails(id);
    }

    @DeleteMapping("/{roleId}/permissions/{permissionId}")
    @Operation(summary = "移除角色权限")
    public Result<Void> removePermission(
            @PathVariable String roleId,
            @PathVariable String permissionId) {
        return roleService.removePermission(roleId, permissionId);
    }

    @GetMapping("/{id}/users")
    @Operation(summary = "获取拥有该角色的用户列表")
    public Result<List<String>> getUsersByRole(@PathVariable String id) {
        return roleService.getUsersByRole(id);
    }
}
