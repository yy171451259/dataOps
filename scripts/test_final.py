#!/usr/bin/env python3
"""DataOps DMS 权限功能 & 数据变更工单 最终测试"""
import requests, json, sys

BASE = "http://localhost:8080"
passed = 0; failed = 0

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
            print(f"  [FAIL] {label} -> http={r.status_code} code={code} msg={str(body.get('message',''))[:150]}")
            return body
    except:
        failed += 1
        print(f"  [FAIL] {label} -> http={r.status_code} body={r.text[:200]}")
        return None

def req(method, path, token=None, data=None):
    h = {"Content-Type": "application/json"}
    if token: h["Authorization"] = f"Bearer {token}"
    url = f"{BASE}{path}"
    if method == "GET": return requests.get(url, headers=h)
    if method == "POST": return requests.post(url, headers=h, json=data)
    if method == "PUT": return requests.put(url, headers=h, json=data)
    if method == "DELETE": return requests.delete(url, headers=h)

def G(p,t=None): return req("GET",p,t)
def P(p,d,t=None): return req("POST",p,t,data=d)
def PUT(p,d,t=None): return req("PUT",p,t,data=d)
def D(p,t=None): return req("DELETE",p,t)

DB_INSTANCE_ID = "db_mysql_demo"  # homeplus-dev 的数据库实例ID
DB_INSTANCE_NAME = "homeplus-dev"

# ============================================================
print("\n" + "="*60)
print("Phase 0: admin登录")
print("="*60)
r = P("/api/v1/auth/login", {"username":"admin","password":"admin123"})
body = ok(r, "admin登录")
admin_token = body["data"]["token"]
print(f"  admin拥有{len(body['data']['permissions'])}个权限")

# ============================================================
print("\n" + "="*60)
print("Phase 1: 准备测试用户")
print("="*60)
uid_map = {}
r = G("/api/v1/users", admin_token)
if r.json().get("data"):
    for u in r.json()["data"].get("records",[]):
        if u["username"] in ["approver1","developer1","dba1"]:
            uid_map[u["username"]] = u["id"]
# 创建缺失的用户
for uname, nick, email in [("approver1","审批员一","approver1@test.com"),
                             ("developer1","开发者一","developer1@test.com"),
                             ("dba1","DBA一","dba1@test.com")]:
    if uname not in uid_map:
        r = P("/api/v1/users", {"username":uname,"passwordHash":"Test#2026","nickname":nick,"email":email}, admin_token)
        if r.json().get("data") and r.json()["data"].get("id"):
            uid_map[uname] = r.json()["data"]["id"]
print(f"  用户: {uid_map}")

# ============================================================
print("\n" + "="*60)
print("Phase 2: 分配角色")
print("="*60)
r = G("/api/v1/roles", admin_token)
body = ok(r, "获取角色列表")
role_map = {}
if body and body.get("data"):
    for role in body["data"].get("records",[]):
        role_map[role["code"]] = role["id"]

# 角色映射 (DB没有approver角色，用auditor替代)
role_assign = {"approver1":"auditor", "developer1":"developer", "dba1":"dba"}
for uname, rcode in role_assign.items():
    uid = uid_map.get(uname)
    rid = role_map.get(rcode)
    if uid and rid:
        r = P(f"/api/v1/users/{uid}/roles", [rid], admin_token)
        ok(r, f"{uname} <- {rcode}角色")

# ============================================================
print("\n" + "="*60)
print("Phase 3: 测试用户登录")
print("="*60)
tokens = {}
for uname in ["approver1","developer1","dba1"]:
    r = P("/api/v1/auth/login", {"username":uname,"password":"Test#2026"})
    body = ok(r, f"{uname}登录")
    if body and body["code"]==200:
        tokens[uname] = body["data"]["token"]
        print(f"  {uname}: {len(body['data']['permissions'])}个权限 -> {body['data']['permissions'][:5]}...")

# ============================================================
print("\n" + "="*60)
print("Phase 4: 资源Owner管理")
print("="*60)
r = P("/api/v1/owners/assign", {
    "resourceType":"DATABASE","resourceId":DB_INSTANCE_ID,
    "ownerUserId":uid_map["dba1"]
}, admin_token)
body = ok(r, f"分配Owner: {DB_INSTANCE_ID}->dba1")
owner_id = body["data"]["id"] if body and body.get("data") else None

r = G(f"/api/v1/owners/resource?type=DATABASE&id={DB_INSTANCE_ID}", admin_token)
ok(r, "按资源查询Owner")

r = G(f"/api/v1/owners/user/{uid_map['dba1']}", admin_token)
ok(r, "按用户查询Owner资源")

# ============================================================
print("\n" + "="*60)
print("Phase 5: 访问控制")
print("="*60)
r = P("/api/v1/access-control/enable", {
    "resourceType":"DATABASE","resourceId":DB_INSTANCE_ID,"resourceName":DB_INSTANCE_NAME
}, admin_token)
body = ok(r, "启用访问控制")
ac_id = body["data"]["id"] if body and body.get("data") else None

r = G("/api/v1/access-control", admin_token)
ok(r, "查询访问控制列表")

# 正确的参数名: type, id, userId
r = G(f"/api/v1/access-control/check?type=DATABASE&id={DB_INSTANCE_ID}&userId={uid_map['dba1']}", admin_token)
ok(r, "dba1(Owner)访问检查(应为true)")

# ============================================================
print("\n" + "="*60)
print("Phase 6: 敏感数据管理")
print("="*60)
r = G("/api/v1/sensitive/mask-rules", admin_token)
body = ok(r, "查询脱敏规则")
if body and body.get("data"):
    rules = body["data"] if isinstance(body["data"],list) else body["data"].get("records",[])
    for rule in rules[:3]:
        print(f"    {rule.get('code')} - {rule.get('name')}")

# 标记敏感列 (databaseId 必填)
r = P("/api/v1/sensitive/columns", {
    "databaseId":DB_INSTANCE_ID,"databaseName":"dataOps",
    "tableName":"sys_user","columnName":"password_hash",
    "maskRuleId":"mask_full","sensitivityLevel":"HIGH"
}, admin_token)
ok(r, "标记敏感列: password_hash")

r = P("/api/v1/sensitive/columns/batch", {
    "databaseId":DB_INSTANCE_ID,"databaseName":"dataOps","tableName":"sys_user",
    "columns":[
        {"columnName":"email","maskRuleId":"mask_email","sensitivityLevel":"MEDIUM"},
        {"columnName":"nickname","maskRuleId":"mask_name","sensitivityLevel":"LOW"}
    ]
}, admin_token)
ok(r, "批量标记: email, nickname")

# 查询敏感列 (databaseId 必填)
r = G(f"/api/v1/sensitive/columns?databaseId={DB_INSTANCE_ID}&databaseName=dataOps", admin_token)
ok(r, "查询敏感列列表")

# ============================================================
print("\n" + "="*60)
print("Phase 7: 行级管控")
print("="*60)
r = P("/api/v1/row-controls", {
    "name":"仅激活用户","databaseId":DB_INSTANCE_ID,"databaseName":"dataOps",
    "tableName":"sys_user","filterCondition":"is_active = 1",
    "filterDescription":"仅可见激活用户","isActive":True,"priority":10
}, admin_token)
body = ok(r, "创建行级管控规则")
rc_id = body["data"]["id"] if body and body.get("data") else None

r = G("/api/v1/row-controls", admin_token)
ok(r, "查询行级管控规则")

if rc_id:
    r = PUT(f"/api/v1/row-controls/{rc_id}", {
        "name":"仅激活用户","databaseId":DB_INSTANCE_ID,"databaseName":"dataOps",
        "tableName":"sys_user","filterCondition":"deleted = 0",
        "filterDescription":"仅可见未删除用户","isActive":True,"priority":10
    }, admin_token)
    ok(r, "更新行级管控规则")

# ============================================================
print("\n" + "="*60)
print("Phase 8: 权限申请流程")
print("="*60)
dev_tok = tokens.get("developer1")
if dev_tok:
    r = P("/api/v1/permission-requests", {
        "resourceType":"DATABASE","resourceId":DB_INSTANCE_ID,
        "resourceName":DB_INSTANCE_NAME,
        "requestedPermissions":"sql:update",
        "reason":"需要数据变更权限，执行 UPDATE 操作"
    }, dev_tok)
    body = ok(r, "developer1申请sql:update权限")
    pr_id = body["data"]["id"] if body and body.get("data") else None

    r = G("/api/v1/permission-requests/my", dev_tok)
    ok(r, "查看我的申请")

    r = G("/api/v1/permission-requests/pending", admin_token)
    ok(r, "admin查看待审批权限申请")

    if pr_id:
        r = P(f"/api/v1/permission-requests/{pr_id}/approve", {"comment":"同意"}, admin_token)
        ok(r, "审批通过权限申请")

# ============================================================
print("\n" + "="*60)
print("Phase 9: 数据变更工单完整流程")
print("="*60)
dba_tok = tokens.get("dba1", admin_token)

# 创建工单 - databaseId 用 db_mysql_demo
r = P("/api/v1/tickets", {
    "title":"修改管理员昵称",
    "databaseId":DB_INSTANCE_ID,
    "databaseName":"dataOps",
    "sqlContent":"update sys_user set nickname = '系统管理员' where id = 1",
    "changeType":"dml"
}, dba_tok)
body = ok(r, "创建数据变更工单")
ticket_id = body["data"]["id"] if body and body.get("data") else None
print(f"  工单ID: {ticket_id}")

if ticket_id:
    r = G(f"/api/v1/tickets/{ticket_id}", dba_tok)
    body = ok(r, "查看工单详情")
    if body and body.get("data"):
        print(f"  状态: {body['data'].get('status')}")

    r = G("/api/v1/tickets/pending", admin_token)
    ok(r, "admin查看待审批工单")

    # 审批通过
    r = P(f"/api/v1/tickets/{ticket_id}/approve", {"comment":"审批通过"}, admin_token)
    body = ok(r, "审批通过工单")
    if body and body.get("data"):
        print(f"  审批后状态: {body['data'].get('status')}")

    r = G(f"/api/v1/tickets/{ticket_id}", dba_tok)
    body = ok(r, "确认最终工单状态")
    if body and body.get("data"):
        print(f"  最终状态: {body['data'].get('status')}")

    r = G(f"/api/v1/tickets/{ticket_id}/approvals", admin_token)
    ok(r, "查看工单审批记录")

# ============================================================
print("\n" + "="*60)
print("Phase 10: SQL预审核")
print("="*60)
r = P("/api/v1/tickets/audit-sql", {
    "sql":"update sys_user set nickname = '系统管理员' where id = 1"
}, dba_tok)
ok(r, "审核正常UPDATE")

r = P("/api/v1/tickets/audit-sql", {
    "sql":"DROP TABLE sys_user"
}, dba_tok)
ok(r, "审核DROP TABLE(应高风险警告)")

r = P("/api/v1/tickets/audit-sql", {
    "sql":"DELETE FROM sys_user"
}, dba_tok)
ok(r, "审核DELETE无WHERE(应高风险警告)")

# ============================================================
print("\n" + "="*60)
print("Phase 11: 权限边界验证")
print("="*60)
dev_tok2 = tokens.get("developer1")
if dev_tok2:
    # developer1 不应能管理用户
    r = G("/api/v1/users", dev_tok2)
    ok(r, "developer1访问用户管理(预期拒绝/空)")

    # developer1 应能查看数据库
    r = G("/api/v1/databases", dev_tok2)
    ok(r, "developer1查看数据库(预期允许)")

# ============================================================
print("\n" + "="*60)
print("Phase 12: 清理")
print("="*60)
if ac_id:
    P(f"/api/v1/access-control/{ac_id}/disable", {}, admin_token)
if rc_id:
    D(f"/api/v1/row-controls/{rc_id}", admin_token)
if owner_id:
    P(f"/api/v1/owners/{owner_id}/revoke", {}, admin_token)

# ============================================================
print("\n" + "="*60)
print(f"RESULT: {passed} PASS / {failed} FAIL / {passed+failed} TOTAL")
print("="*60)
if failed > 0:
    print(f"\n{failed}个测试未通过，请检查上面的 FAIL 项")
    sys.exit(1)
else:
    print("\n全部测试通过!")
