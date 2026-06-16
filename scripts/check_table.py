import pymysql
conn = pymysql.connect(host='192.168.16.163', port=3325, user='devuser', password='Dev#2026$', database='dataops_dms', autocommit=True)
cur = conn.cursor()

# 查看表结构
cur.execute("DESC sys_role_permission")
print("sys_role_permission columns:")
for r in cur.fetchall():
    print(f"  {r}")

cur.execute("DESC sys_user_role")
print("\nsys_user_role columns:")
for r in cur.fetchall():
    print(f"  {r}")

cur.execute("DESC sys_permission")
print("\nsys_permission columns:")
for r in cur.fetchall():
    print(f"  {r}")

conn.close()
