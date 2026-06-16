-- =============================================
-- DataOps DMS 数据库升级脚本 V4
-- 权限控制细化：RBAC中间表 + 审计日志
-- 执行前请确认数据库: dataops_dms
-- =============================================

USE dataops_dms;

-- =============================================
-- 1. 创建 sys_user 表（如果不存在，替代旧的 user 表）
-- =============================================
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `username` VARCHAR(64) NOT NULL COMMENT '用户名',
    `email` VARCHAR(128) COMMENT '邮箱',
    `password_hash` VARCHAR(256) NOT NULL COMMENT '密码哈希',
    `nickname` VARCHAR(64) COMMENT '昵称',
    `avatar` VARCHAR(512) COMMENT '头像URL',
    `is_active` TINYINT DEFAULT 1 COMMENT '是否启用',
    `is_admin` TINYINT DEFAULT 0 COMMENT '是否管理员',
    `create_time` DATETIME COMMENT '创建时间',
    `update_time` DATETIME COMMENT '更新时间',
    `create_by` VARCHAR(64) COMMENT '创建人',
    `update_by` VARCHAR(64) COMMENT '更新人',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- =============================================
-- 2. 创建 sys_role 表（如果不存在）
-- =============================================
CREATE TABLE IF NOT EXISTS `sys_role` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `name` VARCHAR(64) NOT NULL COMMENT '角色名称',
    `code` VARCHAR(64) NOT NULL COMMENT '角色编码',
    `description` VARCHAR(256) COMMENT '角色描述',
    `is_system` TINYINT DEFAULT 0 COMMENT '是否系统内置',
    `create_time` DATETIME COMMENT '创建时间',
    `update_time` DATETIME COMMENT '更新时间',
    `create_by` VARCHAR(64) COMMENT '创建人',
    `update_by` VARCHAR(64) COMMENT '更新人',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色表';

-- =============================================
-- 3. 创建 sys_permission 表（功能权限 + 资源权限）
-- =============================================
CREATE TABLE IF NOT EXISTS `sys_permission` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `name` VARCHAR(128) COMMENT '权限名称',
    `code` VARCHAR(128) COMMENT '权限编码: sql:query, database:manage, etc.',
    `resource` VARCHAR(256) COMMENT '资源路径',
    `action` VARCHAR(64) COMMENT '操作: read,write,export,ddl,*, etc.',
    `description` VARCHAR(256) COMMENT '权限描述',
    -- 细粒度资源权限字段
    `user_id` VARCHAR(64) COMMENT '用户ID（资源级权限被授权用户）',
    `resource_type` VARCHAR(32) COMMENT '资源类型: database, table, column, instance',
    `resource_id` VARCHAR(128) COMMENT '资源ID（数据库实例ID/表ID等）',
    `resource_name` VARCHAR(255) COMMENT '资源名称',
    `field_list` TEXT COMMENT '字段级权限（逗号分隔，null=所有字段）',
    `expire_time` DATETIME COMMENT '权限过期时间（null=永不过期）',
    `create_time` DATETIME COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_resource` (`resource_type`, `resource_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限表（功能权限+资源权限）';

-- =============================================
-- 4. 创建 sys_user_role 中间表（用户-角色多对多）
-- =============================================
CREATE TABLE IF NOT EXISTS `sys_user_role` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `user_id` VARCHAR(64) NOT NULL COMMENT '用户ID',
    `role_id` VARCHAR(64) NOT NULL COMMENT '角色ID',
    `granted_by` VARCHAR(64) COMMENT '授予人ID',
    `granted_at` DATETIME COMMENT '授予时间',
    `expire_time` DATETIME COMMENT '角色有效期（null=永久）',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';

-- =============================================
-- 5. 创建 sys_role_permission 中间表（角色-权限多对多）
-- =============================================
CREATE TABLE IF NOT EXISTS `sys_role_permission` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `role_id` VARCHAR(64) NOT NULL COMMENT '角色ID',
    `permission_id` VARCHAR(64) NOT NULL COMMENT '权限ID',
    `granted_by` VARCHAR(64) COMMENT '授予人ID',
    `granted_at` DATETIME COMMENT '授予时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_perm` (`role_id`, `permission_id`),
    KEY `idx_role_id` (`role_id`),
    KEY `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';

-- =============================================
-- 6. 权限审计日志表
-- =============================================
CREATE TABLE IF NOT EXISTS `sys_permission_audit_log` (
    `id` VARCHAR(64) NOT NULL COMMENT '主键ID',
    `operation` VARCHAR(32) NOT NULL COMMENT '操作: GRANT, REVOKE, ROLE_ASSIGN, ROLE_REVOKE, CLEANUP',
    `target_type` VARCHAR(32) COMMENT '目标类型: USER, ROLE',
    `target_id` VARCHAR(64) COMMENT '目标ID（用户ID或角色ID）',
    `resource_type` VARCHAR(32) COMMENT '资源类型',
    `resource_id` VARCHAR(128) COMMENT '资源ID',
    `action` VARCHAR(64) COMMENT '授予的操作',
    `detail` TEXT COMMENT '操作详情JSON',
    `operator_id` VARCHAR(64) COMMENT '操作人ID',
    `operator_name` VARCHAR(128) COMMENT '操作人名称',
    `ip_address` VARCHAR(64) COMMENT 'IP地址',
    `create_time` DATETIME COMMENT '操作时间',
    PRIMARY KEY (`id`),
    KEY `idx_target` (`target_type`, `target_id`),
    KEY `idx_operator` (`operator_id`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限操作审计日志';

-- =============================================
-- 7. 初始化默认角色数据（迁移到 sys_role）
-- =============================================
INSERT IGNORE INTO `sys_role` (`id`, `name`, `code`, `description`, `is_system`, `create_time`) VALUES
('role_admin', '超级管理员', 'admin', '拥有所有权限', 1, NOW()),
('role_dba', 'DBA', 'dba', '数据库管理员，负责数据库运维和审批', 1, NOW()),
('role_developer', '开发人员', 'developer', '普通开发人员，可提交变更申请', 1, NOW()),
('role_viewer', '只读用户', 'viewer', '仅可查看权限，不可操作', 1, NOW()),
('role_approver', '审批人', 'approver', '工单审批权限', 1, NOW());

-- =============================================
-- 8. 初始化功能权限码（功能级权限点）
-- =============================================
INSERT IGNORE INTO `sys_permission` (`id`, `name`, `code`, `resource`, `action`, `description`, `create_time`) VALUES
('perm_sql_query', 'SQL查询', 'sql:query', '/api/v1/sql/**', 'read', '执行SQL查询操作', NOW()),
('perm_sql_execute', 'SQL执行', 'sql:update', '/api/v1/sql/**', 'write', '执行SQL变更操作', NOW()),
('perm_sql_audit', 'SQL审核', 'sql:audit', '/api/v1/sql/audit', 'read', 'SQL语句审核权限', NOW()),
('perm_db_view', '数据库查看', 'database:view', '/api/v1/databases/**', 'read', '查看数据库实例', NOW()),
('perm_db_manage', '数据库管理', 'database:manage', '/api/v1/databases/**', 'write', '添加/修改/删除数据库实例', NOW()),
('perm_ticket_create', '工单创建', 'ticket:create', '/api/v1/tickets/**', 'write', '创建数据变更工单', NOW()),
('perm_ticket_approve', '工单审批', 'ticket:approve', '/api/v1/tickets/**', 'write', '审批数据变更工单', NOW()),
('perm_ticket_rollback', '工单回滚', 'ticket:rollback', '/api/v1/tickets/**', 'write', '回滚已执行的工单', NOW()),
('perm_audit_view', '审计查看', 'audit:view', '/api/v1/audit/**', 'read', '查看审计日志', NOW()),
('perm_user_manage', '用户管理', 'user:manage', '/api/v1/users/**', 'write', '管理用户账号', NOW()),
('perm_role_manage', '角色管理', 'role:manage', '/api/v1/roles/**', 'write', '管理角色和权限分配', NOW()),
('perm_permission_manage', '权限管理', 'permission:manage', '/api/v1/permissions/**', 'write', '细粒度资源权限管理', NOW()),
('perm_masking_manage', '脱敏管理', 'masking:manage', '/api/v1/masking/**', 'write', '数据脱敏规则管理', NOW()),
('perm_quality_manage', '质量管理', 'quality:manage', '/api/v1/quality/**', 'write', '数据质量规则管理', NOW()),
('perm_metadata_manage', '元数据管理', 'metadata:manage', '/api/v1/metadata/**', 'write', '元数据采集和管理', NOW()),
('perm_pipeline_manage', '流水线管理', 'pipeline:manage', '/api/v1/pipelines/**', 'write', 'DDL部署流水线管理', NOW()),
('perm_monitor_view', '监控查看', 'monitor:view', '/api/v1/monitor/**', 'read', '性能监控查看', NOW()),
('perm_import_execute', '数据导入', 'import:execute', '/api/v1/import/**', 'write', '数据导入操作', NOW()),
('perm_ddl_workbench', 'DDL工作台', 'ddl:workbench', '/api/v1/ddl-workbench/**', 'write', 'DDL变更工作台操作', NOW()),
('perm_export_data', '数据导出', 'export:data', '/api/v1/export/**', 'write', '数据导出操作', NOW()),
('perm_system_settings', '系统设置', 'system:settings', '/api/v1/settings/**', 'write', '系统配置管理', NOW());

-- =============================================
-- 9. 初始化角色-权限关联
-- =============================================

-- 超级管理员：所有权限
INSERT IGNORE INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `granted_at`) VALUES
('ra_p01', 'role_admin', 'perm_sql_query', NOW()),
('ra_p02', 'role_admin', 'perm_sql_execute', NOW()),
('ra_p03', 'role_admin', 'perm_sql_audit', NOW()),
('ra_p04', 'role_admin', 'perm_db_view', NOW()),
('ra_p05', 'role_admin', 'perm_db_manage', NOW()),
('ra_p06', 'role_admin', 'perm_ticket_create', NOW()),
('ra_p07', 'role_admin', 'perm_ticket_approve', NOW()),
('ra_p08', 'role_admin', 'perm_ticket_rollback', NOW()),
('ra_p09', 'role_admin', 'perm_audit_view', NOW()),
('ra_p10', 'role_admin', 'perm_user_manage', NOW()),
('ra_p11', 'role_admin', 'perm_role_manage', NOW()),
('ra_p12', 'role_admin', 'perm_permission_manage', NOW()),
('ra_p13', 'role_admin', 'perm_masking_manage', NOW()),
('ra_p14', 'role_admin', 'perm_quality_manage', NOW()),
('ra_p15', 'role_admin', 'perm_metadata_manage', NOW()),
('ra_p16', 'role_admin', 'perm_pipeline_manage', NOW()),
('ra_p17', 'role_admin', 'perm_monitor_view', NOW()),
('ra_p18', 'role_admin', 'perm_import_execute', NOW()),
('ra_p19', 'role_admin', 'perm_ddl_workbench', NOW()),
('ra_p20', 'role_admin', 'perm_export_data', NOW()),
('ra_p21', 'role_admin', 'perm_system_settings', NOW());

-- DBA：数据库管理 + SQL操作 + 审批 + 回滚
INSERT IGNORE INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `granted_at`) VALUES
('rd_p01', 'role_dba', 'perm_sql_query', NOW()),
('rd_p02', 'role_dba', 'perm_sql_execute', NOW()),
('rd_p03', 'role_dba', 'perm_sql_audit', NOW()),
('rd_p04', 'role_dba', 'perm_db_view', NOW()),
('rd_p05', 'role_dba', 'perm_db_manage', NOW()),
('rd_p06', 'role_dba', 'perm_ticket_create', NOW()),
('rd_p07', 'role_dba', 'perm_ticket_approve', NOW()),
('rd_p08', 'role_dba', 'perm_ticket_rollback', NOW()),
('rd_p09', 'role_dba', 'perm_audit_view', NOW()),
('rd_p10', 'role_dba', 'perm_masking_manage', NOW()),
('rd_p11', 'role_dba', 'perm_quality_manage', NOW()),
('rd_p12', 'role_dba', 'perm_metadata_manage', NOW()),
('rd_p13', 'role_dba', 'perm_pipeline_manage', NOW()),
('rd_p14', 'role_dba', 'perm_monitor_view', NOW()),
('rd_p15', 'role_dba', 'perm_ddl_workbench', NOW());

-- 开发人员：SQL查询 + 工单创建
INSERT IGNORE INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `granted_at`) VALUES
('rdev_p01', 'role_developer', 'perm_sql_query', NOW()),
('rdev_p02', 'role_developer', 'perm_sql_audit', NOW()),
('rdev_p03', 'role_developer', 'perm_db_view', NOW()),
('rdev_p04', 'role_developer', 'perm_ticket_create', NOW()),
('rdev_p05', 'role_developer', 'perm_metadata_manage', NOW()),
('rdev_p06', 'role_developer', 'perm_monitor_view', NOW()),
('rdev_p07', 'role_developer', 'perm_ddl_workbench', NOW());

-- 只读用户：仅查看权限
INSERT IGNORE INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `granted_at`) VALUES
('rv_p01', 'role_viewer', 'perm_sql_query', NOW()),
('rv_p02', 'role_viewer', 'perm_db_view', NOW()),
('rv_p03', 'role_viewer', 'perm_audit_view', NOW()),
('rv_p04', 'role_viewer', 'perm_monitor_view', NOW());

-- 审批人：审批 + 只读查看
INSERT IGNORE INTO `sys_role_permission` (`id`, `role_id`, `permission_id`, `granted_at`) VALUES
('rapp_p01', 'role_approver', 'perm_sql_query', NOW()),
('rapp_p02', 'role_approver', 'perm_db_view', NOW()),
('rapp_p03', 'role_approver', 'perm_ticket_approve', NOW()),
('rapp_p04', 'role_approver', 'perm_audit_view', NOW()),
('rapp_p05', 'role_approver', 'perm_monitor_view', NOW());

-- =============================================
-- 10. 迁移旧表数据到新表（如果存在旧 user 表）
-- =============================================
-- 注意：仅当旧表存在且有数据时执行
-- INSERT IGNORE INTO sys_user (id, username, password_hash, nickname, email, is_active, create_time)
-- SELECT id, username, password, nickname, email, status, create_time FROM user;

-- =============================================
-- 11. 初始化默认用户-角色关联（admin用户默认超级管理员）
-- =============================================
INSERT IGNORE INTO `sys_user_role` (`id`, `user_id`, `role_id`, `granted_at`) VALUES
('ua_admin', 'user_admin', 'role_admin', NOW());

SELECT 'V4 权限控制细化升级完成！新增: sys_user, sys_role, sys_permission (完整DDL), sys_user_role, sys_role_permission, sys_permission_audit_log, 5个默认角色, 21个权限点' AS result;

-- =============================================
-- 12. 修复元数据采集后查询失败的脏数据
--     (deleted字段未设置导致逻辑删除 WHERE deleted=0 过滤掉 NULL 值)
-- =============================================
UPDATE `metadata_table` SET `deleted` = 0 WHERE `deleted` IS NULL;
UPDATE `metadata_column` SET `deleted` = 0 WHERE `deleted` IS NULL;
UPDATE `data_change_backup` SET `deleted` = 0 WHERE `deleted` IS NULL;

-- =============================================
-- 13. 补充 metadata_table / metadata_column 缺失字段
--     (实体类新增字段但数据库表未同步)
-- =============================================
ALTER TABLE `metadata_table` ADD COLUMN IF NOT EXISTS `quality_rating` VARCHAR(10) DEFAULT NULL COMMENT '数据质量评级' AFTER `quality_score`;
ALTER TABLE `metadata_column` ADD COLUMN IF NOT EXISTS `business_desc` VARCHAR(500) DEFAULT NULL COMMENT '业务描述' AFTER `business_name`;
ALTER TABLE `metadata_column` ADD COLUMN IF NOT EXISTS `data_steward` VARCHAR(100) DEFAULT NULL COMMENT '数据管家' AFTER `business_desc`;
