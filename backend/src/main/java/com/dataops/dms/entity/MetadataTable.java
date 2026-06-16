package com.dataops.dms.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("metadata_table")
public class MetadataTable extends BaseEntity {
    private String instanceId;
    private String schemaName;
    private String tableName;
    private String tableComment;
    private String tableType;
    private Long rowCount;
    private Long dataSize;
    private String owner;
    private String maintainer;
    private String businessTags;
    private Float qualityScore;
    /**
     * 数据质量评级: A/B/C/D
     */
    private String qualityRating;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastCollected;
}
