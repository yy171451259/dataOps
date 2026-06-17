package com.dataops.dms.entity;

import com.baomidou.mybatisplus.annotation.TableField;
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
    @TableField(exist = false)
    private String businessName;
    @TableField(exist = false)
    private Boolean isSensitive;
    @TableField(exist = false)
    private String sensitivityLevel;

    /**
     * 业务描述
     */
    @TableField(exist = false)
    private String businessDesc;

    /**
     * 数据管家
     */
    @TableField(exist = false)
    private String dataSteward;
}
