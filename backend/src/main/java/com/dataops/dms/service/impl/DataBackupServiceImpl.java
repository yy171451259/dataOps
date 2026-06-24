package com.dataops.dms.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataops.dms.entity.DataChangeBackup;
import com.dataops.dms.entity.DatabaseInstance;
import com.dataops.dms.mapper.DataChangeBackupMapper;
import com.dataops.dms.service.DataBackupService;
import com.dataops.dms.service.DatabaseInstanceService;
import com.dataops.dms.sql.SqlExecuteResult;
import com.dataops.dms.sql.SqlExecutor;
import com.dataops.dms.sql.SqlRollbackGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.*;

/**
 * 数据变更备份服务实现
 */
@Slf4j
@Service
public class DataBackupServiceImpl extends ServiceImpl<DataChangeBackupMapper, DataChangeBackup> implements DataBackupService {

    @Resource
    private DatabaseInstanceService databaseInstanceService;

    @Resource
    private SqlExecutor sqlExecutor;

    @Resource
    private SqlRollbackGenerator rollbackGenerator;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataChangeBackup backupAndExecute(String ticketId, String databaseId, String databaseName, String sql, String changeType, String operatorId) throws Exception {
        return backupAndExecute(ticketId, databaseId, databaseName, sql, changeType, operatorId, 600);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataChangeBackup backupAndExecute(String ticketId, String databaseId, String databaseName, String sql, String changeType, String operatorId, int executionTimeoutSeconds) throws Exception {
        DatabaseInstance db = databaseInstanceService.getById(databaseId);
        if (db == null) {
            throw new RuntimeException("数据库实例不存在");
        }

        // 解析SQL，识别影响的表和数据
        String tableName = extractTableName(sql, changeType);
        
        // 备份原始数据（只针对UPDATE/DELETE，支持上层传入 DML/UPDATE/DELETE）
        String backupData = null;
        String rollbackSql = null;
        String actualType = changeType != null ? changeType.toUpperCase() : "";
        // 工单表存储的是 "DML"，实际SQL类型需从SQL内容检测
        if (actualType.equals("DML")) {
            actualType = detectChangeTypeFromSql(sql);
        }
        
        if ("UPDATE".equalsIgnoreCase(actualType) || "DELETE".equalsIgnoreCase(actualType)) {
            backupData = backupOriginalData(db, databaseName, sql, actualType, tableName);
            rollbackSql = rollbackGenerator.generateRollbackSql(sql, actualType, tableName, backupData);
            log.info("生成回滚SQL: {}", rollbackSql);
        }

        // 保存备份记录
        DataChangeBackup backup = new DataChangeBackup();
        backup.setTicketId(ticketId);
        backup.setInstanceId(databaseId);
        backup.setTableName(tableName);
        backup.setChangeType(changeType.toUpperCase());
        backup.setOriginalSql(sql);
        backup.setRollbackSql(rollbackSql);
        backup.setBackupData(backupData);
        backup.setStatus("normal");
        backup.setCreateBy(operatorId);
        backup.setDeleted(0); // 逻辑删除标记：未删除
        this.save(backup);

        // 执行变更SQL（带超时保护）
        log.info("开始执行变更SQL（超时: {}秒）: {}", executionTimeoutSeconds, sql);
        
        Connection conn = null;
        Statement stmt = null;
        try {
            conn = getConnection(db, databaseName);
            // 设置会话超时
            stmt = conn.createStatement();
            stmt.execute("SET SESSION max_execution_time = " + (executionTimeoutSeconds * 1000));
            stmt.execute("SET SESSION lock_wait_timeout = " + Math.min(executionTimeoutSeconds, 300));
            log.info("已设置会话超时保护: {}秒", executionTimeoutSeconds);
        } catch (Exception e) {
            log.warn("设置超时保护失败: {}", e.getMessage());
        } finally {
            try { if (stmt != null) stmt.close(); } catch (Exception ignored) {}
            try { if (conn != null && !conn.isClosed()) conn.close(); } catch (Exception ignored) {}
        }
        
        sqlExecutor.executeQuery(db, sql, databaseName);
        log.info("SQL执行完成，备份记录ID: {}", backup.getId());

        return backup;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean rollback(String backupId, String operatorId) throws Exception {
        DataChangeBackup backup = this.getById(backupId);
        if (backup == null) {
            throw new RuntimeException("备份记录不存在");
        }

        if (backup.getRollbackSql() == null || backup.getRollbackSql().isEmpty()) {
            throw new RuntimeException("该操作无法回滚（无回滚SQL）");
        }

        DatabaseInstance db = databaseInstanceService.getById(backup.getInstanceId());
        if (db == null) {
            throw new RuntimeException("数据库实例不存在");
        }

        // 执行回滚SQL
        log.info("开始执行回滚SQL，备份ID: {}, 回滚SQL: {}", backupId, backup.getRollbackSql());
        sqlExecutor.executeQuery(db, backup.getRollbackSql());
        
        // 更新备份状态
        backup.setRollbackTime(new Date());
        backup.setRollbackBy(operatorId);
        backup.setStatus("rolled_back");
        this.updateById(backup);
        
        log.info("回滚完成，备份ID: {}", backupId);
        return true;
    }

    /**
     * 备份原始数据（SELECT查询变更前的数据）
     */
    private String backupOriginalData(DatabaseInstance db, String databaseName, String sql, String changeType, String tableName) throws Exception {
        String selectSql = buildSelectForBackup(sql, changeType, tableName);
        if (selectSql == null) {
            log.warn("无法生成备份查询，跳过备份");
            return null;
        }

        log.info("备份原始数据，查询SQL: {}", selectSql);
        SqlExecuteResult result = sqlExecutor.executeQuery(db, selectSql, databaseName);
        
        // 转换为JSON格式存储
        List<Map<String, Object>> dataList = new ArrayList<>();
        for (Map<String, Object> row : result.getData()) {
            dataList.add(new HashMap<>(row));
        }
        
        return com.alibaba.fastjson2.JSON.toJSONString(dataList);
    }

    /**
     * 从SQL中提取表名
     */
    private String extractTableName(String sql, String changeType) {
        sql = sql.trim().replaceAll("\\s+", " ");
        String upperSql = sql.toUpperCase();
        
        if ("INSERT".equalsIgnoreCase(changeType)) {
            // INSERT INTO table_name ...
            int intoIdx = upperSql.indexOf("INTO");
            if (intoIdx > 0) {
                String rest = sql.substring(intoIdx + 4).trim();
                return rest.split("[\\s(]")[0].trim();
            }
        } else if ("UPDATE".equalsIgnoreCase(changeType)) {
            // UPDATE table_name SET ...
            int updateIdx = upperSql.indexOf("UPDATE");
            if (updateIdx >= 0) {
                String rest = sql.substring(updateIdx + 6).trim();
                return rest.split("[\\s]")[0].trim();
            }
        } else if ("DELETE".equalsIgnoreCase(changeType)) {
            // DELETE FROM table_name WHERE ...
            int fromIdx = upperSql.indexOf("FROM");
            if (fromIdx > 0) {
                String rest = sql.substring(fromIdx + 4).trim();
                return rest.split("[\\s]")[0].trim();
            }
        }
        
        return "unknown";
    }

    /**
     * 获取数据库连接
     */
    private Connection getConnection(DatabaseInstance db, String databaseName) throws Exception {
        String dbName = (databaseName != null && !databaseName.isEmpty()) ? databaseName : db.getDefaultSchemaName();
        String url = String.format("jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowMultiQueries=true&zeroDateTimeBehavior=convertToNull",
            db.getHost(), db.getPort() != null ? db.getPort() : 3306, dbName);
        java.util.Properties props = new java.util.Properties();
        props.put("user", db.getUsername());
        props.put("password", db.getPassword());
        return DriverManager.getConnection(url, props);
    }

    /**
     * 构建备份查询SQL
     */
    private String buildSelectForBackup(String sql, String changeType, String tableName) {
        String upperSql = sql.toUpperCase();
        
        if ("UPDATE".equalsIgnoreCase(changeType)) {
            // 提取WHERE条件：UPDATE table SET ... WHERE ...
            int whereIdx = upperSql.indexOf("WHERE");
            if (whereIdx > 0) {
                String whereClause = sql.substring(whereIdx);
                return String.format("SELECT * FROM %s %s FOR UPDATE", tableName, whereClause);
            }
        } else if ("DELETE".equalsIgnoreCase(changeType)) {
            int whereIdx = upperSql.indexOf("WHERE");
            if (whereIdx > 0) {
                String whereClause = sql.substring(whereIdx);
                return String.format("SELECT * FROM %s %s", tableName, whereClause);
            }
        }
        
        return null;
    }

    /**
     * 从SQL内容直接检测实际变更类型（UPDATE/DELETE/INSERT）
     */
    private String detectChangeTypeFromSql(String sql) {
        if (sql == null) return "UNKNOWN";
        String upper = sql.trim().toUpperCase();
        if (upper.startsWith("UPDATE")) return "UPDATE";
        if (upper.startsWith("DELETE")) return "DELETE";
        if (upper.startsWith("INSERT")) return "INSERT";
        if (upper.startsWith("ALTER") || upper.startsWith("CREATE") || upper.startsWith("DROP")) return "DDL";
        return "UNKNOWN";
    }
}