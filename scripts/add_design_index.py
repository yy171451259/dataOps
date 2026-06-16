#!/usr/bin/env python
# -*- coding: utf-8 -*-
import os

script_dir = os.path.dirname(os.path.abspath(__file__))
proj = os.path.dirname(script_dir)
path = os.path.join(proj, 'frontend/src/pages/SchemaDesignerPage.tsx')

with open(path, 'r', encoding='utf-8') as f:
    c = f.read()

# =====================================================================
# 0. 状态变量
# =====================================================================
# Add index-related state after column edit modal state
old = "  // 列编辑弹窗\n  const [columnEditModalVisible, setColumnEditModalVisible] = useState(false);\n  const [editingColumn, setEditingColumn] = useState<ColumnDef | null>(null);\n  const [editForm] = Form.useForm();"
new = """  // 列编辑弹窗
  const [columnEditModalVisible, setColumnEditModalVisible] = useState(false);
  const [editingColumn, setEditingColumn] = useState<ColumnDef | null>(null);
  const [editForm] = Form.useForm();

  // 索引管理
  const [tableIndexes, setTableIndexes] = useState<any[]>([]);
  const [indexModalVisible, setIndexModalVisible] = useState(false);
  const [loadingIndexes, setLoadingIndexes] = useState(false);

  // 全新设计模式状态
  const [designTableName, setDesignTableName] = useState('');
  const [designComment, setDesignComment] = useState('');
  const [designColumns, setDesignColumns] = useState<ColumnDef[]>([]);
  const [designIsDirty, setDesignIsDirty] = useState(false);"""
assert old in c, 'State var insertion anchor not found!'
c = c.replace(old, new)

# =====================================================================
# 1. 索引加载 + 管理函数（放在handleTableClick后面）
# =====================================================================
old = """  // ========== 解析建表SQL =========="""
new = """  // ========== 加载索引 ==========
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

  // 在加载表结构后自动加载索引
  const handleTableClickWithIndex = async (tableName: string) => {
    await handleTableClick(tableName);
    loadTableIndexes(tableName);
  };

  // ========== 解析建表SQL =========="""
assert old in c, 'parseCreateTableSql anchor not found!'
c = c.replace(old, new)

# =====================================================================
# 2. 替换handleTableClick引用 -> handleTableClickWithIndex
# =====================================================================
# In the click handler in the left sidebar table list
c = c.replace(
  'onClick={() => handleTableClick(tableName)}',
  'onClick={() => handleTableClickWithIndex(tableName)}'
)

# =====================================================================
# 3. 全新设计函数（放在addColumn之前）
# =====================================================================
old = """  // ========== 添加字段 =========="""
new = """  // ========== 全新表设计函数 ==========
  const initNewTableDesign = () => {
    setDesignTableName('');
    setDesignComment('');
    setDesignColumns([]);
    setDesignIsDirty(false);
  };

  const addDesignColumn = () => {
    const newCol: ColumnDef = {
      id: genId(), name: `column_${designColumns.length + 1}`,
      type: 'VARCHAR', length: 255, nullable: true,
      primaryKey: false, autoIncrement: false, unique: false,
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
          ...c,
          name: values.name, type: values.type,
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
    lines.push(`CREATE TABLE \`${designTableName}\` (`);
    designColumns.forEach((col, i) => {
      const typeStr = col.length ? `${col.type}(${col.length})` : col.type;
      const nullStr = col.nullable ? '' : ' NOT NULL';
      const autoStr = col.autoIncrement ? ' AUTO_INCREMENT' : '';
      const defaultStr = col.default ? ` DEFAULT ${col.default}` : '';
      const commentStr = col.comment ? ` COMMENT '${col.comment}'` : '';
      const unsignedStr = col.unsigned ? ' UNSIGNED' : '';
      const comma = i < designColumns.length - 1 ? ',' : '';
      lines.push(`  \`${col.name}\` ${typeStr}${unsignedStr}${nullStr}${autoStr}${defaultStr}${commentStr}${comma}`);
    });
    if (pkCols.length > 0) {
      lines.push(`  PRIMARY KEY (\`${pkCols.join('`, `')}\`)`);
    }
    lines.push(`) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4${designComment ? ` COMMENT='${designComment}'` : ''};`);
    const sql = lines.join('\n');
    setGeneratedSql(sql);
    setRiskAssessment({ level: 'low', message: '新表创建，请确认表结构和字段定义', details: [`表名: ${designTableName}`, `字段数: ${designColumns.length}${pkCols.length > 0 ? `, 主键: ${pkCols.join(', ')}` : ''}`], infoNotes: [] });
    setSqlModalVisible(true);
  };

  // ========== 添加字段 =========="""
assert old in c, 'addColumn anchor not found!'
c = c.replace(old, new)

# =====================================================================
# 4. 替换"全新设计"Tab内容
# =====================================================================
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
          <Spin spinning={false}>
            <Row gutter={16}>
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
                  <Button icon={<ReloadOutlined />} onClick={initNewTableDesign}>重置</Button>
                </Space>
              </Col>
            </Row>
            <Divider />
            {designColumns.length === 0 ? (
              <Empty description="还没有字段，点击"添加字段"开始设计" />
            ) : (
              <Table dataSource={designColumns} rowKey="id" pagination={false} size="small" scroll={{ y: 'calc(100vh - 280px)' }}
                columns={[
                  { title: '字段名', dataIndex: 'name', width: 140, render: (t: string, r: ColumnDef) => (<span style={{ fontWeight: r.primaryKey ? 600 : 400 }}>{t}{r.primaryKey ? ' 🔑' : ''}</span>) },
                  { title: '类型', dataIndex: 'type', width: 80 },
                  { title: '长度', dataIndex: 'length', width: 60 },
                  { title: '可空', dataIndex: 'nullable', width: 60, render: (v: boolean) => v ? <Tag color="green">是</Tag> : <Tag color="red">否</Tag> },
                  { title: '自增', dataIndex: 'autoIncrement', width: 60, render: (v: boolean) => v ? <Tag color="blue">AUTO</Tag> : '' },
                  { title: '默认值', dataIndex: 'default', width: 80, ellipsis: true },
                  { title: '注释', dataIndex: 'comment', ellipsis: true },
                  { title: '操作', width: 120, render: (_: any, record: ColumnDef) => (<Space><Button type="link" size="small" icon={<EditOutlined />} onClick={() => editDesignColumn(record)}>编辑</Button><Popconfirm title="删除此字段？" onConfirm={() => removeDesignColumn(record.id)}><Button type="link" size="small" danger icon={<CloseCircleOutlined />}>删除</Button></Popconfirm></Space>) },
                ]}
              />
            )}
          </Spin>
        </div>
      )}"""
assert old in c, 'design mode anchor not found!'
c = c.replace(old, new)

# =====================================================================
# 5. 在"修改已有表"模式的列表格上方添加索引标签页
# =====================================================================
old = """                  <Descriptions column={3} size="small" title="表信息">
                    <Descriptions.Item label="表名">{currentTableStructure.name}</Descriptions.Item>
                    <Descriptions.Item label="引擎">{currentTableStructure.engine}</Descriptions.Item>
                    <Descriptions.Item label="字符集">{currentTableStructure.charset}</Descriptions.Item>
                    <Descriptions.Item label="字段数">{currentTableStructure.columns.length}</Descriptions.Item>
                    <Descriptions.Item label="注释">{currentTableStructure.comment || '-'}</Descriptions.Item>
                    <Descriptions.Item label="变更字段数">
                      <Tag color="green">{currentTableStructure.columns.filter(c => c.changeType === 'ADD').length} 新增</Tag>
                      <Tag color="orange">{currentTableStructure.columns.filter(c => c.changeType === 'MODIFY').length} 修改</Tag>
                    </Descriptions.Item>
                  </Descriptions>"""
new = """                  <Descriptions column={3} size="small" title="表信息">
                    <Descriptions.Item label="表名">{currentTableStructure.name}</Descriptions.Item>
                    <Descriptions.Item label="引擎">{currentTableStructure.engine}</Descriptions.Item>
                    <Descriptions.Item label="字符集">{currentTableStructure.charset}</Descriptions.Item>
                    <Descriptions.Item label="字段数">{currentTableStructure.columns.length}</Descriptions.Item>
                    <Descriptions.Item label="索引数">{loadingIndexes ? <Spin size="small" /> : tableIndexes.length}</Descriptions.Item>
                    <Descriptions.Item label="注释">{currentTableStructure.comment || '-'}</Descriptions.Item>
                    <Descriptions.Item label="变更字段数">
                      <Tag color="green">{currentTableStructure.columns.filter(c => c.changeType === 'ADD').length} 新增</Tag>
                      <Tag color="orange">{currentTableStructure.columns.filter(c => c.changeType === 'MODIFY').length} 修改</Tag>
                    </Descriptions.Item>
                    <Descriptions.Item label="变更字段">
                      <Button type="link" size="small" icon={<TableOutlined />} onClick={() => setIndexModalVisible(true)} disabled={!selectedTableInList}>
                        查看索引
                      </Button>
                    </Descriptions.Item>
                  </Descriptions>"""
assert old in c, 'table info descriptions not found!'
c = c.replace(old, new)

# =====================================================================
# 6. 索引查看弹窗（放在字段编辑弹窗前面或后面）
# =====================================================================
# 在字段编辑弹窗前面插入索引弹窗
old = """      {/* 字段编辑弹窗 */}
      <Modal"""
new = """      {/* 索引查看弹窗 */}
      <Modal title={`索引管理 - ${selectedTableInList || ''}`} open={indexModalVisible} onCancel={() => setIndexModalVisible(false)} width={700} footer={<Button onClick={() => setIndexModalVisible(false)}>关闭</Button>}>
        <Spin spinning={loadingIndexes}>
          {tableIndexes.length === 0 ? (
            <Empty description="暂无索引" />
          ) : (
            <Table dataSource={tableIndexes} rowKey="indexName" pagination={false} size="small" columns={[
              { title: '索引名', dataIndex: 'indexName', width: 160, render: (t: string) => t === 'PRIMARY' ? <Tag color="blue">PRIMARY</Tag> : t },
              { title: '字段', dataIndex: 'columnName', width: 120 },
              { title: '唯一', dataIndex: 'nonUnique', width: 60, render: (v: boolean) => v ? <Tag color="default">否</Tag> : <Tag color="green">是</Tag> },
              { title: '序号', dataIndex: 'ordinalPosition', width: 50 },
            ]} />
          )}
          <Divider />
          <Alert message="索引变更请通过"生成变更SQL"手动添加 DDL 语句，例如：CREATE INDEX idx_name ON table(column);" type="info" showIcon />
        </Spin>
      </Modal>

      {/* 字段编辑弹窗 */}
      <Modal"""
assert old in c, 'column edit modal anchor not found!'
c = c.replace(old, new)

# =====================================================================
# 7. 修改saveDesignColumn中columnEditModalVisible关闭处理
#    - 当设计模式打开时，保存要用 saveDesignColumn
#    在columnEditModal的onOk中，需要根据当前模式决定用哪个保存函数
# =====================================================================
# 修改onOk处理
old = "onOk={handleSaveColumn}"
# We need to conditionally call either handleSaveColumn or saveDesignColumn
# Replace the onOk for the columnEdit modal to use dynamic routing
# Actually, since the modal has a single onOk, we need to use a wrapper
new = "onOk={() => editMode === 'design' ? saveDesignColumn() : handleSaveColumn()}"
c = c.replace(old, new)

# =====================================================================
# 8. 在修改已有表的列编辑中，generateAlterSqlAndAssess 也包含索引SQL
#    (目前索引变更需要用户手动在SQL里加，暂不做自动索引SQL生成)
# =====================================================================

with open(path, 'w', encoding='utf-8') as f:
    f.write(c)

print('OK: all changes applied')