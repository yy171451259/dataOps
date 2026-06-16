package com.dataops.dms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataops.dms.entity.SensitiveMaskRule;
import org.apache.ibatis.annotations.Mapper;

/**
 * 敏感数据脱敏规则Mapper
 */
@Mapper
public interface SensitiveMaskRuleMapper extends BaseMapper<SensitiveMaskRule> {
}
