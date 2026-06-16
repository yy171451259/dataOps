package com.dataops.dms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataops.dms.entity.MetadataTable;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface MetadataTableMapper extends BaseMapper<MetadataTable> {

    /**
     * 物理删除指定实例+数据库的元数据（绕过逻辑删除）
     */
    @Delete("DELETE FROM metadata_table WHERE database_id = #{databaseId} AND schema_name = #{schemaName}")
    int physicalDeleteBySchema(@Param("instanceId") String instanceId, @Param("schemaName") String schemaName);
}
