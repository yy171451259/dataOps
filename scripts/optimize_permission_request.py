#!/usr/bin/env python
# -*- coding: utf-8 -*-
import os

script_dir = os.path.dirname(os.path.abspath(__file__))
proj = os.path.dirname(script_dir)
path = os.path.join(proj, 'frontend/src/pages/PermissionRequestPage.tsx')

with open(path, 'r', encoding='utf-8') as f:
    c = f.read()

# 1. Replace permissionOptions with checkbox-style + add expire
old_options = """const permissionOptions = [
  { label: 'sql:query - 数据查询', value: 'sql:query' },
  { label: 'sql:update - 数据更新', value: 'sql:update' },
  { label: 'sql:export - 数据导出', value: 'sql:export' },
  { label: 'sql:ddl - DDL操作', value: 'sql:ddl' },
];"""
new_options = """const permissionOptions = [
  { label: '查询 (query)', value: 'query' },
  { label: '导出 (export)', value: 'export' },
  { label: '变更 (update)', value: 'update' },
  { label: '结构变更 (ddl)', value: 'ddl' },
];

const expireOptions = [
  { label: '7天', value: 7 },
  { label: '15天', value: 15 },
  { label: '30天', value: 30 },
  { label: '90天', value: 90 },
  { label: '永久', value: 0 },
];"""
assert old_options in c, 'options not found'
c = c.replace(old_options, new_options)
print('1. permission/expire options updated')

# 2. Add new state variables after existing state
old_state = '  const [selectedResourceType, setSelectedResourceType] = useState<string>(\'\');'
new_state = """  const [selectedResourceType, setSelectedResourceType] = useState<string>('');
  // 多资源选择增强
  const [instances, setInstances] = useState<any[]>([]);
  const [selectedResources, setSelectedResources] = useState<any[]>([]);
  const [checkedPerms, setCheckedPerms] = useState<string[]>(['query']);
  const [expireDays, setExpireDays] = useState<number>(30);
  const [expandedInstances, setExpandedInstances] = useState<Set<string>>(new Set());"""
assert old_state in c, 'state not found'
c = c.replace(old_state, new_state)
print('2. state vars added')

# 3. Add loadInstances + toggle functions after loadDatabaseList
old_load = """  const loadDatabaseList = async () => {
    try {
      const res = await databaseApi.list();
      setDatabases(res.data?.data?.records || res.data?.data || []);
    } catch (e) {
      console.error('加载数据库列表失败', e);
      setDatabases([]);
    }
  };"""
new_load = """  const loadDatabaseList = async () => {
    try {
      const res = await databaseApi.list();
      const data = res.data?.data?.records || res.data?.data || [];
      setDatabases(data);
      setInstances(data.map((db: any) => ({
        id: db.id || db.name, name: db.name || db.id,
        type: db.type || 'mysql', host: db.host || '', port: db.port || 3306,
      })));
    } catch (e) {
      console.error('加载数据库列表失败', e);
      setDatabases([]);
    }
  };

  const toggleInstance = (id: string) => {
    const s = new Set(expandedInstances);
    s.has(id) ? s.delete(id) : s.add(id);
    setExpandedInstances(s);
  };

  const toggleResourceCheck = (dbId: string, checked: boolean) => {
    if (!checked) {
      setSelectedResources(prev => prev.filter(r => r.databaseId !== dbId));
      return;
    }
    const inst = instances.find(i => i.id === dbId);
    if (inst) setSelectedResources(prev => [...prev, {
      instanceId: inst.id, instanceName: inst.name,
      databaseId: inst.id, databaseName: inst.name,
    }]);
  };"""
assert old_load in c, 'loadDatabaseList not found'
c = c.replace(old_load, new_load)
print('3. toggle functions added')

# 4. Replace handleSubmit to use batch submitTicket API
old_submit = """  const handleSubmit = async (values: any) => {
    setSubmitting(true);
    try {
      await permissionRequestApi.submit({
        resourceType: values.resourceType,
        resourceId: values.resourceId,
        resourceName: values.resourceName || values.resourceId,
        permissions: values.permissions,
        reason: values.reason,
      });
      message.success('权限申请已提交，请等待审批');
      submitForm.resetFields();
      setResources([]);
      setSelectedResourceType('');
      loadMyRequests();
    } catch (e: any) {
      message.error('提交失败: ' + (e?.response?.data?.message || e.message));
    } finally {
      setSubmitting(false);
    }
  };"""
new_submit = """  const handleSubmit = async (values: any) => {
    if (selectedResources.length === 0) { message.warning('请至少选择一个数据库'); return; }
    if (checkedPerms.length === 0) { message.warning('请选择权限类型'); return; }
    setSubmitting(true);
    try {
      const submitData = {
        title: values.title || '权限申请',
        reason: values.reason || '',
        ticketType: 'database',
        resources: selectedResources,
        permissionTypes: checkedPerms,
        expireDays: expireDays > 0 ? expireDays : undefined,
      };
      await permissionRequestApi.submit(submitData);
      message.success('权限申请已提交，请等待审批');
      setSelectedResources([]);
      setCheckedPerms(['query']);
      setExpireDays(30);
      submitForm.resetFields();
      loadMyRequests();
    } catch (e: any) {
      message.error('提交失败: ' + (e?.response?.data?.message || e.message));
    } finally {
      setSubmitting(false);
    }
  };"""
assert old_submit in c, 'handleSubmit not found'
c = c.replace(old_submit, new_submit)
print('4. handleSubmit updated to use batch API')

# 5. Replace the "新建申请" tab form content
# Find the create tab children and replace the form
old_form_start = """        children: (
        <Card title="提交权限申请" style={{ maxWidth: 700 }}>"""
new_form_start = """        children: (
        <Card title="提交权限申请" style={{ maxWidth: 800 }}>"""
c = c.replace(old_form_start, new_form_start)
print('5. card width updated')

# 6. Replace the form content - from single resource to multi-select
old_form = """          <Form
            form={submitForm}
            layout="vertical"
            onFinish={handleSubmit}
            initialValues={{ resourceType: undefined, permissions: [] }}
          >
            <Form.Item
              name="resourceType"
              label="资源类型"
              rules={[{ required: true, message: '请选择资源类型' }]}
            >
              <Select
                placeholder="选择资源类型"
                onChange={handleResourceTypeChange}
              >
                <Option value="instance">实例 (instance)</Option>
                <Option value="database">数据库 (database)</Option>
                <Option value="table">数据表 (table)</Option>
                <Option value="column">字段 (column)</Option>
              </Select>
            </Form.Item>

            <Form.Item
              name="resourceId"
              label="资源ID"
              rules={[{ required: true, message: '请选择或输入资源ID' }]}
            >
              {selectedResourceType && resources.length > 0 ? (
                <Select
                  showSearch
                  placeholder="搜索并选择资源"
                  filterOption={(input, option) =>
                    (option?.children as unknown as string)?.toLowerCase().includes(input.toLowerCase())
                  }
                  allowClear
                  options={resources}
                />
              ) : (
                <Input placeholder="输入资源ID" />
              )}
            </Form.Item>

            <Form.Item name="resourceName" label="资源名称">
              <Input placeholder="可选，便于展示的名称" />
            </Form.Item>

            <Form.Item
              name="permissions"
              label="申请权限"
              rules={[{ required: true, message: '请至少选择一项权限' }]}
            >
              <Checkbox.Group options={permissionOptions} />
            </Form.Item>

            <Form.Item
              name="reason"
              label="申请原因"
              rules={[{ required: true, message: '请填写申请原因' }]}
            >
              <TextArea
                rows={4}
                placeholder="请详细说明申请权限的原因..." """
new_form = """          <Form
            form={submitForm}
            layout="vertical"
            onFinish={handleSubmit}
            initialValues={{ title: '', reason: '' }}
          >
            <Form.Item name="title" label="申请标题">
              <Input placeholder="例如: 开发环境数据库查询权限" />
            </Form.Item>

            <Form.Item label="选择数据库（可多选）" required>
              <div style={{ border: '1px solid #d9d9d9', borderRadius: 4, padding: 12, maxHeight: 300, overflow: 'auto' }}>
                {instances.length === 0 ? (
                  <Empty description="暂无可用数据库" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                ) : (
                  instances.map(inst => (
                    <div key={inst.id} style={{ marginBottom: 8 }}>
                      <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <Checkbox
                          checked={selectedResources.some(r => r.databaseId === inst.id)}
                          onChange={e => toggleResourceCheck(inst.id, e.target.checked)}
                        />
                        <span style={{ fontWeight: 500, cursor: 'pointer' }} onClick={() => toggleInstance(inst.id)}>
                          {expandedInstances.has(inst.id) ? '▼' : '▶'} {inst.name}
                        </span>
                        <span style={{ color: '#999', fontSize: 12 }}>{inst.host}:{inst.port}</span>
                      </div>
                    </div>
                  ))
                )}
                {selectedResources.length > 0 && (
                  <div style={{ marginTop: 8, padding: 8, background: '#f6ffed', borderRadius: 4 }}>
                    <Tag color="green">已选 {selectedResources.length} 个库</Tag>
                    {selectedResources.map(r => <Tag key={r.databaseId} closable onClose={() => toggleResourceCheck(r.databaseId, false)}>{r.instanceName}</Tag>)}
                  </div>
                )}
              </div>
            </Form.Item>

            <Form.Item label="申请权限" required>
              <Checkbox.Group options={permissionOptions} value={checkedPerms} onChange={v => setCheckedPerms(v as string[])} />
            </Form.Item>

            <Form.Item label="权限有效期">
              <Select value={expireDays} onChange={setExpireDays} style={{ width: 200 }}>
                {expireOptions.map(o => <Option key={o.value} value={o.value}>{o.label}</Option>)}
              </Select>
              {expireDays > 0 && <span style={{ marginLeft: 8, color: '#999' }}>到期时间: {dayjs().add(expireDays, 'day').format('YYYY-MM-DD')}</span>}
              {expireDays === 0 && <span style={{ marginLeft: 8, color: '#faad14' }}>⚠ 永久有效，请谨慎授予</span>}
            </Form.Item>

            <Form.Item
              name="reason"
              label="申请原因"
              rules={[{ required: true, message: '请填写申请原因' }]}
            >
              <TextArea rows={4} placeholder="请详细说明申请权限的原因..." """
assert old_form in c, 'form content not found'
c = c.replace(old_form, new_form)
print('6. form content replaced with multi-select')

# 7. Update submit button text
c = c.replace('提交申请', '提交权限申请')
print('7. submit button text updated')

# 8. Remove unused state (resources, selectedResourceType, handleResourceTypeChange)
# Actually they'll be left since removing them might break other references. Let me check if they're used elsewhere.
# resources and selectedResourceType are only used in the old form which we replaced. The functions can stay as dead code.

with open(path, 'w', encoding='utf-8') as f:
    f.write(c)
print('\nDone!')