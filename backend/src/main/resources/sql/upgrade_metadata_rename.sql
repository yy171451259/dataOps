-- =============================================
-- 元数据表字段统一命名: database_id → instance_id
-- 同时补充缺失的 schema_name 列
-- 执行前请确认数据库: dataops_dms
-- =============================================

USE dataops_dms;

-- 1) metadata_table: 重命名 database_id → instance_id
ALTER TABLE `metadata_table` 
    CHANGE COLUMN `database_id` `instance_id` VARCHAR(64) COMMENT '实例ID';

-- 2) metadata_table: 添加 schema_name 列
ALTER TABLE `metadata_table` 
    ADD COLUMN `schema_name` VARCHAR(128) COMMENT 'Schema名' AFTER `instance_id`;

-- 3) metadata_table: 删旧索引，建新唯一索引（含 schema_name）
ALTER TABLE `metadata_table` DROP INDEX `uk_db_table`;
ALTER TABLE `metadata_table` 
    ADD UNIQUE INDEX `uk_instance_schema_table` (`instance_id`, `schema_name`, `table_name`);

-- 4) metadata_column: 重命名 database_id → instance_id
ALTER TABLE `metadata_column` 
    CHANGE COLUMN `database_id` `instance_id` VARCHAR(64) COMMENT '实例ID';

SELECT '元数据表字段统一命名完成！' AS result;
