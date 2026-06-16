-- =============================================
-- DataOps DMS 数据库升级脚本 V3
-- 对标阿里云DMS：数据变更功能完善
-- 执行前请确认数据库: dataops_dms
-- =============================================

USE dataops_dms;

-- ticket 表新增列（数据变更影响分析 + 执行控制）
ALTER TABLE `ticket` ADD COLUMN IF NOT EXISTS `estimate_affected_rows` INT COMMENT '预估影响行数（EXPLAIN估算）';
ALTER TABLE `ticket` ADD COLUMN IF NOT EXISTS `execution_timeout_seconds` INT DEFAULT 600 COMMENT '执行超时时间（秒）';
ALTER TABLE `ticket` ADD COLUMN IF NOT EXISTS `dml_status` VARCHAR(32) COMMENT '无锁DML执行状态: running/paused/stopping/stopped/completed';
ALTER TABLE `ticket` ADD COLUMN IF NOT EXISTS `dml_total_batches` INT DEFAULT 0 COMMENT '无锁DML预计总批次数';
ALTER TABLE `ticket` ADD COLUMN IF NOT EXISTS `dml_progress_percent` INT DEFAULT 0 COMMENT '无锁DML执行进度百分比(0-100)';
ALTER TABLE `ticket` ADD COLUMN IF NOT EXISTS `approval_deadline` DATETIME COMMENT '审批截止时间（超时自动拒绝）';
ALTER TABLE `ticket` ADD COLUMN IF NOT EXISTS `approved_level` INT DEFAULT 0 COMMENT '已审批级数';

SELECT 'V3 升级完成！新增字段: estimate_affected_rows, execution_timeout_seconds, dml_status, dml_total_batches, dml_progress_percent, approval_deadline, approved_level' AS result;
