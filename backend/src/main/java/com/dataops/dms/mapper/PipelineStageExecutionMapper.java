package com.dataops.dms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataops.dms.entity.PipelineStageExecution;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PipelineStageExecutionMapper extends BaseMapper<PipelineStageExecution> {
}
