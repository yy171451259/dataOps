package com.dataops.dms.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("data_quality_result")
public class DataQualityResult extends BaseEntity {
    private String ruleId;
    private String instanceId;
    private String checkValue;
    private String expectedValue;
    private Boolean isPass;
    private String errorMessage;
    private Integer executionTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime checkedAt;
}
