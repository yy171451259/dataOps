package com.dataops.dms.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("dms_pipeline_execution")
public class PipelineExecution {
    @TableId(type = IdType.ASSIGN_UUID)
    private String id;
    private String pipelineId;
    private String title;
    private String description;
    private String sqlContent;
    private String createdBy;
    private String currentStageId;
    private Integer currentStageOrder;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
