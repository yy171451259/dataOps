-- ============================================
-- 数据库升级脚本 - 添加无锁变更字段
-- ============================================

-- 添加目标数据库名字段
ALTER TABLE ticket ADD COLUMN database_name VARCHAR(128) COMMENT '目标数据库名(schema)' AFTER database_id;

-- 添加 DDL 无锁变更字段（Online DDL）
ALTER TABLE ticket ADD COLUMN use_online_ddl BOOLEAN DEFAULT FALSE COMMENT '是否使用无锁DDL' AFTER change_type;
ALTER TABLE ticket ADD COLUMN online_ddl_strategy VARCHAR(32) DEFAULT 'mysql_online' COMMENT '无锁DDL策略' AFTER use_online_ddl;
ALTER TABLE ticket ADD COLUMN ddl_progress INT DEFAULT 0 COMMENT 'DDL执行进度(0-100)' AFTER online_ddl_strategy;

-- 添加 DML 无锁变更字段（Lock-Free DML）
ALTER TABLE ticket ADD COLUMN use_lock_free_dml BOOLEAN DEFAULT FALSE COMMENT '是否使用无锁DML' AFTER ddl_progress;
ALTER TABLE ticket ADD COLUMN dml_batch_size INT DEFAULT 1000 COMMENT 'DML批次大小' AFTER use_lock_free_dml;
ALTER TABLE ticket ADD COLUMN dml_batch_interval INT DEFAULT 100 COMMENT 'DML批次间隔毫秒' AFTER dml_batch_size;
ALTER TABLE ticket ADD COLUMN dml_batch_count INT DEFAULT 0 COMMENT 'DML执行批次计数' AFTER dml_batch_interval;
ALTER TABLE ticket ADD COLUMN dml_total_affected BIGINT DEFAULT 0 COMMENT 'DML累计影响行数' AFTER dml_batch_count;