package com.dataops.dms.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dataops.dms.entity.DataChangeBackup;

/**
 * 数据备份服务接口
 */
public interface DataBackupService extends IService<DataChangeBackup> {

    /**
     * 备份数据并执行SQL变更
     * @param ticketId 关联工单ID
     * @param databaseId 数据库ID
     * @param databaseName 数据库名
     * @param sql 执行的SQL
     * @param changeType 变更类型
     * @param operatorId 操作人ID
     * @return 备份记录
     */
    DataChangeBackup backupAndExecute(String ticketId, String databaseId, String databaseName, String sql, String changeType, String operatorId) throws Exception;

    /**
     * 备份数据并执行SQL变更（带超时保护）
     * @param executionTimeoutSeconds 执行超时（秒）
     */
    DataChangeBackup backupAndExecute(String ticketId, String databaseId, String databaseName, String sql, String changeType, String operatorId, int executionTimeoutSeconds) throws Exception;

    /**
     * 回滚数据
     * @param backupId 备份记录ID
     * @param operatorId 操作人ID
     * @return 是否成功
     */
    boolean rollback(String backupId, String operatorId) throws Exception;
}