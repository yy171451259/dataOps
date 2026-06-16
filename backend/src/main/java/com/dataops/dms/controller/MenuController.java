package com.dataops.dms.controller;

import com.dataops.dms.common.result.Result;
import com.dataops.dms.entity.SysMenu;
import com.dataops.dms.service.SysMenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/menus")
@Tag(name = "菜单管理")
public class MenuController {

    @Resource
    private SysMenuService sysMenuService;

    @GetMapping("/tree")
    @Operation(summary = "获取完整菜单树")
    public Result<List<Map<String, Object>>> getMenuTree() {
        return sysMenuService.getMenuTree();
    }

    @GetMapping("/user-tree")
    @Operation(summary = "获取当前用户菜单树")
    public Result<List<Map<String, Object>>> getUserMenuTree(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        if (userId == null) userId = "user_admin";
        return sysMenuService.getUserMenuTree(userId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取菜单详情")
    public Result<SysMenu> getById(@PathVariable String id) {
        return sysMenuService.getById(id);
    }

    @PostMapping
    @Operation(summary = "创建菜单/按钮")
    public Result<SysMenu> create(@RequestBody SysMenu menu) {
        return sysMenuService.create(menu);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新菜单/按钮")
    public Result<SysMenu> update(@PathVariable String id, @RequestBody SysMenu menu) {
        menu.setId(id);
        return sysMenuService.update(menu);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除菜单/按钮")
    public Result<Void> delete(@PathVariable String id) {
        return sysMenuService.delete(id);
    }

    @GetMapping("/roles/{roleId}")
    @Operation(summary = "获取角色已分配的菜单ID")
    public Result<List<String>> getRoleMenuIds(@PathVariable String roleId) {
        return sysMenuService.getRoleMenuIds(roleId);
    }

    @PostMapping("/roles/{roleId}")
    @Operation(summary = "为角色分配菜单权限")
    public Result<Void> assignRoleMenus(
            @PathVariable String roleId,
            @RequestBody List<String> menuIds,
            HttpServletRequest request) {
        String operatorId = (String) request.getAttribute("userId");
        if (operatorId == null) operatorId = "user_admin";
        return sysMenuService.assignRoleMenus(roleId, menuIds, operatorId);
    }
}
