"""执行缺失的SQL：补全权限码 + 创建V6表 + 角色权限"""
import pymysql

conn = pymysql.connect(host='192.168.16.163', port=3325, user='devuser', password='Dev#2026$', database='dataops_dms', autocommit=True)
cur = conn.cursor()

# === 1. 补全V4缺失的13个权限码 ===
perms_v4 = [
    ('perm_sql_audit', 'SQL审核', 'sql:audit', '/api/v1/sql/audit', 'read', 'SQL语句审核权限'),
    ('perm_ticket_rollback', '工单回滚', 'ticket:rollback', '/api/v1/tickets/**', 'write', '回滚已执行的工单'),
    ('perm_role_manage', '角色管理', 'role:manage', '/api/v1/roles/**', 'write', '管理角色和权限分配'),
    ('perm_permission_manage', '权限管理', 'permission:manage', '/api/v1/permissions/**', 'write', '细粒度资源权限管理'),
    ('perm_masking_manage', '脱敏管理', 'masking:manage', '/api/v1/masking/**', 'write', '数据脱敏规则管理'),
    ('perm_quality_manage', '质量管理', 'quality:manage', '/api/v1/quality/**', 'write', '数据质量规则管理'),
    ('perm_metadata_manage', '元数据管理', 'metadata:manage', '/api/v1/metadata/**', 'write', '元数据采集和管理'),
    ('perm_pipeline_manage', '流水线管理', 'pipeline:manage', '/api/v1/pipelines/**', 'write', 'DDL部署流水线管理'),
    ('perm_monitor_view', '监控查看', 'monitor:view', '/api/v1/monitor/**', 'read', '性能监控查看'),
    ('perm_import_execute', '数据导入', 'import:execute', '/api/v1/import/**', 'write', '数据导入操作'),
    ('perm_ddl_workbench', 'DDL工作台', 'ddl:workbench', '/api/v1/ddl-workbench/**', 'write', 'DDL变更工作台操作'),
    ('perm_export_data', '数据导出', 'export:data', '/api/v1/export/**', 'write', '数据导出操作'),
    ('perm_system_settings', '系统设置', 'system:settings', '/api/v1/settings/**', 'write', '系统配置管理'),
]
for p in perms_v4:
    cur.execute("INSERT IGNORE INTO sys_permission (id, name, code, resource, action, description) VALUES (%s,%s,%s,%s,%s,%s)", p)

# === 2. V6的4个新权限码 ===
perms_v6 = [
    ('perm_sensitive_view', '敏感列查看', 'sensitive:view', '/api/v1/sensitive/**', 'read', '查看敏感列配置'),
    ('perm_sensitive_manage', '敏感列管理', 'sensitive:manage', '/api/v1/sensitive/**', 'write', '管理敏感列标记和脱敏规则'),
    ('perm_access_control_manage', '访问控制管理', 'access:manage', '/api/v1/access-control/**', 'write', '管理元数据访问控制规则'),
    ('perm_owner_manage', 'Owner管理', 'owner:manage', '/api/v1/owners/**', 'write', '管理资源Owner分配'),
]
for p in perms_v6:
    cur.execute("INSERT IGNORE INTO sys_permission (id, name, code, resource, action, description) VALUES (%s,%s,%s,%s,%s,%s)", p)

# === 3. 给角色补全权限关联 ===
# 注意：表结构只有 id, role_id, permission_id, create_time
role_perm_map = {
    'role_admin': [
        'perm_sql_audit','perm_ticket_rollback','perm_role_manage','perm_permission_manage',
        'perm_masking_manage','perm_quality_manage','perm_metadata_manage','perm_pipeline_manage',
        'perm_monitor_view','perm_import_execute','perm_ddl_workbench','perm_export_data','perm_system_settings',
        'perm_sensitive_view','perm_sensitive_manage','perm_access_control_manage','perm_owner_manage',
    ],
    'role_dba': [
        'perm_sql_audit','perm_ticket_rollback','perm_masking_manage','perm_quality_manage',
        'perm_metadata_manage','perm_pipeline_manage','perm_monitor_view','perm_ddl_workbench',
        'perm_sensitive_view','perm_sensitive_manage','perm_access_control_manage','perm_owner_manage',
    ],
    'role_developer': [
        'perm_db_view','perm_metadata_manage','perm_monitor_view','perm_ddl_workbench',
    ],
    'role_approver': [
        'perm_db_view','perm_audit_view','perm_monitor_view',
    ],
}

for role_id, perm_ids in role_perm_map.items():
    for perm_id in perm_ids:
        rid = f"rp_{role_id}_{perm_id}"
        cur.execute("INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id) VALUES (%s,%s,%s)", (rid, role_id, perm_id))
        print(f"  {role_id} <- {perm_id}")

# === 4. 创建V6缺失的表 ===
v6_tables_sql = [
    """CREATE TABLE IF NOT EXISTS sys_resource_owner (
        id VARCHAR(64) NOT NULL, resource_type VARCHAR(32) NOT NULL,
        resource_id VARCHAR(128) NOT NULL, resource_name VARCHAR(255),
        parent_resource_type VARCHAR(32), parent_resource_id VARCHAR(128),
        owner_user_id VARCHAR(64) NOT NULL, owner_username VARCHAR(128),
        created_at DATETIME, created_by VARCHAR(64),
        PRIMARY KEY (id), UNIQUE KEY uk_owner (resource_type, resource_id, owner_user_id),
        KEY idx_owner_user (owner_user_id), KEY idx_resource (resource_type, resource_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资源Owner关系表'""",

    """CREATE TABLE IF NOT EXISTS sys_metadata_access_control (
        id VARCHAR(64) NOT NULL, resource_type VARCHAR(32) NOT NULL,
        resource_id VARCHAR(128) NOT NULL, resource_name VARCHAR(255),
        enabled TINYINT(1) DEFAULT 0, config_json TEXT,
        create_time DATETIME, update_time DATETIME, create_by VARCHAR(64), update_by VARCHAR(64),
        PRIMARY KEY (id), UNIQUE KEY uk_resource (resource_type, resource_id), KEY idx_enabled (enabled)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='元数据访问控制表'""",

    """CREATE TABLE IF NOT EXISTS sys_sensitive_mask_rule (
        id VARCHAR(64) NOT NULL, name VARCHAR(128) NOT NULL, code VARCHAR(64) NOT NULL,
        mask_type VARCHAR(32) NOT NULL, mask_pattern VARCHAR(256),
        mask_character VARCHAR(8) DEFAULT '*', keep_prefix_len INT DEFAULT 0, keep_suffix_len INT DEFAULT 0,
        description VARCHAR(256), sample_input VARCHAR(256), sample_output VARCHAR(256),
        is_system TINYINT(1) DEFAULT 0, create_time DATETIME, update_time DATETIME,
        create_by VARCHAR(64), update_by VARCHAR(64),
        PRIMARY KEY (id), UNIQUE KEY uk_code (code)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='敏感数据脱敏规则表'""",

    """CREATE TABLE IF NOT EXISTS sys_sensitive_column (
        id VARCHAR(64) NOT NULL, database_id VARCHAR(64) NOT NULL, database_name VARCHAR(128),
        table_name VARCHAR(128) NOT NULL, column_name VARCHAR(128) NOT NULL,
        sensitivity_level VARCHAR(16) NOT NULL DEFAULT 'L2', category VARCHAR(64),
        mask_rule_id VARCHAR(64), is_active TINYINT(1) DEFAULT 1, description VARCHAR(256),
        created_at DATETIME, updated_at DATETIME, created_by VARCHAR(64),
        PRIMARY KEY (id), UNIQUE KEY uk_column (database_id, database_name, table_name, column_name),
        KEY idx_db_table (database_id, table_name)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='敏感列标记表'""",

    """CREATE TABLE IF NOT EXISTS sys_row_control_rule (
        id VARCHAR(64) NOT NULL, name VARCHAR(128) NOT NULL,
        database_id VARCHAR(64) NOT NULL, database_name VARCHAR(128), table_name VARCHAR(128) NOT NULL,
        filter_condition TEXT NOT NULL, filter_description VARCHAR(256),
        target_user_ids TEXT, target_role_ids TEXT,
        is_active TINYINT(1) DEFAULT 1, priority INT DEFAULT 0,
        created_at DATETIME, updated_at DATETIME, created_by VARCHAR(64),
        PRIMARY KEY (id), KEY idx_db_table (database_id, table_name), KEY idx_active (is_active)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行级管控规则表'""",

    """CREATE TABLE IF NOT EXISTS sys_permission_request (
        id VARCHAR(64) NOT NULL, applicant_id VARCHAR(64) NOT NULL, applicant_name VARCHAR(128),
        resource_type VARCHAR(32) NOT NULL, resource_id VARCHAR(128) NOT NULL, resource_name VARCHAR(255),
        requested_permissions TEXT NOT NULL, reason TEXT,
        status VARCHAR(16) NOT NULL DEFAULT 'pending', approver_id VARCHAR(64), approver_name VARCHAR(128),
        approval_comment TEXT, approval_level INT DEFAULT 1, current_approval_step INT DEFAULT 0,
        created_at DATETIME, approved_at DATETIME, expired_at DATETIME,
        PRIMARY KEY (id), KEY idx_applicant (applicant_id), KEY idx_status (status),
        KEY idx_resource (resource_type, resource_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='权限申请表'""",
]

for sql in v6_tables_sql:
    try:
        cur.execute(sql)
        print(f"  Table created OK")
    except Exception as e:
        print(f"  Table error: {e}")

# === 5. 默认脱敏规则 ===
mask_rules = [
    ('mask_phone', '手机号脱敏', 'phone', 'HALF_MASK', '*', 3, 4, '手机号中间4位替换为*', '13812345678', '138****5678'),
    ('mask_email', '邮箱脱敏', 'email', 'PREFIX_MASK', '*', 1, 0, '邮箱前缀仅保留首字母', 'zhangsan@example.com', 'z*******@example.com'),
    ('mask_idcard', '身份证脱敏', 'idcard', 'HALF_MASK', '*', 6, 4, '身份证中间隐藏', '110101199001011234', '110101********1234'),
    ('mask_bankcard', '银行卡脱敏', 'bankcard', 'SUFFIX_MASK', '*', 4, 0, '银行卡仅显示后4位', '6222021234567890123', '6222**************0123'),
    ('mask_full', '全脱敏', 'full', 'FULL_MASK', '*', 0, 0, '全部替换为****', '敏感数据', '****'),
    ('mask_name', '姓名脱敏', 'name', 'FULL_MASK', '*', 1, 0, '姓名仅保留姓氏', '张三', '张*'),
]
for m in mask_rules:
    cur.execute("""INSERT IGNORE INTO sys_sensitive_mask_rule 
        (id, name, code, mask_type, mask_character, keep_prefix_len, keep_suffix_len, description, sample_input, sample_output, is_system, create_time) 
        VALUES (%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,1,NOW())""", m)

# === 6. 验证 ===
cur.execute("SELECT COUNT(*) FROM sys_permission")
print(f"\n权限码总数: {cur.fetchone()[0]}")

cur.execute("SELECT COUNT(*) FROM sys_role_permission WHERE role_id='role_admin'")
print(f"admin角色权限数: {cur.fetchone()[0]}")

cur.execute("SELECT code FROM sys_permission ORDER BY code")
print("所有权限码:", [r[0] for r in cur.fetchall()])

cur.execute("SHOW TABLES LIKE 'sys_row_control_rule'")
print(f"sys_row_control_rule: {cur.fetchone()}")

conn.close()
print("\n✅ 数据库修复完成！")
