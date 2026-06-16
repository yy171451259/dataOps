"""
Complete fix: find all remaining corrupted bytes and fix them,
then apply character corrections.
"""
import os, sys

pages_dir = r'c:\Users\Administrator\Documents\dataOps\frontend\src\pages'

files = [
    'AuditList.tsx', 'DashboardPage.tsx', 'DataImportPage.tsx',
    'DataMaskingPage.tsx', 'DataQualityPage.tsx', 'DatabaseList.tsx',
    'DatabaseMonitorPage.tsx', 'MetadataPage.tsx',
    'NotificationSettingsPage.tsx', 'PipelinePage.tsx', 'SchemaDesignerPage.tsx',
    'SystemSettingsPage.tsx', 'UserManagementPage.tsx',
]

# First pass: fix remaining invalid UTF-8 at byte level
# Pattern: XX XX 3F where XX XX are first 2 bytes of a 3-byte UTF-8 seq
# Also handle cases where the fix script missed some patterns
for fname in files:
    fpath = os.path.join(pages_dir, fname)
    with open(fpath, 'rb') as f:
        raw = bytearray(f.read())
    
    fixes = 0
    i = 0
    while i < len(raw) - 2:
        b0, b1, b2 = raw[i], raw[i+1], raw[i+2]
        # Check for corrupted 3-byte UTF-8 (3rd byte is 0x3F)
        if 0xe0 <= b0 <= 0xef and 0x80 <= b1 <= 0xbf and b2 == 0x3f:
            # Replace 3F with 80 to make valid UTF-8
            raw[i+2] = 0x80
            # Determine what quote to add
            next_byte = raw[i+3] if i+3 < len(raw) else 0
            if next_byte not in (0x27, 0x22):  # no quote follows
                # Need to add closing quote
                # Search backwards for opening quote
                quote = 0x27  # default '
                for j in range(i-1, max(0, i-200), -1):
                    if raw[j] == 0x22:  # "
                        # Count " between j and i
                        cnt = sum(1 for k in range(j+1, i) if raw[k] == 0x22)
                        if cnt % 2 == 0:
                            quote = 0x22
                        break
                    elif raw[j] == 0x27:  # '
                        cnt = sum(1 for k in range(j+1, i) if raw[k] == 0x27)
                        if cnt % 2 == 0:
                            quote = 0x27
                        break
                # Insert quote after the fixed char
                raw.insert(i+3, quote)
            fixes += 1
        i += 1
    
    if fixes > 0:
        with open(fpath, 'wb') as f:
            f.write(bytes(raw))
        sys.stdout.write(f"{fname}: fixed {fixes} remaining corrupted bytes\n")

sys.stdout.write("\n--- Phase 2: Character corrections ---\n")

# Second pass: fix wrong characters
# Map of wrong_char -> correct_char based on context
# Using longer context strings to avoid ambiguity
context_corrections = [
    # Status labels
    ('待审曀', '待审批'),
    ('已拒砀', '已拒绝'),
    ('已完愀', '已完成'),
    ('已回渀', '已回滚'),
    ('已发帀', '已发布'),
    ('已关闀', '已关闭'),
    ('已复刀', '已复制'),
    ('全部状怀', '全部状态'),
    ('状怀', '状态'),
    # Risk levels
    ("'佀'", "'低'"),
    ("'一'", "'中'"),
    ("'髀'", "'高'"),
    ('低风佀', '低风险'),
    ('中风一', '中风险'),
    ('高风险髀', '高风险'),
    ('低风险陀', '低风险'),
    ('中风陀', '中风险'),
    ('高风险陀', '高风险'),
    # Common field names
    ('用户吀', '用户名'),
    ('密砀', '密码'),
    ('字段吀', '字段名'),
    ('项目名秀', '项目名称'),
    ('系统名秀', '系统名称'),
    ('业务吀', '业务名'),
    ('输入描迀', '输入描述'),
    # Database
    ('数据库实侀', '数据库实例'),
    ('数据库类刑', '数据库类型'),
    ('选择数据庀', '选择数据库'),
    ('数据庀', '数据库'),
    # Environment
    ('开发环叀', '开发环境'),
    ('测试环叀', '测试环境'),
    ('预发布环叀', '预发布环境'),
    ('生产环叀', '生产环境'),
    # Pipeline states
    ('开发完戀', '开发完成'),
    ('设计一', '设计中'),
    ('工单叀', '工单号'),
    ('工单已完戀', '工单已完成'),
    # Actions
    ('删除＀', '删除'),
    ('关闭工单＀', '关闭工单'),
    ('确定关闭＀', '确定关闭'),
    ('阶段已推迀', '阶段已推进'),
    ('项目已关闀', '项目已关闭'),
    # Table operations
    ('新建物理血', '新建物理表'),
    ('添加已有血', '添加已有表'),
    ('或添加新血', '或添加新表'),
    ('无表，请添劀', '无表，请添加'),
    ('除此表变更＀', '除此表变更'),
    ('更到基准库＀', '更到基准库'),
    ('变更相关亀', '变更相关库'),
    ('此工區', '此工单'),
    ('表结构設讀', '表结构设计'),
    ('新建表设讀', '新建表设计'),
    ('最近修攀', '最近修改'),
    ('变更SQL汇怀', '变更SQL汇总'),
    ('配置新字殀', '配置新字段'),
    ('数字下划纀', '数字下划线'),
    ('无符叀', '无符号'),
    ('默认倀', '默认值'),
    ('CREATE TABLE 咀', 'CREATE TABLE 或'),
    ('提交DDL变更刀', '提交DDL变更'),
    ('目标数据库实侀', '目标数据库实例'),
    ('负责人亀', '负责人'),
    ('负责亀', '负责人'),
    ('开叀', '开发'),
    ('优先级纀', '优先级'),
    # Audit
    ('表结构变曀', '表结构变更'),
    ('开发完戀/', '开发完成/'),
    ('已发帀/', '已发布/'),
    ('已关闀/', '已关闭/'),
    ('设计中/', '设计中/'),
    # Monitor
    ('InnoDB缓冲命中窰', 'InnoDB缓冲命中率'),
    ('suffix="窰"', 'suffix="率"'),
    ('行读叀', '行读取'),
    ('行插叀', '行插入'),
    # Settings
    ('设置默认倀', '设置默认值'),
    ('已恢复默认设罀', '已恢复默认设置'),
    ('简体中文斀', '简体中文'),
    ('开吀', '开启'),
    ('更新间隔（秒＀', '更新间隔（秒）'),
    ('更前自动备什', '更前自动备份'),
    ('例如＀', '例如：'),
    ('发件人邮简', '发件人邮箱'),
    ('管理平台，提侀', '管理平台，提供'),
    ('工作浀', '工作流'),
    # Users
    ('所有角艀', '所有角色'),
    ('用户的角艀', '用户的角色'),
    ('分配角艀', '分配角色'),
    ('无角艀', '无角色'),
    ('管理呀', '管理员'),
    ('gold">昀', 'gold">是'),
    ('>吀<', '>否<'),
    # Meta
    ("'昀'", "'是'"),
    ("'吀'", "'否'"),
    # Notification
    ('测试通知已发', '测试通知已发送'),
    ('接收亀', '接收人'),
    # Import
    ('选择目标表或输入新表吀', '选择目标表或输入新表名'),
    ('选择数据渀', '选择数据源'),
    # Masking
    ('手机号脱', '手机号脱敏'),
    ('身份证脱', '身份证脱敏'),
    ('银行卡脱', '银行卡脱敏'),
    # Pipeline specific
    ('状态颜色映尀', '状态颜色映射'),
    ('待执尀', '待执行'),
    ('审批尀', '审批中'),
    ('执行尀', '执行中'),
]

total = 0
for fname in files + ['LoginPage.tsx']:
    fpath = os.path.join(pages_dir, fname)
    try:
        with open(fpath, 'r', encoding='utf-8') as f:
            content = f.read()
    except UnicodeDecodeError as e:
        sys.stdout.write(f"WARN: {fname} still has encoding errors: {e}\n")
        continue
    
    original = content
    file_fixes = 0
    for old, new in context_corrections:
        if old != new and old in content:
            count = content.count(old)
            content = content.replace(old, new)
            file_fixes += count
    
    if content != original:
        with open(fpath, 'w', encoding='utf-8') as f:
            f.write(content)
        sys.stdout.write(f"{fname}: {file_fixes} corrections\n")
        total += file_fixes

sys.stdout.write(f"\nTotal corrections: {total}\n")
sys.stdout.flush()
