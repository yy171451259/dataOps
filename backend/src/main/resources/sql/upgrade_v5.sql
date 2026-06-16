-- =============================================
-- DataOps DMS 数据库升级脚本 V5
-- DDL变更工作台三阶段流水线增强
-- 增加：ddl_change_task 表新字段 + 新状态值
-- =============================================

USE dataops_dms;

-- =============================================
-- 1. ddl_change_task 表扩展字段
-- =============================================
-- 新增 rollback_to_source: 标记是否为回退到来源环境而产生的任务
ALTER TABLE ddl_change_task
  ADD COLUMN rollback_to_source TINYINT(1) DEFAULT 0 COMMENT '是否为回退任务';

-- 新增回退记录字段
ALTER TABLE ddl_change_task
  ADD COLUMN rolled_back_at DATETIME COMMENT '回退时间';

ALTER TABLE ddl_change_task
  ADD COLUMN rolled_back_by VARCHAR(64) COMMENT '回退人';

-- 新增上游来源追溯字段（方便前端展示完整的变更链路）
ALTER TABLE ddl_change_task
  ADD COLUMN source_sql_content LONGTEXT COMMENT '上游环境原始SQL';

ALTER TABLE ddl_change_task
  ADD COLUMN source_executed_by VARCHAR(64) COMMENT '上游环境执行人';

ALTER TABLE ddl_change_task
  ADD COLUMN source_executed_at DATETIME COMMENT '上游环境执行时间';

-- =============================================
-- 2. 修改 status 字段允许新状态值
-- =============================================
-- 注意：如果 ddl_change_task 表的 status 字段有 CHECK 约束或 ENUM 类型，
-- 需要先修改约束。MySQL 5.x 不会对 VARCHAR 自动创建 CHECK，所以只需确认即可。
-- 新增状态值: ROLLED_BACK（已回退）, ROLLING_BACK（回退中）

-- =============================================
-- 3. 完成输出
-- =============================================
SELECT 'V5 DDL变更工作台三阶段流水线升级完成！新增: 6个字段、2个状态值' AS result;