package com.dataops.dms.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dataops.dms.entity.SensitiveColumn;

import java.util.List;

/**
 * 敏感列服务接口
 */
public interface SensitiveColumnService extends IService<SensitiveColumn> {

    /**
     * 标记单个敏感列
     */
    SensitiveColumn markSensitive(SensitiveColumn col, String userId);

    /**
     * 批量标记敏感列
     */
    int batchMark(List<SensitiveColumn> cols, String userId);

    /**
     * 根据数据库获取敏感列列表
     */
    List<SensitiveColumn> getByDatabase(String databaseId, String databaseName);

    /**
     * 根据表获取敏感列列表
     */
    List<SensitiveColumn> getByTable(String databaseId, String databaseName, String tableName);

    /**
     * 删除敏感列标记
     */
    boolean deleteSensitive(String id);
}
