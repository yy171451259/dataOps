package com.dataops.dms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataops.dms.entity.DataChangeBackup;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据变更备份Mapper
 */
@Mapper
public interface DataChangeBackupMapper extends BaseMapper<DataChangeBackup> {
}
