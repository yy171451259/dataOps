"""执行 upgrade_v7.sql 数据库升级脚本"""
import pymysql
import sys
import re

SQL_FILE = r"c:\Users\Administrator\Documents\dataOps\backend\src\main\resources\sql\upgrade_v7.sql"

DB_CONFIG = {
    "host": "192.168.16.163",
    "port": 3325,
    "user": "devuser",
    "password": "Dev#2026$",
    "database": "dataops_dms",
    "charset": "utf8mb4",
}

def main():
    with open(SQL_FILE, "r", encoding="utf-8") as f:
        sql_content = f.read()

    # 去掉注释行后，按分号拆分
    # 保留以 -- 开头注释后面的 SQL 语句
    lines = sql_content.split("\n")
    clean_lines = []
    for line in lines:
        stripped = line.strip()
        if stripped.startswith("--") and not any(kw in stripped.upper() for kw in ["ALTER", "UPDATE", "INSERT", "DELETE", "CREATE", "DROP", "SELECT", "SET"]):
            continue  # 纯注释行
        clean_lines.append(line)
    
    clean_sql = "\n".join(clean_lines)
    
    # 按分号拆分，执行非空语句
    statements = [s.strip() for s in clean_sql.split(";")]
    statements = [s for s in statements if s]

    conn = pymysql.connect(**DB_CONFIG)
    cursor = conn.cursor()

    try:
        for i, stmt in enumerate(statements):
            print(f"[{i+1}/{len(statements)}] 执行: {stmt[:100]}...")
            cursor.execute(stmt)
            print(f"  -> 成功, 影响行数: {cursor.rowcount}")

        conn.commit()
        print("\n✓ upgrade_v7.sql 执行成功！")

        # 验证结果
        cursor.execute("SELECT code, name, mask_algorithm FROM sys_sensitive_mask_rule")
        print("\n当前脱敏规则算法映射:")
        for row in cursor.fetchall():
            print(f"  {row[0]:12s} | {row[1]:10s} | {row[2] or 'NULL'}")

    except Exception as e:
        conn.rollback()
        print(f"\n✗ 执行失败: {e}", file=sys.stderr)
        sys.exit(1)
    finally:
        cursor.close()
        conn.close()

if __name__ == "__main__":
    main()
