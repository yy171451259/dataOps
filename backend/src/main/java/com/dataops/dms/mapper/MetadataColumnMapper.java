package com.dataops.dms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataops.dms.entity.MetadataColumn;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MetadataColumnMapper extends BaseMapper<MetadataColumn> {

    /**
     * 物理删除指定实例的字段元数据（绕过逻辑删除）
     */
    @Delete("DELETE FROM metadata_column WHERE instance_id = #{instanceId}")
    int physicalDeleteByInstanceId(@Param("instanceId") String instanceId);
}
