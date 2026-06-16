package com.dataops.dms.dto;

import com.dataops.dms.entity.Pipeline;
import com.dataops.dms.entity.PipelineStage;
import lombok.Data;
import java.util.List;

@Data
public class PipelineDetailVO extends Pipeline {
    private List<StageDetail> stages;

    @Data
    public static class StageDetail extends PipelineStage {
        private String databaseName;
        private String databaseHost;
        private Integer databasePort;
    }
}
