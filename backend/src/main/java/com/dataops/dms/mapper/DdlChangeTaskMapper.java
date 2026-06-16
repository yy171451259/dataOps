package com.dataops.dms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataops.dms.entity.DdlChangeTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * DDL变更流转任务Mapper
 */
@Mapper
public interface DdlChangeTaskMapper extends BaseMapper<DdlChangeTask> {
}
