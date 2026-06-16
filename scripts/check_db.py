import pymysql
conn = pymysql.connect(host='192.168.16.163', port=3325, user='devuser', password='Dev#2026$', database='dataops_dms')
cur = conn.cursor()

# 检查表
cur.execute("SHOW TABLES LIKE 'sys_role_permission'")
print('sys_role_permission:', cur.fetchone())
cur.execute("SHOW TABLES LIKE 'sys_permission'")
print('sys_permission:', cur.fetchone())
cur.execute("SHOW TABLES LIKE 'sys_user_role'")
print('sys_user_role:', cur.fetchone())
cur.execute("SHOW TABLES LIKE 'sys_row_control_rule'")
print('sys_row_control_rule:', cur.fetchone())

# 权限码
cur.execute('SELECT code, name FROM sys_permission ORDER BY code')
perms = cur.fetchall()
print(f'\n权限码总数: {len(perms)}')
for p in perms:
    print(f'  {p[0]:25s} {p[1]}')

# admin角色
cur.execute('SELECT ur.user_id, ur.role_id, r.code, r.name FROM sys_user_role ur JOIN sys_role r ON ur.role_id=r.id WHERE ur.user_id="user_admin"')
print('\nadmin角色:', cur.fetchall())

# admin权限
cur.execute('SELECT DISTINCT p.code FROM sys_role_permission rp JOIN sys_permission p ON rp.permission_id=p.id JOIN sys_user_role ur ON rp.role_id=ur.role_id WHERE ur.user_id="user_admin"')
perms = cur.fetchall()
print(f'\nadmin实际权限: {[p[0] for p in perms]}')

# 角色列表
cur.execute('SELECT id, code, name FROM sys_role')
print('\n角色:')
for r in cur.fetchall():
    print(f'  {r[0]:20s} {r[1]:20s} {r[2]}')

conn.close()
