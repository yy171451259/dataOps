package com.dataops.dms.service;

import com.dataops.dms.common.result.Result;
import com.dataops.dms.entity.User;

import java.util.List;
import java.util.Map;

/**
 * 认证服务接口
 */
public interface AuthService {

    /**
     * 用户登录
     */
    Result<Map<String, Object>> login(String username, String password);

    /**
     * 用户注册
     */
    Result<User> register(User user);

    /**
     * 刷新Token
     */
    Result<Map<String, Object>> refreshToken(String token);

    /**
     * 获取当前用户信息
     */
    Result<User> getCurrentUser(String userId);

    /**
     * 修改密码
     */
    Result<Void> changePassword(String userId, String oldPassword, String newPassword);

    /**
     * 获取用户可见的菜单树
     */
    Result<List<Map<String, Object>>> getUserMenus(String userId);
}
