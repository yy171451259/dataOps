import pymysql
conn = pymysql.connect(host='192.168.16.163', port=3325, user='devuser', password='Dev#2026$', database='dataops_dms', autocommit=True)
cur = conn.cursor()

# 检查当前角色权限
for role_id, role_code in [("role_dba","dba"), ("role_developer","developer"), ("role_approver","approver")]:
    cur.execute("""SELECT p.code FROM sys_role_permission rp 
        JOIN sys_permission p ON rp.permission_id=p.id 
        WHERE rp.role_id=%s ORDER BY p.code""", (role_id,))
    perms = [r[0] for r in cur.fetchall()]
    print(f"{role_code} 当前权限({len(perms)}): {perms}")

# DBA 应该有的完整权限
dba_all = [
    'sql:query','sql:update','sql:audit',
    'database:view','database:manage',
    'ticket:create','ticket:approve','ticket:rollback',
    'audit:view','masking:manage','quality:manage',
    'metadata:manage','pipeline:manage','monitor:view',
    'ddl:workbench','sensitive:view','sensitive:manage',
    'access:manage','owner:manage',
]
cur.execute("SELECT code FROM sys_permission WHERE code IN ({})".format(','.join(["'"+p+"'" for p in dba_all])))
all_codes = {r[0] for r in cur.fetchall()}

cur.execute("SELECT permission_id FROM sys_role_permission WHERE role_id='role_dba'")
existing = {r[0] for r in cur.fetchall()}

# 找出缺失的权限ID
cur.execute("SELECT id, code FROM sys_permission WHERE code IN ({})".format(','.join(["'"+p+"'" for p in dba_all])))
perm_id_map = {r[1]: r[0] for r in cur.fetchall()}

missing = [c for c in dba_all if c in all_codes and perm_id_map[c] not in existing]
print(f"\nDBA缺失权限: {missing}")

for code in missing:
    pid = perm_id_map[code]
    rid = f"rp_dba_{pid}"
    cur.execute("INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id) VALUES (%s,%s,%s)", (rid, 'role_dba', pid))
    print(f"  补充: dba <- {code}")

# Developer 应有权限
dev_all = ['sql:query','sql:audit','database:view','ticket:create','metadata:manage','monitor:view','ddl:workbench']
cur.execute("SELECT permission_id FROM sys_role_permission WHERE role_id='role_developer'")
dev_existing = {r[0] for r in cur.fetchall()}

dev_missing = [c for c in dev_all if perm_id_map.get(c) and perm_id_map[c] not in dev_existing]
print(f"\nDeveloper缺失权限: {dev_missing}")
for code in dev_missing:
    pid = perm_id_map[code]
    rid = f"rp_dev_{pid}"
    cur.execute("INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id) VALUES (%s,%s,%s)", (rid, 'role_developer', pid))
    print(f"  补充: developer <- {code}")

# Approver 应有权限
app_all = ['sql:query','database:view','ticket:approve','audit:view','monitor:view']
cur.execute("SELECT permission_id FROM sys_role_permission WHERE role_id='role_approver'")
app_existing = {r[0] for r in cur.fetchall()}

app_missing = [c for c in app_all if perm_id_map.get(c) and perm_id_map[c] not in app_existing]
print(f"\nApprover缺失权限: {app_missing}")
for code in app_missing:
    pid = perm_id_map[code]
    rid = f"rp_app_{pid}"
    cur.execute("INSERT IGNORE INTO sys_role_permission (id, role_id, permission_id) VALUES (%s,%s,%s)", (rid, 'role_approver', pid))
    print(f"  补充: approver <- {code}")

print("\nDone!")
conn.close()
