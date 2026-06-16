package com.dataops.dms.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataops.dms.common.result.Result;
import com.dataops.dms.entity.MetadataTable;
import com.dataops.dms.entity.MetadataColumn;

import java.util.List;

/**
 * 元数据管理服务接口
 */
public interface MetadataService {

    /**
     * 采集数据库元数据
     */
    Result<Integer> collectMetadata(String databaseId);

    /**
     * 采集指定数据库的元数据
     */
    Result<Integer> collectMetadata(String databaseId, String databaseName);

    /**
     * 获取表列表
     */
    Result<Page<MetadataTable>> listTables(Integer page, Integer size, String databaseId, String keyword);

    /**
     * 获取指定数据库下的表列表
     */
    Result<Page<MetadataTable>> listTables(Integer page, Integer size, String databaseId, String keyword, String schemaName);

    /**
     * 获取表的字段列表
     */
    Result<List<MetadataColumn>> listColumns(String tableId);

    /**
     * 获取表详情
     */
    Result<MetadataTable> getTableDetail(String tableId);

    /**
     * 搜索元数据
     */
    Result<Page<MetadataTable>> search(String keyword, String databaseId, Integer page, Integer size);

    /**
     * 更新表注释/标签
     */
    Result<MetadataTable> updateTableMeta(String tableId, String tableComment, String businessTags, String owner);

    /**
     * 更新字段注释
     */
    Result<MetadataColumn> updateColumnMeta(String columnId, String columnComment, String businessName, Boolean isSensitive);

    /**
     * 获取数据库统计信息
     */
    Result<Object> getDatabaseStats(String databaseId);
}
