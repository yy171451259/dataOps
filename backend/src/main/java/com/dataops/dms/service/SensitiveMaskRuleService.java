package com.dataops.dms.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dataops.dms.entity.SensitiveMaskRule;

import java.util.List;

/**
 * 敏感数据脱敏规则服务接口
 */
public interface SensitiveMaskRuleService extends IService<SensitiveMaskRule> {

    /**
     * 根据编码获取脱敏规则
     */
    SensitiveMaskRule getByCode(String code);

    /**
     * 获取所有系统内置脱敏规则
     */
    List<SensitiveMaskRule> getSystemRules();

    /**
     * 创建脱敏规则
     */
    SensitiveMaskRule createRule(SensitiveMaskRule rule);

    /**
     * 更新脱敏规则
     */
    SensitiveMaskRule updateRule(SensitiveMaskRule rule);

    /**
     * 删除脱敏规则（系统内置不可删）
     */
    boolean deleteRule(String id);
}
