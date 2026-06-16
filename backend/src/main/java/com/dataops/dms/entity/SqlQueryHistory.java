package com.dataops.dms.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * SQL查询历史实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sql_query_history")
public class SqlQueryHistory extends BaseEntity {

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 实例ID
     */
    private String instanceId;

    /**
     * 数据库名称
     */
    private String schemaName;

    /**
     * SQL语句
     */
    private String sqlText;

    /**
     * 影响行数
     */
    private Integer affectRows;

    /**
     * 执行时间（毫秒）
     */
    private Integer executionTime;

    /**
     * 状态: success, failed, cancelled
     */
    private String status;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 结果集哈希
     */
    private String resultHash;

    /**
     * 执行时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime executedAt;

    /**
     * 客户端IP
     */
    private String clientIp;
}
