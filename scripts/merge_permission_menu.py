# -*- coding: utf-8 -*-
p = r'C:\Users\Administrator\Documents\dataOps\frontend\src\App.tsx'
with open(p, 'r', encoding='utf-8') as f:
    c = f.read()

old = "    { key: '/permissions', icon: <LockOutlined />, label: '权限管理', permission: 'permission:manage' },\n    { key: '/resource-owners', icon: <CrownOutlined />, label: '资源Owner', permission: 'owner:manage' },\n    { key: '/access-control', icon: <SafetyOutlined />, label: '访问控制', permission: 'access:manage' },\n    { key: '/sensitive-data', icon: <EyeInvisibleOutlined />, label: '敏感数据', permission: 'sensitive:view' },\n    { key: '/permission-requests', icon: <SendOutlined />, label: '权限申请' },"
new = "    { key: 'perm-group', icon: <LockOutlined />, label: '权限管理', children: [\n      { key: '/permissions', icon: <SettingOutlined />, label: '权限配置', permission: 'permission:manage' },\n      { key: '/permission-requests', icon: <SendOutlined />, label: '权限申请' },\n    ] },\n    { key: '/resource-owners', icon: <CrownOutlined />, label: '资源Owner', permission: 'owner:manage' },\n    { key: '/access-control', icon: <SafetyOutlined />, label: '访问控制', permission: 'access:manage' },\n    { key: '/sensitive-data', icon: <EyeInvisibleOutlined />, label: '敏感数据', permission: 'sensitive:view' },"
assert old in c, 'old not found!'
c = c.replace(old, new)
with open(p, 'w', encoding='utf-8') as f:
    f.write(c)
print('OK')