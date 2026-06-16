package com.dataops.dms.controller;

import com.dataops.dms.common.result.Result;
import com.dataops.dms.entity.User;
import com.dataops.dms.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "认证管理")
public class AuthController {

    @Resource
    private AuthService authService;

    @PostMapping("/login")
    @Operation(summary = "用户登录")
    public Result<Map<String, Object>> login(@RequestBody Map<String, String> params) {
        String username = params.get("username");
        String password = params.get("password");
        return authService.login(username, password);
    }

    @PostMapping("/register")
    @Operation(summary = "用户注册")
    public Result<User> register(@RequestBody User user) {
        return authService.register(user);
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新Token")
    public Result<Map<String, Object>> refresh(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        return authService.refreshToken(token);
    }

    @GetMapping("/me")
    @Operation(summary = "获取当前用户信息")
    public Result<User> getCurrentUser(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        return authService.getCurrentUser(userId);
    }

    @PostMapping("/change-password")
    @Operation(summary = "修改密码")
    public Result<Void> changePassword(HttpServletRequest request, @RequestBody Map<String, String> params) {
        String userId = (String) request.getAttribute("userId");
        return authService.changePassword(userId, params.get("oldPassword"), params.get("newPassword"));
    }

    @GetMapping("/menus")
    @Operation(summary = "获取当前用户菜单")
    public Result<List<Map<String, Object>>> getUserMenus(HttpServletRequest request) {
        String userId = (String) request.getAttribute("userId");
        if (userId == null) userId = "user_admin";
        return authService.getUserMenus(userId);
    }

    @GetMapping("/health")
    @Operation(summary = "健康检查")
    public Result<String> health() {
        return Result.success("OK", "OK");
    }
}
