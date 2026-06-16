package com.dataops.dms.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataops.dms.common.result.Result;
import com.dataops.dms.entity.DataQualityRule;
import com.dataops.dms.entity.DataQualityResult;

import java.util.List;

/**
 * 数据质量服务接口
 */
public interface DataQualityService {

    Result<Page<DataQualityRule>> listRules(Integer page, Integer size, String databaseId);

    Result<DataQualityRule> getRuleById(String id);

    Result<DataQualityRule> createRule(DataQualityRule rule);

    Result<DataQualityRule> updateRule(DataQualityRule rule);

    Result<Void> deleteRule(String id);

    Result<Void> toggleRule(String id, Boolean enabled);

    /**
     * 执行单条质量规则检查
     */
    Result<DataQualityResult> executeRule(String ruleId);

    /**
     * 执行指定数据库的所有质量规则
     */
    Result<List<DataQualityResult>> executeAllRules(String databaseId);

    /**
     * 获取检查结果历史
     */
    Result<Page<DataQualityResult>> listResults(Integer page, Integer size, String ruleId, String databaseId);
}
