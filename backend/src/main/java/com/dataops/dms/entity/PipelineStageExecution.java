package com.dataops.dms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dms_pipeline_stage_execution")
public class PipelineStageExecution {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String executionId;
    private String stageId;
    private String stageName;
    private Integer stageOrder;
    private String databaseInstanceId;
    private String sqlContent;
    private String status;
    private String executedBy;
    private LocalDateTime executedAt;
    private Integer durationSeconds;
    private String resultMessage;
    private String rollbackSql;
    private String rollbackedBy;
    private LocalDateTime rollbackedAt;
    private String approvalBy;
    private LocalDateTime approvalAt;
    private String approvalComment;
    private LocalDateTime createdAt;
}
