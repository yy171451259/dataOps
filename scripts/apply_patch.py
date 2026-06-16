import pymysql

conn = pymysql.connect(host='192.168.16.163', port=3325, user='devuser', password='Dev#2026$', database='dataops_dms')
cursor = conn.cursor()

patch_sqls = [
    "ALTER TABLE ticket ADD COLUMN use_online_ddl TINYINT DEFAULT 0 COMMENT 'DDL'",
    "ALTER TABLE ticket ADD COLUMN online_ddl_strategy VARCHAR(32) COMMENT 'DDL strategy'",
    "ALTER TABLE ticket ADD COLUMN ddl_progress INT DEFAULT 0 COMMENT 'DDL progress'",
    "ALTER TABLE ticket ADD COLUMN use_lock_free_dml TINYINT DEFAULT 0 COMMENT 'lock free DML'",
    "ALTER TABLE ticket ADD COLUMN dml_batch_size INT DEFAULT 1000 COMMENT 'DML batch size'",
    "ALTER TABLE ticket ADD COLUMN dml_batch_interval INT DEFAULT 100 COMMENT 'DML batch interval'",
    "ALTER TABLE ticket ADD COLUMN dml_batch_count INT DEFAULT 0 COMMENT 'DML batch count'",
    "ALTER TABLE ticket ADD COLUMN dml_total_affected BIGINT DEFAULT 0 COMMENT 'DML total affected'",
    "ALTER TABLE ticket_approval CHANGE COLUMN approved status VARCHAR(32) DEFAULT 'pending' COMMENT 'status'",
    "ALTER TABLE ticket_approval CHANGE COLUMN approve_time approved_at DATETIME COMMENT 'approved_at'",
]

for sql in patch_sqls:
    try:
        cursor.execute(sql)
        print(f"OK: {sql[:60]}...")
    except Exception as e:
        if 'Duplicate column' in str(e) or 'Unknown column' in str(e):
            print(f"SKIP: {str(e)}")
        else:
            print(f"ERROR: {e}")

conn.commit()
cursor.close()
conn.close()
print("Done!")
