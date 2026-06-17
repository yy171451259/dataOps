package com.dataops.dms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataops.dms.common.result.Result;
import com.dataops.dms.entity.User;
import com.dataops.dms.mapper.PermissionMapper;
import com.dataops.dms.mapper.RoleMapper;
import com.dataops.dms.mapper.UserMapper;
import com.dataops.dms.service.AuthService;
import com.dataops.dms.util.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AuthServiceImpl implements AuthService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private PermissionMapper permissionMapper;

    @Resource
    private RoleMapper roleMapper;

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private com.dataops.dms.service.SysMenuService sysMenuService;

    @Override
    public Result<Map<String, Object>> login(String username, String password) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, username);
        User user = userMapper.selectOne(wrapper);

        if (user == null) {
            return Result.error("用户名或密码错误");
        }

        if (user.getIsActive() != null && !user.getIsActive()) {
            return Result.error("账户已被禁用");
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            return Result.error("用户名或密码错误");
        }

        String token = jwtUtil.generateToken(user.getId(), user.getUsername());

        // 加载用户权限码列表
        List<String> permissionCodes = permissionMapper.findPermissionCodesByUserId(user.getId());

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("userId", user.getId());
        result.put("username", user.getUsername());
        result.put("nickname", user.getNickname());
        result.put("isAdmin", user.getIsAdmin());
        result.put("permissions", permissionCodes);

        // 加载用户菜单树
        try {
            com.dataops.dms.common.result.Result<List<Map<String, Object>>> menuResult = sysMenuService.getUserMenuTree(user.getId());
            result.put("menus", menuResult.getData());
        } catch (Exception e) {
            result.put("menus", new ArrayList<>());
        }

        return Result.success("登录成功", result);
    }

    @Override
    public Result<User> register(User user) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, user.getUsername());
        if (userMapper.selectCount(wrapper) > 0) {
            return Result.error("用户名已存在");
        }

        user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));
        user.setIsActive(true);
        user.setIsAdmin(false);
        userMapper.insert(user);

        // 为新注册用户分配默认角色：开发人员（developer）
        String roleRecordId = java.util.UUID.randomUUID().toString().replace("-", "");
        roleMapper.insertUserRole(roleRecordId, user.getId(), "role_developer", "system");

        // 不返回密码
        user.setPasswordHash(null);
        return Result.success("注册成功", user);
    }

    @Override
    public Result<Map<String, Object>> refreshToken(String token) {
        if (!jwtUtil.validateToken(token)) {
            return Result.error("Token无效或已过期");
        }

        String userId = jwtUtil.getUserIdFromToken(token);
        String username = jwtUtil.getUsernameFromToken(token);
        String newToken = jwtUtil.generateToken(userId, username);

        Map<String, Object> result = new HashMap<>();
        result.put("token", newToken);
        return Result.success(result);
    }

    @Override
    public Result<User> getCurrentUser(String userId) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        user.setPasswordHash(null);
        return Result.success(user);
    }

    @Override
    public Result<Void> changePassword(String userId, String oldPassword, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }

        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            return Result.error("原密码错误");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
        return Result.success("密码修改成功");
    }

    @Override
    public Result<List<Map<String, Object>>> getUserMenus(String userId) {
        return sysMenuService.getUserMenuTree(userId);
    }

    @Resource
    private com.dataops.dms.service.DingTalkOAuthService dingTalkOAuthService;

    @Override
    public String getDingTalkAuthUrl(String state) {
        return dingTalkOAuthService.getAuthUrl(state);
    }

    @Override
    public Result<Map<String, Object>> dingTalkLogin(String authCode) {
        try {
            // 1. 通过授权码获取钉钉用户信息
            Map<String, Object> dingTalkUser = dingTalkOAuthService.getUserInfoByAuthCode(authCode);
            String unionId = (String) dingTalkUser.get("unionId");
            String userId = (String) dingTalkUser.get("userId");
            String nickname = (String) dingTalkUser.get("nick");
            String avatar = (String) dingTalkUser.get("avatarUrl");

            if (unionId == null) {
                return Result.error("获取钉钉用户信息失败");
            }

            // 2. 查找是否已存在该钉钉用户
            LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(User::getDingtalkUnionId, unionId);
            User user = userMapper.selectOne(wrapper);

            // 3. 如果用户不存在，创建新用户
            if (user == null) {
                user = new User();
                user.setUsername("dt_" + userId); // 使用钉钉用户ID作为用户名
                user.setNickname(nickname != null ? nickname : "钉钉用户");
                user.setAvatar(avatar);
                user.setDingtalkUnionId(unionId);
                user.setDingtalkUserId(userId);
                user.setIsActive(true);
                user.setIsAdmin(false);
                userMapper.insert(user);

                // 为新用户分配默认角色：开发人员（developer）
                String roleRecordId = java.util.UUID.randomUUID().toString().replace("-", "");
                roleMapper.insertUserRole(roleRecordId, user.getId(), "role_developer", "system");
            } else {
                // 更新钉钉用户ID（可能变化）
                user.setDingtalkUserId(userId);
                if (nickname != null) {
                    user.setNickname(nickname);
                }
                if (avatar != null) {
                    user.setAvatar(avatar);
                }
                userMapper.updateById(user);
            }

            // 4. 生成JWT Token
            String token = jwtUtil.generateToken(user.getId(), user.getUsername());

            // 5. 加载用户权限码列表
            List<String> permissionCodes = permissionMapper.findPermissionCodesByUserId(user.getId());

            // 6. 构建返回结果
            Map<String, Object> result = new HashMap<>();
            result.put("token", token);
            result.put("userId", user.getId());
            result.put("username", user.getUsername());
            result.put("nickname", user.getNickname());
            result.put("isAdmin", user.getIsAdmin());
            result.put("permissions", permissionCodes);

            // 7. 加载用户菜单树
            try {
                com.dataops.dms.common.result.Result<List<Map<String, Object>>> menuResult = sysMenuService.getUserMenuTree(user.getId());
                result.put("menus", menuResult.getData());
            } catch (Exception e) {
                result.put("menus", new ArrayList<>());
            }

            return Result.success("钉钉登录成功", result);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("钉钉登录失败：" + e.getMessage());
        }
    }
}
