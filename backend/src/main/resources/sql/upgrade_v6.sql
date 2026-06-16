-- =============================================
-- DataOps DMS 数据库升级脚本 V6
-- 新增：DDL项目工单表 + 表变更明细表
-- 对标阿里云DMS的项目工单驱动设计
-- =============================================

USE dataops_dms;

-- =============================================
-- 1. ddl_project 项目工单主表
-- =============================================
CREATE TABLE IF NOT EXISTS `ddl_project` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `project_name` VARCHAR(256) NOT NULL COMMENT '项目名称',
    `business_background` TEXT COMMENT '项目背景/业务描述',
    `base_database_id` VARCHAR(64) COMMENT '基准库实例ID',
    `base_database_name` VARCHAR(128) COMMENT '基准库实例名',
    `base_schema_name` VARCHAR(128) COMMENT '基准Schema名',
    `status` VARCHAR(32) DEFAULT 'DESIGNING' COMMENT '状态: DESIGNING,DEV_EXECUTING,DEV_DONE,INTEGRATION_PENDING,INTEGRATION_DONE,STAGING_PENDING,STAGING_DONE,PRODUCTION_PENDING,PRODUCTION_DONE,PUBLISHED,CLOSED',
    `current_stage` VARCHAR(32) DEFAULT 'CREATE' COMMENT '当前阶段: CREATE,DESIGN,DEV,TEST,INTEGRATION,STAGING,PRODUCTION,FINISH',
    `owner` VARCHAR(64) COMMENT '负责人',
    `related_persons` TEXT COMMENT '变更相关人(JSON数组)',
    `security_rule` VARCHAR(64) DEFAULT 'auto' COMMENT '安全规则',
    `priority` VARCHAR(32) DEFAULT 'normal' COMMENT '优先级: low,normal,high,urgent',
    `table_count` INT DEFAULT 0 COMMENT '变更表数量',
    `closed_at` DATETIME COMMENT '关闭时间',
    `created_by` VARCHAR(64) COMMENT '创建人',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_status` (`status`),
    KEY `idx_owner` (`owner`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='DDL项目工单';

-- =============================================
-- 2. ddl_project_table 表变更明细
-- =============================================
CREATE TABLE IF NOT EXISTS `ddl_project_table` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `project_id` VARCHAR(64) NOT NULL COMMENT '所属项目工单ID',
    `table_name` VARCHAR(128) NOT NULL COMMENT '表名',
    `change_type` VARCHAR(16) NOT NULL COMMENT '变更类型: NEW, MODIFY',
    `original_ddl` LONGTEXT COMMENT '原始DDL',
    `modified_ddl` LONGTEXT COMMENT '修改后DDL',
    `change_sql` LONGTEXT COMMENT '自动生成的变更SQL',
    `version` INT DEFAULT 0 COMMENT '表版本号',
    `last_operator` VARCHAR(64) COMMENT '最后操作人',
    `last_modified_at` DATETIME COMMENT '最近修改时间',
    `env_status` TEXT COMMENT '各环境执行状态JSON',
    `env_details` LONGTEXT COMMENT '各环境执行详情JSON',
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_project_id` (`project_id`),
    KEY `idx_table_name` (`table_name`),
    UNIQUE KEY `uk_project_table` (`project_id`, `table_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='DDL项目工单-表变更明细';

-- =============================================
-- 完成
-- =============================================
SELECT 'V6 DDL项目工单升级完成！新增: ddl_project, ddl_project_table' AS result;
