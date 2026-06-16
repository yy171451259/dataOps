package com.dataops.dms.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * 数据变更备份实体
 * 在执行数据变更前自动备份原始数据，用于回滚
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("data_change_backup")
public class DataChangeBackup extends BaseEntity {

    /**
     * 关联工单ID
     */
    private String ticketId;

    /**
     * 实例ID
     */
    private String instanceId;

    /**
     * 表名
     */
    private String tableName;

    /**
     * 变更类型: INSERT, UPDATE, DELETE
     */
    private String changeType;

    /**
     * 原始SQL
     */
    private String originalSql;

    /**
     * 回滚SQL
     */
    private String rollbackSql;

    /**
     * 备份数据JSON（大字段）
     */
    private String backupData;

    /**
     * 状态: normal, rolled_back
     */
    private String status;

    /**
     * 回滚时间
     */
    private Date rollbackTime;

    /**
     * 回滚操作人ID
     */
    private String rollbackBy;
}