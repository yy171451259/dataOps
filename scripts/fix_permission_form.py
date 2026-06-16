#!/usr/bin/env python
# -*- coding: utf-8 -*-
import os

script_dir = os.path.dirname(os.path.abspath(__file__))
proj = os.path.dirname(script_dir)
path = os.path.join(proj, 'frontend/src/pages/PermissionRequestPage.tsx')

with open(path, 'r', encoding='utf-8') as f:
    c = f.read()

# Remove from "资源类型" Select to the end of TextArea (before submit button)
old_start = '            <Form.Item\n              name="resourceType"\n              label="资源类型"'
old_end = 'placeholder="请详细说明申请权限的原因..."'
idx_start = c.find(old_start)
idx_end = c.find(old_end) + len(old_end)

if idx_start >= 0 and idx_end > idx_start:
    old_form_block = c[idx_start:idx_end]
    print(f'Found form block: {len(old_form_block)} chars from line {c[:idx_start].count(chr(10))+1}')

    new_form_block = """            <Form.Item name="title" label="申请标题">
              <Input placeholder="例如: 开发环境数据库查询权限" />
            </Form.Item>

            <Form.Item label="选择数据库（可多选）" required>
              <div style={{ border: '1px solid #d9d9d9', borderRadius: 4, padding: '8px 12px', maxHeight: 250, overflow: 'auto' }}>
                {instances.length === 0 ? (
                  <Empty description="暂无可用数据库" image={Empty.PRESENTED_IMAGE_SIMPLE} />
                ) : (
                  instances.map(inst => (
                    <div key={inst.id} style={{ marginBottom: 6, display: 'flex', alignItems: 'center', gap: 8 }}>
                      <Checkbox
                        checked={selectedResources.some(r => r.databaseId === inst.id)}
                        onChange={e => toggleResourceCheck(inst.id, e.target.checked)}
                      />
                      <span style={{ fontWeight: 500, cursor: 'pointer' }} onClick={() => toggleInstance(inst.id)}>
                        {expandedInstances.has(inst.id) ? '▼' : '▶'} {inst.name}
                      </span>
                      <span style={{ color: '#999', fontSize: 12 }}>{inst.host}:{inst.port}</span>
                    </div>
                  ))
                )}
                {selectedResources.length > 0 && (
                  <div style={{ marginTop: 8, padding: '4px 8px', background: '#f6ffed', borderRadius: 4 }}>
                    <Tag color="green">已选 {selectedResources.length} 个</Tag>
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
              {expireDays > 0 && <span style={{ marginLeft: 8, color: '#999' }}>到期: {dayjs().add(expireDays, 'day').format('YYYY-MM-DD')}</span>}
              {expireDays === 0 && <span style={{ marginLeft: 8, color: '#faad14' }}>⚠ 永久有效，请谨慎授予</span>}
            </Form.Item>

            <Form.Item name="reason" label="申请原因" rules={[{ required: true, message: '请填写申请原因' }]}>
              <TextArea rows={4} placeholder="请详细说明申请权限的原因..." """

    c = c[:idx_start] + new_form_block + c[idx_end:]
    print('Form replaced!')
else:
    print('Form markers not found!')
    print(f'idx_start={idx_start}, idx_end={idx_end}')

with open(path, 'w', encoding='utf-8') as f:
    f.write(c)
print('Done!')