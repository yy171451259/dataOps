package com.dataops.dms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataops.dms.entity.Pipeline;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PipelineMapper extends BaseMapper<Pipeline> {
}
