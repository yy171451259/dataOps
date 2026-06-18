-- =============================================
-- DataOps DMS 数据库初始化脚本
-- 数据库: dataops_dms
-- 创建时间: 2024-06-11
-- =============================================

-- 确保使用正确的数据库
USE dataops_dms;

-- =============================================
-- 1. 基础表结构
-- =============================================

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `username` VARCHAR(64) NOT NULL COMMENT '用户名',
    `password` VARCHAR(256) NOT NULL COMMENT '密码',
    `nickname` VARCHAR(64) COMMENT '昵称',
    `email` VARCHAR(128) COMMENT '邮箱',
    `phone` VARCHAR(32) COMMENT '手机号',
    `avatar` VARCHAR(512) COMMENT '头像URL',
    `role_id` VARCHAR(64) COMMENT '角色ID',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0禁用, 1启用',
    `create_time` DATETIME COMMENT '创建时间',
    `update_time` DATETIME COMMENT '更新时间',
    `create_by` VARCHAR(64) COMMENT '创建人',
    `update_by` VARCHAR(64) COMMENT '更新人',
    `deleted` TINYINT DEFAULT 0 COMMENT '删除标记: 0未删除, 1已删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- 角色表
CREATE TABLE IF NOT EXISTS `role` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `name` VARCHAR(64) NOT NULL COMMENT '角色名称',
    `code` VARCHAR(64) NOT NULL COMMENT '角色编码',
    `description` VARCHAR(256) COMMENT '角色描述',
    `permissions` TEXT COMMENT '权限列表JSON',
    `create_time` DATETIME COMMENT '创建时间',
    `update_time` DATETIME COMMENT '更新时间',
    `create_by` VARCHAR(64) COMMENT '创建人',
    `update_by` VARCHAR(64) COMMENT '更新人',
    `deleted` TINYINT DEFAULT 0 COMMENT '删除标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色表';

-- 数据库实例表
CREATE TABLE IF NOT EXISTS `database_instance` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `name` VARCHAR(128) NOT NULL COMMENT '实例名称',
    `db_type` VARCHAR(32) DEFAULT 'mysql' COMMENT '数据库类型: mysql, postgresql, oracle',
    `host` VARCHAR(256) NOT NULL COMMENT '主机地址',
    `port` INT COMMENT '端口',
    `default_schema_name` VARCHAR(128) COMMENT '默认Schema名',
    `username` VARCHAR(128) NOT NULL COMMENT '用户名',
    `password` VARCHAR(256) NOT NULL COMMENT '密码',
    `description` VARCHAR(512) COMMENT '描述',
    `environment` VARCHAR(32) COMMENT '环境: dev, test, prod',
    `status` TINYINT DEFAULT 1 COMMENT '状态: 0离线, 1在线',
    `create_time` DATETIME COMMENT '创建时间',
    `update_time` DATETIME COMMENT '更新时间',
    `create_by` VARCHAR(64) COMMENT '创建人',
    `update_by` VARCHAR(64) COMMENT '更新人',
    `deleted` TINYINT DEFAULT 0 COMMENT '删除标记',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据库实例表';

-- =============================================
-- 2. 工单相关表
-- =============================================

-- 工单表
CREATE TABLE IF NOT EXISTS `ticket` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `type` VARCHAR(32) DEFAULT 'data_change' COMMENT '工单类型',
    `title` VARCHAR(256) NOT NULL COMMENT '工单标题',
    `description` TEXT COMMENT '工单描述',
    `status` VARCHAR(32) DEFAULT 'pending' COMMENT '状态: pending,approving,approved,rejected,cancelled,executing,done,failed,rolled_back',
    `priority` VARCHAR(32) DEFAULT 'normal' COMMENT '优先级: low,normal,high,urgent',
    `creator_id` VARCHAR(64) COMMENT '创建人ID',
    `current_approver_id` VARCHAR(64) COMMENT '当前审批人ID',
    `instance_id` VARCHAR(64) COMMENT '实例ID',
    `schema_name` VARCHAR(128) COMMENT '目标Schema名',
    `sql_content` TEXT COMMENT 'SQL内容',
    `change_type` VARCHAR(32) COMMENT '变更类型: INSERT,UPDATE,DELETE',
    `use_online_ddl` TINYINT DEFAULT 0 COMMENT '是否使用无锁DDL',
    `online_ddl_strategy` VARCHAR(32) COMMENT '无锁DDL策略: mysql_online, pt_osc, gh_ost',
    `ddl_progress` INT DEFAULT 0 COMMENT 'DDL执行进度(0-100)',
    `use_lock_free_dml` TINYINT DEFAULT 0 COMMENT '是否使用无锁DML',
    `dml_batch_size` INT DEFAULT 1000 COMMENT 'DML批次大小',
    `dml_batch_interval` INT DEFAULT 100 COMMENT 'DML批次间隔(ms)',
    `dml_batch_count` INT DEFAULT 0 COMMENT 'DML已处理批次',
    `dml_total_affected` BIGINT DEFAULT 0 COMMENT 'DML累计影响行数',
    `execute_time` DATETIME COMMENT '执行时间',
    `rollback_time` DATETIME COMMENT '回滚时间',
    `error_msg` TEXT COMMENT '错误信息',
    `content` TEXT COMMENT '扩展内容JSON',
    `create_time` DATETIME COMMENT '创建时间',
    `update_time` DATETIME COMMENT '更新时间',
    `create_by` VARCHAR(64) COMMENT '创建人',
    `update_by` VARCHAR(64) COMMENT '更新人',
    `deleted` TINYINT DEFAULT 0 COMMENT '删除标记',
    PRIMARY KEY (`id`),
    KEY `idx_creator_id` (`creator_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单表';

-- 工单审批记录表
CREATE TABLE IF NOT EXISTS `ticket_approval` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `ticket_id` VARCHAR(64) NOT NULL COMMENT '工单ID',
    `approver_id` VARCHAR(64) NOT NULL COMMENT '审批人ID',
    `status` VARCHAR(32) DEFAULT 'pending' COMMENT '审批状态: approved, rejected',
    `comment` VARCHAR(1024) COMMENT '审批意见',
    `approved_at` DATETIME COMMENT '审批时间',
    `create_time` DATETIME COMMENT '创建时间',
    `update_time` DATETIME COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '删除标记',
    PRIMARY KEY (`id`),
    KEY `idx_ticket_id` (`ticket_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工单审批记录表';

-- 数据变更备份表
CREATE TABLE IF NOT EXISTS `data_change_backup` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `ticket_id` VARCHAR(64) COMMENT '关联工单ID',
    `database_id` VARCHAR(64) COMMENT '数据库实例ID',
    `table_name` VARCHAR(256) COMMENT '表名',
    `change_type` VARCHAR(32) COMMENT '变更类型: INSERT,UPDATE,DELETE',
    `original_sql` TEXT COMMENT '原始SQL',
    `rollback_sql` TEXT COMMENT '回滚SQL',
    `backup_data` LONGTEXT COMMENT '备份数据JSON',
    `status` VARCHAR(32) DEFAULT 'normal' COMMENT '状态: normal,rolled_back',
    `rollback_time` DATETIME COMMENT '回滚时间',
    `rollback_by` VARCHAR(64) COMMENT '回滚操作人ID',
    `create_time` DATETIME COMMENT '创建时间',
    `update_time` DATETIME COMMENT '更新时间',
    `create_by` VARCHAR(64) COMMENT '创建人',
    `update_by` VARCHAR(64) COMMENT '更新人',
    `deleted` TINYINT DEFAULT 0 COMMENT '删除标记',
    PRIMARY KEY (`id`),
    KEY `idx_ticket_id` (`ticket_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据变更备份表';

-- =============================================
-- 3. SQL相关表
-- =============================================

-- SQL查询历史表
CREATE TABLE IF NOT EXISTS `sql_query_history` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `database_id` VARCHAR(64) COMMENT '数据库ID',
    `sql_content` TEXT NOT NULL COMMENT 'SQL内容',
    `execute_result` TEXT COMMENT '执行结果',
    `affect_rows` INT COMMENT '影响行数',
    `execution_time` BIGINT COMMENT '执行耗时(ms)',
    `operator_id` VARCHAR(64) COMMENT '操作人ID',
    `create_time` DATETIME COMMENT '创建时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '删除标记',
    PRIMARY KEY (`id`),
    KEY `idx_operator_id` (`operator_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='SQL查询历史表';


-- =============================================
-- 4. 审计日志表
-- =============================================

CREATE TABLE IF NOT EXISTS `audit_log` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `operation` VARCHAR(128) NOT NULL COMMENT '操作类型',
    `module` VARCHAR(64) COMMENT '模块',
    `description` VARCHAR(512) COMMENT '操作描述',
    `request_method` VARCHAR(16) COMMENT '请求方法: GET,POST等',
    `request_url` VARCHAR(512) COMMENT '请求URL',
    `request_params` TEXT COMMENT '请求参数',
    `response_result` TEXT COMMENT '响应结果',
    `ip_address` VARCHAR(64) COMMENT 'IP地址',
    `user_agent` VARCHAR(512) COMMENT '用户代理',
    `operator_id` VARCHAR(64) COMMENT '操作人ID',
    `operator_name` VARCHAR(128) COMMENT '操作人名称',
    `execution_time` BIGINT COMMENT '执行耗时(ms)',
    `risk_level` VARCHAR(32) DEFAULT 'low' COMMENT '风险等级: low,medium,high',
    `create_time` DATETIME COMMENT '创建时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '删除标记',
    PRIMARY KEY (`id`),
    KEY `idx_operator_id` (`operator_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计日志表';

-- =============================================
-- 5. 数据脱敏规则表
-- =============================================

CREATE TABLE IF NOT EXISTS `data_masking_rule` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `name` VARCHAR(256) NOT NULL COMMENT '规则名称',
    `database_id` VARCHAR(64) COMMENT '数据库ID',
    `table_name` VARCHAR(256) NOT NULL COMMENT '表名',
    `column_name` VARCHAR(256) NOT NULL COMMENT '列名',
    `masking_type` VARCHAR(64) NOT NULL COMMENT '脱敏类型: phone,email,id_card,name等',
    `masking_algorithm` VARCHAR(64) COMMENT '脱敏算法',
    `is_enabled` TINYINT DEFAULT 1 COMMENT '是否启用',
    `description` VARCHAR(512) COMMENT '描述',
    `create_time` DATETIME COMMENT '创建时间',
    `update_time` DATETIME COMMENT '更新时间',
    `create_by` VARCHAR(64) COMMENT '创建人',
    `update_by` VARCHAR(64) COMMENT '更新人',
    `deleted` TINYINT DEFAULT 0 COMMENT '删除标记',
    PRIMARY KEY (`id`),
    KEY `idx_table_column` (`table_name`, `column_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据脱敏规则表';

-- =============================================
-- 6. 数据质量相关表
-- =============================================

CREATE TABLE IF NOT EXISTS `data_quality_rule` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `name` VARCHAR(256) NOT NULL COMMENT '规则名称',
    `rule_type` VARCHAR(64) COMMENT '规则类型: null_check,duplicate_check,format_check,range_check等',
    `database_id` VARCHAR(64) COMMENT '数据库ID',
    `table_name` VARCHAR(256) COMMENT '表名',
    `column_name` VARCHAR(256) COMMENT '列名',
    `check_sql` TEXT COMMENT '检查SQL',
    `expected_result` VARCHAR(512) COMMENT '预期结果',
    `severity` VARCHAR(32) DEFAULT 'medium' COMMENT '严重程度: low,medium,high,critical',
    `is_enabled` TINYINT DEFAULT 1 COMMENT '是否启用',
    `schedule_cron` VARCHAR(128) COMMENT '调度表达式',
    `description` VARCHAR(512) COMMENT '描述',
    `create_time` DATETIME COMMENT '创建时间',
    `update_time` DATETIME COMMENT '更新时间',
    `create_by` VARCHAR(64) COMMENT '创建人',
    `update_by` VARCHAR(64) COMMENT '更新人',
    `deleted` TINYINT DEFAULT 0 COMMENT '删除标记',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据质量规则表';

CREATE TABLE IF NOT EXISTS `data_quality_result` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `rule_id` VARCHAR(64) NOT NULL COMMENT '规则ID',
    `check_time` DATETIME COMMENT '检查时间',
    `check_result` VARCHAR(32) COMMENT '检查结果: pass,fail,error',
    `actual_value` VARCHAR(512) COMMENT '实际值',
    `expected_value` VARCHAR(512) COMMENT '期望值',
    `error_message` TEXT COMMENT '错误信息',
    `detail_data` LONGTEXT COMMENT '详细数据JSON',
    `operator_id` VARCHAR(64) COMMENT '操作人ID',
    `create_time` DATETIME COMMENT '创建时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '删除标记',
    PRIMARY KEY (`id`),
    KEY `idx_rule_id` (`rule_id`),
    KEY `idx_check_time` (`check_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='数据质量检查结果表';

-- =============================================
-- 8. 通知相关表
-- =============================================

CREATE TABLE IF NOT EXISTS `notification_config` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `name` VARCHAR(256) NOT NULL COMMENT '配置名称',
    `notify_type` VARCHAR(64) NOT NULL COMMENT '通知类型: email,webhook,sms',
    `notify_event` VARCHAR(128) COMMENT '通知事件: ticket_create,ticket_approve,ticket_execute等',
    `config_data` TEXT COMMENT '配置JSON',
    `is_enabled` TINYINT DEFAULT 1 COMMENT '是否启用',
    `create_time` DATETIME COMMENT '创建时间',
    `update_time` DATETIME COMMENT '更新时间',
    `create_by` VARCHAR(64) COMMENT '创建人',
    `update_by` VARCHAR(64) COMMENT '更新人',
    `deleted` TINYINT DEFAULT 0 COMMENT '删除标记',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知配置表';

CREATE TABLE IF NOT EXISTS `notification_log` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `config_id` VARCHAR(64) COMMENT '配置ID',
    `notify_type` VARCHAR(64) COMMENT '通知类型',
    `title` VARCHAR(512) COMMENT '标题',
    `content` TEXT COMMENT '内容',
    `targets` TEXT COMMENT '接收目标JSON',
    `status` VARCHAR(32) COMMENT '状态: success,failed',
    `error_msg` TEXT COMMENT '错误信息',
    `retry_count` INT DEFAULT 0 COMMENT '重试次数',
    `send_time` DATETIME COMMENT '发送时间',
    `create_time` DATETIME COMMENT '创建时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '删除标记',
    PRIMARY KEY (`id`),
    KEY `idx_send_time` (`send_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知日志表';

-- =============================================
-- 初始化数据
-- =============================================

-- 插入默认管理员用户 (密码: admin123)
INSERT IGNORE INTO `user` (`id`, `username`, `password`, `nickname`, `email`, `status`, `role_id`, `create_time`) VALUES
('user_admin', 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5E', '系统管理员', 'admin@dataops.com', 1, 'role_admin', NOW());

-- 插入默认角色
INSERT IGNORE INTO `role` (`id`, `name`, `code`, `description`, `create_time`) VALUES
('role_admin', '超级管理员', 'admin', '拥有所有权限', NOW()),
('role_dba', 'DBA', 'dba', '数据库管理员', NOW()),
('role_developer', '开发人员', 'developer', '普通开发人员', NOW());

-- 插入示例数据库配置 (连接到自身)
INSERT IGNORE INTO `database_instance` (`id`, `name`, `db_type`, `host`, `port`, `db_name`, `username`, `password`, `environment`, `status`, `create_time`) VALUES
('db_local', '本地测试库', 'mysql', '192.168.16.163', 3325, 'dataops_dms', 'devuser', 'Dev#2026$', 'dev', 1, NOW());

-- =============================================
-- Flowable 工作流表会由 Flowable 自动创建
-- =============================================

SELECT 'Database initialization completed successfully!' AS result;

-- =============================================
-- Schema 补丁（对已初始化的数据库执行，已存在则忽略报错）
-- =============================================

-- ticket 表补充 DDL/DML 相关列
ALTER TABLE `ticket` ADD COLUMN `use_online_ddl` TINYINT DEFAULT 0 COMMENT '是否使用无锁DDL';
ALTER TABLE `ticket` ADD COLUMN `online_ddl_strategy` VARCHAR(32) COMMENT '无锁DDL策略';
ALTER TABLE `ticket` ADD COLUMN `ddl_progress` INT DEFAULT 0 COMMENT 'DDL执行进度(0-100)';
ALTER TABLE `ticket` ADD COLUMN `use_lock_free_dml` TINYINT DEFAULT 0 COMMENT '是否使用无锁DML';
ALTER TABLE `ticket` ADD COLUMN `dml_batch_size` INT DEFAULT 1000 COMMENT 'DML批次大小';
ALTER TABLE `ticket` ADD COLUMN `dml_batch_interval` INT DEFAULT 100 COMMENT 'DML批次间隔(ms)';
ALTER TABLE `ticket` ADD COLUMN `dml_batch_count` INT DEFAULT 0 COMMENT 'DML已处理批次';
ALTER TABLE `ticket` ADD COLUMN `dml_total_affected` BIGINT DEFAULT 0 COMMENT 'DML累计影响行数';

-- ticket_approval 表修复列名
ALTER TABLE `ticket_approval` CHANGE COLUMN `approved` `status` VARCHAR(32) DEFAULT 'pending' COMMENT '审批状态: approved, rejected';
ALTER TABLE `ticket_approval` CHANGE COLUMN `approve_time` `approved_at` DATETIME COMMENT '审批时间';