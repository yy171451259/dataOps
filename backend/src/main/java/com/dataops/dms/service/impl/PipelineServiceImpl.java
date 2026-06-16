package com.dataops.dms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataops.dms.common.result.Result;
import com.dataops.dms.dto.*;
import com.dataops.dms.entity.*;
import com.dataops.dms.mapper.*;
import com.dataops.dms.service.DatabaseInstanceService;
import com.dataops.dms.service.PipelineService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PipelineServiceImpl extends ServiceImpl<PipelineMapper, Pipeline> implements PipelineService {

    @Resource
    private PipelineStageMapper stageMapper;
    @Resource
    private PipelineExecutionMapper executionMapper;
    @Resource
    private PipelineStageExecutionMapper stageExecutionMapper;
    @Resource
    private DatabaseInstanceService databaseInstanceService;
    @Resource
    private DatabaseInstanceMapper databaseInstanceMapper;
    @Resource
    private com.dataops.dms.mapper.RoleMapper roleMapper;

    @Override
    @Transactional
    public Pipeline createPipeline(CreatePipelineRequest request, String userId) {
        Pipeline pipeline = new Pipeline();
        pipeline.setName(request.getName());
        pipeline.setDescription(request.getDescription());
        pipeline.setStatus("ACTIVE");
        pipeline.setCreatedBy(userId);
        pipeline.setCreatedAt(LocalDateTime.now());
        save(pipeline);

        List<CreatePipelineRequest.StageConfig> stages = request.getStages();
        stages.sort(Comparator.comparingInt(CreatePipelineRequest.StageConfig::getStageOrder));

        for (CreatePipelineRequest.StageConfig config : stages) {
            PipelineStage stage = new PipelineStage();
            stage.setPipelineId(pipeline.getId());
            stage.setStageName(config.getStageName());
            stage.setStageOrder(config.getStageOrder());
            stage.setDatabaseInstanceId(config.getDatabaseInstanceId());
            stage.setRequireApproval(config.getRequireApproval() != null && config.getRequireApproval());
            stage.setApprovalRole(config.getApprovalRole());
            stage.setAutoExecute(config.getAutoExecute() != null ? config.getAutoExecute() : true);
            stage.setCreatedAt(LocalDateTime.now());
            stageMapper.insert(stage);
        }

        return pipeline;
    }

    @Override
    public Page<Pipeline> listPipelines(Integer page, Integer size) {
        Page<Pipeline> pageParam = new Page<>(page, size);
        return page(pageParam, new LambdaQueryWrapper<Pipeline>().orderByDesc(Pipeline::getCreatedAt));
    }

    @Override
    public PipelineDetailVO getPipelineDetail(String id) {
        Pipeline pipeline = getById(id);
        if (pipeline == null) return null;

        PipelineDetailVO vo = new PipelineDetailVO();
        vo.setId(pipeline.getId());
        vo.setName(pipeline.getName());
        vo.setDescription(pipeline.getDescription());
        vo.setStatus(pipeline.getStatus());
        vo.setCreatedBy(pipeline.getCreatedBy());
        vo.setCreatedAt(pipeline.getCreatedAt());
        vo.setUpdatedAt(pipeline.getUpdatedAt());

        List<PipelineStage> stages = stageMapper.selectList(
            new LambdaQueryWrapper<PipelineStage>()
                .eq(PipelineStage::getPipelineId, id)
                .orderByAsc(PipelineStage::getStageOrder)
        );

        Set<String> dbIds = stages.stream().map(PipelineStage::getDatabaseInstanceId).collect(Collectors.toSet());
        Map<String, DatabaseInstance> dbMap = new HashMap<>();
        if (!dbIds.isEmpty()) {
            List<DatabaseInstance> dbs = databaseInstanceMapper.selectBatchIds(dbIds);
            dbMap = dbs.stream().collect(Collectors.toMap(DatabaseInstance::getId, db -> db));
        }

        List<PipelineDetailVO.StageDetail> stageDetails = new ArrayList<>();
        for (PipelineStage stage : stages) {
            PipelineDetailVO.StageDetail detail = new PipelineDetailVO.StageDetail();
            detail.setId(stage.getId());
            detail.setPipelineId(stage.getPipelineId());
            detail.setStageName(stage.getStageName());
            detail.setStageOrder(stage.getStageOrder());
            detail.setDatabaseInstanceId(stage.getDatabaseInstanceId());
            detail.setRequireApproval(stage.getRequireApproval());
            detail.setApprovalRole(stage.getApprovalRole());
            detail.setAutoExecute(stage.getAutoExecute());
            detail.setCreatedAt(stage.getCreatedAt());

            DatabaseInstance db = dbMap.get(stage.getDatabaseInstanceId());
            if (db != null) {
                detail.setDatabaseName(db.getName());
                detail.setDatabaseHost(db.getHost());
                detail.setDatabasePort(db.getPort());
            }
            stageDetails.add(detail);
        }
        vo.setStages(stageDetails);

        return vo;
    }

    @Override
    @Transactional
    public boolean deletePipeline(String id) {
        stageMapper.delete(new LambdaQueryWrapper<PipelineStage>().eq(PipelineStage::getPipelineId, id));
        return removeById(id);
    }

    @Override
    @Transactional
    public String startExecution(StartExecutionRequest request, String userId) {
        Pipeline pipeline = getById(request.getPipelineId());
        if (pipeline == null || !"ACTIVE".equals(pipeline.getStatus())) {
            throw new RuntimeException("流水线不存在或未启用");
        }

        List<PipelineStage> stages = getPipelineStages(request.getPipelineId());
        if (stages.isEmpty()) {
            throw new RuntimeException("流水线未配置阶段");
        }

        PipelineExecution execution = new PipelineExecution();
        execution.setPipelineId(request.getPipelineId());
        execution.setTitle(request.getTitle());
        execution.setDescription(request.getDescription());
        execution.setSqlContent(request.getSqlContent());
        execution.setCreatedBy(userId);
        execution.setStatus("IN_PROGRESS");
        execution.setCreatedAt(LocalDateTime.now());
        executionMapper.insert(execution);

        PipelineStage firstStage = stages.get(0);
        execution.setCurrentStageId(firstStage.getId());
        execution.setCurrentStageOrder(firstStage.getStageOrder());
        executionMapper.updateById(execution);

        for (PipelineStage stage : stages) {
            PipelineStageExecution stageExec = new PipelineStageExecution();
            stageExec.setExecutionId(execution.getId());
            stageExec.setStageId(stage.getId());
            stageExec.setStageName(stage.getStageName());
            stageExec.setStageOrder(stage.getStageOrder());
            stageExec.setDatabaseInstanceId(stage.getDatabaseInstanceId());
            stageExec.setSqlContent(request.getSqlContent());
            stageExec.setStatus(stage.getStageOrder().equals(firstStage.getStageOrder()) ? 
                (stage.getRequireApproval() ? "APPROVING" : "PENDING") : "PENDING");
            stageExec.setCreatedAt(LocalDateTime.now());
            stageExecutionMapper.insert(stageExec);
        }

        PipelineStageExecution firstStageExec = getStageExecution(execution.getId(), firstStage.getId());
        if (firstStageExec != null && !firstStage.getRequireApproval() && firstStage.getAutoExecute()) {
            doExecuteStage(firstStageExec, userId);
        }

        return execution.getId();
    }

    @Override
    public Page<PipelineExecution> listExecutions(String pipelineId, Integer page, Integer size) {
        Page<PipelineExecution> pageParam = new Page<>(page, size);
        return executionMapper.selectPage(pageParam,
            new LambdaQueryWrapper<PipelineExecution>()
                .eq(pipelineId != null, PipelineExecution::getPipelineId, pipelineId)
                .orderByDesc(PipelineExecution::getCreatedAt));
    }

    @Override
    public ExecutionDetailVO getExecutionDetail(String executionId) {
        PipelineExecution execution = executionMapper.selectById(executionId);
        if (execution == null) return null;

        ExecutionDetailVO vo = new ExecutionDetailVO();
        vo.setId(execution.getId());
        vo.setPipelineId(execution.getPipelineId());
        vo.setTitle(execution.getTitle());
        vo.setDescription(execution.getDescription());
        vo.setSqlContent(execution.getSqlContent());
        vo.setCreatedBy(execution.getCreatedBy());
        vo.setCurrentStageId(execution.getCurrentStageId());
        vo.setCurrentStageOrder(execution.getCurrentStageOrder());
        vo.setStatus(execution.getStatus());
        vo.setCreatedAt(execution.getCreatedAt());
        vo.setUpdatedAt(execution.getUpdatedAt());

        Pipeline pipeline = getById(execution.getPipelineId());
        if (pipeline != null) {
            vo.setPipelineName(pipeline.getName());
        }

        List<PipelineStageExecution> stageExecs = stageExecutionMapper.selectList(
            new LambdaQueryWrapper<PipelineStageExecution>()
                .eq(PipelineStageExecution::getExecutionId, executionId)
                .orderByAsc(PipelineStageExecution::getStageOrder)
        );

        Set<String> dbIds = stageExecs.stream().map(PipelineStageExecution::getDatabaseInstanceId).collect(Collectors.toSet());
        Map<String, DatabaseInstance> dbMap = new HashMap<>();
        if (!dbIds.isEmpty()) {
            List<DatabaseInstance> dbs = databaseInstanceMapper.selectBatchIds(dbIds);
            dbMap = dbs.stream().collect(Collectors.toMap(DatabaseInstance::getId, db -> db));
        }

        List<ExecutionDetailVO.StageExecutionDetail> details = new ArrayList<>();
        for (PipelineStageExecution se : stageExecs) {
            ExecutionDetailVO.StageExecutionDetail detail = new ExecutionDetailVO.StageExecutionDetail();
            detail.setId(se.getId());
            detail.setExecutionId(se.getExecutionId());
            detail.setStageId(se.getStageId());
            detail.setStageName(se.getStageName());
            detail.setStageOrder(se.getStageOrder());
            detail.setDatabaseInstanceId(se.getDatabaseInstanceId());
            detail.setSqlContent(se.getSqlContent());
            detail.setStatus(se.getStatus());
            detail.setExecutedBy(se.getExecutedBy());
            detail.setExecutedAt(se.getExecutedAt());
            detail.setDurationSeconds(se.getDurationSeconds());
            detail.setResultMessage(se.getResultMessage());
            detail.setRollbackSql(se.getRollbackSql());
            detail.setRollbackedBy(se.getRollbackedBy());
            detail.setRollbackedAt(se.getRollbackedAt());
            detail.setApprovalBy(se.getApprovalBy());
            detail.setApprovalAt(se.getApprovalAt());
            detail.setApprovalComment(se.getApprovalComment());
            detail.setCreatedAt(se.getCreatedAt());

            DatabaseInstance db = dbMap.get(se.getDatabaseInstanceId());
            if (db != null) {
                detail.setDatabaseName(db.getName());
                detail.setDatabaseHost(db.getHost());
            }
            details.add(detail);
        }
        vo.setStageExecutions(details);

        return vo;
    }

    @Override
    @Transactional
    public boolean executeStage(String stageExecutionId, String userId) {
        PipelineStageExecution stageExec = stageExecutionMapper.selectById(stageExecutionId);
        if (stageExec == null) {
            throw new RuntimeException("阶段执行记录不存在");
        }
        if (!"PENDING".equals(stageExec.getStatus())) {
            throw new RuntimeException("当前阶段状态不允许执行");
        }
        return doExecuteStage(stageExec, userId);
    }

    private boolean doExecuteStage(PipelineStageExecution stageExec, String userId) {
        PipelineStage stage = stageMapper.selectById(stageExec.getStageId());
        PipelineExecution execution = executionMapper.selectById(stageExec.getExecutionId());

        stageExec.setStatus("EXECUTING");
        stageExecutionMapper.updateById(stageExec);

        LocalDateTime startTime = LocalDateTime.now();
        try {
            DatabaseInstance db = databaseInstanceMapper.selectById(stageExec.getDatabaseInstanceId());
            if (db == null) {
                throw new RuntimeException("数据库实例不存在");
            }

            try (Connection conn = databaseInstanceService.getConnection(db);
                 Statement stmt = conn.createStatement()) {
                stmt.execute(stageExec.getSqlContent());
            }

            // 自动生成回滚SQL
            String rollbackSql = generateRollbackSql(stageExec.getSqlContent());
            stageExec.setRollbackSql(rollbackSql);

            int duration = (int) ChronoUnit.SECONDS.between(startTime, LocalDateTime.now());
            stageExec.setStatus("SUCCESS");
            stageExec.setExecutedBy(userId);
            stageExec.setExecutedAt(LocalDateTime.now());
            stageExec.setDurationSeconds(duration);
            stageExec.setResultMessage(rollbackSql != null ? "执行成功（已自动生成回滚SQL）" : "执行成功（无法自动生成回滚SQL，请手动配置）");
            stageExecutionMapper.updateById(stageExec);

            moveToNextStage(execution, stage, userId);
            return true;

        } catch (Exception e) {
            log.error("DDL执行失败", e);
            stageExec.setStatus("FAILED");
            stageExec.setExecutedBy(userId);
            stageExec.setExecutedAt(LocalDateTime.now());
            stageExec.setResultMessage("执行失败: " + e.getMessage());
            stageExecutionMapper.updateById(stageExec);

            execution.setStatus("FAILED");
            executionMapper.updateById(execution);
            return false;
        }
    }

    private void moveToNextStage(PipelineExecution execution, PipelineStage currentStage, String userId) {
        List<PipelineStage> stages = getPipelineStages(execution.getPipelineId());
        int currentIdx = -1;
        for (int i = 0; i < stages.size(); i++) {
            if (stages.get(i).getId().equals(currentStage.getId())) {
                currentIdx = i;
                break;
            }
        }

        if (currentIdx < 0 || currentIdx == stages.size() - 1) {
            execution.setStatus("SUCCESS");
            execution.setCurrentStageId(null);
            execution.setCurrentStageOrder(null);
            executionMapper.updateById(execution);
            return;
        }

        PipelineStage nextStage = stages.get(currentIdx + 1);
        PipelineStageExecution nextStageExec = getStageExecution(execution.getId(), nextStage.getId());

        execution.setCurrentStageId(nextStage.getId());
        execution.setCurrentStageOrder(nextStage.getStageOrder());
        executionMapper.updateById(execution);

        if (nextStage.getRequireApproval()) {
            nextStageExec.setStatus("APPROVING");
            stageExecutionMapper.updateById(nextStageExec);
        } else if (nextStage.getAutoExecute()) {
            nextStageExec.setStatus("PENDING");
            stageExecutionMapper.updateById(nextStageExec);
            doExecuteStage(nextStageExec, userId);
        } else {
            nextStageExec.setStatus("PENDING");
            stageExecutionMapper.updateById(nextStageExec);
        }
    }

    @Override
    @Transactional
    public boolean approveStage(String stageExecutionId, String comment, String userId) {
        PipelineStageExecution stageExec = stageExecutionMapper.selectById(stageExecutionId);
        if (stageExec == null || !"APPROVING".equals(stageExec.getStatus())) {
            throw new RuntimeException("状态不允许审批");
        }

        // 审批角色校验：检查操作人是否有权限审批此阶段
        PipelineStage stage = stageMapper.selectById(stageExec.getStageId());
        if (stage.getApprovalRole() != null && !stage.getApprovalRole().isEmpty()) {
            List<String> userRoleIds = roleMapper.findRoleIdsByUserId(userId);
            List<String> userRoleCodes = new ArrayList<>();
            if (!userRoleIds.isEmpty()) {
                List<com.dataops.dms.entity.Role> roles = roleMapper.selectBatchIds(userRoleIds);
                userRoleCodes = roles.stream()
                        .filter(r -> r != null)
                        .map(com.dataops.dms.entity.Role::getCode)
                        .collect(Collectors.toList());
            }
            // 检查用户是否拥有审批角色
            if (!userRoleCodes.contains(stage.getApprovalRole())) {
                throw new RuntimeException("无权审批此阶段，需要角色: " + stage.getApprovalRole());
            }
            log.info("审批角色校验通过: userId={}, requiredRole={}, userRoles={}", userId, stage.getApprovalRole(), userRoleCodes);
        }

        stageExec.setApprovalBy(userId);
        stageExec.setApprovalAt(LocalDateTime.now());
        stageExec.setApprovalComment(comment);
        stageExec.setStatus("PENDING");
        stageExecutionMapper.updateById(stageExec);

        if (stage.getAutoExecute()) {
            return doExecuteStage(stageExec, userId);
        }
        return true;
    }

    @Override
    @Transactional
    public boolean rejectStage(String stageExecutionId, String comment, String userId) {
        PipelineStageExecution stageExec = stageExecutionMapper.selectById(stageExecutionId);
        if (stageExec == null || !"APPROVING".equals(stageExec.getStatus())) {
            throw new RuntimeException("状态不允许审批");
        }

        // 审批角色校验
        PipelineStage stage = stageMapper.selectById(stageExec.getStageId());
        if (stage.getApprovalRole() != null && !stage.getApprovalRole().isEmpty()) {
            List<String> userRoleIds = roleMapper.findRoleIdsByUserId(userId);
            List<String> userRoleCodes = new ArrayList<>();
            if (!userRoleIds.isEmpty()) {
                List<com.dataops.dms.entity.Role> roles = roleMapper.selectBatchIds(userRoleIds);
                userRoleCodes = roles.stream()
                        .filter(r -> r != null)
                        .map(com.dataops.dms.entity.Role::getCode)
                        .collect(Collectors.toList());
            }
            if (!userRoleCodes.contains(stage.getApprovalRole())) {
                throw new RuntimeException("无权审批此阶段，需要角色: " + stage.getApprovalRole());
            }
        }

        stageExec.setApprovalBy(userId);
        stageExec.setApprovalAt(LocalDateTime.now());
        stageExec.setApprovalComment(comment);
        stageExec.setStatus("FAILED");
        stageExecutionMapper.updateById(stageExec);

        PipelineExecution execution = executionMapper.selectById(stageExec.getExecutionId());
        execution.setStatus("FAILED");
        executionMapper.updateById(execution);

        return true;
    }

    @Override
    @Transactional
    public boolean rollbackStage(String stageExecutionId, String userId) {
        PipelineStageExecution stageExec = stageExecutionMapper.selectById(stageExecutionId);
        if (stageExec == null || !"SUCCESS".equals(stageExec.getStatus())) {
            throw new RuntimeException("只有成功执行的阶段才能回滚");
        }

        if (stageExec.getRollbackSql() == null || stageExec.getRollbackSql().isEmpty()) {
            throw new RuntimeException("未配置回滚SQL，无法回滚。请手动编辑该阶段的回滚SQL后重试。");
        }

        try {
            DatabaseInstance db = databaseInstanceMapper.selectById(stageExec.getDatabaseInstanceId());
            try (Connection conn = databaseInstanceService.getConnection(db);
                 Statement stmt = conn.createStatement()) {
                stmt.execute(stageExec.getRollbackSql());
            }

            stageExec.setStatus("ROLLBACKED");
            stageExec.setRollbackedBy(userId);
            stageExec.setRollbackedAt(LocalDateTime.now());
            stageExecutionMapper.updateById(stageExec);

            PipelineExecution execution = executionMapper.selectById(stageExec.getExecutionId());
            execution.setStatus("FAILED");
            executionMapper.updateById(execution);

            return true;
        } catch (Exception e) {
            throw new RuntimeException("回滚失败: " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public boolean cancelExecution(String executionId, String userId) {
        PipelineExecution execution = executionMapper.selectById(executionId);
        if (execution == null || "SUCCESS".equals(execution.getStatus()) || "FAILED".equals(execution.getStatus())) {
            throw new RuntimeException("当前状态不允许取消");
        }

        execution.setStatus("CANCELLED");
        executionMapper.updateById(execution);

        List<PipelineStageExecution> stageExecs = stageExecutionMapper.selectList(
            new LambdaQueryWrapper<PipelineStageExecution>()
                .eq(PipelineStageExecution::getExecutionId, executionId)
                .in(PipelineStageExecution::getStatus, "PENDING", "APPROVING")
        );

        for (PipelineStageExecution se : stageExecs) {
            se.setStatus("SKIPPED");
            stageExecutionMapper.updateById(se);
        }

        return true;
    }

    @Override
    public List<PipelineStage> getPipelineStages(String pipelineId) {
        return stageMapper.selectList(
            new LambdaQueryWrapper<PipelineStage>()
                .eq(PipelineStage::getPipelineId, pipelineId)
                .orderByAsc(PipelineStage::getStageOrder)
        );
    }

    private PipelineStageExecution getStageExecution(String executionId, String stageId) {
        return stageExecutionMapper.selectOne(
            new LambdaQueryWrapper<PipelineStageExecution>()
                .eq(PipelineStageExecution::getExecutionId, executionId)
                .eq(PipelineStageExecution::getStageId, stageId)
        );
    }

    /**
     * 尝试根据DDL语句自动生成回滚SQL
     * 支持: CREATE TABLE -> DROP TABLE, ALTER TABLE ADD COLUMN -> ALTER TABLE DROP COLUMN
     * @return 回滚SQL, 无法自动生成时返回null
     */
    private String generateRollbackSql(String ddl) {
        if (ddl == null || ddl.trim().isEmpty()) return null;

        String sql = ddl.trim().replaceAll("\\s+", " ");

        // CREATE TABLE ... -> DROP TABLE IF EXISTS ...
        Matcher createMatcher = Pattern.compile(
            "(?i)CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?`?(\\w+)`?\\s*\\(").matcher(sql);
        if (createMatcher.find()) {
            return "DROP TABLE IF EXISTS `" + createMatcher.group(1) + "`;";
        }

        // ALTER TABLE ... ADD COLUMN ... -> ALTER TABLE ... DROP COLUMN ...
        Matcher addColMatcher = Pattern.compile(
            "(?i)ALTER\\s+TABLE\\s+`?(\\w+)`?\\s+ADD\\s+(?:COLUMN\\s+)?`?(\\w+)`?\\s").matcher(sql);
        if (addColMatcher.find()) {
            return "ALTER TABLE `" + addColMatcher.group(1) + "` DROP COLUMN `" + addColMatcher.group(2) + "`;";
        }

        // DROP TABLE -> 无法自动回滚(数据已丢失)
        // ALTER TABLE DROP COLUMN -> 无法自动回滚(数据已丢失)
        // ALTER TABLE MODIFY/CHANGE COLUMN -> 无法自动回滚(需旧定义)
        return null;
    }
}
