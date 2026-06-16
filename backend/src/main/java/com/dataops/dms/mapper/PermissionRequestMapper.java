package com.dataops.dms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataops.dms.entity.PermissionRequest;
import org.apache.ibatis.annotations.Mapper;

/**
 * 权限申请Mapper
 */
@Mapper
public interface PermissionRequestMapper extends BaseMapper<PermissionRequest> {
}
