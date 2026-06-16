package com.dataops.dms.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataops.dms.common.result.Result;
import com.dataops.dms.entity.User;
import com.dataops.dms.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "用户管理")
public class UserController {

    @Resource
    private UserService userService;

    @GetMapping
    @Operation(summary = "获取用户列表")
    public Result<Page<User>> list(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String keyword) {
        return userService.list(page, size, keyword);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取用户详情")
    public Result<User> getById(@PathVariable String id) {
        return userService.getById(id);
    }

    @PostMapping
    @Operation(summary = "创建用户")
    public Result<User> create(@RequestBody User user) {
        return userService.create(user);
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新用户")
    public Result<User> update(@PathVariable String id, @RequestBody User user) {
        user.setId(id);
        return userService.update(user);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除用户")
    public Result<Void> delete(@PathVariable String id) {
        return userService.delete(id);
    }

    @PostMapping("/{id}/reset-password")
    @Operation(summary = "重置密码")
    public Result<Void> resetPassword(@PathVariable String id, @RequestParam String newPassword) {
        return userService.resetPassword(id, newPassword);
    }

    @PostMapping("/{id}/roles")
    @Operation(summary = "分配角色")
    public Result<Void> assignRoles(@PathVariable String id, @RequestBody List<String> roleIds) {
        return userService.assignRoles(id, roleIds);
    }

    @GetMapping("/{id}/roles")
    @Operation(summary = "获取用户角色")
    public Result<Object> getUserRoles(@PathVariable String id) {
        return userService.getUserRoles(id);
    }
}
