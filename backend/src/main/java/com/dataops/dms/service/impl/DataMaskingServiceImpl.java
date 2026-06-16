package com.dataops.dms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataops.dms.common.result.Result;
import com.dataops.dms.entity.DataMaskingRule;
import com.dataops.dms.mapper.DataMaskingRuleMapper;
import com.dataops.dms.service.DataMaskingService;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;

@Service
public class DataMaskingServiceImpl implements DataMaskingService {

    @Resource
    private DataMaskingRuleMapper ruleMapper;

    @Override
    public Result<Page<DataMaskingRule>> listRules(Integer page, Integer size, String databaseId) {
        LambdaQueryWrapper<DataMaskingRule> wrapper = new LambdaQueryWrapper<>();
        if (databaseId != null && !databaseId.isEmpty()) {
            wrapper.eq(DataMaskingRule::getInstanceId, databaseId);
        }
        wrapper.orderByAsc(DataMaskingRule::getPriority);
        return Result.success(ruleMapper.selectPage(new Page<>(page, size), wrapper));
    }

    @Override
    public Result<DataMaskingRule> getRuleById(String id) {
        return Result.success(ruleMapper.selectById(id));
    }

    @Override
    public Result<DataMaskingRule> createRule(DataMaskingRule rule) {
        rule.setIsEnabled(rule.getIsEnabled() != null ? rule.getIsEnabled() : true);
        ruleMapper.insert(rule);
        return Result.success("创建成功", rule);
    }

    @Override
    public Result<DataMaskingRule> updateRule(DataMaskingRule rule) {
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
        DataMaskingRule rule = ruleMapper.selectById(id);
        if (rule != null) {
            rule.setIsEnabled(enabled);
            ruleMapper.updateById(rule);
        }
        return Result.success();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Result<Object> applyMasking(String databaseId, String tableName, Object data) {
        if (!(data instanceof List)) {
            return Result.success(data);
        }

        LambdaQueryWrapper<DataMaskingRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DataMaskingRule::getIsEnabled, true);
        wrapper.eq(DataMaskingRule::getInstanceId, databaseId);
        wrapper.eq(DataMaskingRule::getTableName, tableName);
        List<DataMaskingRule> rules = ruleMapper.selectList(wrapper);

        if (rules.isEmpty()) {
            return Result.success(data);
        }

        List<Map<String, Object>> rows = (List<Map<String, Object>>) data;
        for (Map<String, Object> row : rows) {
            for (DataMaskingRule rule : rules) {
                String column = rule.getColumnName();
                if (row.containsKey(column)) {
                    Object value = row.get(column);
                    if (value != null) {
                        row.put(column, maskValue(value.toString(), rule));
                    }
                }
            }
        }
        return Result.success(rows);
    }

    private String maskValue(String value, DataMaskingRule rule) {
        String algorithm = rule.getMaskAlgorithm();
        if (algorithm == null) {
            return value;
        }

        switch (algorithm.toUpperCase()) {
            case "REPLACE":
                return maskReplace(value);
            case "MASK":
                return maskChar(value, '*');
            case "HASH":
                return maskHash(value);
            case "TRUNCATE":
                return maskTruncate(value);
            case "ENCRYPT":
                return maskEncrypt(value);
            case "PHONE":
                return maskPhone(value);
            case "EMAIL":
                return maskEmail(value);
            case "ID_CARD":
                return maskIdCard(value);
            case "BANK_CARD":
                return maskBankCard(value);
            default:
                return value;
        }
    }

    private String maskReplace(String value) {
        return "****";
    }

    private String maskChar(String value, char c) {
        if (value.length() <= 2) return "**";
        StringBuilder sb = new StringBuilder();
        sb.append(value.charAt(0));
        for (int i = 1; i < value.length() - 1; i++) {
            sb.append(c);
        }
        if (value.length() > 1) {
            sb.append(value.charAt(value.length() - 1));
        }
        return sb.toString();
    }

    private String maskHash(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(value.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString().substring(0, 8);
        } catch (Exception e) {
            return "****";
        }
    }

    private String maskTruncate(String value) {
        if (value.length() > 4) {
            return value.substring(0, 2) + "..." + value.substring(value.length() - 2);
        }
        return "****";
    }

    private String maskEncrypt(String value) {
        try {
            return "ENC:" + Base64.getEncoder().encodeToString(value.getBytes());
        } catch (Exception e) {
            return "****";
        }
    }

    private String maskPhone(String value) {
        if (value.length() == 11) {
            return value.substring(0, 3) + "****" + value.substring(7);
        }
        return maskChar(value, '*');
    }

    private String maskEmail(String value) {
        int atIndex = value.indexOf('@');
        if (atIndex > 0) {
            String prefix = value.substring(0, atIndex);
            String suffix = value.substring(atIndex);
            if (prefix.length() <= 2) {
                return "*" + suffix;
            }
            return prefix.charAt(0) + "***" + prefix.charAt(prefix.length() - 1) + suffix;
        }
        return maskChar(value, '*');
    }

    private String maskIdCard(String value) {
        if (value.length() == 18) {
            return value.substring(0, 6) + "********" + value.substring(14);
        }
        return maskChar(value, '*');
    }

    private String maskBankCard(String value) {
        if (value.length() >= 16) {
            return value.substring(0, 4) + "******" + value.substring(value.length() - 4);
        }
        return maskChar(value, '*');
    }
}
