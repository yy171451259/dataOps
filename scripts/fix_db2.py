"""修复数据库表结构，使其与Java实体匹配"""
import pymysql
conn = pymysql.connect(host='192.168.16.163', port=3325, user='devuser', password='Dev#2026$', database='dataops_dms', autocommit=True)
cur = conn.cursor()

# 1. sys_user_role 添加 granted_by 列
try:
    cur.execute("ALTER TABLE sys_user_role ADD COLUMN granted_by VARCHAR(64) COMMENT '授予人ID' AFTER role_id")
    print("1. sys_user_role 添加 granted_by OK")
except Exception as e:
    print(f"1. sys_user_role granted_by: {e}")

try:
    cur.execute("ALTER TABLE sys_user_role ADD COLUMN granted_at DATETIME COMMENT '授予时间' AFTER granted_by")
    print("1b. sys_user_role 添加 granted_at OK")
except Exception as e:
    print(f"1b. sys_user_role granted_at: {e}")

# 2. sys_metadata_access_control 添加 deleted 列 (因为实体继承BaseEntity有deleted)
try:
    cur.execute("ALTER TABLE sys_metadata_access_control ADD COLUMN deleted TINYINT DEFAULT 0 COMMENT '逻辑删除'")
    print("2. sys_metadata_access_control 添加 deleted OK")
except Exception as e:
    print(f"2. sys_metadata_access_control deleted: {e}")

# 3. sys_sensitive_mask_rule 添加 deleted 列
try:
    cur.execute("ALTER TABLE sys_sensitive_mask_rule ADD COLUMN deleted TINYINT DEFAULT 0 COMMENT '逻辑删除'")
    print("3. sys_sensitive_mask_rule 添加 deleted OK")
except Exception as e:
    print(f"3. sys_sensitive_mask_rule deleted: {e}")

# 4. sys_resource_owner 添加 deleted 列
try:
    cur.execute("ALTER TABLE sys_resource_owner ADD COLUMN deleted TINYINT DEFAULT 0 COMMENT '逻辑删除'")
    print("4. sys_resource_owner 添加 deleted OK")
except Exception as e:
    print(f"4. sys_resource_owner deleted: {e}")

# 5. sys_metadata_access_control 添加 createTime/updateTime 等BaseEntity字段（如果缺少）
for col, col_def in [
    ("create_time", "DATETIME COMMENT '创建时间'"),
    ("update_time", "DATETIME COMMENT '更新时间'"),
    ("create_by", "VARCHAR(64) COMMENT '创建人'"),
    ("update_by", "VARCHAR(64) COMMENT '更新人'"),
]:
    try:
        cur.execute(f"ALTER TABLE sys_metadata_access_control ADD COLUMN {col} {col_def}")
        print(f"5. sys_metadata_access_control 添加 {col} OK")
    except Exception as e:
        pass  # 列已存在

# 6. sys_sensitive_mask_rule 添加 BaseEntity 需要的字段
for col, col_def in [
    ("create_time", "DATETIME COMMENT '创建时间'"),
    ("update_time", "DATETIME COMMENT '更新时间'"),
    ("create_by", "VARCHAR(64) COMMENT '创建人'"),
    ("update_by", "VARCHAR(64) COMMENT '更新人'"),
]:
    try:
        cur.execute(f"ALTER TABLE sys_sensitive_mask_rule ADD COLUMN {col} {col_def}")
        print(f"6. sys_sensitive_mask_rule 添加 {col} OK")
    except Exception as e:
        pass

# 7. sys_resource_owner 添加 update_time
for col, col_def in [
    ("update_time", "DATETIME COMMENT '更新时间'"),
]:
    try:
        cur.execute(f"ALTER TABLE sys_resource_owner ADD COLUMN {col} {col_def}")
        print(f"7. sys_resource_owner 添加 {col} OK")
    except Exception as e:
        pass

print("\nDone!")
conn.close()
