#!/usr/bin/env python
# -*- coding: utf-8 -*-
import os

script_dir = os.path.dirname(os.path.abspath(__file__))
proj = os.path.dirname(script_dir)
path = os.path.join(proj, 'frontend/src/pages/SchemaDesignerPage.tsx')

with open(path, 'r', encoding='utf-8') as f:
    c = f.read()

# 1. Replace handleNewTicket to open DB selection modal
old = '  const handleNewTicket = () => {\n    setSelectedTicket(null);\n    setViewMode(\'editor\');\n  };'
new = """  const handleNewTicket = () => {
    if (databaseList.length === 0) {
      loadDatabaseList();
    }
    setNewTicketDbId('');
    setNewTicketDbModalVisible(true);
  };

  const confirmNewTicketDb = async () => {
    if (!newTicketDbId) {
      message.warning('请选择目标数据库实例');
      return;
    }
    setSelectedTicket(null);
    // await handleDatabaseChange(newTicketDbId);
    // We'll just set the DB and go to editor - schemas/table will load when needed
    setSelectedDatabase(newTicketDbId);
    setNewTicketDbModalVisible(false);
    setViewMode('editor');
  };"""
assert old in c, 'handleNewTicket not found!'
c = c.replace(old, new)
print('1. handleNewTicket updated')

# 2. Add new ticket DB modal in the render (before the first modal, SQL preview)
old = '      </>)}\n      {/* SQL预览弹窗 - 增强版 */}'
new = """      </>)}
      {/* 新建工单 - 选择数据库弹窗 */}
      <Modal title="新建结构设计工单" open={newTicketDbModalVisible} onCancel={() => setNewTicketDbModalVisible(false)} onOk={confirmNewTicketDb} okText="进入设计" width={450}>
        <Form layout="vertical">
          <Form.Item label="目标数据库实例" required>
            <Select placeholder="请选择数据库" value={newTicketDbId || undefined} onChange={v => setNewTicketDbId(v)} style={{ width: '100%' }}>
              {databaseList.map((db: any) => (
                <Option key={db.id} value={db.id}>{db.name} ({db.host}:{db.port})</Option>
              ))}
            </Select>
          </Form.Item>
          <Alert message="选择数据库后进入结构设计器，对该库下的表进行结构变更" type="info" showIcon />
        </Form>
      </Modal>

      {/* SQL预览弹窗 - 增强版 */}"""
assert old in c, 'modal anchor not found!'
c = c.replace(old, new)
print('2. DB selection modal added')

with open(path, 'w', encoding='utf-8') as f:
    f.write(c)
print('\nDone!')