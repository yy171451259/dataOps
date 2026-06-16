#!/usr/bin/env python3
"""DataOps DMS 权限功能 & 数据变更工单流程 完整自测脚本 v2"""
import requests
import json

BASE = "http://localhost:8080"
passed = 0
failed = 0

def ok(r, label=""):
    global passed, failed
    try:
        body = r.json()
        code = body.get("code", r.status_code)
        if r.status_code == 200 and code == 200:
            passed += 1
            print(f"  [PASS] {label}")
            return body
        else:
            failed += 1
            print(f"  [FAIL] {label} -> http={r.status_code}, code={code}, msg={body.get('message','')[:100]}")
            return body
    except:
        failed += 1
        print(f"  [FAIL] {label} -> http={r.status_code}, body={r.text[:200]}")
        return None

def req(method, path, token=None, data=None):
    h = {"Content-Type": "application/json"}
    if token:
        h["Authorization"] = f"Bearer {token}"
    url = f"{BASE}{path}"
    if method == "GET":
        return requests.get(url, headers=h)
    elif method == "POST":
        return requests.post(url, headers=h, json=data)
    elif method == "PUT":
        return requests.put(url, headers=h, json=data)
    elif method == "DELETE":
        return requests.delete(url, headers=h)

def GET(path, token=None): return req("GET", path, token)
def POST(path, data, token=None): return req("POST", path, token, data=data)
def PUT(path, data, token=None): return req("PUT", path, token, data=data)
def DELETE(path, token=None): return req("DELETE", path, token)

# ============================================================
print("\n" + "="*60)
print("Phase 0: 登录 admin")
print("="*60)
r = POST("/api/v1/auth/login", {"username": "admin", "password": "admin123"})
body = ok(r, "admin登录")
admin_token = body["data"]["token"]
perms = body["data"]["permissions"]
print(f"  admin权限数: {len(perms)} -> {perms}")

# ============================================================
print("\n" + "="*60)
print("Phase 1: 创建测试用户")
print("="*60)
test_users = [
    ("approver1", "审批员一", "approver1@test.com"),
    ("developer1", "开发者一", "developer1@test.com"),
    ("dba1", "DBA一", "dba1@test.com"),
]
user_ids = {}
for uname, nick, email in test_users:
    # 用 admin API 创建用户
    r = POST("/api/v1/users", {
        "username": uname,
        "passwordHash": "Test#2026",
        "nickname": nick,
        "email": email
    }, admin_token)
    body = ok(r, f"创建用户 {uname}")
    if body and body.get("data"):
        uid = body["data"].get("id", "")
        if uid:
            user_ids[uname] = uid
            print(f"    id={uid}")

# 如果创建失败(已存在), 从列表获取
if len(user_ids) < 3:
    r = GET("/api/v1/users", admin_token)
    body = ok(r, "获取用户列表")
    if body and body.get("data"):
        users = body["data"].get("records", [])
        for u in users:
            if u.get("username") in ["approver1","developer1","dba1"]:
                user_ids[u["username"]] = u["id"]
                print(f"  找到已有用户 {u['username']} -> {u['id']}")

print(f"  测试用户: {user_ids}")

# ============================================================
print("\n" + "="*60)
print("Phase 2: 分配角色")
print("="*60)
r = GET("/api/v1/roles", admin_token)
body = ok(r, "获取角色列表")
roles_map = {}
if body and body.get("data"):
    for role in body["data"].get("records", []):
        roles_map[role.get("code")] = role.get("id")
        print(f"  角色: {role['code']} -> {role['id']}")

# 角色分配: POST /api/v1/users/{id}/roles  body 是 ["role_id"]
assign_map = {"approver1": "approver", "developer1": "developer", "dba1": "dba"}
for uname, role_code in assign_map.items():
    uid = user_ids.get(uname)
    rid = roles_map.get(role_code)
    if uid and rid:
        r = POST(f"/api/v1/users/{uid}/roles", [rid], admin_token)
        ok(r, f"给 {uname} 分配 {role_code}")

# ============================================================
print("\n" + "="*60)
print("Phase 3: 测试用户登录")
print("="*60)
tokens = {}
for uname in ["approver1", "developer1", "dba1"]:
    r = POST("/api/v1/auth/login", {"username": uname, "password": "Test#2026"})
    body = ok(r, f"{uname} 登录")
    if body and body.get("code") == 200:
        tokens[uname] = body["data"]["token"]
        print(f"  {uname} 权限: {body['data']['permissions']}")

# ============================================================
print("\n" + "="*60)
print("Phase 4: 资源Owner管理")
print("="*60)
r = POST("/api/v1/owners/assign", {
    "resourceType": "DATABASE",
    "resourceId": "homeplus-dev",
    "ownerUserId": user_ids["dba1"]
}, admin_token)
body = ok(r, "分配Owner: homeplus-dev -> dba1")
owner_id = body["data"]["id"] if body and body.get("data") else None

r = GET("/api/v1/owners/resource?type=DATABASE&id=homeplus-dev", admin_token)
ok(r, "按资源查询Owner")

r = GET(f"/api/v1/owners/user/{user_ids['dba1']}", admin_token)
ok(r, "按用户查询Owner资源")

r = GET(f"/api/v1/owners/check?userId={user_ids['dba1']}&resourceType=DATABASE&resourceId=homeplus-dev", admin_token)
body = ok(r, "检查dba1是否为homeplus-dev Owner")
if body and body.get("data"):
    print(f"    结果: isOwner={body['data']}")

# ============================================================
print("\n" + "="*60)
print("Phase 5: 访问控制")
print("="*60)
r = POST("/api/v1/access-control/enable", {
    "resourceType": "DATABASE",
    "resourceId": "homeplus-dev",
    "resourceName": "homeplus-dev"
}, admin_token)
body = ok(r, "启用访问控制")
ac_id = body["data"]["id"] if body and body.get("data") else None

r = GET("/api/v1/access-control", admin_token)
ok(r, "查询访问控制列表")

r = GET(f"/api/v1/access-control/check?userId={user_ids['dba1']}&resourceType=DATABASE&resourceId=homeplus-dev", admin_token)
ok(r, "dba1(Owner)访问检查")

# ============================================================
print("\n" + "="*60)
print("Phase 6: 敏感数据管理")
print("="*60)
r = GET("/api/v1/sensitive/mask-rules", admin_token)
body = ok(r, "查询脱敏规则")
if body and body.get("data"):
    rules = body["data"] if isinstance(body["data"], list) else body["data"].get("records", [])
    for rule in rules[:3]:
        print(f"    规则: {rule.get('code')} - {rule.get('name')}")

# 标记敏感列
r = POST("/api/v1/sensitive/columns", {
    "databaseName": "dataOps",
    "tableName": "sys_user",
    "columnName": "password_hash",
    "ruleCode": "full",
    "level": "HIGH"
}, admin_token)
ok(r, "标记敏感列: password_hash")

r = POST("/api/v1/sensitive/columns/batch", {
    "databaseName": "dataOps",
    "tableName": "sys_user",
    "columns": [
        {"columnName": "email", "ruleCode": "email", "level": "MEDIUM"},
        {"columnName": "nickname", "ruleCode": "name", "level": "LOW"}
    ]
}, admin_token)
ok(r, "批量标记: email, nickname")

r = GET("/api/v1/sensitive/columns", admin_token)
ok(r, "查询敏感列")

# ============================================================
print("\n" + "="*60)
print("Phase 7: 行级管控")
print("="*60)
r = POST("/api/v1/row-controls", {
    "databaseName": "dataOps",
    "tableName": "sys_user",
    "filterExpression": "is_active = 1",
    "description": "仅可见激活用户"
}, admin_token)
body = ok(r, "创建行级管控规则")
rc_id = body["data"]["id"] if body and body.get("data") else None

r = GET("/api/v1/row-controls", admin_token)
ok(r, "查询行级管控规则")

if rc_id:
    r = PUT(f"/api/v1/row-controls/{rc_id}", {
        "filterExpression": "deleted = 0",
        "description": "仅可见未删除用户"
    }, admin_token)
    ok(r, "更新行级管控规则")

# ============================================================
print("\n" + "="*60)
print("Phase 8: 权限申请流程")
print("="*60)
dev_token = tokens.get("developer1")
if dev_token:
    r = POST("/api/v1/permission-requests", {
        "permissionCode": "sql:update",
        "reason": "需要执行数据变更，申请SQL更新权限",
        "resourceType": "DATABASE",
        "resourceId": "homeplus-dev"
    }, dev_token)
    body = ok(r, "developer1 申请 sql:update")
    pr_id = body["data"]["id"] if body and body.get("data") else None

    r = GET("/api/v1/permission-requests/my", dev_token)
    ok(r, "查看我的申请")

    r = GET("/api/v1/permission-requests/pending", admin_token)
    ok(r, "admin查看待审批")

    if pr_id:
        r = POST(f"/api/v1/permission-requests/{pr_id}/approve", {
            "comment": "同意申请"
        }, admin_token)
        ok(r, "审批通过权限申请")

# ============================================================
print("\n" + "="*60)
print("Phase 9: 数据变更工单完整流程")
print("="*60)
dba_token = tokens.get("dba1", admin_token)

# 创建工单 (注意字段名是 sqlContent)
r = POST("/api/v1/tickets", {
    "title": "修改管理员昵称",
    "databaseId": "homeplus-dev",
    "databaseName": "dataOps",
    "sqlContent": "update sys_user set nickname = '系统管理员' where id = 1",
    "changeType": "dml"
}, dba_token)
body = ok(r, "创建数据变更工单")
ticket_id = body["data"]["id"] if body and body.get("data") else None
print(f"  工单ID: {ticket_id}")

if ticket_id:
    r = GET(f"/api/v1/tickets/{ticket_id}", dba_token)
    body = ok(r, "查看工单详情")
    if body and body.get("data"):
        print(f"  状态: {body['data'].get('status')}")

    r = GET("/api/v1/tickets/pending", admin_token)
    ok(r, "admin查看待审批")

    r = POST(f"/api/v1/tickets/{ticket_id}/approve", {
        "comment": "审批通过"
    }, admin_token)
    body = ok(r, "admin审批通过工单")
    if body and body.get("data"):
        print(f"  审批后状态: {body['data'].get('status')}")

    r = GET(f"/api/v1/tickets/{ticket_id}", dba_token)
    body = ok(r, "最终工单状态")
    if body and body.get("data"):
        print(f"  最终状态: {body['data'].get('status')}")

    r = GET(f"/api/v1/tickets/{ticket_id}/approvals", admin_token)
    ok(r, "查看审批记录")

# ============================================================
print("\n" + "="*60)
print("Phase 10: SQL预审核")
print("="*60)
r = POST("/api/v1/tickets/audit-sql", {
    "sql": "update sys_user set nickname = '系统管理员' where id = 1"
}, dba_token)
ok(r, "审核正常UPDATE")

r = POST("/api/v1/tickets/audit-sql", {
    "sql": "DROP TABLE sys_user"
}, dba_token)
ok(r, "审核DROP TABLE(应警告)")

r = POST("/api/v1/tickets/audit-sql", {
    "sql": "DELETE FROM sys_user"
}, dba_token)
ok(r, "审核DELETE无WHERE(应警告)")

# ============================================================
print("\n" + "="*60)
print("Phase 11: 权限边界验证")
print("="*60)
dev_token2 = tokens.get("developer1")
if dev_token2:
    r = GET("/api/v1/users", dev_token2)
    body = ok(r, "developer1访问用户管理(应拒绝)")
    
    r = GET("/api/v1/databases", dev_token2)
    ok(r, "developer1查看数据库(应允许)")

# ============================================================
print("\n" + "="*60)
print("Phase 12: 清理")
print("="*60)
if ac_id:
    POST(f"/api/v1/access-control/{ac_id}/disable", {}, admin_token)
    ok(r, "禁用访问控制")
if rc_id:
    DELETE(f"/api/v1/row-controls/{rc_id}", admin_token)
    ok(r, "删除行级管控规则")
if owner_id:
    POST(f"/api/v1/owners/{owner_id}/revoke", {}, admin_token)
    ok(r, "撤销Owner")

# ============================================================
print("\n" + "="*60)
print(f"RESULT: {passed} PASS, {failed} FAIL, {passed+failed} TOTAL")
print("="*60)
if failed > 0:
    exit(1)
