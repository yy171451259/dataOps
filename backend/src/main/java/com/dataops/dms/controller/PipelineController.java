package com.dataops.dms.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataops.dms.common.result.Result;
import com.dataops.dms.dto.*;
import com.dataops.dms.entity.*;
import com.dataops.dms.service.PipelineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/pipelines")
@Tag(name = "DDL部署流水线")
public class PipelineController {

    @Resource
    private PipelineService pipelineService;

    @PostMapping
    @Operation(summary = "创建流水线")
    public Result<Pipeline> createPipeline(@Valid @RequestBody CreatePipelineRequest request, Principal principal) {
        Pipeline pipeline = pipelineService.createPipeline(request, principal.getName());
        return Result.success("创建成功", pipeline);
    }

    @GetMapping
    @Operation(summary = "流水线列表")
    public Result<Page<Pipeline>> listPipelines(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(pipelineService.listPipelines(page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "流水线详情")
    public Result<PipelineDetailVO> getPipelineDetail(@PathVariable String id) {
        return Result.success(pipelineService.getPipelineDetail(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除流水线")
    public Result<Boolean> deletePipeline(@PathVariable String id) {
        return Result.success("删除成功", pipelineService.deletePipeline(id));
    }

    @PostMapping("/executions")
    @Operation(summary = "启动变更执行")
    public Result<String> startExecution(@Valid @RequestBody StartExecutionRequest request, Principal principal) {
        String executionId = pipelineService.startExecution(request, principal.getName());
        return Result.success("已启动", executionId);
    }

    @GetMapping("/executions")
    @Operation(summary = "执行记录列表")
    public Result<Page<PipelineExecution>> listExecutions(
            @RequestParam(required = false) String pipelineId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        return Result.success(pipelineService.listExecutions(pipelineId, page, size));
    }

    @GetMapping("/executions/{executionId}")
    @Operation(summary = "执行详情")
    public Result<ExecutionDetailVO> getExecutionDetail(@PathVariable String executionId) {
        return Result.success(pipelineService.getExecutionDetail(executionId));
    }

    @PostMapping("/stage-executions/{id}/execute")
    @Operation(summary = "执行阶段")
    public Result<Boolean> executeStage(@PathVariable String id, Principal principal) {
        return Result.success("执行完成", pipelineService.executeStage(id, principal.getName()));
    }

    @PostMapping("/stage-executions/{id}/approve")
    @Operation(summary = "审批通过")
    public Result<Boolean> approveStage(@PathVariable String id, @RequestBody Map<String, String> body, Principal principal) {
        return Result.success("审批通过", pipelineService.approveStage(id, body.get("comment"), principal.getName()));
    }

    @PostMapping("/stage-executions/{id}/reject")
    @Operation(summary = "审批拒绝")
    public Result<Boolean> rejectStage(@PathVariable String id, @RequestBody Map<String, String> body, Principal principal) {
        return Result.success("已拒绝", pipelineService.rejectStage(id, body.get("comment"), principal.getName()));
    }

    @PostMapping("/stage-executions/{id}/rollback")
    @Operation(summary = "回滚阶段")
    public Result<Boolean> rollbackStage(@PathVariable String id, Principal principal) {
        return Result.success("回滚完成", pipelineService.rollbackStage(id, principal.getName()));
    }

    @PostMapping("/executions/{id}/cancel")
    @Operation(summary = "取消执行")
    public Result<Boolean> cancelExecution(@PathVariable String id, Principal principal) {
        return Result.success("已取消", pipelineService.cancelExecution(id, principal.getName()));
    }

    @GetMapping("/{id}/stages")
    @Operation(summary = "获取流水线阶段")
    public Result<List<PipelineStage>> getPipelineStages(@PathVariable String id) {
        return Result.success(pipelineService.getPipelineStages(id));
    }
}
