package com.dataops.dms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataops.dms.entity.SensitiveMaskRule;
import com.dataops.dms.mapper.SensitiveMaskRuleMapper;
import com.dataops.dms.service.SensitiveMaskRuleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 敏感数据脱敏规则服务实现
 */
@Slf4j
@Service
public class SensitiveMaskRuleServiceImpl extends ServiceImpl<SensitiveMaskRuleMapper, SensitiveMaskRule> implements SensitiveMaskRuleService {

    @Override
    public SensitiveMaskRule getByCode(String code) {
        LambdaQueryWrapper<SensitiveMaskRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SensitiveMaskRule::getCode, code);
        return this.getOne(wrapper);
    }

    @Override
    public List<SensitiveMaskRule> getSystemRules() {
        LambdaQueryWrapper<SensitiveMaskRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SensitiveMaskRule::getIsSystem, true);
        return this.list(wrapper);
    }

    @Override
    public SensitiveMaskRule createRule(SensitiveMaskRule rule) {
        // 检查编码唯一性
        LambdaQueryWrapper<SensitiveMaskRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SensitiveMaskRule::getCode, rule.getCode());
        if (this.count(wrapper) > 0) {
            throw new RuntimeException("规则编码 [" + rule.getCode() + "] 已存在");
        }
        rule.setIsSystem(false);
        this.save(rule);
        log.info("创建脱敏规则: {} ({})", rule.getName(), rule.getCode());
        return rule;
    }

    @Override
    public SensitiveMaskRule updateRule(SensitiveMaskRule rule) {
        SensitiveMaskRule existing = this.getById(rule.getId());
        if (existing == null) {
            throw new RuntimeException("脱敏规则不存在: " + rule.getId());
        }
        if (Boolean.TRUE.equals(existing.getIsSystem())) {
            throw new RuntimeException("系统内置规则不可编辑");
        }
        this.updateById(rule);
        log.info("更新脱敏规则: {} ({})", rule.getName(), rule.getCode());
        return this.getById(rule.getId());
    }

    @Override
    public boolean deleteRule(String id) {
        SensitiveMaskRule existing = this.getById(id);
        if (existing == null) {
            throw new RuntimeException("脱敏规则不存在: " + id);
        }
        if (Boolean.TRUE.equals(existing.getIsSystem())) {
            throw new RuntimeException("系统内置规则不可删除");
        }
        this.removeById(id);
        log.info("删除脱敏规则: {} ({})", existing.getName(), existing.getCode());
        return true;
    }
}
