package com.dataops.dms.dto;

import lombok.Data;

@Data
public class MarkSensitiveDTO {
    private String instanceId;
    private String schemaName;
    private String tableName;
    private String columnName;
    private String sensitivityLevel;
    private String category;
    private String maskRuleId;
    private String description;
}
