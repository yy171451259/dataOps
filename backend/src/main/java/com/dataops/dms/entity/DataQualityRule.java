package com.dataops.dms.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("data_quality_rule")
public class DataQualityRule extends BaseEntity {
    private String name;
    private String ruleType;
    private String instanceId;
    private String tableName;
    private String columnName;
    private String checkSql;
    private String expectedValue;
    private String operator;
    private String severity;
    private Boolean isEnabled;
    private String cronExpression;
}
