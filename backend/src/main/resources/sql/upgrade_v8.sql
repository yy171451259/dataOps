-- =============================================
-- DataOps DMS 数据库升级脚本 V8
-- 术语统一: database → Schema / Instance
-- 将 L2（库级）概念的字段名统一为 schema_name，
-- 将 L1（实例级）的 database_id 改为 instance_id
-- =============================================

USE dataops_dms;

-- =============================================
-- 1. database_instance 表: db_name → default_schema_name
-- =============================================
ALTER TABLE `database_instance` CHANGE `db_name` `default_schema_name` VARCHAR(128)  NULL COMMENT '默认Schema名';

-- =============================================
-- 2. ticket 表: database_id → instance_id, database_name → schema_name
-- =============================================
UPDATE `ticket` SET `database_id` = '' WHERE `database_id` IS NULL;
ALTER TABLE `ticket` CHANGE `database_id` `instance_id` VARCHAR(64) COMMENT '实例ID';
UPDATE `ticket` SET `database_name` = '' WHERE `database_name` IS NULL;
ALTER TABLE `ticket` CHANGE `database_name` `schema_name` VARCHAR(128) COMMENT '目标Schema名';

-- =============================================
-- 3. ddl_change_task 表: database_instance_id → instance_id,
--    database_instance_name → instance_name, database_name → schema_name
-- =============================================
UPDATE `ddl_change_task` SET `database_instance_id` = '' WHERE `database_instance_id` IS NULL;
ALTER TABLE `ddl_change_task` CHANGE `database_instance_id` `instance_id` VARCHAR(64) COMMENT '目标实例ID';
UPDATE `ddl_change_task` SET `database_instance_name` = '' WHERE `database_instance_name` IS NULL;
ALTER TABLE `ddl_change_task` CHANGE `database_instance_name` `instance_name` VARCHAR(128) COMMENT '目标实例名称';
UPDATE `ddl_change_task` SET `database_name` = '' WHERE `database_name` IS NULL;
ALTER TABLE `ddl_change_task` CHANGE `database_name` `schema_name` VARCHAR(128) COMMENT '目标Schema名';

-- =============================================
-- 4. ddl_project 表: base_database_id → base_instance_id,
--    base_database_name → base_instance_name
-- =============================================
UPDATE `ddl_project` SET `base_database_id` = '' WHERE `base_database_id` IS NULL;
ALTER TABLE `ddl_project` CHANGE `base_database_id` `base_instance_id` VARCHAR(64) COMMENT '基准实例ID';
UPDATE `ddl_project` SET `base_database_name` = '' WHERE `base_database_name` IS NULL;
ALTER TABLE `ddl_project` CHANGE `base_database_name` `base_instance_name` VARCHAR(128) COMMENT '基准实例名称';

-- =============================================
-- 5. data_change_backup 表: database_id → instance_id
-- =============================================
UPDATE `data_change_backup` SET `database_id` = '' WHERE `database_id` IS NULL;
ALTER TABLE `data_change_backup` CHANGE `database_id` `instance_id` VARCHAR(64) COMMENT '实例ID';

-- =============================================
-- 6. 更新 sys_permission 表中的权限编码和路径
--    database:view → schema:view, database:manage → schema:manage
--    /api/v1/databases/** → /api/v1/instances/**
-- =============================================
UPDATE `sys_permission` SET `code` = 'schema:view', `name` = 'Schema查看', `resource` = '/api/v1/instances/**' WHERE `code` = 'database:view';
UPDATE `sys_permission` SET `code` = 'schema:manage', `name` = 'Schema管理', `resource` = '/api/v1/instances/**' WHERE `code` = 'database:manage';

-- =============================================
-- 完成
-- =============================================
SELECT 'V8 术语统一升级完成！已将 database→Schema/Instance 重命名' AS result;