package com.dataops.dms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataops.dms.entity.UserPermission;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户-数据资源权限 Mapper
 */
@Mapper
public interface UserPermissionMapper extends BaseMapper<UserPermission> {
}
