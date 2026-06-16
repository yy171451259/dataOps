USE dataops_dms;

-- ticket 表补充 DDL/DML 相关列
ALTER TABLE ticket ADD COLUMN use_online_ddl TINYINT DEFAULT 0 COMMENT '是否使用无锁DDL';
ALTER TABLE ticket ADD COLUMN online_ddl_strategy VARCHAR(32) COMMENT '无锁DDL策略';
ALTER TABLE ticket ADD COLUMN ddl_progress INT DEFAULT 0 COMMENT 'DDL执行进度(0-100)';
ALTER TABLE ticket ADD COLUMN use_lock_free_dml TINYINT DEFAULT 0 COMMENT '是否使用无锁DML';
ALTER TABLE ticket ADD COLUMN dml_batch_size INT DEFAULT 1000 COMMENT 'DML批次大小';
ALTER TABLE ticket ADD COLUMN dml_batch_interval INT DEFAULT 100 COMMENT 'DML批次间隔(ms)';
ALTER TABLE ticket ADD COLUMN dml_batch_count INT DEFAULT 0 COMMENT 'DML已处理批次';
ALTER TABLE ticket ADD COLUMN dml_total_affected BIGINT DEFAULT 0 COMMENT 'DML累计影响行数';

-- ticket_approval 表修复列名
ALTER TABLE ticket_approval CHANGE COLUMN approved status VARCHAR(32) DEFAULT 'pending' COMMENT '审批状态: approved, rejected';
ALTER TABLE ticket_approval CHANGE COLUMN approve_time approved_at DATETIME COMMENT '审批时间';

SELECT 'Patch applied!' AS result;
