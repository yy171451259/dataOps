package com.dataops.dms.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;

@Data
public class StartExecutionRequest {
    @NotBlank(message = "流水线ID不能为空")
    private String pipelineId;
    @NotBlank(message = "变更标题不能为空")
    private String title;
    private String description;
    @NotBlank(message = "SQL内容不能为空")
    private String sqlContent;
}
