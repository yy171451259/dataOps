package com.dataops.dms.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataops.dms.common.result.Result;
import com.dataops.dms.entity.DataMaskingRule;

/**
 * 数据脱敏服务接口
 */
public interface DataMaskingService {

    Result<Page<DataMaskingRule>> listRules(Integer page, Integer size, String databaseId);

    Result<DataMaskingRule> getRuleById(String id);

    Result<DataMaskingRule> createRule(DataMaskingRule rule);

    Result<DataMaskingRule> updateRule(DataMaskingRule rule);

    Result<Void> deleteRule(String id);

    Result<Void> toggleRule(String id, Boolean enabled);

    /**
     * 对查询结果应用脱敏规则
     */
    Result<Object> applyMasking(String databaseId, String tableName, Object data);
}
