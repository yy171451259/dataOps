package com.dataops.dms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataops.dms.common.result.Result;
import com.dataops.dms.entity.DataQualityResult;
import com.dataops.dms.entity.DataQualityRule;
import com.dataops.dms.mapper.DataQualityResultMapper;
import com.dataops.dms.mapper.DataQualityRuleMapper;
import com.dataops.dms.service.DataQualityService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class DataQualityServiceImpl implements DataQualityService {

    @Resource
    private DataQualityRuleMapper ruleMapper;

    @Resource
    private DataQualityResultMapper resultMapper;

    @Override
    public Result<Page<DataQualityRule>> listRules(Integer page, Integer size, String databaseId) {
        LambdaQueryWrapper<DataQualityRule> wrapper = new LambdaQueryWrapper<>();
        if (databaseId != null && !databaseId.isEmpty()) {
            wrapper.eq(DataQualityRule::getInstanceId, databaseId);
        }
        wrapper.orderByDesc(DataQualityRule::getCreateTime);
        return Result.success(ruleMapper.selectPage(new Page<>(page, size), wrapper));
    }

    @Override
    public Result<DataQualityRule> getRuleById(String id) {
        return Result.success(ruleMapper.selectById(id));
    }

    @Override
    public Result<DataQualityRule> createRule(DataQualityRule rule) {
        rule.setIsEnabled(rule.getIsEnabled() != null ? rule.getIsEnabled() : true);
        ruleMapper.insert(rule);
        return Result.success("创建成功", rule);
    }

    @Override
    public Result<DataQualityRule> updateRule(DataQualityRule rule) {
        ruleMapper.updateById(rule);
        return Result.success("更新成功", rule);
    }

    @Override
    public Result<Void> deleteRule(String id) {
        ruleMapper.deleteById(id);
        return Result.success("删除成功");
    }

    @Override
    public Result<Void> toggleRule(String id, Boolean enabled) {
        DataQualityRule rule = ruleMapper.selectById(id);
        if (rule != null) {
            rule.setIsEnabled(enabled);
            ruleMapper.updateById(rule);
        }
        return Result.success();
    }

    @Override
    public Result<DataQualityResult> executeRule(String ruleId) {
        DataQualityRule rule = ruleMapper.selectById(ruleId);
        if (rule == null) {
            return Result.error("规则不存在");
        }

        DataQualityResult result = new DataQualityResult();
        result.setRuleId(ruleId);
        result.setInstanceId(rule.getInstanceId());
        result.setExpectedValue(rule.getExpectedValue());
        result.setCheckedAt(LocalDateTime.now());

        long startTime = System.currentTimeMillis();

        try {
            // TODO: 真实场景需连接目标数据库执行 check_sql，此处为占位实现
            result.setCheckValue("N/A");
            result.setIsPass(true);
            result.setExecutionTime((int) (System.currentTimeMillis() - startTime));
        } catch (Exception e) {
            result.setIsPass(false);
            result.setErrorMessage(e.getMessage());
            result.setExecutionTime((int) (System.currentTimeMillis() - startTime));
        }

        resultMapper.insert(result);
        return Result.success(result);
    }

    @Override
    public Result<List<DataQualityResult>> executeAllRules(String databaseId) {
        LambdaQueryWrapper<DataQualityRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DataQualityRule::getInstanceId, databaseId);
        wrapper.eq(DataQualityRule::getIsEnabled, true);
        List<DataQualityRule> rules = ruleMapper.selectList(wrapper);

        List<DataQualityResult> results = new ArrayList<>();
        for (DataQualityRule rule : rules) {
            Result<DataQualityResult> r = executeRule(rule.getId());
            if (r.getData() != null) {
                results.add(r.getData());
            }
        }

        return Result.success("执行完成，共检查" + results.size() + "条规则", results);
    }

    @Override
    public Result<Page<DataQualityResult>> listResults(Integer page, Integer size, String ruleId, String databaseId) {
        LambdaQueryWrapper<DataQualityResult> wrapper = new LambdaQueryWrapper<>();
        if (ruleId != null && !ruleId.isEmpty()) {
            wrapper.eq(DataQualityResult::getRuleId, ruleId);
        }
        if (databaseId != null && !databaseId.isEmpty()) {
            wrapper.eq(DataQualityResult::getInstanceId, databaseId);
        }
        wrapper.orderByDesc(DataQualityResult::getCheckedAt);
        return Result.success(resultMapper.selectPage(new Page<>(page, size), wrapper));
    }
}
