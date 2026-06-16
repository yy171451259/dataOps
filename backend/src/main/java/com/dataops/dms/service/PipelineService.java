package com.dataops.dms.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.dataops.dms.dto.*;
import com.dataops.dms.entity.*;

import java.util.List;

public interface PipelineService extends IService<Pipeline> {
    Pipeline createPipeline(CreatePipelineRequest request, String userId);
    Page<Pipeline> listPipelines(Integer page, Integer size);
    PipelineDetailVO getPipelineDetail(String id);
    boolean deletePipeline(String id);

    String startExecution(StartExecutionRequest request, String userId);
    Page<PipelineExecution> listExecutions(String pipelineId, Integer page, Integer size);
    ExecutionDetailVO getExecutionDetail(String executionId);
    boolean executeStage(String stageExecutionId, String userId);
    boolean approveStage(String stageExecutionId, String comment, String userId);
    boolean rejectStage(String stageExecutionId, String comment, String userId);
    boolean rollbackStage(String stageExecutionId, String userId);
    boolean cancelExecution(String executionId, String userId);

    List<PipelineStage> getPipelineStages(String pipelineId);
}
