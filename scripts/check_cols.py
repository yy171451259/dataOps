import pymysql

conn = pymysql.connect(host='192.168.16.163', port=3325, user='devuser', password='Dev#2026$', database='dataops_dms')
cursor = conn.cursor()
cursor.execute("DESCRIBE database_instance")
for row in cursor.fetchall():
    print(row)
cursor.close()
conn.close()
