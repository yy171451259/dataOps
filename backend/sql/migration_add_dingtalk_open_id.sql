-- ============================================
-- 数据库迁移脚本：添加钉钉 openId 字段
-- ============================================

ALTER TABLE sys_user
ADD COLUMN dingtalk_open_id VARCHAR(100) DEFAULT NULL COMMENT '钉钉开放ID（openId，新版API使用）'
AFTER dingtalk_user_id;
