package com.dataops.dms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataops.dms.entity.SensitiveColumn;
import org.apache.ibatis.annotations.Mapper;

/**
 * 敏感列Mapper
 */
@Mapper
public interface SensitiveColumnMapper extends BaseMapper<SensitiveColumn> {
}
