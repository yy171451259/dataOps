package com.dataops.dms.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataops.dms.common.result.Result;
import com.dataops.dms.entity.User;

/**
 * 用户管理服务接口
 */
public interface UserService {

    Result<Page<User>> list(Integer page, Integer size, String keyword);

    Result<User> getById(String id);

    Result<User> create(User user);

    Result<User> update(User user);

    Result<Void> delete(String id);

    Result<Void> resetPassword(String id, String newPassword);

    Result<Void> assignRoles(String userId, java.util.List<String> roleIds);

    Result<Object> getUserRoles(String userId);
}
