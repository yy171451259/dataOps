-- ============================================
-- upgrade_v12: 数据变更工单增加执行方式字段
-- ============================================
ALTER TABLE `ticket`
ADD COLUMN `exec_mode` VARCHAR(16) DEFAULT 'auto' COMMENT '执行方式: auto(审批通过后自动执行), manual(审批通过后提交者执行)' AFTER `approved_level`;
