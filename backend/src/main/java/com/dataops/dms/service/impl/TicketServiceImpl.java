package com.dataops.dms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataops.dms.common.result.PageResult;
import com.dataops.dms.dto.TicketCreateDTO;
import com.dataops.dms.entity.DataChangeBackup;
import com.dataops.dms.entity.DatabaseInstance;
import com.dataops.dms.entity.Ticket;
import com.dataops.dms.entity.TicketApproval;
import com.dataops.dms.mapper.TicketMapper;
import com.dataops.dms.service.DataBackupService;
import com.dataops.dms.service.DatabaseInstanceService;
import com.dataops.dms.service.TicketApprovalService;
import com.dataops.dms.service.TicketService;
import com.dataops.dms.sql.LockFreeDmlEngine;
import com.dataops.dms.sql.OnlineDdlEngine;
import com.dataops.dms.sql.SqlAuditEngine;
import com.dataops.dms.sql.SqlExecutor;
import com.dataops.dms.util.TicketIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.Map;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Ticket Service Implementation
 * 对标阿里云DMS：完整的数据变更工单管理
 * - 变更影响行数预估
 * - 执行超时保护
 * - 无锁DML暂停/继续/终止
 * - DML进度实时查询
 * - 审批超时自动处理
 */
@Slf4j
@Service
public class TicketServiceImpl extends ServiceImpl<TicketMapper, Ticket> implements TicketService {

    @Resource
    private TicketApprovalService ticketApprovalService;

    @Resource
    private DataBackupService dataBackupService;

    @Resource
    private SqlAuditEngine sqlAuditEngine;

    @Resource
    private OnlineDdlEngine onlineDdlEngine;

    @Resource
    private LockFreeDmlEngine lockFreeDmlEngine;

    @Resource
    private DatabaseInstanceService databaseInstanceService;

    @Resource
    private SqlExecutor sqlExecutor;

    /**
     * 无锁DML执行控制信号：ticketId -> 暂停信号
     */
    private final Map<String, AtomicBoolean> dmlPauseSignals = new ConcurrentHashMap<>();

    /**
     * 无锁DML执行控制信号：ticketId -> 停止信号
     */
    private final Map<String, AtomicBoolean> dmlStopSignals = new ConcurrentHashMap<>();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Ticket createDataChangeTicket(TicketCreateDTO dto, String creatorId) {
        // 0. Trim SQL content（去除前后空白/换行，防止后续拼接SQL时位置偏移）
        if (dto.getSqlContent() != null) {
            dto.setSqlContent(dto.getSqlContent().trim());
        }

        // 1. SQL pre-audit
        SqlAuditEngine.SqlAuditResult auditResult = sqlAuditEngine.audit(dto.getSqlContent());
        if (!auditResult.isPassed()) {
            throw new RuntimeException("SQL审核未通过: " + getAuditIssuesMessage(auditResult.getIssues()));
        }

        // 2. 预估影响行数（对标阿里云DMS：变更前影响分析）
        Integer estimateAffectedRows = null;
        if (dto.getSqlContent().toUpperCase().matches(".*\\b(UPDATE|DELETE|INSERT)\\b.*")) {
            try {
                estimateAffectedRows = estimateAffectedRows(dto.getInstanceId(), dto.getSchemaName(), dto.getSqlContent());
                log.info("预估影响行数: {} 行", estimateAffectedRows);
            } catch (Exception e) {
                log.warn("无法预估影响行数: {}", e.getMessage());
                // 不影响工单创建，但会记录警告
            }
        }

        // 3. DDL/DML auto-detect
        if (dto.getUseOnlineDdl() == null) {
            dto.setUseOnlineDdl(false);
        }
        if (dto.getUseLockFreeDml() == null) {
            dto.setUseLockFreeDml(false);
        }

        // 4. 计算审批截止时间（0=不超时）
        int approvalTimeoutHours = dto.getApprovalTimeoutHours() != null ? dto.getApprovalTimeoutHours() : 0;
        LocalDateTime approvalDeadline = approvalTimeoutHours > 0 
            ? LocalDateTime.now().plusHours(approvalTimeoutHours) 
            : null;
        
        // 5. 安全风险判断：大影响行数自动建议无锁DML
        if (!Boolean.TRUE.equals(dto.getUseLockFreeDml()) 
            && estimateAffectedRows != null 
            && estimateAffectedRows > 10000
            && (dto.getChangeType().equalsIgnoreCase("dml") 
                || dto.getChangeType().equalsIgnoreCase("update")
                || dto.getChangeType().equalsIgnoreCase("delete"))) {
            log.warn("预估影响 {} 行(>1万行)，强烈建议开启无锁DML分批执行", estimateAffectedRows);
        }

        // 6. Create ticket
        Ticket ticket = new Ticket();
        ticket.setId(TicketIdGenerator.generate());
        ticket.setType("data_change");
        ticket.setTitle(dto.getTitle());
        ticket.setDescription(dto.getDescription());
        ticket.setStatus("pending");
        ticket.setPriority(dto.getPriority() != null ? dto.getPriority() : "normal");
        ticket.setCreatorId(creatorId);
        ticket.setInstanceId(dto.getInstanceId());
        ticket.setSchemaName(dto.getSchemaName());
        ticket.setSqlContent(dto.getSqlContent());
        ticket.setChangeType(dto.getChangeType());
        // DDL settings
        ticket.setUseOnlineDdl(dto.getUseOnlineDdl());
        ticket.setOnlineDdlStrategy(dto.getOnlineDdlStrategy());
        ticket.setDdlProgress(0);
        // DML settings
        ticket.setUseLockFreeDml(dto.getUseLockFreeDml());
        ticket.setDmlBatchSize(dto.getDmlBatchSize() != null ? dto.getDmlBatchSize() : 1000);
        ticket.setDmlBatchInterval(dto.getDmlBatchInterval() != null ? dto.getDmlBatchInterval() : 100);
        ticket.setDmlBatchCount(0);
        ticket.setDmlTotalAffected(0L);
        ticket.setDmlStatus(null);
        ticket.setDmlTotalBatches(0);
        ticket.setDmlProgressPercent(0);
        // 新增字段
        ticket.setEstimateAffectedRows(estimateAffectedRows);
        ticket.setExecutionTimeoutSeconds(dto.getExecutionTimeoutSeconds() != null ? dto.getExecutionTimeoutSeconds() : 600);
        ticket.setApprovalDeadline(approvalDeadline);
        ticket.setApprovedLevel(0);
        
        ticket.setCreateTime(LocalDateTime.now());
        
        this.save(ticket);
        log.info("工单创建成功: {}, 数据库: {}, OnlineDDL: {}, 无锁DML: {}, 预估影响行数: {}, 审批截止: {}",
            ticket.getId(), dto.getInstanceId(), dto.getUseOnlineDdl(), dto.getUseLockFreeDml(),
            estimateAffectedRows, approvalDeadline);
        return ticket;
    }

    /**
     * Check if SQL needs online DDL（结构变更检测）
     */
    @Override
    public OnlineDdlEngine.DdlCheckResult checkOnlineDdl(String instanceId, String schemaName, String sql) throws Exception {
        DatabaseInstance db = databaseInstanceService.getById(instanceId);
        if (db == null) {
            throw new RuntimeException("Database instance not found");
        }

        Connection conn = null;
        try {
            conn = getConnection(db, schemaName);
            return onlineDdlEngine.checkNeedOnlineDdl(sql, conn);
        } finally {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        }
    }

    /**
     * Check if SQL needs lock-free DML（数据变更检测）
     */
    @Override
    public LockFreeDmlEngine.DmlCheckResult checkLockFreeDml(String instanceId, String schemaName, String sql) throws Exception {
        DatabaseInstance db = databaseInstanceService.getById(instanceId);
        if (db == null) {
            throw new RuntimeException("Database instance not found");
        }

        Connection conn = null;
        try {
            conn = getConnection(db, schemaName);
            return lockFreeDmlEngine.checkNeedLockFree(instanceId, sql, conn);
        } finally {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        }
    }

    /**
     * 预估影响行数（对标阿里云DMS：通过SELECT COUNT获取真实行数）
     * 支持多条SQL（用 ; 分隔），累加所有语句的影响行数
     */
    private Integer estimateAffectedRows(String instanceId, String schemaName, String sql) throws Exception {
        DatabaseInstance db = databaseInstanceService.getById(instanceId);
        if (db == null) {
            return null;
        }

        List<String> statements = splitSqlStatements(sql);
        if (statements.isEmpty()) return null;

        int totalRows = 0;
        for (String stmt : statements) {
            String upper = stmt.trim().toUpperCase();

            if (upper.startsWith("UPDATE") || upper.startsWith("DELETE")) {
                Integer rows = countAffectedRows(db, schemaName, stmt);
                if (rows != null) totalRows += rows;
            } else if (upper.startsWith("INSERT")) {
                totalRows += countInsertRows(stmt);
            } else {
                // DDL等：用 EXPLAIN 估算
                Integer rows = estimateByExplain(db, schemaName, stmt);
                if (rows != null) totalRows += rows;
            }
        }
        return totalRows;
    }

    /**
     * 拆分多条SQL语句（按 ; 分隔，忽略空语句和纯空白）
     */
    private List<String> splitSqlStatements(String sql) {
        if (sql == null || sql.trim().isEmpty()) return new ArrayList<>();
        List<String> result = new ArrayList<>();
        String[] parts = sql.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /**
     * 将 UPDATE/DELETE SQL 转为 SELECT COUNT(*) 获取真实影响行数
     */
    private Integer countAffectedRows(DatabaseInstance db, String schemaName, String sql) throws Exception {
        String normalized = sql.trim().replaceAll("\\s+", " ");
        String countSql;

        if (normalized.toUpperCase().startsWith("UPDATE")) {
            // UPDATE table SET ... WHERE ...
            // 提取表名（第一个单词之后，SET 之前）
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "(?i)^UPDATE\\s+(.+?)\\s+SET\\s+.+?(WHERE\\s+.+)?$", java.util.regex.Pattern.DOTALL).matcher(normalized);
            if (m.matches()) {
                String table = m.group(1).trim();
                String where = m.group(2) != null ? " " + m.group(2).trim() : "";
                countSql = "SELECT COUNT(*) FROM " + table + where;
            } else {
                log.warn("无法解析 UPDATE 语句，回退 EXPLAIN: {}", sql);
                return estimateByExplain(db, schemaName, sql);
            }
        } else {
            // DELETE FROM table WHERE ...
            java.util.regex.Matcher m = java.util.regex.Pattern.compile(
                "(?i)^DELETE\\s+FROM\\s+(.+?)(\\s+WHERE\\s+.+)?$", java.util.regex.Pattern.DOTALL).matcher(normalized);
            if (m.matches()) {
                String table = m.group(1).trim();
                String where = m.group(2) != null ? " " + m.group(2).trim() : "";
                countSql = "SELECT COUNT(*) FROM " + table + where;
            } else {
                log.warn("无法解析 DELETE 语句，回退 EXPLAIN: {}", sql);
                return estimateByExplain(db, schemaName, sql);
            }
        }

        log.info("执行真实计数: {}", countSql);
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            conn = getConnection(db, schemaName);
            stmt = conn.createStatement();
            rs = stmt.executeQuery(countSql);
            if (rs.next()) {
                long count = rs.getLong(1);
                log.info("真实影响行数: {}", count);
                return (int) Math.min(count, Integer.MAX_VALUE);
            }
        } finally {
            try { if (rs != null) rs.close(); } catch (Exception ignored) {}
            try { if (stmt != null) stmt.close(); } catch (Exception ignored) {}
            try { if (conn != null && !conn.isClosed()) conn.close(); } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * EXPLAIN 回退方法
     */
    private Integer estimateByExplain(DatabaseInstance db, String schemaName, String sql) throws Exception {
        Connection conn = null;
        Statement stmt = null;
        ResultSet rs = null;
        try {
            conn = getConnection(db, schemaName);
            stmt = conn.createStatement();
            rs = stmt.executeQuery("EXPLAIN " + sql);
            if (rs.next()) {
                long rows = rs.getLong("rows");
                try {
                    String extra = rs.getString("Extra");
                    if (extra != null && extra.toUpperCase().contains("IMPOSSIBLE WHERE")) {
                        return 0;
                    }
                } catch (Exception ignored) {}
                return (int) Math.min(rows, Integer.MAX_VALUE);
            }
        } finally {
            try { if (rs != null) rs.close(); } catch (Exception ignored) {}
            try { if (stmt != null) stmt.close(); } catch (Exception ignored) {}
            try { if (conn != null && !conn.isClosed()) conn.close(); } catch (Exception ignored) {}
        }
        return null;
    }

    /**
     * 统计 INSERT 语句中 VALUES 行数
     */
    private Integer countInsertRows(String sql) {
        String upperSql = sql.toUpperCase();
        // 统计 VALUES 中括号对数量
        int count = 0;
        boolean inValues = false;
        for (int i = 0; i < upperSql.length() - 5; i++) {
            if (!inValues && upperSql.substring(i, i + 6).equals("VALUES")) {
                inValues = true;
                i += 5;
                continue;
            }
            if (inValues && upperSql.charAt(i) == '(') {
                count++;
            }
        }
        return count > 0 ? count : 1;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean approveTicket(String ticketId, String approverId, boolean approved, String comment) {
        Ticket ticket = this.getById(ticketId);
        if (ticket == null) {
            throw new RuntimeException("工单不存在");
        }
        
        if (!"pending".equals(ticket.getStatus())) {
            throw new RuntimeException("当前工单状态不允许审批: " + ticket.getStatus());
        }

        // 1. Record approval
        TicketApproval approval = new TicketApproval();
        approval.setTicketId(ticketId);
        approval.setApproverId(approverId);
        approval.setStatus(approved ? "approved" : "rejected");
        approval.setComment(comment);
        approval.setApprovedAt(LocalDateTime.now());
        ticketApprovalService.save(approval);

        // 2. 更新已审批级数
        ticket.setApprovedLevel((ticket.getApprovedLevel() != null ? ticket.getApprovedLevel() : 0) + 1);

        // 3. Update ticket status and execute
        if (approved) {
            ticket.setStatus("executing");
            ticket.setCurrentApproverId(approverId);
            ticket.setUpdateTime(LocalDateTime.now());
            this.updateById(ticket);
            
            // 4. Execute SQL with timeout protection
            try {
                executeTicketSql(ticket, approverId);
                ticket.setStatus("done");
                ticket.setExecuteTime(LocalDateTime.now());
                ticket.setDdlProgress(100);
                ticket.setDmlProgressPercent(100);
                this.updateById(ticket);
                log.info("工单审批通过并执行成功: {}", ticketId);
            } catch (Exception e) {
                ticket.setStatus("failed");
                ticket.setErrorMsg(e.getMessage());
                ticket.setUpdateTime(LocalDateTime.now());
                this.updateById(ticket);
                log.error("工单执行失败: {}, 错误: {}", ticketId, e.getMessage(), e);
                throw new RuntimeException("执行失败: " + e.getMessage());
            }
        } else {
            ticket.setStatus("rejected");
            ticket.setUpdateTime(LocalDateTime.now());
            this.updateById(ticket);
            log.info("工单审批拒绝: {}, 审批人: {}", ticketId, approverId);
        }
        
        return true;
    }

    /**
     * Execute ticket SQL，支持普通DML、Online DDL、无锁DML
     * 支持多条SQL（用 ; 分隔），逐条执行
     */
    private void executeTicketSql(Ticket ticket, String operatorId) throws Exception {
        String instanceId = ticket.getInstanceId();
        String sql = ticket.getSqlContent();
        
        if (instanceId == null || sql == null) {
            throw new RuntimeException("工单数据不完整，缺少databaseId或SQL内容");
        }

        DatabaseInstance db = databaseInstanceService.getById(instanceId);
        if (db == null) {
            throw new RuntimeException("数据库实例不存在");
        }

        String effectiveSchemaName = ticket.getSchemaName();
        if (effectiveSchemaName == null || effectiveSchemaName.isEmpty()) {
            effectiveSchemaName = db.getDefaultSchemaName();
        }
        if (effectiveSchemaName == null || effectiveSchemaName.isEmpty()) {
            throw new RuntimeException("数据库实例 [" + db.getName() + "] 未配置Schema名");
        }

        // 拆分多条SQL
        List<String> statements = splitSqlStatements(sql);
        if (statements.isEmpty()) {
            throw new RuntimeException("SQL内容为空");
        }
        log.info("共 {} 条SQL待执行", statements.size());

        boolean useLockFreeDml = Boolean.TRUE.equals(ticket.getUseLockFreeDml());
        boolean useOnlineDdl = Boolean.TRUE.equals(ticket.getUseOnlineDdl()) 
            && ticket.getChangeType() != null 
            && (ticket.getChangeType().equalsIgnoreCase("ddl") || ticket.getChangeType().equalsIgnoreCase("alter"));

        // 逐条执行
        for (int i = 0; i < statements.size(); i++) {
            String singleSql = statements.get(i);
            log.info("执行第 {}/{} 条SQL: {}...", i + 1, statements.size(), 
                singleSql.length() > 80 ? singleSql.substring(0, 80) + "..." : singleSql);

            if (useLockFreeDml) {
                executeLockFreeDml(ticket, db, effectiveSchemaName, instanceId, singleSql);
            } else if (useOnlineDdl) {
                executeOnlineDdl(ticket, db, effectiveSchemaName, singleSql);
            } else {
                executeNormalDml(ticket, db, effectiveSchemaName, instanceId, singleSql, operatorId);
            }
        }
        log.info("全部 {} 条SQL执行完成", statements.size());
    }

    /**
     * 执行无锁DML单条语句
     */
    private void executeLockFreeDml(Ticket ticket, DatabaseInstance db,
            String effectiveSchemaName, String instanceId, String sql) throws Exception {
        log.info("使用无锁DML执行，批次大小: {}, 间隔: {}ms", 
            ticket.getDmlBatchSize(), ticket.getDmlBatchInterval());
        
        Connection conn = null;
        try {
            conn = getConnection(db, effectiveSchemaName);
            int timeoutSeconds = ticket.getExecutionTimeoutSeconds() != null ? ticket.getExecutionTimeoutSeconds() : 600;
            setSessionTimeout(conn, timeoutSeconds);
            
            LockFreeDmlEngine.DmlCheckResult checkResult =
                lockFreeDmlEngine.checkNeedLockFree(instanceId, sql, conn);
            
            if (!checkResult.isHasPrimaryKey()) {
                throw new RuntimeException("表 " + checkResult.getTableName() + " 没有主键，不支持无锁DML分批执行");
            }
            
            String ticketId = ticket.getId();
            dmlPauseSignals.put(ticketId, new AtomicBoolean(false));
            dmlStopSignals.put(ticketId, new AtomicBoolean(false));
            
            ticket.setDmlStatus("running");
            ticket.setDmlBatchCount(0);
            ticket.setDmlTotalAffected(0L);
            ticket.setDmlProgressPercent(0);
            if (ticket.getEstimateAffectedRows() != null && ticket.getDmlBatchSize() != null && ticket.getDmlBatchSize() > 0) {
                ticket.setDmlTotalBatches((int) Math.ceil((double) ticket.getEstimateAffectedRows() / ticket.getDmlBatchSize()));
            }
            this.updateById(ticket);
            
            int batchSize = ticket.getDmlBatchSize() != null ? ticket.getDmlBatchSize() : 1000;
            int intervalMs = ticket.getDmlBatchInterval() != null ? ticket.getDmlBatchInterval() : 100;
            
            LockFreeDmlEngine.DmlExecutionResult result = lockFreeDmlEngine.executeLockFree(
                conn, sql, checkResult.getTableName(), checkResult.getPrimaryKey(),
                batchSize, intervalMs,
                (batchCount, totalAffected, batchAffected) -> {
                    AtomicBoolean pauseSignal = dmlPauseSignals.get(ticketId);
                    if (pauseSignal != null && pauseSignal.get()) {
                        log.info("无锁DML已暂停，等待恢复... ticketId: {}", ticketId);
                        synchronized (pauseSignal) {
                            while (pauseSignal.get() && !dmlStopSignals.get(ticketId).get()) {
                                try { pauseSignal.wait(5000); } catch (InterruptedException e) { break; }
                            }
                        }
                        log.info("无锁DML恢复执行 ticketId: {}", ticketId);
                    }
                    AtomicBoolean stopSignal = dmlStopSignals.get(ticketId);
                    if (stopSignal != null && stopSignal.get()) {
                        throw new RuntimeException("无锁DML已被用户终止");
                    }
                    ticket.setDmlBatchCount(batchCount);
                    ticket.setDmlTotalAffected(totalAffected);
                    int totalBatches = ticket.getDmlTotalBatches() != null && ticket.getDmlTotalBatches() > 0 
                        ? ticket.getDmlTotalBatches() : 1;
                    ticket.setDmlProgressPercent(Math.min(100, batchCount * 100 / totalBatches));
                    this.updateById(ticket);
                }
            );
            
            dmlPauseSignals.remove(ticketId);
            dmlStopSignals.remove(ticketId);
            
            if (!result.isSuccess()) {
                ticket.setDmlStatus("failed");
                throw new RuntimeException("无锁DML执行失败: " + result.getMessage());
            }
            
            ticket.setDmlStatus("completed");
            ticket.setDmlBatchCount(result.getBatchCount());
            ticket.setDmlTotalAffected(result.getTotalAffected());
            ticket.setDmlProgressPercent(100);
            log.info("无锁DML执行完成 - 批次: {}, 总影响: {}行", result.getBatchCount(), result.getTotalAffected());
        } finally {
            if (conn != null && !conn.isClosed()) conn.close();
        }
    }

    /**
     * 执行Online DDL单条语句
     */
    private void executeOnlineDdl(Ticket ticket, DatabaseInstance db, 
            String effectiveSchemaName, String sql) throws Exception {
        log.info("使用Online DDL，策略: {}", ticket.getOnlineDdlStrategy());
        Connection conn = null;
        try {
            conn = getConnection(db, effectiveSchemaName);
            int timeoutSeconds = ticket.getExecutionTimeoutSeconds() != null ? ticket.getExecutionTimeoutSeconds() : 600;
            setSessionTimeout(conn, timeoutSeconds);
            
            String strategy = ticket.getOnlineDdlStrategy() != null ? 
                ticket.getOnlineDdlStrategy() : "mysql_online";
            
            OnlineDdlEngine.DdlExecutionResult result = 
                onlineDdlEngine.executeOnlineDdl(conn, sql, strategy);
            
            if (!result.isSuccess()) {
                throw new RuntimeException("Online DDL执行失败: " + result.getErrorMessage());
            }
            log.info("Online DDL执行完成，策略: {}", strategy);
        } finally {
            if (conn != null && !conn.isClosed()) conn.close();
        }
    }

    /**
     * 执行普通DML单条语句（带备份）
     */
    private void executeNormalDml(Ticket ticket, DatabaseInstance db,
            String effectiveSchemaName, String instanceId, String sql, String operatorId) throws Exception {
        log.info("执行普通DML，变更类型: {}", ticket.getChangeType());
        DataChangeBackup backup = dataBackupService.backupAndExecute(
            ticket.getId(), instanceId, effectiveSchemaName, sql,
            ticket.getChangeType() != null ? ticket.getChangeType() : detectChangeType(sql),
            operatorId,
            ticket.getExecutionTimeoutSeconds() != null ? ticket.getExecutionTimeoutSeconds() : 600
        );
        log.info("SQL执行完成，备份ID: {}", backup.getId());
    }
    
    /**
     * 设置MySQL会话级执行超时（防止长事务锁表）
     */
    private void setSessionTimeout(Connection conn, int timeoutSeconds) {
        try (Statement stmt = conn.createStatement()) {
            // 设置会话最大执行时间（MySQL 5.7.4+）
            stmt.execute("SET SESSION max_execution_time = " + (timeoutSeconds * 1000));
            // 设置锁等待超时
            stmt.execute("SET SESSION lock_wait_timeout = " + Math.min(timeoutSeconds, 300));
            // 设置空闲事务超时
            stmt.execute("SET SESSION idle_transaction_timeout = " + (timeoutSeconds * 1000));
            log.info("已设置会话超时: max_execution_time={}ms, lock_wait_timeout={}s", 
                timeoutSeconds * 1000, Math.min(timeoutSeconds, 300));
        } catch (Exception e) {
            log.warn("设置会话超时失败（可能不支持的MySQL版本）: {}", e.getMessage());
        }
    }
    
    // ============ 无锁DML运行态控制（对标阿里云DMS：暂停/继续/终止） ============

    @Override
    public boolean pauseDmlExecution(String ticketId, String operatorId) {
        Ticket ticket = this.getById(ticketId);
        if (ticket == null) throw new RuntimeException("工单不存在");
        
        if (!Boolean.TRUE.equals(ticket.getUseLockFreeDml())) {
            throw new RuntimeException("该工单未使用无锁DML");
        }
        if (!"executing".equals(ticket.getStatus()) || !"running".equals(ticket.getDmlStatus())) {
            throw new RuntimeException("无锁DML当前不在执行中，无法暂停");
        }
        
        AtomicBoolean pauseSignal = dmlPauseSignals.get(ticketId);
        if (pauseSignal != null) {
            pauseSignal.set(true);
        } else {
            dmlPauseSignals.put(ticketId, new AtomicBoolean(true));
        }
        
        ticket.setDmlStatus("paused");
        ticket.setUpdateTime(LocalDateTime.now());
        this.updateById(ticket);
        log.info("无锁DML已暂停: {}, 操作人: {}", ticketId, operatorId);
        return true;
    }

    @Override
    public boolean resumeDmlExecution(String ticketId, String operatorId) {
        Ticket ticket = this.getById(ticketId);
        if (ticket == null) throw new RuntimeException("工单不存在");
        
        if (!Boolean.TRUE.equals(ticket.getUseLockFreeDml())) {
            throw new RuntimeException("该工单未使用无锁DML");
        }
        if (!"executing".equals(ticket.getStatus()) || !"paused".equals(ticket.getDmlStatus())) {
            throw new RuntimeException("无锁DML当前不在暂停状态，无法恢复");
        }
        
        AtomicBoolean pauseSignal = dmlPauseSignals.get(ticketId);
        if (pauseSignal != null) {
            synchronized (pauseSignal) {
                pauseSignal.set(false);
                pauseSignal.notifyAll();
            }
        }
        
        ticket.setDmlStatus("running");
        ticket.setUpdateTime(LocalDateTime.now());
        this.updateById(ticket);
        log.info("无锁DML已恢复: {}, 操作人: {}", ticketId, operatorId);
        return true;
    }

    @Override
    public boolean stopDmlExecution(String ticketId, String operatorId) {
        Ticket ticket = this.getById(ticketId);
        if (ticket == null) throw new RuntimeException("工单不存在");
        
        if (!Boolean.TRUE.equals(ticket.getUseLockFreeDml())) {
            throw new RuntimeException("该工单未使用无锁DML");
        }
        if (!"executing".equals(ticket.getStatus())) {
            throw new RuntimeException("工单不在执行状态");
        }
        
        // 设置停止信号
        AtomicBoolean stopSignal = dmlStopSignals.get(ticketId);
        if (stopSignal != null) {
            stopSignal.set(true);
        } else {
            dmlStopSignals.put(ticketId, new AtomicBoolean(true));
        }
        
        // 如果有暂停信号，同时唤醒等待线程
        AtomicBoolean pauseSignal = dmlPauseSignals.get(ticketId);
        if (pauseSignal != null) {
            synchronized (pauseSignal) {
                pauseSignal.set(false);
                pauseSignal.notifyAll();
            }
        }
        
        ticket.setDmlStatus("stopped");
        ticket.setErrorMsg("用户主动终止: " + operatorId);
        ticket.setUpdateTime(LocalDateTime.now());
        this.updateById(ticket);
        log.info("无锁DML已终止: {}, 操作人: {}", ticketId, operatorId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean rollbackTicket(String ticketId, String operatorId) throws Exception {
        Ticket ticket = this.getById(ticketId);
        if (ticket == null) {
            throw new RuntimeException("Ticket not found");
        }
        
        if (!"done".equals(ticket.getStatus())) {
            throw new RuntimeException("Only completed tickets can be rolled back");
        }

        // DDL changes do not support auto rollback
        if (Boolean.TRUE.equals(ticket.getUseOnlineDdl())) {
            throw new RuntimeException("DDL changes do not support auto rollback, please handle manually");
        }

        // Lock-free DML also supports rollback via backup
        if (Boolean.TRUE.equals(ticket.getUseLockFreeDml())) {
            log.warn("Lock-free DML rollback requires manual review, using backup mechanism");
        }

        // Query backup records
        LambdaQueryWrapper<DataChangeBackup> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(DataChangeBackup::getTicketId, ticketId).orderByDesc(DataChangeBackup::getCreateTime);
        List<DataChangeBackup> backups = dataBackupService.list(wrapper);
        
        if (backups.isEmpty()) {
            throw new RuntimeException("No backup records found, unable to rollback");
        }

        // Rollback latest backup
        DataChangeBackup backup = backups.get(0);
        boolean success = dataBackupService.rollback(backup.getId(), operatorId);
        
        if (success) {
            ticket.setStatus("rolled_back");
            ticket.setUpdateTime(LocalDateTime.now());
            this.updateById(ticket);
            log.info("Ticket rollback completed: {}", ticketId);
        }
        
        return success;
    }

    @Override
    public boolean cancelTicket(String ticketId, String userId) {
        Ticket ticket = this.getById(ticketId);
        if (ticket == null) {
            throw new RuntimeException("Ticket not found");
        }
        if (!"pending".equals(ticket.getStatus())) {
            throw new RuntimeException("Cannot cancel ticket in current status");
        }
        ticket.setStatus("cancelled");
        ticket.setUpdateTime(LocalDateTime.now());
        this.updateById(ticket);
        log.info("Ticket cancelled: {}", ticketId);
        return true;
    }

    @Override
    public List<Ticket> getMyPendingTickets(String approverId) {
        LambdaQueryWrapper<Ticket> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Ticket::getStatus, "pending")
               .orderByDesc(Ticket::getCreateTime);
        return this.list(wrapper);
    }

    @Override
    public PageResult<Ticket> getMyPendingTicketsPage(String approverId, Integer page, Integer size) {
        Integer pageNum = page == null || page <= 0 ? 1 : page;
        Integer pageSize = size == null || size <= 0 ? 15 : (size > 200 ? 200 : size);

        LambdaQueryWrapper<Ticket> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Ticket::getStatus, "pending")
               .orderByDesc(Ticket::getCreateTime);

        Page<Ticket> query = new Page<>(pageNum, pageSize);
        IPage<Ticket> result = this.page(query, wrapper);
        return PageResult.of(pageNum, pageSize, result.getTotal(), result.getRecords());
    }

    @Override
    public List<Ticket> getMyCreatedTickets(String creatorId) {
        LambdaQueryWrapper<Ticket> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Ticket::getCreatorId, creatorId)
               .orderByDesc(Ticket::getCreateTime);
        return this.list(wrapper);
    }

    @Override
    public PageResult<Ticket> getMyCreatedTicketsPage(String creatorId, String status, Integer page, Integer size) {
        Integer pageNum = page == null || page <= 0 ? 1 : page;
        Integer pageSize = size == null || size <= 0 ? 15 : (size > 200 ? 200 : size);

        LambdaQueryWrapper<Ticket> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Ticket::getCreatorId, creatorId);
        if (status != null && !status.isEmpty()) {
            wrapper.eq(Ticket::getStatus, status);
        }
        wrapper.orderByDesc(Ticket::getCreateTime);

        Page<Ticket> query = new Page<>(pageNum, pageSize);
        IPage<Ticket> result = this.page(query, wrapper);
        return PageResult.of(pageNum, pageSize, result.getTotal(), result.getRecords());
    }

    @Override
    public List<Ticket> getAllTickets() {
        LambdaQueryWrapper<Ticket> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Ticket::getCreateTime);
        return this.list(wrapper);
    }

    @Override
    public List<Ticket> queryTickets(String changeType, String status, String keyword, String instanceId) {
        LambdaQueryWrapper<Ticket> wrapper = new LambdaQueryWrapper<>();
        if (changeType != null && !changeType.isEmpty()) {
            wrapper.eq(Ticket::getChangeType, changeType);
        }
        if (status != null && !status.isEmpty()) {
            // 支持逗号分隔的多状态筛选，如 "done,failed,rejected,cancelled,rolled_back"
            String[] statuses = status.split(",");
            if (statuses.length == 1) {
                wrapper.eq(Ticket::getStatus, status);
            } else {
                wrapper.in(Ticket::getStatus, (Object[]) statuses);
            }
        }
        if (instanceId != null && !instanceId.isEmpty()) {
            wrapper.eq(Ticket::getInstanceId, instanceId);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(Ticket::getTitle, keyword)
                    .or().like(Ticket::getDescription, keyword)
                    .or().like(Ticket::getId, keyword)
                    .or().like(Ticket::getCreatorId, keyword));
        }
        wrapper.orderByDesc(Ticket::getCreateTime);
        return this.list(wrapper);
    }

    @Override
    public PageResult<Ticket> queryTicketsPage(String changeType, String status, String keyword, String instanceId, Integer page, Integer size) {
        Integer pageNum = page == null || page <= 0 ? 1 : page;
        Integer pageSize = size == null || size <= 0 ? 15 : (size > 200 ? 200 : size);

        LambdaQueryWrapper<Ticket> wrapper = new LambdaQueryWrapper<>();
        if (changeType != null && !changeType.isEmpty()) {
            wrapper.eq(Ticket::getChangeType, changeType);
        }
        if (status != null && !status.isEmpty()) {
            String[] statuses = status.split(",");
            if (statuses.length == 1) {
                wrapper.eq(Ticket::getStatus, status);
            } else {
                wrapper.in(Ticket::getStatus, (Object[]) statuses);
            }
        }
        if (instanceId != null && !instanceId.isEmpty()) {
            wrapper.eq(Ticket::getInstanceId, instanceId);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(Ticket::getTitle, keyword)
                    .or().like(Ticket::getDescription, keyword)
                    .or().like(Ticket::getId, keyword)
                    .or().like(Ticket::getCreatorId, keyword));
        }
        wrapper.orderByDesc(Ticket::getCreateTime);

        Page<Ticket> query = new Page<>(pageNum, pageSize);
        IPage<Ticket> result = this.page(query, wrapper);
        return PageResult.of(pageNum, pageSize, result.getTotal(), result.getRecords());
    }

    @Override
    public void updateTicketInfo(String ticketId, Map<String, Object> updates) {
        Ticket ticket = this.getById(ticketId);
        if (ticket == null) {
            throw new RuntimeException("Ticket not found: " + ticketId);
        }
        if (updates.containsKey("status")) {
            ticket.setStatus((String) updates.get("status"));
        }
        if (updates.containsKey("executeResult")) {
            ticket.setExecuteResult((String) updates.get("executeResult"));
        }
        if (updates.containsKey("executeTime")) {
            ticket.setExecuteTime((String) updates.get("executeTime"));
        }
        if (updates.containsKey("affectedRows")) {
            Object val = updates.get("affectedRows");
            if (val != null) {
                ticket.setEstimateAffectedRows(Integer.valueOf(String.valueOf(val)));
            }
        }
        if (updates.containsKey("relatedPersons")) {
            Object val = updates.get("relatedPersons");
            String content = ticket.getContent();
            Map<String, Object> contentMap;
            if (content != null && !content.isEmpty()) {
                try {
                    contentMap = new com.fasterxml.jackson.databind.ObjectMapper().readValue(content, Map.class);
                } catch (Exception e) {
                    contentMap = new java.util.HashMap<>();
                }
            } else {
                contentMap = new java.util.HashMap<>();
            }
            contentMap.put("relatedPersons", val);
            try {
                ticket.setContent(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(contentMap));
            } catch (Exception e) {
                log.error("Failed to serialize relatedPersons", e);
            }
        }
        this.updateById(ticket);
    }

    @Override
    public Ticket getTicketDetail(String ticketId) {
        return this.getById(ticketId);
    }

    // ============ 对标阿里云DMS：DML进度查询 ============

    @Override
    public Map<String, Object> getDmlProgress(String ticketId) {
        Ticket ticket = this.getById(ticketId);
        if (ticket == null) {
            throw new RuntimeException("工单不存在");
        }
        
        Map<String, Object> progress = new LinkedHashMap<>();
        progress.put("ticketId", ticket.getId());
        progress.put("status", ticket.getStatus());
        progress.put("dmlStatus", ticket.getDmlStatus());
        progress.put("dmlBatchCount", ticket.getDmlBatchCount());
        progress.put("dmlTotalBatches", ticket.getDmlTotalBatches());
        progress.put("dmlProgressPercent", ticket.getDmlProgressPercent());
        progress.put("dmlTotalAffected", ticket.getDmlTotalAffected());
        progress.put("dmlBatchSize", ticket.getDmlBatchSize());
        progress.put("dmlBatchInterval", ticket.getDmlBatchInterval());
        progress.put("estimateAffectedRows", ticket.getEstimateAffectedRows());
        progress.put("useLockFreeDml", ticket.getUseLockFreeDml());
        
        // 计算预计剩余时间
        if (ticket.getDmlBatchCount() != null && ticket.getDmlBatchCount() > 0 
            && ticket.getDmlBatchInterval() != null && ticket.getDmlTotalBatches() != null) {
            int remainingBatches = ticket.getDmlTotalBatches() - ticket.getDmlBatchCount();
            long estimatedRemainingMs = (long) remainingBatches * ticket.getDmlBatchInterval();
            progress.put("remainingBatches", remainingBatches);
            progress.put("estimatedRemainingSeconds", estimatedRemainingMs / 1000);
        }
        
        return progress;
    }

    // ============ 对标阿里云DMS：审批超时处理 ============

    @Override
    public int processApprovalTimeout() {
        LambdaQueryWrapper<Ticket> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Ticket::getStatus, "pending")
               .isNotNull(Ticket::getApprovalDeadline)
               .lt(Ticket::getApprovalDeadline, LocalDateTime.now());
        List<Ticket> timeoutTickets = this.list(wrapper);
        
        int count = 0;
        for (Ticket ticket : timeoutTickets) {
            ticket.setStatus("rejected");
            ticket.setErrorMsg("审批超时（截止: " + ticket.getApprovalDeadline() + "），工单自动拒绝");
            ticket.setUpdateTime(LocalDateTime.now());
            this.updateById(ticket);
            log.info("审批超时自动拒绝: {}, 截止时间: {}", ticket.getId(), ticket.getApprovalDeadline());
            count++;
        }
        
        if (count > 0) {
            log.info("本次处理审批超时工单: {} 个", count);
        }
        return count;
    }

    @Override
    public List<Map<String, Object>> getApprovalRecords(String ticketId) {
        LambdaQueryWrapper<TicketApproval> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TicketApproval::getTicketId, ticketId)
               .orderByAsc(TicketApproval::getApprovedAt);
        List<TicketApproval> approvals = ticketApprovalService.list(wrapper);
        
        List<Map<String, Object>> records = new ArrayList<>();
        for (TicketApproval approval : approvals) {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("id", approval.getId());
            record.put("approverId", approval.getApproverId());
            record.put("status", approval.getStatus());
            record.put("comment", approval.getComment());
            record.put("approvedAt", approval.getApprovedAt());
            records.add(record);
        }
        return records;
    }

    /**
     * Auto-detect SQL change type
     */
    private String detectChangeType(String sql) {
        String upper = sql.trim().toUpperCase();
        if (upper.startsWith("INSERT")) {
            return "INSERT";
        } else if (upper.startsWith("UPDATE")) {
            return "UPDATE";
        } else if (upper.startsWith("DELETE")) {
            return "DELETE";
        } else if (upper.startsWith("ALTER") || upper.startsWith("CREATE INDEX") 
                 || upper.startsWith("DROP INDEX")) {
            return "DDL";
        }
        return "OTHER";
    }

    /**
     * Get database connection with specific schema name
     */
    private Connection getConnection(DatabaseInstance db, String schemaName) throws Exception {
        String dbName = (schemaName != null && !schemaName.isEmpty()) ? schemaName : db.getDefaultSchemaName();
        String url = String.format("jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false",
            db.getHost(), db.getPort() != null ? db.getPort() : 3306, dbName);
        
        Properties props = new Properties();
        props.put("user", db.getUsername());
        props.put("password", db.getPassword());
        
        return DriverManager.getConnection(url, props);
    }

    /**
     * Extract audit issue messages
     */
    private String getAuditIssuesMessage(java.util.List<SqlAuditEngine.AuditIssue> issues) {
        java.util.List<String> messages = new java.util.ArrayList<>();
        for (SqlAuditEngine.AuditIssue issue : issues) {
            messages.add(issue.getMessage());
        }
        return String.join("; ", messages);
    }
}