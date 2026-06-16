package com.dataops.dms.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dataops.dms.common.result.Result;
import com.dataops.dms.entity.DatabaseInstance;
import com.dataops.dms.entity.DdlChangeTask;
import com.dataops.dms.entity.DdlProject;
import com.dataops.dms.entity.DdlProjectTable;
import com.dataops.dms.entity.Ticket;
import com.dataops.dms.mapper.DdlChangeTaskMapper;
import com.dataops.dms.mapper.DdlProjectMapper;
import com.dataops.dms.mapper.DdlProjectTableMapper;
import com.dataops.dms.service.DatabaseInstanceService;
import com.dataops.dms.service.TicketService;
import com.dataops.dms.sql.OnlineDdlEngine;
import com.dataops.dms.sql.SqlAuditEngine;
import com.dataops.dms.sql.SqlExecutor;
import com.dataops.dms.sql.SqlRollbackGenerator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * DDL变更一体化工作台控制器
 * 实现：选库→选表→可视化修改→SQL预览→风险评估→提交审批→执行监控 全链路
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/ddl-workbench")
@Tag(name = "DDL变更工作台")
public class DdlWorkbenchController {

    @Resource
    private DatabaseInstanceService databaseInstanceService;

    @Resource
    private SqlExecutor sqlExecutor;

    @Resource
    private SqlAuditEngine sqlAuditEngine;

    @Resource
    private OnlineDdlEngine onlineDdlEngine;

    @Resource
    private TicketService ticketService;

    @Resource
    private DdlChangeTaskMapper ddlChangeTaskMapper;

    @Resource
    private DdlProjectMapper ddlProjectMapper;

    @Resource
    private DdlProjectTableMapper ddlProjectTableMapper;

    // 执行任务状态跟踪（内存中，生产环境应用Redis或DB）
    private final Map<String, DdlExecutionTask> executionTasks = new ConcurrentHashMap<>();

    /**
     * DDL风险评估 - 在设计器中实时调用
     */
    @PostMapping("/assess-risk")
    @Operation(summary = "DDL变更风险评估")
    public Result<DdlRiskAssessment> assessRisk(@RequestBody Map<String, String> request) {
        String instanceId = request.get("instanceId");
        String schemaName = request.get("schemaName");
        String sql = request.get("sql");

        if (instanceId == null || sql == null) {
            return Result.error(400, "参数不完整");
        }

        DatabaseInstance db = databaseInstanceService.getById(instanceId);
        if (db == null) {
            return Result.error(400, "数据库实例不存在");
        }

        DdlRiskAssessment assessment = new DdlRiskAssessment();
        assessment.setSql(sql);

        // 1. SQL审核
        SqlAuditEngine.SqlAuditResult auditResult = sqlAuditEngine.audit(sql);
        assessment.setAuditResult(auditResult);

        // 2. Online DDL检测
        try {
            OnlineDdlEngine.DdlCheckResult ddlCheck = ticketService.checkOnlineDdl(instanceId, schemaName, sql);
            assessment.setDdlCheck(ddlCheck);
            assessment.setNeedOnlineDdl(ddlCheck.isNeedOnlineDdl());
            assessment.setTableSize(ddlCheck.getTableSizeHuman());
            assessment.setRecommendedStrategy(ddlCheck.getRecommendedStrategy());
            assessment.setSupportedStrategies(ddlCheck.getSupportedStrategies());

            // 综合风险等级
            if ("high".equals(ddlCheck.getRiskLevel())) {
                assessment.setRiskLevel("high");
                assessment.setRiskMessage("表较大（" + ddlCheck.getTableSizeHuman() + "），强烈建议使用Online DDL无锁变更");
            } else if ("medium".equals(ddlCheck.getRiskLevel())) {
                assessment.setRiskLevel("medium");
                assessment.setRiskMessage("表中等大小（" + ddlCheck.getTableSizeHuman() + "），建议评估后选择变更策略");
            } else {
                assessment.setRiskLevel(auditResult.getRiskLevel());
                assessment.setRiskMessage(auditResult.isPassed() ? "变更风险较低，可直接执行" : "存在审核问题，请检查SQL");
            }
        } catch (Exception e) {
            log.warn("DDL风险评估失败: {}", e.getMessage());
            assessment.setRiskLevel(auditResult.getRiskLevel());
            assessment.setRiskMessage(auditResult.isPassed() ? "变更风险较低" : "存在审核问题");
        }

        // 3. 生成回滚建议
        assessment.setRollbackAdvice(generateRollbackAdvice(sql));

        return Result.success(assessment);
    }

    /**
     * 直接执行DDL（跳过审批，用于开发/测试环境）
     */
    @PostMapping("/execute-direct")
    @Operation(summary = "直接执行DDL变更")
    public Result<DdlExecutionResult> executeDirect(@RequestBody DdlExecuteRequest request) {
        DatabaseInstance db = databaseInstanceService.getById(request.getInstanceId());
        if (db == null) {
            return Result.error(400, "数据库实例不存在");
        }

        String taskId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        DdlExecutionTask task = new DdlExecutionTask();
        task.setTaskId(taskId);
        task.setStatus("running");
        task.setProgress(0);
        task.setSql(request.getSql());
        task.setStrategy(request.getStrategy());
        task.setStartTime(LocalDateTime.now());
        executionTasks.put(taskId, task);

        // 异步执行
        new Thread(() -> executeDdlAsync(task, db, request)).start();

        DdlExecutionResult result = new DdlExecutionResult();
        result.setTaskId(taskId);
        result.setStatus("running");
        result.setMessage("DDL变更任务已启动，请轮询进度");
        return Result.success(result);
    }

    /**
     * 查询DDL执行进度
     */
    @GetMapping("/execution-progress/{taskId}")
    @Operation(summary = "查询DDL执行进度")
    public Result<DdlExecutionTask> getProgress(@PathVariable String taskId) {
        DdlExecutionTask task = executionTasks.get(taskId);
        if (task == null) {
            return Result.error(404, "任务不存在");
        }
        return Result.success(task);
    }

    /**
     * 查询所有DDL执行历史
     */
    @GetMapping("/execution-history")
    @Operation(summary = "DDL执行历史")
    public Result<List<DdlExecutionTask>> executionHistory(
            @RequestParam(defaultValue = "20") int limit) {
        List<DdlExecutionTask> history = new ArrayList<>(executionTasks.values());
        history.sort((a, b) -> b.getStartTime().compareTo(a.getStartTime()));
        if (history.size() > limit) {
            history = history.subList(0, limit);
        }
        return Result.success(history);
    }

    /**
     * 提交DDL变更工单（一键从设计器发起审批）
     */
    @PostMapping("/submit-ticket")
    @Operation(summary = "一键提交DDL变更工单")
    public Result<Ticket> submitTicket(@RequestBody DdlTicketRequest request) {
        try {
            // 1. 先做风险评估
            SqlAuditEngine.SqlAuditResult auditResult = sqlAuditEngine.audit(request.getSqlContent());

            // 2. 创建工单（自动带入设计器上下文）
            com.dataops.dms.dto.TicketCreateDTO dto = new com.dataops.dms.dto.TicketCreateDTO();
            dto.setTitle(request.getTitle());
            dto.setDescription(request.getDescription());
            dto.setInstanceId(request.getInstanceId());
            dto.setSchemaName(request.getSchemaName());
            dto.setSqlContent(request.getSqlContent());
            dto.setChangeType("DDL");
            dto.setPriority(request.getPriority() != null ? request.getPriority() : "normal");
            dto.setUseOnlineDdl(request.getUseOnlineDdl() != null ? request.getUseOnlineDdl() : false);
            dto.setOnlineDdlStrategy(request.getOnlineDdlStrategy());
            dto.setRollbackSql(request.getRollbackSql());

            Ticket ticket = ticketService.createDataChangeTicket(dto, "user_admin");
            return Result.success("工单创建成功，已提交审批", ticket);
        } catch (Exception e) {
            log.error("提交DDL工单失败: {}", e.getMessage());
            return Result.error("提交工单失败: " + e.getMessage());
        }
    }

    /**
     * 获取表的索引信息（用于设计器中展示和优化建议）
     */
    @GetMapping("/table-indexes")
    @Operation(summary = "获取表索引信息")
    public Result<List<Map<String, Object>>> getTableIndexes(
            @RequestParam String instanceId,
            @RequestParam String tableName,
            @RequestParam(required = false) String schemaName) {
        DatabaseInstance db = databaseInstanceService.getById(instanceId);
        if (db == null) {
            return Result.error(400, "数据库实例不存在");
        }

        List<Map<String, Object>> indexes = new ArrayList<>();
        Connection conn = null;
        try {
            conn = getConnection(db, schemaName);
            ResultSet rs = conn.getMetaData().getIndexInfo(null, schemaName, tableName, false, false);
            while (rs.next()) {
                Map<String, Object> idx = new LinkedHashMap<>();
                idx.put("indexName", rs.getString("INDEX_NAME"));
                idx.put("columnName", rs.getString("COLUMN_NAME"));
                idx.put("nonUnique", rs.getBoolean("NON_UNIQUE"));
                idx.put("type", rs.getShort("TYPE"));
                idx.put("ordinalPosition", rs.getShort("ORDINAL_POSITION"));
                indexes.add(idx);
            }
            rs.close();
        } catch (Exception e) {
            log.error("获取表索引失败: {}", e.getMessage());
            return Result.error("获取索引信息失败: " + e.getMessage());
        } finally {
            if (conn != null) try { conn.close(); } catch (Exception ignored) {}
        }
        return Result.success(indexes);
    }

    /**
     * 预览DDL变更的回滚SQL
     */
    @PostMapping("/preview-rollback")
    @Operation(summary = "预览DDL回滚SQL")
    public Result<Map<String, String>> previewRollback(@RequestBody Map<String, String> request) {
        String sql = request.get("sql");
        String tableName = request.get("tableName");

        Map<String, String> result = new LinkedHashMap<>();
        result.put("originalSql", sql);
        result.put("rollbackSql", generateDdlRollbackSql(sql, tableName));
        result.put("advice", generateRollbackAdvice(sql));
        return Result.success(result);
    }

    // ========== 异步执行DDL ==========

    private void executeDdlAsync(DdlExecutionTask task, DatabaseInstance db, DdlExecuteRequest request) {
        Connection conn = null;
        try {
            conn = getConnection(db, request.getSchemaName());
            task.setProgress(10);

            String sql = request.getSql();
            String strategy = request.getStrategy() != null ? request.getStrategy() : "direct";

            // 根据策略执行
            if ("mysql_online".equals(strategy)) {
                sql = onlineDdlEngine.generateMysqlOnlineDdl(sql);
                task.setExecutedSql(sql);
            }

            task.setProgress(30);
            Statement stmt = conn.createStatement();
            stmt.execute(sql);
            stmt.close();

            task.setProgress(100);
            task.setStatus("completed");
            task.setMessage("DDL变更执行成功");
            task.setSuccess(true);
            log.info("DDL执行成功, taskId: {}", task.getTaskId());

        } catch (Exception e) {
            task.setStatus("failed");
            task.setMessage("执行失败: " + e.getMessage());
            task.setSuccess(false);
            log.error("DDL执行失败, taskId: {}, error: {}", task.getTaskId(), e.getMessage());
        } finally {
            task.setEndTime(LocalDateTime.now());
            if (conn != null) try { conn.close(); } catch (Exception ignored) {}
        }
    }

    private Connection getConnection(DatabaseInstance db, String schemaName) throws Exception {
        String effectiveSchema = (schemaName != null && !schemaName.isEmpty()) ? schemaName : db.getDefaultSchemaName();
        String url = String.format("jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false",
                db.getHost(), db.getPort() != null ? db.getPort() : 3306, effectiveSchema);
        Properties props = new Properties();
        props.put("user", db.getUsername());
        props.put("password", db.getPassword());
        return DriverManager.getConnection(url, props);
    }

    private String generateRollbackAdvice(String sql) {
        String upper = sql.trim().toUpperCase();
        if (upper.contains("DROP COLUMN")) {
            return "⚠️ 删除字段操作不可逆，建议先备份表数据（CREATE TABLE backup_xxx AS SELECT * FROM xxx），或使用RENAME COLUMN替代";
        } else if (upper.contains("DROP TABLE")) {
            return "⚠️ 删除表操作不可逆，建议先RENAME TABLE为备份表，观察一段时间后再删除";
        } else if (upper.contains("MODIFY COLUMN") || upper.contains("CHANGE COLUMN")) {
            return "修改字段类型可能导致数据丢失，建议先备份数据，回滚时使用原始字段类型重新MODIFY";
        } else if (upper.contains("ADD COLUMN")) {
            return "新增字段可通过 ALTER TABLE xxx DROP COLUMN yyy 回滚";
        } else if (upper.contains("ADD INDEX") || upper.contains("CREATE INDEX")) {
            return "新增索引可通过 DROP INDEX xxx ON yyy 回滚";
        } else if (upper.contains("RENAME")) {
            return "重命名操作可通过反向RENAME回滚";
        }
        return "建议在执行前备份表结构和数据";
    }

    private String generateDdlRollbackSql(String sql, String tableName) {
        String upper = sql.trim().toUpperCase();
        if (tableName == null || tableName.isEmpty()) {
            return "-- 无法自动生成回滚SQL，请手动处理";
        }
        if (upper.contains("ADD COLUMN")) {
            return "-- 回滚: ALTER TABLE " + tableName + " DROP COLUMN <新增字段名>;";
        } else if (upper.contains("DROP COLUMN")) {
            return "-- ⚠️ 删除字段不可自动回滚，请从备份表恢复";
        } else if (upper.contains("ADD INDEX") || upper.contains("CREATE INDEX")) {
            return "-- 回滚: DROP INDEX <索引名> ON " + tableName + ";";
        }
        return "-- 请根据变更内容手动编写回滚SQL";
    }

    // ========== DTO/VO ==========

    // ========== 项目工单管理接口 ==========

    /**
     * 创建项目工单
     */
    @PostMapping("/projects")
    @Operation(summary = "创建DDL项目工单")
    public Result<DdlProject> createProject(@RequestBody CreateProjectRequest request) {
        try {
            if (request.getProjectName() == null || request.getProjectName().trim().isEmpty()) {
                return Result.error(400, "项目名称不能为空");
            }
            if (request.getBaseInstanceId() == null || request.getBaseInstanceId().isEmpty()) {
                return Result.error(400, "请选择变更基准库");
            }

            DatabaseInstance db = databaseInstanceService.getById(request.getBaseInstanceId());
            if (db == null) {
                return Result.error(400, "基准库实例不存在");
            }

            DdlProject project = new DdlProject();
            project.setId(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            project.setProjectName(request.getProjectName());
            project.setBusinessBackground(request.getBusinessBackground());
            project.setBaseInstanceId(request.getBaseInstanceId());
            project.setBaseInstanceName(db.getName());
            project.setBaseSchemaName(request.getBaseSchemaName());
            project.setStatus("DESIGNING");
            project.setCurrentStage("DESIGN");
            project.setOwner(request.getOwner() != null ? request.getOwner() : "user_admin");
            project.setRelatedPersons(request.getRelatedPersons());
            project.setSecurityRule(request.getSecurityRule() != null ? request.getSecurityRule() : "auto");
            project.setPriority(request.getPriority() != null ? request.getPriority() : "normal");
            project.setTableCount(0);
            project.setCreatedBy("user_admin");
            project.setCreatedAt(LocalDateTime.now());
            project.setUpdatedAt(LocalDateTime.now());

            ddlProjectMapper.insert(project);
            log.info("DDL项目工单创建成功: {} ({})", project.getId(), project.getProjectName());
            return Result.success("项目工单创建成功", project);
        } catch (Exception e) {
            log.error("创建项目工单失败", e);
            return Result.error("创建失败: " + e.getMessage());
        }
    }

    /**
     * 获取项目工单详情
     */
    @GetMapping("/projects/{id}")
    @Operation(summary = "获取项目工单详情")
    public Result<DdlProject> getProject(@PathVariable String id) {
        DdlProject project = ddlProjectMapper.selectById(id);
        if (project == null) {
            return Result.error(404, "项目工单不存在");
        }
        return Result.success(project);
    }

    /**
     * 查询项目工单列表
     */
    @GetMapping("/projects")
    @Operation(summary = "查询项目工单列表")
    public Result<List<DdlProject>> listProjects(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "50") int limit) {
        LambdaQueryWrapper<DdlProject> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            wrapper.eq(DdlProject::getStatus, status);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(DdlProject::getProjectName, keyword)
                    .or().like(DdlProject::getBusinessBackground, keyword));
        }
        wrapper.orderByDesc(DdlProject::getCreatedAt)
               .last("LIMIT " + limit);
        return Result.success(ddlProjectMapper.selectList(wrapper));
    }

    /**
     * 更新项目工单
     */
    @PutMapping("/projects/{id}")
    @Operation(summary = "更新项目工单")
    public Result<DdlProject> updateProject(@PathVariable String id, @RequestBody Map<String, String> updates) {
        DdlProject project = ddlProjectMapper.selectById(id);
        if (project == null) {
            return Result.error(404, "项目工单不存在");
        }
        if (updates.containsKey("projectName")) project.setProjectName(updates.get("projectName"));
        if (updates.containsKey("businessBackground")) project.setBusinessBackground(updates.get("businessBackground"));
        if (updates.containsKey("owner")) project.setOwner(updates.get("owner"));
        if (updates.containsKey("relatedPersons")) project.setRelatedPersons(updates.get("relatedPersons"));
        if (updates.containsKey("priority")) project.setPriority(updates.get("priority"));
        if (updates.containsKey("status")) project.setStatus(updates.get("status"));
        if (updates.containsKey("currentStage")) project.setCurrentStage(updates.get("currentStage"));
        project.setUpdatedAt(LocalDateTime.now());
        ddlProjectMapper.updateById(project);
        return Result.success("更新成功", project);
    }

    /**
     * 关闭项目工单
     */
    @PostMapping("/projects/{id}/close")
    @Operation(summary = "关闭项目工单")
    public Result<DdlProject> closeProject(@PathVariable String id) {
        DdlProject project = ddlProjectMapper.selectById(id);
        if (project == null) {
            return Result.error(404, "项目工单不存在");
        }
        project.setStatus("CLOSED");
        project.setClosedAt(LocalDateTime.now());
        project.setUpdatedAt(LocalDateTime.now());
        ddlProjectMapper.updateById(project);
        return Result.success("项目工单已关闭", project);
    }

    /**
     * 推进项目阶段
     */
    @PostMapping("/projects/{id}/advance-stage")
    @Operation(summary = "推进项目到下一阶段")
    public Result<DdlProject> advanceStage(@PathVariable String id) {
        DdlProject project = ddlProjectMapper.selectById(id);
        if (project == null) {
            return Result.error(404, "项目工单不存在");
        }
        String[] stages = {"CREATE", "DESIGN", "DEV", "TEST", "INTEGRATION", "STAGING", "PRODUCTION", "FINISH"};
        String current = project.getCurrentStage();
        int idx = -1;
        for (int i = 0; i < stages.length; i++) {
            if (stages[i].equals(current)) { idx = i; break; }
        }
        if (idx < 0 || idx >= stages.length - 1) {
            return Result.error(400, "当前阶段无法继续推进: " + current);
        }
        project.setCurrentStage(stages[idx + 1]);
        project.setUpdatedAt(LocalDateTime.now());
        ddlProjectMapper.updateById(project);
        log.info("项目 {} 推进到阶段: {}", id, stages[idx + 1]);
        return Result.success("已推进到: " + stages[idx + 1], project);
    }

    // ========== 项目表变更管理接口 ==========

    /**
     * 添加表到项目（新建表或导入已有表）
     */
    @PostMapping("/projects/{projectId}/tables")
    @Operation(summary = "添加表变更到项目")
    public Result<DdlProjectTable> addProjectTable(@PathVariable String projectId,
                                                   @RequestBody AddProjectTableRequest request) {
        DdlProject project = ddlProjectMapper.selectById(projectId);
        if (project == null) {
            return Result.error(404, "项目工单不存在");
        }
        if (request.getTableName() == null || request.getTableName().trim().isEmpty()) {
            return Result.error(400, "表名不能为空");
        }

        // 检查是否已存在
        LambdaQueryWrapper<DdlProjectTable> checkWrapper = new LambdaQueryWrapper<>();
        checkWrapper.eq(DdlProjectTable::getProjectId, projectId)
                    .eq(DdlProjectTable::getTableName, request.getTableName());
        if (ddlProjectTableMapper.selectCount(checkWrapper) > 0) {
            return Result.error(400, "该表已在项目中: " + request.getTableName());
        }

        DdlProjectTable pt = new DdlProjectTable();
        pt.setId(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        pt.setProjectId(projectId);
        pt.setTableName(request.getTableName());
        pt.setChangeType(request.getChangeType() != null ? request.getChangeType() : "MODIFY");
        pt.setOriginalDdl(request.getOriginalDdl());
        pt.setModifiedDdl(request.getModifiedDdl());
        pt.setChangeSql(request.getChangeSql());
        pt.setVersion(0);
        pt.setLastOperator("user_admin");
        pt.setLastModifiedAt(LocalDateTime.now());
        pt.setEnvStatus("{\"dev\":\"NONE\",\"test\":\"NONE\",\"integration\":\"NONE\",\"staging\":\"NONE\",\"production\":\"NONE\"}");
        pt.setCreatedAt(LocalDateTime.now());
        pt.setUpdatedAt(LocalDateTime.now());

        ddlProjectTableMapper.insert(pt);

        // 更新项目表计数
        project.setTableCount(project.getTableCount() + 1);
        project.setUpdatedAt(LocalDateTime.now());
        ddlProjectMapper.updateById(project);

        return Result.success("表已添加", pt);
    }

    /**
     * 查询项目的表变更列表
     */
    @GetMapping("/projects/{projectId}/tables")
    @Operation(summary = "查询项目表变更列表")
    public Result<List<DdlProjectTable>> listProjectTables(@PathVariable String projectId) {
        LambdaQueryWrapper<DdlProjectTable> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DdlProjectTable::getProjectId, projectId)
               .orderByAsc(DdlProjectTable::getCreatedAt);
        return Result.success(ddlProjectTableMapper.selectList(wrapper));
    }

    /**
     * 获取单张表变更详情
     */
    @GetMapping("/projects/{projectId}/tables/{tableId}")
    @Operation(summary = "获取表变更详情")
    public Result<DdlProjectTable> getProjectTable(@PathVariable String projectId, @PathVariable String tableId) {
        DdlProjectTable pt = ddlProjectTableMapper.selectById(tableId);
        if (pt == null || !projectId.equals(pt.getProjectId())) {
            return Result.error(404, "表变更不存在");
        }
        return Result.success(pt);
    }

    /**
     * 更新表变更（编辑DDL）
     */
    @PutMapping("/projects/{projectId}/tables/{tableId}")
    @Operation(summary = "更新表变更DDL")
    public Result<DdlProjectTable> updateProjectTable(@PathVariable String projectId,
                                                       @PathVariable String tableId,
                                                       @RequestBody Map<String, String> updates) {
        DdlProjectTable pt = ddlProjectTableMapper.selectById(tableId);
        if (pt == null || !projectId.equals(pt.getProjectId())) {
            return Result.error(404, "表变更不存在");
        }
        if (updates.containsKey("modifiedDdl")) pt.setModifiedDdl(updates.get("modifiedDdl"));
        if (updates.containsKey("changeSql")) pt.setChangeSql(updates.get("changeSql"));
        if (updates.containsKey("tableName")) pt.setTableName(updates.get("tableName"));
        pt.setLastOperator("user_admin");
        pt.setLastModifiedAt(LocalDateTime.now());
        pt.setUpdatedAt(LocalDateTime.now());
        ddlProjectTableMapper.updateById(pt);
        return Result.success("已更新", pt);
    }

    /**
     * 删除表变更
     */
    @DeleteMapping("/projects/{projectId}/tables/{tableId}")
    @Operation(summary = "删除表变更")
    public Result<Void> deleteProjectTable(@PathVariable String projectId, @PathVariable String tableId) {
        DdlProjectTable pt = ddlProjectTableMapper.selectById(tableId);
        if (pt == null || !projectId.equals(pt.getProjectId())) {
            return Result.error(404, "表变更不存在");
        }
        ddlProjectTableMapper.deleteById(tableId);

        // 更新项目表计数
        DdlProject project = ddlProjectMapper.selectById(projectId);
        if (project != null) {
            long count = ddlProjectTableMapper.selectCount(
                new LambdaQueryWrapper<DdlProjectTable>().eq(DdlProjectTable::getProjectId, projectId));
            project.setTableCount((int) count);
            project.setUpdatedAt(LocalDateTime.now());
            ddlProjectMapper.updateById(project);
        }
        return Result.success("已删除", null);
    }

    /**
     * 批量预览SQL（项目内所有表的变更SQL汇总）
     */
    @GetMapping("/projects/{projectId}/preview-sql")
    @Operation(summary = "批量预览项目所有表变更SQL")
    public Result<Map<String, Object>> previewProjectSql(@PathVariable String projectId) {
        LambdaQueryWrapper<DdlProjectTable> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DdlProjectTable::getProjectId, projectId);
        List<DdlProjectTable> tables = ddlProjectTableMapper.selectList(wrapper);

        StringBuilder allSql = new StringBuilder();
        allSql.append("-- ============================================\n");
        allSql.append("-- DDL项目变更SQL汇总\n");
        allSql.append("-- 生成时间: ").append(LocalDateTime.now()).append("\n");
        allSql.append("-- 变更表数量: ").append(tables.size()).append("\n");
        allSql.append("-- ============================================\n\n");

        int modifyCount = 0, newCount = 0;
        for (DdlProjectTable pt : tables) {
            allSql.append("-- 表: ").append(pt.getTableName())
                  .append(" (").append(pt.getChangeType()).append(")\n");
            if (pt.getChangeSql() != null && !pt.getChangeSql().isEmpty()) {
                allSql.append(pt.getChangeSql()).append("\n\n");
            } else if ("NEW".equals(pt.getChangeType()) && pt.getModifiedDdl() != null) {
                allSql.append(pt.getModifiedDdl()).append("\n\n");
                newCount++;
            } else {
                allSql.append("-- 暂无变更SQL\n\n");
            }
            if ("MODIFY".equals(pt.getChangeType())) modifyCount++;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("sql", allSql.toString());
        result.put("tableCount", tables.size());
        result.put("modifyCount", modifyCount);
        result.put("newCount", newCount);
        return Result.success(result);
    }

    /**
     * 导入SQL到项目（解析SQL中的CREATE/ALTER语句，自动创建表变更条目）
     */
    @PostMapping("/projects/{projectId}/import-sql")
    @Operation(summary = "导入SQL到项目")
    public Result<Map<String, Object>> importSql(@PathVariable String projectId,
                                                  @RequestBody Map<String, String> request) {
        DdlProject project = ddlProjectMapper.selectById(projectId);
        if (project == null) {
            return Result.error(404, "项目工单不存在");
        }
        String sql = request.get("sql");
        if (sql == null || sql.trim().isEmpty()) {
            return Result.error(400, "SQL内容不能为空");
        }

        // 按分号分割SQL语句
        String[] statements = sql.split(";\\s*\\n");
        int imported = 0;
        List<String> skipped = new ArrayList<>();

        for (String stmt : statements) {
            stmt = stmt.trim();
            if (stmt.isEmpty() || stmt.startsWith("--")) continue;

            String upper = stmt.toUpperCase();
            String tableName = null;
            String changeType = null;

            if (upper.contains("CREATE TABLE") || upper.contains("CREATE TABLE IF NOT EXISTS")) {
                java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("CREATE\\s+TABLE\\s+(?:IF\\s+NOT\\s+EXISTS\\s+)?`?(\\w+)`?", java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(stmt);
                if (m.find()) {
                    tableName = m.group(1);
                    changeType = "NEW";
                }
            } else if (upper.contains("ALTER TABLE")) {
                java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("ALTER\\s+TABLE\\s+`?(\\w+)`?", java.util.regex.Pattern.CASE_INSENSITIVE)
                    .matcher(stmt);
                if (m.find()) {
                    tableName = m.group(1);
                    changeType = "MODIFY";
                }
            }

            if (tableName != null) {
                // 检查是否已存在
                LambdaQueryWrapper<DdlProjectTable> checkWrapper = new LambdaQueryWrapper<>();
                checkWrapper.eq(DdlProjectTable::getProjectId, projectId)
                            .eq(DdlProjectTable::getTableName, tableName);
                DdlProjectTable existing = ddlProjectTableMapper.selectOne(checkWrapper);
                if (existing != null) {
                    // 追加SQL
                    String existingSql = existing.getChangeSql() != null ? existing.getChangeSql() : "";
                    existing.setChangeSql(existingSql + "\n" + stmt + ";");
                    existing.setLastModifiedAt(LocalDateTime.now());
                    existing.setUpdatedAt(LocalDateTime.now());
                    ddlProjectTableMapper.updateById(existing);
                } else {
                    DdlProjectTable pt = new DdlProjectTable();
                    pt.setId(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
                    pt.setProjectId(projectId);
                    pt.setTableName(tableName);
                    pt.setChangeType(changeType);
                    pt.setChangeSql(stmt + ";");
                    pt.setVersion(0);
                    pt.setLastOperator("user_admin");
                    pt.setLastModifiedAt(LocalDateTime.now());
                    pt.setEnvStatus("{\"dev\":\"NONE\",\"test\":\"NONE\",\"integration\":\"NONE\",\"staging\":\"NONE\",\"production\":\"NONE\"}");
                    pt.setCreatedAt(LocalDateTime.now());
                    pt.setUpdatedAt(LocalDateTime.now());
                    ddlProjectTableMapper.insert(pt);
                }
                imported++;
            } else {
                skipped.add(stmt.length() > 50 ? stmt.substring(0, 50) + "..." : stmt);
            }
        }

        // 更新项目表计数
        long count = ddlProjectTableMapper.selectCount(
            new LambdaQueryWrapper<DdlProjectTable>().eq(DdlProjectTable::getProjectId, projectId));
        project.setTableCount((int) count);
        project.setUpdatedAt(LocalDateTime.now());
        ddlProjectMapper.updateById(project);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("imported", imported);
        result.put("skipped", skipped.size());
        result.put("skippedStatements", skipped);
        return Result.success("导入完成", result);
    }

    /**
     * 执行项目表变更到基准库（开发环境）
     */
    @PostMapping("/projects/{projectId}/tables/{tableId}/execute")
    @Operation(summary = "执行单张表变更到基准库")
    public Result<DdlProjectTable> executeProjectTableChange(@PathVariable String projectId,
                                                              @PathVariable String tableId) {
        DdlProject project = ddlProjectMapper.selectById(projectId);
        if (project == null) {
            return Result.error(404, "项目工单不存在");
        }
        DdlProjectTable pt = ddlProjectTableMapper.selectById(tableId);
        if (pt == null || !projectId.equals(pt.getProjectId())) {
            return Result.error(404, "表变更不存在");
        }

        String sql = pt.getChangeSql();
        if (sql == null || sql.trim().isEmpty()) {
            sql = pt.getModifiedDdl();
        }
        if (sql == null || sql.trim().isEmpty()) {
            return Result.error(400, "无变更SQL可执行");
        }

        DatabaseInstance db = databaseInstanceService.getById(project.getBaseInstanceId());
        if (db == null) {
            return Result.error(400, "基准库实例不存在");
        }

        Connection conn = null;
        try {
            conn = getConnection(db, project.getBaseSchemaName());
            // 按分号逐条执行
            String[] stmts = sql.split(";\\s*\\n");
            for (String s : stmts) {
                s = s.trim();
                if (s.isEmpty() || s.startsWith("--")) continue;
                Statement stmt = conn.createStatement();
                stmt.execute(s);
                stmt.close();
            }

            pt.setVersion(pt.getVersion() + 1);
            pt.setLastOperator("user_admin");
            pt.setLastModifiedAt(LocalDateTime.now());
            // 更新dev环境状态
            String envStatus = pt.getEnvStatus();
            if (envStatus != null) {
                envStatus = envStatus.replace("\"dev\":\"NONE\"", "\"dev\":\"SUCCESS\"")
                                     .replace("\"dev\":\"PENDING\"", "\"dev\":\"SUCCESS\"")
                                     .replace("\"dev\":\"FAILED\"", "\"dev\":\"SUCCESS\"");
                pt.setEnvStatus(envStatus);
            }
            pt.setUpdatedAt(LocalDateTime.now());
            ddlProjectTableMapper.updateById(pt);

            log.info("项目 {} 表 {} 变更执行成功", projectId, pt.getTableName());
            return Result.success("执行成功", pt);
        } catch (Exception e) {
            // 更新dev环境状态为失败
            String envStatus = pt.getEnvStatus();
            if (envStatus != null) {
                envStatus = envStatus.replace("\"dev\":\"NONE\"", "\"dev\":\"FAILED\"")
                                     .replace("\"dev\":\"PENDING\"", "\"dev\":\"FAILED\"");
                pt.setEnvStatus(envStatus);
                pt.setUpdatedAt(LocalDateTime.now());
                ddlProjectTableMapper.updateById(pt);
            }
            log.error("项目 {} 表 {} 执行失败: {}", projectId, pt.getTableName(), e.getMessage());
            return Result.error("执行失败: " + e.getMessage());
        } finally {
            if (conn != null) try { conn.close(); } catch (Exception ignored) {}
        }
    }

    /**
     * 批量执行项目所有表变更到基准库
     */
    @PostMapping("/projects/{projectId}/execute-all")
    @Operation(summary = "批量执行所有表变更到基准库")
    public Result<Map<String, Object>> executeAllProjectTables(@PathVariable String projectId) {
        DdlProject project = ddlProjectMapper.selectById(projectId);
        if (project == null) {
            return Result.error(404, "项目工单不存在");
        }

        LambdaQueryWrapper<DdlProjectTable> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DdlProjectTable::getProjectId, projectId);
        List<DdlProjectTable> tables = ddlProjectTableMapper.selectList(wrapper);

        int success = 0, failed = 0;
        List<String> errors = new ArrayList<>();

        for (DdlProjectTable pt : tables) {
            String sql = pt.getChangeSql() != null ? pt.getChangeSql() : pt.getModifiedDdl();
            if (sql == null || sql.trim().isEmpty()) continue;

            DatabaseInstance db = databaseInstanceService.getById(project.getBaseInstanceId());
            Connection conn = null;
            try {
                conn = getConnection(db, project.getBaseSchemaName());
                String[] stmts = sql.split(";\\s*\\n");
                for (String s : stmts) {
                    s = s.trim();
                    if (s.isEmpty() || s.startsWith("--")) continue;
                    Statement stmt = conn.createStatement();
                    stmt.execute(s);
                    stmt.close();
                }
                pt.setVersion(pt.getVersion() + 1);
                pt.setLastModifiedAt(LocalDateTime.now());
                pt.setUpdatedAt(LocalDateTime.now());
                ddlProjectTableMapper.updateById(pt);
                success++;
            } catch (Exception e) {
                failed++;
                errors.add(pt.getTableName() + ": " + e.getMessage());
            } finally {
                if (conn != null) try { conn.close(); } catch (Exception ignored) {}
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", tables.size());
        result.put("success", success);
        result.put("failed", failed);
        result.put("errors", errors);
        return Result.success("批量执行完成", result);
    }

    // ========== 请求DTO ==========

    @Data
    public static class CreateProjectRequest {
        private String projectName;
        private String businessBackground;
        private String baseInstanceId;
        private String baseSchemaName;
        private String owner;
        private String relatedPersons;
        private String securityRule;
        private String priority;
    }

    @Data
    public static class AddProjectTableRequest {
        private String tableName;
        private String changeType;  // NEW or MODIFY
        private String originalDdl;
        private String modifiedDdl;
        private String changeSql;
    }

    @Data
    public static class DdlExecuteRequest {
        private String instanceId;
        private String schemaName;
        private String sql;
        private String strategy; // direct, mysql_online, pt_osc
    }

    @Data
    public static class DdlTicketRequest {
        private String title;
        private String description;
        private String instanceId;
        private String schemaName;
        private String sqlContent;
        private String priority;
        private Boolean useOnlineDdl;
        private String onlineDdlStrategy;
        private String rollbackSql;
    }

    @Data
    public static class DdlRiskAssessment {
        private String sql;
        private String riskLevel;
        private String riskMessage;
        private SqlAuditEngine.SqlAuditResult auditResult;
        private OnlineDdlEngine.DdlCheckResult ddlCheck;
        private boolean needOnlineDdl;
        private String tableSize;
        private String recommendedStrategy;
        private List<String> supportedStrategies;
        private String rollbackAdvice;
    }

    @Data
    public static class DdlExecutionResult {
        private String taskId;
        private String status;
        private String message;
    }

    @Data
    public static class DdlExecutionTask {
        private String taskId;
        private String status; // running, completed, failed
        private int progress;  // 0-100
        private String sql;
        private String executedSql;
        private String strategy;
        private boolean success;
        private String message;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
    }

    // ========== 环境流转接口 ==========

    /**
     * 提交DDL变更到目标环境
     * 从当前环境执行成功后，将同一份SQL提交到下一个环境（集成/生产）
     */
    @PostMapping("/change-tasks/submit")
    @Operation(summary = "提交DDL变更到目标环境")
    public Result<DdlChangeTask> submitToEnvironment(@RequestBody SubmitChangeRequest request) {
        try {
            // 校验目标数据库实例
            DatabaseInstance db = databaseInstanceService.getById(request.getInstanceId());
            if (db == null) {
                return Result.error(400, "目标数据库实例不存在");
            }

            // 阶段顺序校验
            validateStageOrder(request.getEnvironment(), request.getSourceTaskId());

            DdlChangeTask task = new DdlChangeTask();
            task.setId(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
            task.setTitle(request.getTitle());
            task.setEnvironment(request.getEnvironment());
            task.setStatus("PENDING");
            task.setInstanceId(request.getInstanceId());
            task.setInstanceName(db.getName());
            task.setSchemaName(request.getSchemaName());
            task.setSqlContent(request.getSqlContent());
            task.setSourceTaskId(request.getSourceTaskId());
            task.setCreatedBy(request.getCreatedBy() != null ? request.getCreatedBy() : "user_admin");
            task.setCreatedAt(LocalDateTime.now());

            // 携带上游执行信息（来源追溯）
            if (request.getSourceTaskId() != null && !request.getSourceTaskId().isEmpty()) {
                DdlChangeTask sourceTask = ddlChangeTaskMapper.selectById(request.getSourceTaskId());
                if (sourceTask != null) {
                    task.setSourceSqlContent(sourceTask.getSqlContent());
                    task.setSourceExecutedBy(sourceTask.getExecutedBy());
                    task.setSourceExecutedAt(sourceTask.getExecutedAt());
                }
            }

            ddlChangeTaskMapper.insert(task);

            log.info("DDL变更任务已提交到{}: {}, SQL: {}", request.getEnvironment(), task.getId(),
                request.getSqlContent().substring(0, Math.min(50, request.getSqlContent().length())));
            return Result.success("变更已提交到" + envLabel(request.getEnvironment()), task);
        } catch (IllegalArgumentException e) {
            log.warn("提交DDL变更校验失败: {}", e.getMessage());
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("提交DDL变更失败", e);
            return Result.error("提交失败: " + e.getMessage());
        }
    }

    /**
     * 查询指定环境的变更任务列表
     */
    @GetMapping("/change-tasks")
    @Operation(summary = "查询环境变更任务列表")
    public Result<List<DdlChangeTask>> listChangeTasks(
            @RequestParam String environment,
            @RequestParam(defaultValue = "20") int limit) {
        LambdaQueryWrapper<DdlChangeTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DdlChangeTask::getEnvironment, environment)
               .orderByDesc(DdlChangeTask::getCreatedAt)
               .last("LIMIT " + limit);
        List<DdlChangeTask> tasks = ddlChangeTaskMapper.selectList(wrapper);
        return Result.success(tasks);
    }

    /**
     * 执行指定环境的待执行任务
     */
    @PostMapping("/change-tasks/{id}/execute")
    @Operation(summary = "执行环境变更任务")
    public Result<DdlChangeTask> executeChangeTask(@PathVariable String id) {
        DdlChangeTask task = ddlChangeTaskMapper.selectById(id);
        if (task == null) {
            return Result.error(404, "任务不存在");
        }
        if (!"PENDING".equals(task.getStatus())) {
            return Result.error(400, "当前状态不允许执行: " + task.getStatus());
        }

        DatabaseInstance db = databaseInstanceService.getById(task.getInstanceId());
        if (db == null) {
            return Result.error(400, "目标数据库实例不存在");
        }

        task.setStatus("EXECUTING");
        ddlChangeTaskMapper.updateById(task);

        LocalDateTime startTime = LocalDateTime.now();
        Connection conn = null;
        try {
            conn = getConnection(db, task.getSchemaName());
            Statement stmt = conn.createStatement();
            stmt.execute(task.getSqlContent());
            stmt.close();

            int duration = (int) ChronoUnit.SECONDS.between(startTime, LocalDateTime.now());
            task.setStatus("SUCCESS");
            task.setExecutedBy("user_admin");
            task.setExecutedAt(LocalDateTime.now());
            task.setDurationSeconds(duration);
            task.setResultMessage("DDL变更执行成功");
            task.setRollbackSql(generateRollbackAdvice(task.getSqlContent()));
            ddlChangeTaskMapper.updateById(task);

            log.info("DDL变更任务执行成功: {} ({})", task.getId(), task.getEnvironment());
            return Result.success("执行成功", task);
        } catch (Exception e) {
            task.setStatus("FAILED");
            task.setExecutedAt(LocalDateTime.now());
            task.setResultMessage("执行失败: " + e.getMessage());
            ddlChangeTaskMapper.updateById(task);
            log.error("DDL变更任务执行失败: {}", task.getId(), e);
            return Result.error("执行失败: " + e.getMessage());
        } finally {
            if (conn != null) try { conn.close(); } catch (Exception ignored) {}
        }
    }

    /**
     * 跳过指定环境的任务（不执行，直接标记为跳过）
     */
    @PostMapping("/change-tasks/{id}/skip")
    @Operation(summary = "跳过环境变更任务")
    public Result<DdlChangeTask> skipChangeTask(@PathVariable String id) {
        DdlChangeTask task = ddlChangeTaskMapper.selectById(id);
        if (task == null) {
            return Result.error(404, "任务不存在");
        }
        if (!"PENDING".equals(task.getStatus())) {
            return Result.error(400, "当前状态不允许跳过: " + task.getStatus());
        }
        task.setStatus("SKIPPED");
        task.setResultMessage("已跳过，未在此环境执行");
        task.setExecutedAt(LocalDateTime.now());
        ddlChangeTaskMapper.updateById(task);
        log.info("DDL变更任务已跳过: {} ({})", task.getId(), task.getEnvironment());
        return Result.success("已跳过", task);
    }

    /**
     * 获取任务来源详情（链路追溯）
     * 递归查询上游任务，组装完整的 DEV → INTEGRATION → PRODUCTION 链路
     */
    @GetMapping("/change-tasks/{id}/source-detail")
    @Operation(summary = "获取任务来源详情")
    public Result<Map<String, Object>> getSourceTaskDetail(@PathVariable String id) {
        DdlChangeTask task = ddlChangeTaskMapper.selectById(id);
        if (task == null) {
            return Result.error(404, "任务不存在");
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("taskId", task.getId());
        detail.put("environment", task.getEnvironment());
        detail.put("sqlContent", task.getSqlContent());
        detail.put("executedBy", task.getExecutedBy());
        detail.put("executedAt", task.getExecutedAt());
        detail.put("status", task.getStatus());
        detail.put("sourceSqlContent", task.getSourceSqlContent());
        detail.put("sourceExecutedBy", task.getSourceExecutedBy());
        detail.put("sourceExecutedAt", task.getSourceExecutedAt());

        // 递归构建上游链路
        List<Map<String, Object>> chain = new ArrayList<>();
        chain.add(0, detail);
        String currentSourceTaskId = task.getSourceTaskId();
        while (currentSourceTaskId != null && !currentSourceTaskId.isEmpty()) {
            DdlChangeTask sourceTask = ddlChangeTaskMapper.selectById(currentSourceTaskId);
            if (sourceTask == null) break;
            Map<String, Object> sourceDetail = new LinkedHashMap<>();
            sourceDetail.put("taskId", sourceTask.getId());
            sourceDetail.put("environment", sourceTask.getEnvironment());
            sourceDetail.put("sqlContent", sourceTask.getSqlContent());
            sourceDetail.put("executedBy", sourceTask.getExecutedBy());
            sourceDetail.put("executedAt", sourceTask.getExecutedAt());
            sourceDetail.put("status", sourceTask.getStatus());
            chain.add(0, sourceDetail);
            currentSourceTaskId = sourceTask.getSourceTaskId();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("current", detail);
        result.put("chain", chain);
        return Result.success(result);
    }

    /**
     * 回退变更任务到开发环境
     * 仅 INTEGRATION 环境的 PENDING/FAILED 任务可回退
     */
    @PostMapping("/change-tasks/{id}/rollback-to-dev")
    @Operation(summary = "回退变更任务到开发环境")
    public Result<DdlChangeTask> rollbackToDev(@PathVariable String id) {
        DdlChangeTask task = ddlChangeTaskMapper.selectById(id);
        if (task == null) {
            return Result.error(404, "任务不存在");
        }

        // 校验：仅 INTEGRATION 环境可回退
        if (!"INTEGRATION".equals(task.getEnvironment())) {
            return Result.error(400, "仅集成环境的任务可以回退到开发环境");
        }
        // 校验：仅 PENDING 或 FAILED 状态可回退
        if (!"PENDING".equals(task.getStatus()) && !"FAILED".equals(task.getStatus())) {
            return Result.error(400, "当前状态不允许回退: " + task.getStatus());
        }

        // 检查是否有来源任务
        if (task.getSourceTaskId() == null || task.getSourceTaskId().isEmpty()) {
            return Result.error(400, "该任务没有来源信息，无法回退");
        }

        // 将当前任务标记为已回退
        task.setStatus("ROLLED_BACK");
        task.setRolledBackAt(LocalDateTime.now());
        task.setRolledBackBy("user_admin");
        task.setResultMessage("已回退到开发环境");
        ddlChangeTaskMapper.updateById(task);

        // 在开发环境创建新的任务（沿用原始SQL）
        DdlChangeTask devTask = new DdlChangeTask();
        devTask.setId(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        devTask.setTitle(task.getTitle() + " (回退后重试)");
        devTask.setEnvironment("DEV");
        devTask.setStatus("PENDING");
        devTask.setInstanceId(task.getInstanceId());
        devTask.setInstanceName(task.getInstanceName());
        devTask.setSchemaName(task.getSchemaName());
        devTask.setSqlContent(task.getSqlContent());
        devTask.setSourceTaskId(task.getId());
        devTask.setRollbackToSource(true);
        devTask.setCreatedBy("user_admin");
        devTask.setCreatedAt(LocalDateTime.now());
        ddlChangeTaskMapper.insert(devTask);

        log.info("DDL变更任务已回退: {} (INTEGRATION) -> {} (DEV)", task.getId(), devTask.getId());
        return Result.success("已回退到开发环境，请在开发环境重新修改后执行", devTask);
    }

    /**
     * 校验阶段顺序
     * 提交到INTEGRATION：要求上游DEV任务已成功
     * 提交到PRODUCTION：要求上游INTEGRATION任务已成功
     */
    private void validateStageOrder(String environment, String sourceTaskId) {
        if (sourceTaskId == null || sourceTaskId.isEmpty()) {
            if ("INTEGRATION".equals(environment)) {
                throw new IllegalArgumentException("缺少来源任务ID，请从开发环境提交");
            } else if ("PRODUCTION".equals(environment)) {
                throw new IllegalArgumentException("缺少来源任务ID，请从集成环境提交");
            }
            return;
        }

        DdlChangeTask sourceTask = ddlChangeTaskMapper.selectById(sourceTaskId);
        if (sourceTask == null) {
            throw new IllegalArgumentException("来源任务不存在");
        }

        if ("INTEGRATION".equals(environment)) {
            if (!"DEV".equals(sourceTask.getEnvironment())) {
                throw new IllegalArgumentException("提交到集成环境的任务必须来自开发环境");
            }
            if (!"SUCCESS".equals(sourceTask.getStatus())) {
                throw new IllegalArgumentException("开发环境的任务尚未执行成功，请先在开发环境执行");
            }
        } else if ("PRODUCTION".equals(environment)) {
            if (!"INTEGRATION".equals(sourceTask.getEnvironment())) {
                throw new IllegalArgumentException("提交到生产环境的任务必须来自集成环境");
            }
            if (!"SUCCESS".equals(sourceTask.getStatus())) {
                throw new IllegalArgumentException("集成环境的任务尚未执行成功，请先在集成环境执行");
            }
        }
    }

    private String envLabel(String env) {
        if ("INTEGRATION".equals(env)) return "集成环境";
        if ("PRODUCTION".equals(env)) return "生产环境";
        return env;
    }

    // ========== 请求DTO ==========

    @Data
    public static class SubmitChangeRequest {
        private String title;
        private String environment;          // INTEGRATION / PRODUCTION
        private String instanceId;
        private String schemaName;
        private String sqlContent;
        private String sourceTaskId;          // 来源任务ID
        private String createdBy;
    }
}
