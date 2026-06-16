package com.dataops.dms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataops.dms.entity.DatabaseInstance;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据库实例Mapper
 */
@Mapper
public interface DatabaseInstanceMapper extends BaseMapper<DatabaseInstance> {
}
