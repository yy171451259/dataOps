package com.dataops.dms.controller;

import com.dataops.dms.common.result.Result;
import com.dataops.dms.config.DingTalkConfig;
import com.dataops.dms.entity.User;
import com.dataops.dms.service.AuthService;
import com.dataops.dms.service.DingTalkOAuthService;
import com.dataops.dms.service.impl.DingTalkQrSession;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "认证管理")
public class AuthController {

    @Resource
    private AuthService authService;

    @Resource
    private DingTalkOAuthService dingTalkOAuthService;

    @Resource
    private DingTalkConfig dingTalkConfig;

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

    @GetMapping("/dingtalk/auth-url")
    @Operation(summary = "获取钉钉授权URL")
    public Result<String> getDingTalkAuthUrl(@RequestParam(required = false) String state, 
                                              @RequestParam(required = false) String redirectUri) {
        if (state == null || state.isEmpty()) {
            state = "dataops_dms_" + System.currentTimeMillis();
        }
        String authUrl = authService.getDingTalkAuthUrl(state, redirectUri);
        return Result.success("获取成功", authUrl);
    }

    @PostMapping("/dingtalk/callback")
    @Operation(summary = "钉钉登录回调")
    public Result<Map<String, Object>> dingTalkCallback(@RequestBody Map<String, String> params) {
        String authCode = params.get("authCode");
        return authService.dingTalkLogin(authCode);
    }

    // ========== 钉钉扫码登录（PC端轮询模式） ==========

    /**
     * 获取钉钉扫码登录二维码会话
     * PC前端调用：创建会话，返回 scanUrl 用于生成二维码
     */
    @GetMapping("/dingtalk/qr-code")
    @Operation(summary = "获取钉钉扫码登录二维码")
    public Result<Map<String, Object>> getDingTalkQrCode() {
        // 1. 创建会话
        String sessionId = dingTalkOAuthService.createQrSession();

        // 2. 构造扫码URL（手机扫描后访问的地址，使用公网地址）
        String baseUrl = dingTalkConfig.getBackendBaseUrl();
        String scanUrl = baseUrl + "/api/v1/auth/dingtalk/qr-scan?sid=" + sessionId;

        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId);
        result.put("scanUrl", scanUrl);
        return Result.success("获取成功", result);
    }

    /**
     * PC前端轮询：查询扫码登录结果
     */
    @GetMapping("/dingtalk/qr-status")
    @Operation(summary = "查询扫码登录状态")
    public Result<Map<String, Object>> getDingTalkQrStatus(@RequestParam String sid) {
        DingTalkQrSession session = dingTalkOAuthService.getQrSession(sid);
        Map<String, Object> result = new HashMap<>();

        if (session == null) {
            result.put("status", "EXPIRED");
            result.put("message", "二维码已过期，请刷新重试");
            return Result.success(result);
        }

        result.put("status", session.getStatus());

        if ("SUCCESS".equals(session.getStatus())) {
            result.put("token", session.getToken());
            result.put("loginResult", session.getLoginResult());
        } else if ("SCANNED".equals(session.getStatus())) {
            result.put("message", "已扫码，请在手机上确认登录");
        } else if ("PENDING".equals(session.getStatus())) {
            result.put("message", "请使用钉钉扫描二维码");
        }

        return Result.success(result);
    }

    /**
     * 手机扫码后访问：记录扫码状态，重定向到钉钉OAuth授权页面
     */
    @GetMapping("/dingtalk/qr-scan")
    @Operation(summary = "钉钉扫码入口")
    public void dingTalkQrScan(@RequestParam String sid,
                                HttpServletResponse response) throws IOException {
        DingTalkQrSession session = dingTalkOAuthService.getQrSession(sid);

        if (session == null || session.isExpired()) {
            response.setContentType("text/html;charset=UTF-8");
            PrintWriter writer = response.getWriter();
            writer.write("<!DOCTYPE html><html><body><h2>二维码已过期</h2><p>请刷新PC端页面获取新的二维码</p></body></html>");
            writer.flush();
            return;
        }

        // 标记已扫码
        dingTalkOAuthService.updateQrSessionStatus(sid, "SCANNED");

        // 构造钉钉OAuth回调地址（回到后端的 qr-callback 接口，使用公网地址）
        String baseUrl = dingTalkConfig.getBackendBaseUrl();
        String callbackUrl = baseUrl + "/api/v1/auth/dingtalk/qr-callback";

        // 生成钉钉授权URL，state中携带sessionId
        String authUrl = dingTalkOAuthService.getAuthUrl(sid, callbackUrl);

        // 重定向到钉钉授权页面
        response.sendRedirect(authUrl);
    }

    /**
     * 钉钉OAuth回调：完成登录，将结果存入会话
     */
    @GetMapping("/dingtalk/qr-callback")
    @Operation(summary = "钉钉扫码登录回调")
    public void dingTalkQrCallback(@RequestParam(required = false) String authCode,
                                    @RequestParam(required = false) String code,
                                    @RequestParam(required = false) String state,
                                    HttpServletResponse response) throws IOException {
        response.setContentType("text/html;charset=UTF-8");
        PrintWriter writer = response.getWriter();

        String realAuthCode = (authCode != null) ? authCode : code;
        String sessionId = state;

        if (realAuthCode == null || realAuthCode.isEmpty()) {
            writer.write("<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body style='text-align:center;padding-top:100px;font-family:sans-serif;'>"
                + "<h2 style='color:#ff4d4f;'>授权失败</h2><p>未获取到授权码，请重新扫码</p></body></html>");
            writer.flush();
            return;
        }

        try {
            // 调用钉钉登录逻辑
            Result<Map<String, Object>> loginResult = authService.dingTalkLogin(realAuthCode);

            if (loginResult.getCode() == 200 && sessionId != null) {
                Map<String, Object> data = loginResult.getData();
                String token = (String) data.get("token");
                // 将会话标记为成功，PC端轮询将获取到token
                dingTalkOAuthService.completeQrSession(sessionId, token, data);

                writer.write("<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body style='text-align:center;padding-top:100px;font-family:sans-serif;'>"
                    + "<h2 style='color:#52c41a;'>登录成功</h2><p>PC端将自动跳转，请返回电脑查看</p></body></html>");
            } else {
                writer.write("<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body style='text-align:center;padding-top:100px;font-family:sans-serif;'>"
                    + "<h2 style='color:#ff4d4f;'>登录失败</h2><p>" + loginResult.getMessage() + "</p></body></html>");
            }
        } catch (Exception e) {
            writer.write("<!DOCTYPE html><html><head><meta charset='UTF-8'></head><body style='text-align:center;padding-top:100px;font-family:sans-serif;'>"
                + "<h2 style='color:#ff4d4f;'>登录失败</h2><p>" + e.getMessage() + "</p></body></html>");
        }
        writer.flush();
    }

}
