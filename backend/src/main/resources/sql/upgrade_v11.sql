-- =============================================
-- DataOps DMS 数据库升级脚本 V11
-- 多审批人支持：
--   1. 添加 approver_ids 和 approver_names 字段到 sys_user_permission_request 表
--   2. 添加 approver_ids 和 approver_names 字段到 ticket 表
-- =============================================

USE dataops_dms;

-- =============================================
-- 1. 添加多审批人字段到 sys_user_permission_request 表
-- =============================================
ALTER TABLE `sys_user_permission_request`
ADD COLUMN `approver_ids` VARCHAR(500) DEFAULT NULL COMMENT '所有审批人ID（逗号分隔）' AFTER `approver_name`,
ADD COLUMN `approver_names` VARCHAR(500) DEFAULT NULL COMMENT '所有审批人姓名（逗号分隔）' AFTER `approver_ids`;

-- =============================================
-- 2. 添加多审批人字段到 ticket 表
-- =============================================
ALTER TABLE `ticket`
ADD COLUMN `approver_ids` VARCHAR(500) DEFAULT NULL COMMENT '所有审批人ID（逗号分隔）' AFTER `current_approver_id`,
ADD COLUMN `approver_names` VARCHAR(500) DEFAULT NULL COMMENT '所有审批人姓名（逗号分隔）' AFTER `approver_ids`;

-- =============================================
-- 3. 添加索引以提升查询性能
-- =============================================
CREATE INDEX `idx_approver_ids` ON `sys_user_permission_request`(`approver_ids`);
CREATE INDEX `idx_ticket_approver_ids` ON `ticket`(`approver_ids`);

-- =============================================
-- 完成
-- =============================================
SELECT 'V11 多审批人字段添加完成！' AS result;
