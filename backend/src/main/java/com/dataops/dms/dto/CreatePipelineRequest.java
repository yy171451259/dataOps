package com.dataops.dms.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.util.List;

@Data
public class CreatePipelineRequest {
    @NotBlank(message = "流水线名称不能为空")
    private String name;
    private String description;
    @NotEmpty(message = "阶段配置不能为空")
    private List<StageConfig> stages;

    @Data
    public static class StageConfig {
        private String stageName;
        private Integer stageOrder;
        private String databaseInstanceId;
        private Boolean requireApproval;
        private String approvalRole;
        private Boolean autoExecute;
    }
}
