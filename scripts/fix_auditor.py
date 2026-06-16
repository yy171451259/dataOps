import pymysql
c = pymysql.connect(host='192.168.16.163',port=3325,user='devuser',password='Dev#2026$',database='dataops_dms',autocommit=True)
cur = c.cursor()
perms = [('perm_sql_query','sql:query'),('perm_db_view','database:view'),('perm_ticket_approve','ticket:approve'),('perm_audit_view','audit:view'),('perm_monitor_view','monitor:view')]
for pid, code in perms:
    cur.execute("INSERT IGNORE INTO sys_role_permission (id,role_id,permission_id) VALUES (%s,'role_auditor',%s)", (f"rp_aud_{pid}", pid))
cur.execute("SELECT p.code FROM sys_role_permission rp JOIN sys_permission p ON rp.permission_id=p.id WHERE rp.role_id='role_auditor'")
print('auditor:', [r[0] for r in cur.fetchall()])
c.close()
print("Done")
