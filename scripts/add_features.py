#!/usr/bin/env python
# -*- coding: utf-8 -*-
import os

script_dir = os.path.dirname(os.path.abspath(__file__))
proj = os.path.dirname(script_dir)
path = os.path.join(proj, 'frontend/src/pages/SchemaDesignerPage.tsx')

with open(path, 'r', encoding='utf-8') as f:
    c = f.read()

# 1. State variables for design mode + index (after editForm)
old = "  const [editForm] = Form.useForm();\n  \n  // SQL预览与工单"
new = "  const [editForm] = Form.useForm();\n\n  // 全新设计模式状态\n  const [designTableName, setDesignTableName] = useState('');\n  const [designComment, setDesignComment] = useState('');\n  const [designColumns, setDesignColumns] = useState<ColumnDef[]>([]);\n  const [designIsDirty, setDesignIsDirty] = useState(false);\n\n  // 索引管理\n  const [tableIndexes, setTableIndexes] = useState<any[]>([]);\n  const [indexModalVisible, setIndexModalVisible] = useState(false);\n  const [loadingIndexes, setLoadingIndexes] = useState(false);\n\n  // SQL预览与工单"
if old in c:
    c = c.replace(old, new)
    print("1. State vars added")
else:
    print("1. FAIL - editForm anchor not found")
    idx = c.find('const [editForm]')
    if idx >= 0:
        print(repr(c[idx:idx+120]))

# 2. Add loadTableIndexes + handleTableClickWithIndex before parseCreateTableSql
old = "\n  // ========== 解析建表SQL =========="
new = """\n  // ========== 加载索引 ==========
  const loadTableIndexes = async (tableName: string) => {
    if (!selectedDatabase) return;
    setLoadingIndexes(true);
    try {
      const res = await ddlWorkbenchApi.getTableIndexes(selectedDatabase, tableName, selectedSchema);
      const idxList = res.data?.data || [];
      setTableIndexes(idxList);
    } catch (e) {
      console.warn('加载索引失败', e);
      setTableIndexes([]);
    } finally {
      setLoadingIndexes(false);
    }
  };

  const handleTableClickWithIndex = async (tableName: string) => {
    await handleTableClick(tableName);
    loadTableIndexes(tableName);
  };

  // ========== 解析建表SQL =========="""
if old in c:
    c = c.replace(old, new)
    print("2. Index functions added")
else:
    print("2. FAIL - parseCreateTableSql anchor not found")

# 3. Replace table click references
c = c.replace(
    'onClick={() => handleTableClick(tableName)}',
    'onClick={() => handleTableClickWithIndex(tableName)}'
)
print("3. Table click ref updated:", c.count('handleTableClickWithIndex'), "occurrences")

# 4. Add design functions before addColumn
old = "  // ========== 添加字段 =========="
new = """  // ========== 全新表设计函数 ==========
  const initNewTableDesign = () => {
    setDesignTableName('');
    setDesignComment('');
    setDesignColumns([]);
    setDesignIsDirty(false);
    message.info('已重置，开始设计新表');
  };

  const addDesignColumn = () => {
    const newCol: ColumnDef = {
      id: genId(), name: 'col_' + (designColumns.length + 1),
      type: 'VARCHAR', length: 255, nullable: true,
      primaryKey: false, autoIncrement: false, unique: false, unsigned: false,
      comment: '', changeType: 'ADD', isNew: true,
    };
    setDesignColumns([...designColumns, newCol]);
    setDesignIsDirty(true);
  };

  const editDesignColumn = (col: ColumnDef) => {
    setEditingColumn(col);
    editForm.setFieldsValue({
      name: col.name, type: col.type, length: col.length,
      nullable: col.nullable, primaryKey: col.primaryKey,
      autoIncrement: col.autoIncrement, unsigned: col.unsigned,
      defaultValue: col.default, comment: col.comment,
    });
    setColumnEditModalVisible(true);
  };

  const saveDesignColumn = async () => {
    if (!editingColumn) return;
    try {
      const values = await editForm.validateFields();
      setDesignColumns(prev => prev.map(c =>
        c.id === editingColumn.id ? {
          ...c, name: values.name, type: values.type,
          length: ['VARCHAR','CHAR','INT','BIGINT'].includes(values.type) ? values.length : undefined,
          nullable: values.nullable, primaryKey: values.primaryKey || false,
          autoIncrement: values.autoIncrement || false, unsigned: values.unsigned || false,
          default: values.defaultValue, comment: values.comment || '',
        } : c
      ));
      setDesignIsDirty(true);
      setColumnEditModalVisible(false);
    } catch (e) { /* form validation */ }
  };

  const removeDesignColumn = (colId: string) => {
    setDesignColumns(prev => prev.filter(c => c.id !== colId));
    setDesignIsDirty(true);
  };

  const generateCreateTableSql = () => {
    if (!designTableName || designColumns.length === 0) {
      message.warning('请填写表名并至少添加一个字段');
      return;
    }
    const pkCols = designColumns.filter(c => c.primaryKey).map(c => c.name);
    const lines: string[] = [];
    lines.push('-- ============================================');
    lines.push('-- 新建表: ' + designTableName);
    lines.push('-- ============================================\n');
    lines.push('CREATE TABLE `' + designTableName + '` (');
    designColumns.forEach((col, i) => {
      const typeStr = col.length ? col.type + '(' + col.length + ')' : col.type;
      const nullStr = col.nullable ? '' : ' NOT NULL';
      const autoStr = col.autoIncrement ? ' AUTO_INCREMENT' : '';
      const defStr = col.default ? ' DEFAULT ' + col.default : '';
      const cmtStr = col.comment ? " COMMENT '" + col.comment + "'" : '';
      const unsignStr = col.unsigned ? ' UNSIGNED' : '';
      const comma = i < designColumns.length - 1 ? ',' : '';
      lines.push('  `' + col.name + '` ' + typeStr + unsignStr + nullStr + autoStr + defStr + cmtStr + comma);
    });
    if (pkCols.length > 0) {
      lines.push('  PRIMARY KEY (`' + pkCols.join('`, `') + '`)');
    }
    lines.push(') ENGINE=InnoDB DEFAULT CHARSET=utf8mb4' + (designComment ? " COMMENT='" + designComment + "'" : '') + ';');
    const sql = lines.join('\n');
    setGeneratedSql(sql);
    setRiskAssessment({
      level: 'low',
      message: '新表创建，请确认表结构',
      details: ['表名: ' + designTableName, '字段数: ' + designColumns.length + (pkCols.length > 0 ? ', 主键: ' + pkCols.join(', ') : '')],
      infoNotes: [],
    });
    setSqlModalVisible(true);
  };

  // ========== 添加字段 =========="""
if old in c:
    c = c.replace(old, new)
    print("4. Design functions added")
else:
    print("4. FAIL - addColumn anchor not found")

# 5. Replace empty "全新设计" placeholder with real content
old = """      {/* 全新设计模式（简化） */}
      {editMode === 'design' && (
        <div style={{ flex: 1, overflow: 'auto', background: '#f5f5f5', padding: 24 }}>
          <Card size="small" title="全新表设计">
            <Alert
              message="您正在创建全新的表。设计完成后可导出SQL或直接提交工单执行。"
              type="info"
              showIcon
            />
            <div style={{ marginTop: 24, textAlign: 'center', padding: 60 }}>
              <Button type="primary" size="large" icon={<PlusOutlined />} onClick={() => setEditMode('alter')}>
                切换到"修改已有表"体验完整功能
              </Button>
            </div>
          </Card>
        </div>
      )}"""
new = """      {/* 全新表设计模式 */}
      {editMode === 'design' && (
        <div style={{ flex: 1, overflow: 'auto', background: '#f5f5f5', padding: 16 }}>
          <Card size="small" title="全新表设计" extra={<Button icon={<ReloadOutlined />} onClick={initNewTableDesign}>重置</Button>}>
            <Row gutter={16} style={{ marginBottom: 16 }}>
              <Col span={8}>
                <Input addonBefore="表名" placeholder="例如: user_info" value={designTableName} onChange={e => { setDesignTableName(e.target.value); setDesignIsDirty(true); }} />
              </Col>
              <Col span={8}>
                <Input addonBefore="注释" placeholder="表说明" value={designComment} onChange={e => { setDesignComment(e.target.value); setDesignIsDirty(true); }} />
              </Col>
              <Col span={8}>
                <Space>
                  <Button type="primary" icon={<PlusOutlined />} onClick={addDesignColumn}>添加字段</Button>
                  <Button icon={<ThunderboltOutlined />} onClick={generateCreateTableSql} disabled={!designTableName || designColumns.length === 0}>生成建表SQL</Button>
                </Space>
              </Col>
            </Row>
            {designColumns.length === 0 ? (
              <Empty description={<span>还没有字段，点击 <Button type="link" icon={<PlusOutlined />} onClick={addDesignColumn}>添加字段</Button> 开始设计</span>} />
            ) : (
              <Table dataSource={designColumns} rowKey="id" pagination={false} size="small" scroll={{ y: 'calc(100vh - 320px)' }}
                columns={[
                  { title: '字段名', dataIndex: 'name', width: 140, render: (t: string, r: ColumnDef) => (<span style={{ fontWeight: r.primaryKey ? 600 : 400 }}>{t}{r.primaryKey ? ' 🔑' : ''}</span>) },
                  { title: '类型', dataIndex: 'type', width: 80 },
                  { title: '长度', dataIndex: 'length', width: 60 },
                  { title: '可空', dataIndex: 'nullable', width: 60, render: (v: boolean) => v ? <Tag color="green">是</Tag> : <Tag color="red">否</Tag> },
                  { title: '自增', dataIndex: 'autoIncrement', width: 60, render: (v: boolean) => v ? <Tag color="blue">AUTO</Tag> : '' },
                  { title: '默认值', dataIndex: 'default', width: 80, ellipsis: true },
                  { title: '注释', dataIndex: 'comment', ellipsis: true },
                  { title: '操作', width: 120, fixed: 'right', render: (_: any, record: ColumnDef) => (<Space><Button type="link" size="small" icon={<EditOutlined />} onClick={() => editDesignColumn(record)}>编辑</Button><Popconfirm title="删除此字段？" onConfirm={() => removeDesignColumn(record.id)}><Button type="link" size="small" danger icon={<CloseCircleOutlined />}>删除</Button></Popconfirm></Space>) },
                ]}
              />
            )}
          </Card>
        </div>
      )}"""
if old in c:
    c = c.replace(old, new)
    print("5. Design tab replaced")
else:
    print("5. FAIL - design placeholder not found")
    idx = c.find('全新设计模式')
    if idx >= 0:
        print(repr(c[idx:idx+80]))

# 6. Add index button in table info descriptions
old = "<Descriptions.Item label=\"字符集\">{currentTableStructure.charset}</Descriptions.Item>"
new = old + '\n                    <Descriptions.Item label=\"索引\">\n                      {loadingIndexes ? <Spin size=\"small\" /> : <Button type=\"link\" size=\"small\" icon={<TableOutlined />} onClick={() => setIndexModalVisible(true)} disabled={!selectedTableInList}>{tableIndexes.length} 个</Button>}\n                    </Descriptions.Item>'
if old in c:
    c = c.replace(old, new, 1)
    print("6. Index button added")
else:
    print("6. FAIL - charset description not found")

# 7. Update column edit modal's onOk
old = "onOk={handleSaveColumn}"
new = "onOk={() => editMode === 'design' ? saveDesignColumn() : handleSaveColumn()}"
if old in c:
    c = c.replace(old, new)
    print("7. onOk updated")
else:
    print("7. onOk not found")

with open(path, 'w', encoding='utf-8') as f:
    f.write(c)
print('\nDone!')