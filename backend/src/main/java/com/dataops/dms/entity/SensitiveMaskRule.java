package com.dataops.dms.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 敏感数据脱敏规则实体
 * 定义数据脱敏的规则和模式
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_sensitive_mask_rule")
public class SensitiveMaskRule extends BaseEntity {

    /**
     * 规则名称
     */
    private String name;

    /**
     * 规则编码（唯一标识）
     */
    private String code;

    /**
     * 脱敏类型: FULL_MASK, PARTIAL_MASK, REGEX_MASK, EMAIL_MASK, PHONE_MASK
     */
    private String maskType;

    /**
     * 脱敏算法: PHONE/EMAIL/ID_CARD/BANK_CARD/FULL_MASK/NAME_MASK/CUSTOM
     */
    private String maskAlgorithm;

    /**
     * 脱敏模式/正则表达式
     */
    private String maskPattern;

    /**
     * 脱敏替换字符
     */
    private String maskCharacter;

    /**
     * 保留前缀长度
     */
    private Integer keepPrefixLen;

    /**
     * 保留后缀长度
     */
    private Integer keepSuffixLen;

    /**
     * 规则描述
     */
    private String description;

    /**
     * 示例输入
     */
    private String sampleInput;

    /**
     * 示例输出
     */
    private String sampleOutput;

    /**
     * 是否系统内置规则
     */
    private Boolean isSystem;
}
