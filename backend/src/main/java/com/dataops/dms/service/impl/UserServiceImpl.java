package com.dataops.dms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataops.dms.common.result.Result;
import com.dataops.dms.entity.Role;
import com.dataops.dms.entity.User;
import com.dataops.dms.mapper.RoleMapper;
import com.dataops.dms.mapper.UserMapper;
import com.dataops.dms.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserServiceImpl implements UserService {

    @Resource
    private UserMapper userMapper;

    @Resource
    private RoleMapper roleMapper;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Override
    public Result<Page<User>> list(Integer page, Integer size, String keyword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w
                    .like(User::getUsername, keyword)
                    .or()
                    .like(User::getNickname, keyword)
                    .or()
                    .like(User::getEmail, keyword));
        }
        wrapper.orderByDesc(User::getCreateTime);
        Page<User> result = userMapper.selectPage(new Page<>(page, size), wrapper);
        // 清除密码
        result.getRecords().forEach(u -> u.setPasswordHash(null));
        return Result.success(result);
    }

    @Override
    public Result<User> getById(String id) {
        User user = userMapper.selectById(id);
        if (user != null) {
            user.setPasswordHash(null);
        }
        return Result.success(user);
    }

    @Override
    public Result<User> create(User user) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(User::getUsername, user.getUsername());
        if (userMapper.selectCount(wrapper) > 0) {
            return Result.error("用户名已存在");
        }

        user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));
        user.setIsActive(user.getIsActive() != null ? user.getIsActive() : true);
        user.setIsAdmin(user.getIsAdmin() != null ? user.getIsAdmin() : false);
        userMapper.insert(user);

        // 为新用户分配默认角色：开发人员（developer）
        String roleRecordId = UUID.randomUUID().toString().replace("-", "");
        roleMapper.insertUserRole(roleRecordId, user.getId(), "role_developer", "system");

        user.setPasswordHash(null);
        return Result.success("创建成功", user);
    }

    @Override
    public Result<User> update(User user) {
        // 不更新密码
        user.setPasswordHash(null);
        userMapper.updateById(user);

        User updated = userMapper.selectById(user.getId());
        if (updated != null) {
            updated.setPasswordHash(null);
        }
        return Result.success("更新成功", updated);
    }

    @Override
    @Transactional
    public Result<Void> delete(String id) {
        // 同时删除用户的角色关联
        roleMapper.deleteUserRoles(id);
        userMapper.deleteById(id);
        return Result.success("删除成功");
    }

    @Override
    public Result<Void> resetPassword(String id, String newPassword) {
        User user = userMapper.selectById(id);
        if (user == null) {
            return Result.error("用户不存在");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);
        return Result.success("密码重置成功");
    }

    @Override
    @Transactional
    public Result<Void> assignRoles(String userId, List<String> roleIds) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }

        // 清除旧的角色关联
        roleMapper.deleteUserRoles(userId);

        // 插入新的角色关联
        if (roleIds != null && !roleIds.isEmpty()) {
            for (String roleId : roleIds) {
                String recordId = UUID.randomUUID().toString().replace("-", "");
                roleMapper.insertUserRole(recordId, userId, roleId, "system");
            }
        }

        log.info("用户[{}]角色分配完成，共 {} 个角色", user.getUsername(), roleIds != null ? roleIds.size() : 0);
        return Result.success("角色分配成功");
    }

    @Override
    public Result<Object> getUserRoles(String userId) {
        List<String> roleIds = roleMapper.findRoleIdsByUserId(userId);
        if (roleIds.isEmpty()) {
            return Result.success(new ArrayList<>());
        }
        // 返回完整的角色信息
        List<Role> roles = roleMapper.selectBatchIds(roleIds);
        // 过滤掉已删除的
        roles = roles.stream()
                .filter(r -> r != null)
                .collect(Collectors.toList());

        List<Map<String, Object>> result = roles.stream().map(role -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", role.getId());
            map.put("name", role.getName());
            map.put("code", role.getCode());
            map.put("description", role.getDescription());
            map.put("isSystem", role.getIsSystem());
            return map;
        }).collect(Collectors.toList());

        return Result.success(result);
    }
}
