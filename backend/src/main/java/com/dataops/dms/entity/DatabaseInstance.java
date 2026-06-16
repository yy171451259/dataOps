package com.dataops.dms.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 数据库实例实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("database_instance")
public class DatabaseInstance extends BaseEntity {

    /**
     * 实例名称
     */
    private String name;

    /**
     * 数据库类型: mysql, postgresql, oracle, sqlserver, etc.
     */
    private String dbType;

    /**
     * 主机地址
     */
    private String host;

    /**
     * 端口
     */
    private Integer port;

    /**
     * 用户名
     */
    private String username;

    /**
     * 密码（加密存储）
     */
    private String password;

    /**
     * 默认Schema名
     */
    @TableField("default_schema_name")
    private String defaultSchemaName;

    /**
     * 字符集
     */
    private String charset;

    /**
     * 版本号
     */
    private String version;

    /**
     * 环境: dev, test, prod
     */
    private String environment;

    /**
     * 标签（JSON格式）
     */
    private String tags;

    /**
     * 是否SSL连接
     */
    private Boolean isSsl;

    /**
     * 扩展配置（JSON格式）
     */
    private String config;

    /**
     * 状态: active, inactive, error
     */
    private String status;

    /**
     * 最后心跳时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastHeartbeat;
}
