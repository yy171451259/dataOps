package com.dataops.dms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataops.dms.entity.DataQualityRule;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DataQualityRuleMapper extends BaseMapper<DataQualityRule> {
}
