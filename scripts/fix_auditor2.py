import pymysql
c = pymysql.connect(host='192.168.16.163',port=3325,user='devuser',password='Dev#2026$',database='dataops_dms',autocommit=True)
cur = c.cursor()
cur.execute("INSERT IGNORE INTO sys_role_permission (id,role_id,permission_id) VALUES ('rp_aud_dbv','role_auditor','perm_db_view')")
cur.execute("SELECT p.code FROM sys_role_permission rp JOIN sys_permission p ON rp.permission_id=p.id WHERE rp.role_id='role_auditor' ORDER BY p.code")
print('auditor:', [r[0] for r in cur.fetchall()])
c.close()
