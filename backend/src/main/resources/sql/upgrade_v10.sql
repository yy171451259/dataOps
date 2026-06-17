-- =============================================
-- DataOps DMS 数据库升级脚本 V10
-- 钉钉集成：
--   1. 添加钉钉用户ID字段到 sys_user 表
-- =============================================

USE dataops_dms;

-- =============================================
-- 1. 添加钉钉字段到 sys_user 表
-- =============================================
ALTER TABLE `sys_user`
ADD COLUMN `dingtalk_union_id` VARCHAR(64) DEFAULT NULL COMMENT '钉钉UnionID（跨应用唯一）' AFTER `is_admin`,
ADD COLUMN `dingtalk_user_id` VARCHAR(64) DEFAULT NULL COMMENT '钉钉用户ID' AFTER `dingtalk_union_id`;

-- =============================================
-- 2. 添加索引以提升查询性能
-- =============================================
CREATE INDEX `idx_dingtalk_union_id` ON `sys_user`(`dingtalk_union_id`);
CREATE INDEX `idx_dingtalk_user_id` ON `sys_user`(`dingtalk_user_id`);

-- =============================================
-- 完成
-- =============================================
SELECT 'V10 钉钉集成字段添加完成！已添加 dingtalk_union_id 和 dingtalk_user_id 字段' AS result;