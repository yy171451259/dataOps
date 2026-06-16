package com.dataops.dms.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("data_masking_rule")
public class DataMaskingRule extends BaseEntity {
    private String name;
    private String ruleType;
    private String instanceId;
    private String tableName;
    private String columnName;
    private String maskAlgorithm;
    private String maskConfig;
    private String pattern;
    private Boolean isEnabled;
    private Integer priority;
}
