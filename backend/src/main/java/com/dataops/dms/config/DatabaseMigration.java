package com.dataops.dms.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

/**
 * Database migration runner - adds missing columns on startup
 */
@Slf4j
@Component
public class DatabaseMigration implements CommandLineRunner {

    @Resource
    private DataSource dataSource;

    @Override
    public void run(String... args) {
        try (Connection conn = dataSource.getConnection()) {
            // Check if schema_name column exists in ticket table
            if (!columnExists(conn, "ticket", "schema_name")) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE `ticket` ADD COLUMN `schema_name` VARCHAR(128) COMMENT '目标Schema名' AFTER `instance_id`");
                    log.info("Migration: Added schema_name column to ticket table");
                }
            }
            // Check if default_schema_name column exists in database_instance table
            if (!columnExists(conn, "database_instance", "default_schema_name")) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE `database_instance` ADD COLUMN `default_schema_name` VARCHAR(128) COMMENT '默认Schema名' AFTER `port`");
                    log.info("Migration: Added default_schema_name column to database_instance table");
                }
            }

            // Permission table: add fine-grained permission columns
            // 如果存在旧列 role_id 但不存在 user_id，先改名再继续
            if (columnExists(conn, "sys_permission", "role_id") && !columnExists(conn, "sys_permission", "user_id")) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE `sys_permission` CHANGE COLUMN `role_id` `user_id` VARCHAR(64) COMMENT '用户ID（资源级权限被授权用户）'");
                    log.info("Migration: Renamed sys_permission.role_id -> user_id");
                }
            }
            addColumnIfNotExists(conn, "sys_permission", "user_id", "VARCHAR(64) COMMENT '用户ID（资源级权限被授权用户）'");
            addColumnIfNotExists(conn, "sys_permission", "resource_type", "VARCHAR(32) COMMENT '资源类型: schema/table/column'");
            addColumnIfNotExists(conn, "sys_permission", "resource_id", "VARCHAR(128) COMMENT '资源ID'");
            addColumnIfNotExists(conn, "sys_permission", "resource_name", "VARCHAR(255) COMMENT '资源名称'");
            addColumnIfNotExists(conn, "sys_permission", "field_list", "TEXT COMMENT '字段级权限(逗号分隔)'");
            addColumnIfNotExists(conn, "sys_permission", "expire_time", "DATETIME COMMENT '权限过期时间'");

            // ============ V4: RBAC 权限控制细化 ============
            // 创建用户角色中间表
            createTableIfNotExists(conn, "sys_user_role",
                "CREATE TABLE sys_user_role (" +
                " id VARCHAR(64) NOT NULL COMMENT '主键ID'," +
                " user_id VARCHAR(64) NOT NULL COMMENT '用户ID'," +
                " role_id VARCHAR(64) NOT NULL COMMENT '角色ID'," +
                " granted_by VARCHAR(64) COMMENT '授予人ID'," +
                " granted_at DATETIME COMMENT '授予时间'," +
                " expire_time DATETIME COMMENT '角色有效期'," +
                " PRIMARY KEY (id)," +
                " UNIQUE KEY uk_user_role (user_id, role_id)," +
                " KEY idx_user_id (user_id)," +
                " KEY idx_role_id (role_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表'");

            // 创建角色权限中间表
            createTableIfNotExists(conn, "sys_role_permission",
                "CREATE TABLE sys_role_permission (" +
                " id VARCHAR(64) NOT NULL COMMENT '主键ID'," +
                " role_id VARCHAR(64) NOT NULL COMMENT '角色ID'," +
                " permission_id VARCHAR(64) NOT NULL COMMENT '权限ID'," +
                " granted_by VARCHAR(64) COMMENT '授予人ID'," +
                " granted_at DATETIME COMMENT '授予时间'," +
                " PRIMARY KEY (id)," +
                " UNIQUE KEY uk_role_perm (role_id, permission_id)," +
                " KEY idx_role_id (role_id)," +
                " KEY idx_permission_id (permission_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表'");

            // 创建权限审计日志表
            createTableIfNotExists(conn, "sys_permission_audit_log",
                "CREATE TABLE sys_permission_audit_log (" +
                " id VARCHAR(64) NOT NULL COMMENT '主键ID'," +
                " operation VARCHAR(32) NOT NULL COMMENT '操作: GRANT/REVOKE/ROLE_ASSIGN/CLEANUP'," +
                " target_type VARCHAR(32) COMMENT '目标类型: USER/ROLE'," +
                " target_id VARCHAR(64) COMMENT '目标ID'," +
                " resource_type VARCHAR(32) COMMENT '资源类型'," +
                " resource_id VARCHAR(128) COMMENT '资源ID'," +
                " action VARCHAR(64) COMMENT '操作'," +
                " detail TEXT COMMENT '详情JSON'," +
                " operator_id VARCHAR(64) COMMENT '操作人ID'," +
                " operator_name VARCHAR(128) COMMENT '操作人名称'," +
                " ip_address VARCHAR(64) COMMENT 'IP地址'," +
                " create_time DATETIME COMMENT '操作时间'," +
                " PRIMARY KEY (id)," +
                " KEY idx_target (target_type, target_id)," +
                " KEY idx_operator (operator_id)," +
                " KEY idx_create_time (create_time)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限操作审计日志'");

            // sys_role 表补字段
            addColumnIfNotExists(conn, "sys_role", "is_system", "TINYINT DEFAULT 0 COMMENT '是否系统内置'");
            addColumnIfNotExists(conn, "sys_role", "create_by", "VARCHAR(64) COMMENT '创建人'");
            addColumnIfNotExists(conn, "sys_role", "update_by", "VARCHAR(64) COMMENT '更新人'");
            addColumnIfNotExists(conn, "sys_role", "deleted", "TINYINT DEFAULT 0 COMMENT '逻辑删除'");

            // ============ DDL变更流转任务表 ============
            createTableIfNotExists(conn, "ddl_change_task",
                "CREATE TABLE ddl_change_task (" +
                " id VARCHAR(64) NOT NULL COMMENT '主键ID'," +
                " title VARCHAR(200) COMMENT '变更标题'," +
                " environment VARCHAR(20) NOT NULL COMMENT '环境: DEV/INTEGRATION/PRODUCTION'," +
                " status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/EXECUTING/SUCCESS/FAILED/SKIPPED'," +
                " database_instance_id VARCHAR(64) COMMENT '目标数据库实例ID'," +
                " database_instance_name VARCHAR(128) COMMENT '目标数据库实例名称'," +
                " database_name VARCHAR(128) COMMENT '目标Schema名'," +
                " sql_content TEXT COMMENT 'DDL变更SQL'," +
                " source_task_id VARCHAR(64) COMMENT '来源任务ID'," +
                " executed_by VARCHAR(64) COMMENT '执行人'," +
                " executed_at DATETIME COMMENT '执行时间'," +
                " duration_seconds INT COMMENT '执行耗时(秒)'," +
                " result_message TEXT COMMENT '执行结果消息'," +
                " rollback_sql TEXT COMMENT '回滚SQL'," +
                " created_by VARCHAR(64) COMMENT '创建人'," +
                " created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                " PRIMARY KEY (id)," +
                " KEY idx_environment (environment)," +
                " KEY idx_status (status)," +
                " KEY idx_source_task (source_task_id)," +
                " KEY idx_created_at (created_at)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='DDL变更流转任务'");

            // 初始化默认角色（如果sys_role表为空）
            seedDefaultRoles(conn);
            // 初始化功能权限码（如果sys_permission表没有code数据）
            seedDefaultPermissions(conn);
            // 初始化角色-权限关联
            seedDefaultRolePermissions(conn);
            // 初始化admin用户角色关联
            seedDefaultUserRoles(conn);

            // ============ V5: 菜单权限管理 ============
            createTableIfNotExists(conn, "sys_menu",
                "CREATE TABLE sys_menu (" +
                " id VARCHAR(64) NOT NULL COMMENT '主键ID'," +
                " parent_id VARCHAR(64) DEFAULT NULL COMMENT '父菜单ID'," +
                " name VARCHAR(64) NOT NULL COMMENT '菜单名称'," +
                " type VARCHAR(16) NOT NULL DEFAULT 'menu' COMMENT '类型: menu(菜单)/button(按钮)'," +
                " path VARCHAR(128) DEFAULT NULL COMMENT '前端路由路径'," +
                " component VARCHAR(128) DEFAULT NULL COMMENT '前端组件路径'," +
                " icon VARCHAR(64) DEFAULT NULL COMMENT 'Ant Design图标名称'," +
                " permission_code VARCHAR(64) DEFAULT NULL COMMENT '关联的功能权限码'," +
                " sort_order INT DEFAULT 0 COMMENT '排序号'," +
                " visible TINYINT DEFAULT 1 COMMENT '是否可见'," +
                " status VARCHAR(16) DEFAULT 'active' COMMENT '状态'," +
                " create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                " update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'," +
                " PRIMARY KEY (id)," +
                " KEY idx_parent_id (parent_id)," +
                " KEY idx_type (type)," +
                " KEY idx_permission_code (permission_code)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统菜单表'");

            createTableIfNotExists(conn, "sys_role_menu",
                "CREATE TABLE sys_role_menu (" +
                " id VARCHAR(64) NOT NULL COMMENT '主键ID'," +
                " role_id VARCHAR(64) NOT NULL COMMENT '角色ID'," +
                " menu_id VARCHAR(64) NOT NULL COMMENT '菜单ID'," +
                " created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                " PRIMARY KEY (id)," +
                " UNIQUE KEY uk_role_menu (role_id, menu_id)," +
                " KEY idx_role_id (role_id)," +
                " KEY idx_menu_id (menu_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色菜单关联表'");

            // 初始化数据
            seedDefaultMenus(conn);
            seedDefaultRoleMenus(conn);

            // 清理按钮类型菜单（权限粒度收归菜单级别）
            cleanupButtonMenus(conn);

            // 补齐存量用户缺失的角色（为新用户默认角色兜底）
            fixMissingUserRoles(conn);

            // 补齐存量数据库缺失的权限（menu:manage等）
            fixMissingPermissions(conn);

            // 补齐 data_change_backup 表缺失字段
            addColumnIfNotExists(conn, "data_change_backup", "rollback_by", "VARCHAR(64) COMMENT '回滚操作人ID'");

            // ============ V7: 数据权限体系分离 ============
            // 1. 新建 sys_user_permission —— 存储用户级数据资源权限（替代原本混入 sys_permission 的数据权限记录）
            createTableIfNotExists(conn, "sys_user_permission",
                "CREATE TABLE sys_user_permission (" +
                " id VARCHAR(64) NOT NULL COMMENT '主键ID'," +
                " user_id VARCHAR(64) NOT NULL COMMENT '被授权用户ID'," +
                " resource_type VARCHAR(32) NOT NULL COMMENT '资源类型: instance/schema/table/column'," +
                " resource_id VARCHAR(128) NOT NULL COMMENT '资源ID'," +
                " resource_name VARCHAR(255) COMMENT '资源名称（便于展示）'," +
                " action VARCHAR(64) COMMENT '权限操作: query/export/update/ddl/*'," +
                " field_list TEXT COMMENT '字段级权限(逗号分隔，null=全部字段)'," +
                " expire_time DATETIME COMMENT '权限过期时间(null=永不过期)'," +
                " granted_by VARCHAR(64) COMMENT '授权人ID'," +
                " granted_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '授权时间'," +
                " PRIMARY KEY (id)," +
                " KEY idx_user_id (user_id)," +
                " KEY idx_resource (resource_type, resource_id)," +
                " KEY idx_expire (expire_time)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户-数据资源权限表（用户级数据访问权限）'");

            // 2. 重命名 sys_permission_request → sys_user_permission_request（申请工单表）
            if (tableExists(conn, "sys_permission_request") && !tableExists(conn, "sys_user_permission_request")) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("RENAME TABLE `sys_permission_request` TO `sys_user_permission_request`");
                    log.info("Migration: Renamed sys_permission_request -> sys_user_permission_request");
                }
            }

            // 3. 迁移旧权限数据（可选）：如果 sys_permission 中存在 resourceType/resourceId 的数据，迁移到新表
            if (columnExists(conn, "sys_permission", "user_id") && columnExists(conn, "sys_permission", "resource_id")) {
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT COUNT(*) AS cnt FROM sys_permission WHERE resource_id IS NOT NULL AND resource_id <> ''")) {
                    if (rs.next() && rs.getInt("cnt") > 0) {
                        int migrated = stmt.executeUpdate(
                            "INSERT IGNORE INTO sys_user_permission " +
                            "(id, user_id, resource_type, resource_id, resource_name, action, field_list, expire_time, granted_at) " +
                            "SELECT UUID(), user_id, resource_type, resource_id, resource_name, action, field_list, expire_time, NOW() " +
                            "FROM sys_permission WHERE resource_id IS NOT NULL AND resource_id <> ''");
                        log.info("Migration: Migrated {} records from sys_permission to sys_user_permission", migrated);
                    }
                }
            }

            // ============ V6: DDL项目工单表 ============
            createTableIfNotExists(conn, "ddl_project",
                "CREATE TABLE ddl_project (" +
                " id VARCHAR(64) NOT NULL COMMENT '主键ID'," +
                " project_name VARCHAR(256) NOT NULL COMMENT '项目名称'," +
                " business_background TEXT COMMENT '项目背景'," +
                " base_database_id VARCHAR(64) COMMENT '基准库实例ID'," +
                " base_database_name VARCHAR(128) COMMENT '基准库实例名'," +
                " base_schema_name VARCHAR(128) COMMENT '基准Schema名'," +
                " status VARCHAR(32) DEFAULT 'DESIGNING' COMMENT '状态'," +
                " current_stage VARCHAR(32) DEFAULT 'CREATE' COMMENT '当前阶段'," +
                " owner VARCHAR(64) COMMENT '负责人'," +
                " related_persons TEXT COMMENT '变更相关人'," +
                " security_rule VARCHAR(64) DEFAULT 'auto' COMMENT '安全规则'," +
                " priority VARCHAR(32) DEFAULT 'normal' COMMENT '优先级'," +
                " table_count INT DEFAULT 0 COMMENT '变更表数量'," +
                " closed_at DATETIME COMMENT '关闭时间'," +
                " created_by VARCHAR(64) COMMENT '创建人'," +
                " created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                " updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'," +
                " PRIMARY KEY (id)," +
                " KEY idx_status (status)," +
                " KEY idx_owner (owner)," +
                " KEY idx_created_at (created_at)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='DDL项目工单'");

            createTableIfNotExists(conn, "ddl_project_table",
                "CREATE TABLE ddl_project_table (" +
                " id VARCHAR(64) NOT NULL COMMENT '主键ID'," +
                " project_id VARCHAR(64) NOT NULL COMMENT '项目ID'," +
                " table_name VARCHAR(128) NOT NULL COMMENT '表名'," +
                " change_type VARCHAR(16) NOT NULL COMMENT '变更类型: NEW/MODIFY'," +
                " original_ddl LONGTEXT COMMENT '原始DDL'," +
                " modified_ddl LONGTEXT COMMENT '修改后DDL'," +
                " change_sql LONGTEXT COMMENT '变更SQL'," +
                " version INT DEFAULT 0 COMMENT '版本号'," +
                " last_operator VARCHAR(64) COMMENT '最后操作人'," +
                " last_modified_at DATETIME COMMENT '最近修改时间'," +
                " env_status TEXT COMMENT '环境状态JSON'," +
                " env_details LONGTEXT COMMENT '环境详情JSON'," +
                " created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'," +
                " updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'," +
                " PRIMARY KEY (id)," +
                " KEY idx_project_id (project_id)," +
                " KEY idx_table_name (table_name)," +
                " UNIQUE KEY uk_project_table (project_id, table_name)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='DDL项目工单-表变更明细'");

        } catch (Exception e) {
            log.warn("DatabaseMigration skipped (column may already exist or DB not ready): {}", e.getMessage());
        }
    }

    private void addColumnIfNotExists(Connection conn, String tableName, String columnName, String columnDef) {
        try {
            if (!columnExists(conn, tableName, columnName)) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE `" + tableName + "` ADD COLUMN `" + columnName + "` " + columnDef);
                    log.info("Migration: Added {} column to {} table", columnName, tableName);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to add column {}.{}: {}", tableName, columnName, e.getMessage());
        }
    }

    private boolean columnExists(Connection conn, String tableName, String columnName) throws Exception {
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getColumns(null, null, tableName, columnName)) {
            return rs.next();
        }
    }

    private boolean tableExists(Connection conn, String tableName) throws Exception {
        DatabaseMetaData meta = conn.getMetaData();
        try (ResultSet rs = meta.getTables(null, null, tableName, null)) {
            return rs.next();
        }
    }

    private void createTableIfNotExists(Connection conn, String tableName, String ddl) {
        try {
            if (!tableExists(conn, tableName)) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(ddl);
                    log.info("Migration: Created table {}", tableName);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to create table {}: {}", tableName, e.getMessage());
        }
    }

    private void seedDefaultRoles(Connection conn) {
        try {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM sys_role")) {
                if (rs.next() && rs.getInt(1) > 0) return;
            }
            String[] roles = {
                "('role_admin','超级管理员','admin','拥有所有权限',1)",
                "('role_dba','DBA','dba','数据库管理员',1)",
                "('role_developer','开发人员','developer','普通开发人员',1)",
                "('role_viewer','只读用户','viewer','仅查看权限',1)",
                "('role_approver','审批人','approver','工单审批权限',1)"
            };
            try (Statement stmt = conn.createStatement()) {
                for (String r : roles) {
                    stmt.executeUpdate("INSERT IGNORE INTO sys_role (id,name,code,description,is_system,create_time) VALUES " + r + ",NOW()");
                }
                log.info("Migration: Seeded {} default roles", roles.length);
            }
        } catch (Exception e) {
            log.warn("Failed to seed roles: {}", e.getMessage());
        }
    }

    private void seedDefaultPermissions(Connection conn) {
        try {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM sys_permission WHERE code IS NOT NULL")) {
                if (rs.next() && rs.getInt(1) > 0) return;
            }
            String[][] perms = {
                {"perm_sql_query","SQL查询","sql:query","/api/v1/sql/**","read","执行SQL查询操作"},
                {"perm_sql_execute","SQL执行","sql:update","/api/v1/sql/**","write","执行SQL变更操作"},
                {"perm_sql_audit","SQL审核","sql:audit","/api/v1/sql/audit","read","SQL语句审核"},
                {"perm_db_view","数据库查看","database:view","/api/v1/databases/**","read","查看数据库实例"},
                {"perm_db_manage","数据库管理","database:manage","/api/v1/databases/**","write","管理数据库实例"},
                {"perm_ticket_create","工单创建","ticket:create","/api/v1/tickets/**","write","创建变更工单"},
                {"perm_ticket_approve","工单审批","ticket:approve","/api/v1/tickets/**","write","审批变更工单"},
                {"perm_ticket_rollback","工单回滚","ticket:rollback","/api/v1/tickets/**","write","回滚工单"},
                {"perm_audit_view","审计查看","audit:view","/api/v1/audit/**","read","查看审计日志"},
                {"perm_user_manage","用户管理","user:manage","/api/v1/users/**","write","管理用户"},
                {"perm_role_manage","角色管理","role:manage","/api/v1/roles/**","write","管理角色和权限"},
                {"perm_permission_manage","权限管理","permission:manage","/api/v1/permissions/**","write","资源权限管理"},
                {"perm_masking_manage","脱敏管理","masking:manage","/api/v1/masking/**","write","脱敏规则管理"},
                {"perm_quality_manage","质量管理","quality:manage","/api/v1/quality/**","write","质量规则管理"},
                {"perm_metadata_manage","元数据管理","metadata:manage","/api/v1/metadata/**","write","元数据管理"},
                {"perm_pipeline_manage","流水线管理","pipeline:manage","/api/v1/pipelines/**","write","DDL流水线"},
                {"perm_monitor_view","监控查看","monitor:view","/api/v1/monitor/**","read","性能监控"},
                {"perm_import_execute","数据导入","import:execute","/api/v1/import/**","write","数据导入"},
                {"perm_ddl_workbench","DDL工作台","ddl:workbench","/api/v1/ddl-workbench/**","write","DDL工作台"},
                {"perm_export_data","数据导出","export:data","/api/v1/export/**","write","数据导出"},
                {"perm_system_settings","系统设置","system:settings","/api/v1/settings/**","write","系统配置"},
                {"perm_menu_manage","菜单管理","menu:manage","/api/v1/menus/**","write","菜单权限管理"},
                {"perm_sensitive_view","敏感数据查看","sensitive:view","/api/v1/sensitive/**","read","查看敏感数据"},
                {"perm_sensitive_manage","敏感数据管理","sensitive:manage","/api/v1/sensitive/**","write","管理敏感数据"},
                {"perm_owner_manage","资源Owner","owner:manage","/api/v1/owners/**","write","资源Owner管理"},
            };
            try (Statement stmt = conn.createStatement()) {
                for (String[] p : perms) {
                    stmt.executeUpdate("INSERT IGNORE INTO sys_permission (id,name,code,resource,action,description,create_time) VALUES ('"
                        + p[0] + "','" + p[1] + "','" + p[2] + "','" + p[3] + "','" + p[4] + "','" + p[5] + "',NOW())");
                }
                log.info("Migration: Seeded {} permission codes", perms.length);
            }
        } catch (Exception e) {
            log.warn("Failed to seed permissions: {}", e.getMessage());
        }
    }

    private void seedDefaultRolePermissions(Connection conn) {
        try {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM sys_role_permission")) {
                if (rs.next() && rs.getInt(1) > 0) return;
            }
            // admin gets all
            String[] allPerms = {"perm_sql_query","perm_sql_execute","perm_sql_audit","perm_db_view","perm_db_manage",
                "perm_ticket_create","perm_ticket_approve","perm_ticket_rollback","perm_audit_view","perm_user_manage",
                "perm_role_manage","perm_permission_manage","perm_masking_manage","perm_quality_manage","perm_metadata_manage",
                "perm_pipeline_manage","perm_monitor_view","perm_import_execute","perm_ddl_workbench","perm_export_data",
                "perm_system_settings","perm_menu_manage","perm_sensitive_view","perm_sensitive_manage",
                "perm_owner_manage","perm_access_manage"};
            String[] dbaPerms = {"perm_sql_query","perm_sql_execute","perm_sql_audit","perm_db_view","perm_db_manage",
                "perm_ticket_create","perm_ticket_approve","perm_ticket_rollback","perm_audit_view","perm_masking_manage",
                "perm_quality_manage","perm_metadata_manage","perm_pipeline_manage","perm_monitor_view","perm_ddl_workbench"};
            String[] devPerms = {"perm_sql_query","perm_sql_audit","perm_db_view","perm_ticket_create","perm_metadata_manage",
                "perm_monitor_view","perm_ddl_workbench"};
            String[] viewerPerms = {"perm_sql_query","perm_db_view","perm_audit_view","perm_monitor_view"};
            String[] approverPerms = {"perm_sql_query","perm_db_view","perm_ticket_approve","perm_audit_view","perm_monitor_view"};

            try (Statement stmt = conn.createStatement()) {
                seedRolePerms(stmt, "role_admin", allPerms);
                seedRolePerms(stmt, "role_dba", dbaPerms);
                seedRolePerms(stmt, "role_developer", devPerms);
                seedRolePerms(stmt, "role_viewer", viewerPerms);
                seedRolePerms(stmt, "role_approver", approverPerms);
                log.info("Migration: Seeded role-permission mappings");
            }
        } catch (Exception e) {
            log.warn("Failed to seed role permissions: {}", e.getMessage());
        }
    }

    private void seedRolePerms(Statement stmt, String roleId, String[] permIds) throws Exception {
        for (String permId : permIds) {
            String recordId = java.util.UUID.randomUUID().toString().replace("-", "");
            stmt.executeUpdate("INSERT IGNORE INTO sys_role_permission (id,role_id,permission_id,granted_at) VALUES ('"
                + recordId + "','" + roleId + "','" + permId + "',NOW())");
        }
    }

    private void seedDefaultUserRoles(Connection conn) {
        try {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM sys_user_role")) {
                if (rs.next() && rs.getInt(1) > 0) return;
            }
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("INSERT IGNORE INTO sys_user_role (id,user_id,role_id,granted_at) VALUES ('ua_admin','user_admin','role_admin',NOW())");
                log.info("Migration: Seeded admin user role");
            }
        } catch (Exception e) {
            log.warn("Failed to seed user roles: {}", e.getMessage());
        }
    }

    /**
     * 补齐存量用户缺失的角色：为所有活跃但无角色的用户分配 developer 角色
     */
    private void fixMissingUserRoles(Connection conn) {
        try {
            // 查询没有角色的活跃用户
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                     "SELECT u.id FROM sys_user u " +
                     "WHERE u.is_active = 1 " +
                     "AND u.id NOT IN (SELECT DISTINCT user_id FROM sys_user_role)")) {
                int count = 0;
                while (rs.next()) {
                    String userId = rs.getString("id");
                    String recordId = java.util.UUID.randomUUID().toString().replace("-", "");
                    try (Statement insertStmt = conn.createStatement()) {
                        insertStmt.executeUpdate(
                            "INSERT IGNORE INTO sys_user_role (id,user_id,role_id,granted_by,granted_at) VALUES ('"
                            + recordId + "','" + userId + "','role_developer','system',NOW())");
                        count++;
                    }
                }
                if (count > 0) {
                    log.info("Migration: 已为 {} 个存量用户补齐 developer 角色", count);
                }
            }
        } catch (Exception e) {
            log.warn("修复存量用户角色失败: {}", e.getMessage());
        }
    }

    private void seedDefaultMenus(Connection conn) {
        try {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM sys_menu")) {
                if (rs.next() && rs.getInt(1) > 0) return;
            }
            // [id, parentId, name, type, path, component, icon, permissionCode, sortOrder, visible, status]
            String[][] menus = {
                // 顶级菜单
                {"menu_dashboard", null, "工作台", "menu", "/dashboard", null, "DashboardOutlined", null, "1", "1", "active"},
                {"menu_sql", null, "SQL查询", "menu", "/sql", null, "FileSearchOutlined", null, "2", "1", "active"},
                {"menu_databases", null, "实例管理", "menu", "/databases", null, "DatabaseOutlined", null, "3", "1", "active"},
                {"menu_schema", null, "结构设计", "menu", "/schema-designer", null, "BuildOutlined", null, "4", "1", "active"},
                {"menu_import", null, "数据导入", "menu", "/import", null, "ImportOutlined", null, "5", "1", "active"},
                {"menu_monitor", null, "性能监控", "menu", "/monitor", null, "LineChartOutlined", null, "6", "1", "active"},
                {"menu_metadata", null, "元数据管理", "menu", "/metadata", null, "TableOutlined", null, "7", "1", "active"},
                {"menu_tickets", null, "数据变更", "menu", "/tickets", null, "CheckSquareOutlined", null, "8", "1", "active"},
                {"menu_audit", null, "审计日志", "menu", "/audit", null, "AuditOutlined", null, "9", "1", "active"},
                {"menu_perm_requests", null, "权限申请", "menu", "/permission-requests", null, "SendOutlined", null, "10", "1", "active"},
                // 安全管理分组
                {"menu_security_group", null, "安全管理", "menu", null, null, "SafetyOutlined", null, "30", "1", "active"},
                {"menu_resource_owners", "menu_security_group", "资源Owner", "menu", "/resource-owners", null, "CrownOutlined", null, "1", "1", "active"},
                {"menu_sensitive", "menu_security_group", "敏感数据", "menu", "/sensitive-data", null, "EyeInvisibleOutlined", null, "2", "1", "active"},
                {"menu_row_controls", "menu_security_group", "行级管控", "menu", "/row-controls", null, "FilterOutlined", null, "3", "1", "active"},
                // 系统管理分组
                {"menu_system_group", null, "系统管理", "menu", null, null, "SettingOutlined", null, "90", "1", "active"},
                {"menu_users", "menu_system_group", "用户管理", "menu", "/users", null, "UserOutlined", null, "1", "1", "active"},
                {"menu_roles", "menu_system_group", "角色管理", "menu", "/roles", null, "TeamOutlined", null, "2", "1", "active"},
                {"menu_notifications", "menu_system_group", "通知管理", "menu", "/notifications", null, "BellOutlined", null, "3", "1", "active"},
                {"menu_settings", "menu_system_group", "系统设置", "menu", "/settings", null, "SettingOutlined", null, "4", "1", "active"},
                {"menu_menu_manage", "menu_system_group", "菜单管理", "menu", "/menus", null, "MenuOutlined", null, "5", "1", "active"},
            };
            try (Statement stmt = conn.createStatement()) {
                for (String[] m : menus) {
                    stmt.executeUpdate("INSERT IGNORE INTO sys_menu (id,parent_id,name,type,path,component,icon,permission_code,sort_order,visible,status,create_time) VALUES ('"
                        + m[0] + "'," + (m[1] == null ? "NULL" : "'" + m[1] + "'") + ",'" + m[2] + "','" + m[3] + "',"
                        + (m[4] == null ? "NULL" : "'" + m[4] + "'") + ","
                        + (m[5] == null ? "NULL" : "'" + m[5] + "'") + ","
                        + (m[6] == null ? "NULL" : "'" + m[6] + "'") + ","
                        + (m[7] == null ? "NULL" : "'" + m[7] + "'") + ","
                        + m[8] + "," + m[9] + ",'" + m[10] + "',NOW())");
                }
                log.info("Migration: Seeded {} menu items", menus.length);
            }
        } catch (Exception e) {
            log.warn("Failed to seed menus: {}", e.getMessage());
        }
    }

    private void seedDefaultRoleMenus(Connection conn) {
        try {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM sys_role_menu")) {
                if (rs.next() && rs.getInt(1) > 0) return;
            }
            // admin 拥有所有菜单
            String[] allMenuIds = {"menu_dashboard","menu_sql","menu_databases","menu_schema",
                "menu_import","menu_monitor","menu_metadata","menu_tickets","menu_audit",
                "menu_perm_requests",
                "menu_security_group","menu_resource_owners","menu_sensitive","menu_row_controls",
                "menu_system_group","menu_users","menu_roles","menu_notifications","menu_settings","menu_menu_manage"};
            
            // dba 菜单（无用户管理/系统设置/菜单管理）
            String[] dbaMenuIds = {"menu_dashboard","menu_sql","menu_databases","menu_schema",
                "menu_import","menu_monitor","menu_metadata","menu_tickets","menu_audit",
                "menu_perm_requests",
                "menu_security_group","menu_resource_owners","menu_sensitive","menu_row_controls"};
            
            // developer 菜单
            String[] devMenuIds = {"menu_dashboard","menu_sql","menu_databases","menu_schema",
                "menu_monitor","menu_metadata","menu_tickets",
                "menu_perm_requests"};
            
            // viewer 菜单（只读）
            String[] viewerMenuIds = {"menu_dashboard","menu_sql","menu_databases",
                "menu_monitor","menu_audit"};
            
            // approver 菜单
            String[] approverMenuIds = {"menu_dashboard","menu_sql","menu_databases",
                "menu_tickets","menu_audit","menu_monitor"};

            try (Statement stmt = conn.createStatement()) {
                seedRoleMenuItems(stmt, "role_admin", allMenuIds);
                seedRoleMenuItems(stmt, "role_dba", dbaMenuIds);
                seedRoleMenuItems(stmt, "role_developer", devMenuIds);
                seedRoleMenuItems(stmt, "role_viewer", viewerMenuIds);
                seedRoleMenuItems(stmt, "role_approver", approverMenuIds);
            }
            log.info("Migration: Seeded role-menu mappings");
        } catch (Exception e) {
            log.warn("Failed to seed role menus: {}", e.getMessage());
        }
    }

    private void seedRoleMenuItems(Statement stmt, String roleId, String[] menuIds) throws Exception {
        for (String menuId : menuIds) {
            String recordId = java.util.UUID.randomUUID().toString().replace("-", "");
            stmt.executeUpdate("INSERT IGNORE INTO sys_role_menu (id,role_id,menu_id,created_at) VALUES ('"
                + recordId + "','" + roleId + "','" + menuId + "',NOW())");
        }
    }

    /**
     * 清理按钮类型菜单数据（权限粒度简化：只控制菜单不控制操作）
     */
    private void cleanupButtonMenus(Connection conn) {
        try {
            // 删除按钮类型在角色菜单关联中的记录
            try (Statement stmt = conn.createStatement()) {
                int deleted = stmt.executeUpdate(
                    "DELETE rm FROM sys_role_menu rm " +
                    "INNER JOIN sys_menu m ON rm.menu_id = m.id " +
                    "WHERE m.type = 'button'");
                if (deleted > 0) {
                    log.info("Migration(cleanup): Removed {} button items from sys_role_menu", deleted);
                }
            }
            // 删除按钮类型的菜单条目
            try (Statement stmt = conn.createStatement()) {
                int deleted = stmt.executeUpdate("DELETE FROM sys_menu WHERE type = 'button'");
                if (deleted > 0) {
                    log.info("Migration(cleanup): Removed {} button items from sys_menu", deleted);
                }
            }
        } catch (Exception e) {
            log.warn("cleanupButtonMenus failed: {}", e.getMessage());
        }
    }

    /**
     * 补齐存量数据库缺失的权限码（seedDefaultPermissions 已有数据时会跳过）
     */
    private void fixMissingPermissions(Connection conn) {
        String[][] missingPerms = {
            {"perm_menu_manage","菜单管理","menu:manage","/api/v1/menus/**","write","菜单权限管理"},
            {"perm_sensitive_view","敏感数据查看","sensitive:view","/api/v1/sensitive/**","read","查看敏感数据"},
            {"perm_sensitive_manage","敏感数据管理","sensitive:manage","/api/v1/sensitive/**","write","管理敏感数据"},
            {"perm_owner_manage","资源Owner","owner:manage","/api/v1/owners/**","write","资源Owner管理"},
        };
        try {
            for (String[] p : missingPerms) {
                // 1. 插入缺失的权限码
                boolean permExists = false;
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM sys_permission WHERE code='" + p[2] + "'")) {
                    if (rs.next() && rs.getInt(1) > 0) permExists = true;
                }
                if (!permExists) {
                    try (Statement stmt = conn.createStatement()) {
                        stmt.executeUpdate("INSERT INTO sys_permission (id,name,code,resource,action,description,create_time) VALUES ('"
                            + p[0] + "','" + p[1] + "','" + p[2] + "','" + p[3] + "','" + p[4] + "','" + p[5] + "',NOW())");
                        log.info("Migration: Added missing permission {} ({})", p[0], p[2]);
                    }
                }
                // 2. 给 admin 角色授权（独立检查，不受权限码检查影响）
                boolean rolePermExists = false;
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM sys_role_permission WHERE role_id='role_admin' AND permission_id='" + p[0] + "'")) {
                    if (rs.next() && rs.getInt(1) > 0) rolePermExists = true;
                }
                if (!rolePermExists) {
                    try (Statement stmt = conn.createStatement()) {
                        String recordId = java.util.UUID.randomUUID().toString().replace("-", "");
                        stmt.executeUpdate("INSERT IGNORE INTO sys_role_permission (id,role_id,permission_id) VALUES ('"
                            + recordId + "','role_admin','" + p[0] + "')");
                        log.info("Migration: Granted {} to admin role", p[2]);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("fixMissingPermissions failed: {}", e.getMessage());
        }
    }
}
