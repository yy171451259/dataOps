package com.dataops.dms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataops.dms.entity.PermissionAuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PermissionAuditLogMapper extends BaseMapper<PermissionAuditLog> {
}
