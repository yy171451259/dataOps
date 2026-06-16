package com.dataops.dms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dms_pipeline_stage")
public class PipelineStage {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String pipelineId;
    private String stageName;
    private Integer stageOrder;
    private String databaseInstanceId;
    private Boolean requireApproval;
    private String approvalRole;
    private Boolean autoExecute;
    private LocalDateTime createdAt;
}
