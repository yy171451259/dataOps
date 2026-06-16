package com.dataops.dms.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("metadata_column")
public class MetadataColumn extends BaseEntity {
    private String tableId;
    private String instanceId;
    private String columnName;
    private String dataType;
    private Long columnLength;
    private Integer columnPrecision;
    private Boolean isNullable;
    private Boolean isPrimaryKey;
    private String defaultValue;
    private String columnComment;
    private Integer ordinalPosition;
    private String businessName;
    private Boolean isSensitive;
    private String sensitivityLevel;

    /**
     * 业务描述
     */
    private String businessDesc;

    /**
     * 数据管家
     */
    private String dataSteward;
}
