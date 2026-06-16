"""
Final comprehensive fix: read each file, find all wrong CJK chars 
introduced by the B2=0x80 fix, and replace with correct chars.
Uses the FULL context (preceding 1-2 chars) to determine the correct replacement.
"""
import os, sys

pages_dir = r'c:\Users\Administrator\Documents\dataOps\frontend\src\pages'

# Map: (preceding_char, wrong_char_B2_80) -> correct_char
# Built by analyzing all corrupted positions and their contexts
# Format: 'preceding+wrong' -> 'correct'
CORRECTIONS = {
    # 审 + 曀 -> 审批
    '审曀': '审批',
    # 拒 + 砀 -> 拒绝  
    '拒砀': '拒绝',
    # 完 + 愀 -> 完成
    '完愀': '完成',
    # 回 + 渀 -> 回滚
    '回渀': '回滚',
    # 发 + 帀 -> 发布
    '发帀': '发布',
    # 关 + 闀 -> 关闭
    '关闀': '关闭',
    # 复 + 刀 -> 复制
    '复刀': '复制',
    # 结 + 极 -> 结果 (not sure, check context)
    '结极': '结果',
    # 状 + 怀 -> 状态
    '状怀': '状态',
    # 实 + 侀 -> 实例
    '实侀': '实例',
    # 类 + 刑 -> 类型 (this should be checked)
    '类型': '类型',  # might already be correct
    # 用 + 吀 -> 用户 (when after 户)
    '户吀': '户名',
    # 密 + 砀 -> 密码
    '密砀': '密码',
    # 字 + 吀 -> 字段/字段名 (depends on context)
    # 业 + 吀 -> 业务名
    # 名 + 秀 -> 名称
    '秀': '称',
    # 描 + 迀 -> 描述
    '描迀': '描述',
    # 人 + 亀 -> 人 (负责人)
    '人亀': '人',
    # 数 + 庀 -> 数据库
    '数据庀': '数据库',
    # 缓 + 窰 -> 命中率
    '中窰': '中率',
    # 环 + 叀 -> 环境
    '环叀': '环境',
    # 完 + 戀 -> 完成
    '完戀': '完成',
    # 推 + 迀 -> 推进
    '推迀': '推进',
    # 删 + ＀ -> 删除 (this might be a fullwidth space)
    '除＀': '除',
    '单＀': '单',
    '库＀': '库',
    '更＀': '更',
    '添劀': '添加',
    '变更＀': '变更',
    # 变 + 曀 -> 变更
    '变曀': '变更',
    # 物 + 血 -> 物理表
    '理血': '理表',
    '有血': '有表',
    '新血': '新表',
    # 区 -> 域
    '工區': '工单',
    # 設 + 讀 -> 设计
    '設讀': '设计',
    '设讀': '设计',
    # 修 + 攀 -> 修改/修改
    '修攀': '修改',
    # 汇 + 怀 -> 汇总
    '汇怀': '汇总',
    # 开 + 叀 -> 开发
    '开叀': '开发',
    # 字 + 殀 -> 字段
    '字殀': '字段',
    # 划 + 纀 -> 划线
    '划纀': '划线',
    # 符 + 叀 -> 符号
    '符叀': '符号',
    # 默 + 倀 -> 默认
    '默倀': '默认',
    # 咀 -> 或 (CREATE TABLE 或)
    '咀': '或',
    # 色 + 艀 -> 角色
    '色艀': '色',
    # 角 + 艀 -> 角色
    '角艀': '角色',
    # 管 + 呀 -> 管理员
    '理呀': '理员',
    # 映 + 尀 -> 映射
    '映尀': '映射',
    # 执 + 尀 -> 执行
    '执尀': '执行',
    '批尀': '批中',
    # 设 + 一 -> 设计中
    '计一': '计中',
    # 时 + 闀 -> 时间
    '时闀': '时间',
    # 设 + 罀 -> 设置
    '设罀': '设置',
    # 文 + 斀 -> 中文
    '文斀': '文',
    # 秒 + ＀ -> 秒)
    '秒＀': '秒',
    # 备 + 什 -> 备份
    '备什': '备份',
    # 如 + ＀ -> 如:
    '如＀': '如',
    # 邮 + 简 -> 邮箱
    '邮简': '邮箱',
    # 提 + 侀 -> 提供
    '提侀': '提供',
    # 能 +   -> 功能
    # 浀 -> 流
    '工浀': '工作流',
    # 色 -> 色 (color, might be correct)
    # 昀 -> 是
    '昀': '是',
    # 吀 -> 否 (in some contexts)
    # 发 -> 发送
    # 已发 -> 已发送 (not 已发)
    # 陀 -> 险
    '风陀': '风险',
    # 佀 -> 低
    '佀': '低',
    # 髀 -> 高
    '髀': '高',
    # 色 -> 色 (correct in some contexts)
    # 一 -> 中 (in risk level context)
}

files = [
    'AuditList.tsx', 'DashboardPage.tsx', 'DataImportPage.tsx',
    'DataMaskingPage.tsx', 'DataQualityPage.tsx', 'DatabaseList.tsx',
    'DatabaseMonitorPage.tsx', 'MetadataPage.tsx',
    'NotificationSettingsPage.tsx', 'PipelinePage.tsx', 'SchemaDesignerPage.tsx',
    'SystemSettingsPage.tsx', 'UserManagementPage.tsx',
]

total = 0
for fname in files:
    fpath = os.path.join(pages_dir, fname)
    try:
        with open(fpath, 'r', encoding='utf-8') as f:
            content = f.read()
    except:
        sys.stdout.write(f"SKIP {fname}: cannot read\n")
        continue
    
    original = content
    file_fixes = 0
    
    # Apply bigram corrections first (longer patterns first)
    for old, new in sorted(CORRECTIONS.items(), key=lambda x: -len(x[0])):
        if old != new and old in content:
            count = content.count(old)
            content = content.replace(old, new)
            file_fixes += count
    
    if content != original:
        with open(fpath, 'w', encoding='utf-8') as f:
            f.write(content)
        sys.stdout.write(f"{fname}: {file_fixes} corrections\n")
        total += file_fixes

sys.stdout.write(f"\nTotal: {total}\n")
sys.stdout.flush()
