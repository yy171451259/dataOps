package com.dataops.dms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 敏感列实体
 * 标记数据库中包含敏感数据的列
 */
@Data
@TableName("sys_sensitive_column")
public class SensitiveColumn {

    @TableId(type = IdType.ASSIGN_UUID)
    private String id;

    /**
     * 实例ID
     */
    private String instanceId;

    /**
     * 数据库名称
     */
    private String schemaName;

    /**
     * 表名
     */
    private String tableName;

    /**
     * 列名
     */
    private String columnName;

    /**
     * 敏感级别: HIGH, MEDIUM, LOW
     */
    private String sensitivityLevel;

    /**
     * 敏感分类: PHONE, EMAIL, ID_CARD, NAME, ADDRESS, BANK_CARD, CUSTOM
     */
    private String category;

    /**
     * 关联的脱敏规则ID
     */
    private String maskRuleId;

    /**
     * 是否启用
     */
    private Boolean isActive;

    /**
     * 描述
     */
    private String description;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;

    /**
     * 创建人
     */
    private String createdBy;
}
