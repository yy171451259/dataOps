package com.dataops.dms.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DDL项目工单 - 表变更明细
 * 一个项目工单可包含多张表的变更，每张表独立编辑DDL、独立跟踪状态
 */
@Data
@TableName("ddl_project_table")
public class DdlProjectTable {

    /**
     * 主键ID
     */
    private String id;

    /**
     * 所属项目工单ID
     */
    private String projectId;

    /**
     * 表名
     */
    private String tableName;

    /**
     * 变更类型: NEW(新建表), MODIFY(修改表)
     */
    private String changeType;

    /**
     * 原始DDL（修改表时为当前线上DDL，新建表时为空）
     */
    private String originalDdl;

    /**
     * 修改后DDL（用户编辑后的完整DDL）
     */
    private String modifiedDdl;

    /**
     * 自动生成的变更SQL（ALTER TABLE 或 CREATE TABLE）
     */
    private String changeSql;

    /**
     * 表版本号（每次执行到基准库后+1）
     */
    private Integer version;

    /**
     * 最后操作人
     */
    private String lastOperator;

    /**
     * 最近修改时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime lastModifiedAt;

    /**
     * 各环境执行状态JSON:
     * {"dev":"SUCCESS","test":"PENDING","integration":"NONE","staging":"NONE","production":"NONE"}
     */
    private String envStatus;

    /**
     * 各环境执行详情JSON（执行人/执行时间/耗时等）
     */
    private String envDetails;

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updatedAt;
}
