package com.dataops.dms.dto;

import com.dataops.dms.entity.PipelineExecution;
import com.dataops.dms.entity.PipelineStageExecution;
import lombok.Data;
import java.util.List;

@Data
public class ExecutionDetailVO extends PipelineExecution {
    private String pipelineName;
    private List<StageExecutionDetail> stageExecutions;

    @Data
    public static class StageExecutionDetail extends PipelineStageExecution {
        private String databaseName;
        private String databaseHost;
    }
}
