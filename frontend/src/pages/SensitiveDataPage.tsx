import React, { useState, useEffect } from 'react';
import {
  Tabs, Table, Button, Select, message, Card, Space, Tag, Modal,
  Radio, Descriptions, Empty, Spin, Row, Col, Input, Form, Popconfirm,
  Typography, Tooltip,
} from 'antd';
import {
  EyeInvisibleOutlined, SafetyOutlined, TagOutlined,
  PlusOutlined, DeleteOutlined, ReloadOutlined, EditOutlined,
  DatabaseOutlined, TableOutlined, ColumnHeightOutlined,
} from '@ant-design/icons';
import { sensitiveApi, instanceApi } from '../utils/api';
import dayjs from 'dayjs';

const { Text } = Typography;
const { Option } = Select;

interface MaskRule {
  id: string;
  name: string;
  code: string;
  maskType: string;
  maskAlgorithm: string;
  maskPattern: string;
  maskCharacter: string;
  keepPrefixLen: number;
  keepSuffixLen: number;
  description: string;
  sampleInput: string;
  sampleOutput: string;
  isSystem: boolean;
}

interface SensitiveColumn {
  id: string;
  databaseId: string;
  databaseName: string;
  tableName: string;
  columnName: string;
  sensitivityLevel: string;
  category: string;
  maskRuleId: string;
  maskRuleName?: string;
  createdAt: string;
}

const sensitivityLevelColors: Record<string, string> = {
  L1: 'blue', L2: 'green', L3: 'orange', L4: 'red',
};

const maskTypeLabels: Record<string, string> = {
  FULL_MASK: '全遮蔽', HALF_MASK: '半遮蔽', PARTIAL_MASK: '部分遮蔽',
  PREFIX_MASK: '前缀遮蔽', SUFFIX_MASK: '后缀遮蔽', REGEX_MASK: '正则',
  HASH: '哈希', RANDOM: '随机替换', CUSTOM: '自定义',
};

const algorithmLabels: Record<string, string> = {
  PHONE: '手机号', EMAIL: '邮箱', ID_CARD: '身份证',
  BANK_CARD: '银行卡', FULL_MASK: '全遮蔽', NAME_MASK: '姓名',
  CUSTOM: '自定义参数',
};

const algorithmOptions = Object.entries(algorithmLabels).map(([k, v]) => ({
  value: k, label: v,
}));

const SensitiveDataPage: React.FC = () => {
  const [activeTab, setActiveTab] = useState('rules');

  // =========== Tab 1: 脱敏规则 ===========
  const [maskRules, setMaskRules] = useState<MaskRule[]>([]);
  const [rulesLoading, setRulesLoading] = useState(false);

  // Rule modal
  const [ruleModalVisible, setRuleModalVisible] = useState(false);
  const [ruleEditing, setRuleEditing] = useState<MaskRule | null>(null);
  const [ruleForm, setRuleForm] = useState<Partial<MaskRule>>({});
  const [ruleSubmitting, setRuleSubmitting] = useState(false);

  // =========== Tab 2: 敏感列标识 ===========
  const [databases, setDatabases] = useState<any[]>([]);
  const [selectedDbId, setSelectedDbId] = useState<string>('');
  const [selectedDbName, setSelectedDbName] = useState<string>('');
  const [tables, setTables] = useState<string[]>([]);
  const [selectedTable, setSelectedTable] = useState<string>('');
  const [columns, setColumns] = useState<string[]>([]);
  const [sensitiveColumns, setSensitiveColumns] = useState<SensitiveColumn[]>([]);
  const [columnsLoading, setColumnsLoading] = useState(false);

  // Mark column modal
  const [markVisible, setMarkVisible] = useState(false);
  const [markColumn, setMarkColumn] = useState<string>('');
  const [sensitivityLevel, setSensitivityLevel] = useState<string>('L2');
  const [category, setCategory] = useState<string>('');
  const [selectedMaskRuleId, setSelectedMaskRuleId] = useState<string>('');
  const [markSubmitting, setMarkSubmitting] = useState(false);

  useEffect(() => {
    if (activeTab === 'rules') loadMaskRules();
    if (activeTab === 'columns') loadDatabases();
  }, [activeTab]);

  // ========== 脱敏规则操作 ==========
  const loadMaskRules = async () => {
    setRulesLoading(true);
    try {
      const res = await sensitiveApi.listMaskRules();
      const arr = Array.isArray(res?.data?.data) ? res.data.data : [];
      setMaskRules(arr);
    } catch {
      setMaskRules([]);
    } finally {
      setRulesLoading(false);
    }
  };

  const openCreateRule = () => {
    setRuleEditing(null);
    setRuleForm({
      name: '', code: '', maskType: 'HALF_MASK',
      maskAlgorithm: '', maskCharacter: '*',
      keepPrefixLen: 0, keepSuffixLen: 0,
      description: '',
    });
    setRuleModalVisible(true);
  };

  const openEditRule = (rule: MaskRule) => {
    setRuleEditing(rule);
    setRuleForm({ ...rule });
    setRuleModalVisible(true);
  };

  const handleRuleSubmit = async () => {
    if (!ruleForm.name || !ruleForm.code) {
      message.warning('OK');
      return;
    }
    setRuleSubmitting(true);
    try {
      if (ruleEditing) {
        await sensitiveApi.updateMaskRule(ruleEditing.id, {
          ...ruleForm,
          maskAlgorithm: ruleForm.maskAlgorithm || undefined,
        });
        message.success('OK');
      } else {
        await sensitiveApi.createMaskRule({
          ...ruleForm,
          maskAlgorithm: ruleForm.maskAlgorithm || undefined,
        });
        message.success('OK');
      }
      setRuleModalVisible(false);
      loadMaskRules();
    } catch (e: any) {
      message.error(e?.response?.data?.message || '操作失败');
    } finally {
      setRuleSubmitting(false);
    }
  };

  const handleDeleteRule = async (id: string) => {
    try {
      await sensitiveApi.deleteMaskRule(id);
      message.success('OK');
      loadMaskRules();
    } catch (e: any) {
      message.error(e?.response?.data?.message || '删除失败');
    }
  };

  // ========== 敏感列操作 ==========
  const loadDatabases = async () => {
    try {
      const res = await instanceApi.list();
      const dbs = Array.isArray(res?.data?.data) ? res.data.data : [];
      setDatabases(dbs);
    } catch {
      setDatabases([]);
    }
  };

  const handleDbChange = async (dbId: string) => {
    setSelectedDbId(dbId);
    setSelectedTable('');
    setColumns([]);
    setSensitiveColumns([]);
    const db = databases.find((d: any) => d.id === dbId);
    setSelectedDbName(db?.name || db?.databaseName || dbId);
    try {
      const res = await instanceApi.getTableNames(dbId, db?.name || db?.databaseName || dbId);
      setTables(Array.isArray(res?.data?.data) ? res.data.data : []);
    } catch {
      setTables([]);
    }
  };

  const handleTableChange = async (tableName: string) => {
    setSelectedTable(tableName);
    setColumnsLoading(true);
    try {
      const res = await sensitiveApi.listColumns(selectedDbId, selectedDbName, tableName);
      const data = res?.data?.data;
      if (data) {
        setColumns(data.allColumns || data.columns || []);
        // 用 maskRules 补齐 maskRuleName
        const scs = Array.isArray(data.sensitiveColumns) ? data.sensitiveColumns : (Array.isArray(data) ? data : []);
        const ruleMap: Record<string, string> = {};
        maskRules.forEach(r => { ruleMap[r.id] = r.name; });
        setSensitiveColumns(scs.map((sc: any) => ({
          ...sc,
          maskRuleName: ruleMap[sc.maskRuleId] || sc.maskRuleName || '',
          createdAt: sc.createdAt || sc.createTime,
        })));
      }
    } catch {
      setColumns([]);
      setSensitiveColumns([]);
    } finally {
      setColumnsLoading(false);
    }
  };

  const handleMarkColumn = (colName: string) => {
    setMarkColumn(colName);
    setSensitivityLevel('L2');
    setCategory('');
    setSelectedMaskRuleId('');
    setMarkVisible(true);
  };

  const handleSubmitMark = async () => {
    if (!markColumn) return;
    setMarkSubmitting(true);
    try {
      await sensitiveApi.markColumn({
        databaseId: selectedDbId,
        databaseName: selectedDbName,
        tableName: selectedTable,
        columnName: markColumn,
        sensitivityLevel,
        category: category || undefined,
        maskRuleId: selectedMaskRuleId || undefined,
      });
      message.success("OK");
      setMarkVisible(false);
      handleTableChange(selectedTable);
    } catch (e: any) {
      message.error(e?.response?.data?.message || '标记失败');
    } finally {
      setMarkSubmitting(false);
    }
  };

  const handleUnmark = async (record: SensitiveColumn) => {
    try {
      await sensitiveApi.deleteColumn(record.id);
      message.success("OK");
      handleTableChange(selectedTable);
    } catch (e: any) {
      message.error(e?.response?.data?.message || '取消失败');
    }
  };

  const isColumnMarked = (colName: string) =>
    sensitiveColumns.some((sc) => sc.columnName === colName);

  // ========== 表格列定义 ==========
  const maskRuleColumns = [
    { title: '规则名称', dataIndex: 'name', key: 'name', width: 140 },
    {
      title: '编码', dataIndex: 'code', key: 'code', width: 120,
      render: (c: string) => <code style={{ fontSize: 12 }}>{c}</code>,
    },
    {
      title: '脱敏类型', dataIndex: 'maskType', key: 'maskType', width: 100,
      render: (t: string) => <Tag>{maskTypeLabels[t] || t}</Tag>,
    },
    {
      title: '算法', dataIndex: 'maskAlgorithm', key: 'maskAlgorithm', width: 100,
      render: (a: string) => a ? <Tag color="blue">{algorithmLabels[a] || a}</Tag> : <Tag>默认</Tag>,
    },
    {
      title: '参数', key: 'params', width: 150,
      render: (_: any, r: MaskRule) => (
        <Text type="secondary" style={{ fontSize: 12 }}>
          {r.keepPrefixLen > 0 && `前缀${r.keepPrefixLen} `}
          {r.keepSuffixLen > 0 && `后缀${r.keepSuffixLen} `}
          字符"{r.maskCharacter || '*'}"
        </Text>
      ),
    },
    {
      title: '示例', key: 'example', width: 260,
      render: (_: any, r: MaskRule) => {
        if (!r.sampleInput && !r.sampleOutput) return '-';
        return (
          <Space size={4}>
            <Text type="secondary" style={{ fontSize: 11 }}>{r.sampleInput || '-'}</Text>
            <Text style={{ fontSize: 11 }}>&rarr;</Text>
            <Text code style={{ fontSize: 11 }}>{r.sampleOutput || '-'}</Text>
          </Space>
        );
      },
    },
    {
      title: '来源', key: 'isSystem', width: 70,
      render: (_: any, r: MaskRule) => r.isSystem ? <Tag color="gold">系统</Tag> : <Tag>自定义</Tag>,
    },
    {
      title: '操作', key: 'action', width: 120,
      render: (_: any, r: MaskRule) => (
        <Space size="small">
          <Button
            type="link" size="small" icon={<EditOutlined />}
            onClick={() => openEditRule(r)}
            disabled={r.isSystem}
          >编辑</Button>
          <Popconfirm
            title="确定删除此规则？"
            disabled={r.isSystem}
            onConfirm={() => handleDeleteRule(r.id)}
          >
            <Button type="link" danger size="small" icon={<DeleteOutlined />} disabled={r.isSystem}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const sensitiveColumnsTable = [
    {
      title: '列名', dataIndex: 'columnName', key: 'columnName',
      render: (n: string) => (
        <Space><EyeInvisibleOutlined style={{ color: '#ff4d4f' }} /><Text strong>{n}</Text></Space>
      ),
    },
    {
      title: '敏感级别', dataIndex: 'sensitivityLevel', key: 'sensitivityLevel', width: 90,
      render: (l: string) => <Tag color={sensitivityLevelColors[l] || 'default'}>{l}</Tag>,
    },
    { title: '分类', dataIndex: 'category', key: 'category', width: 120, render: (c: string) => c || '-' },
    {
      title: '脱敏规则', dataIndex: 'maskRuleName', key: 'maskRuleName', width: 130,
      render: (n: string) => n ? <Tag color="blue">{n}</Tag> : <Tag>无</Tag>,
    },
    {
      title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 150,
      render: (t: string) => t ? dayjs(t).format('YYYY-MM-DD HH:mm') : '-',
    },
    {
      title: '操作', key: 'action', width: 80,
      render: (_: any, record: SensitiveColumn) => (
        <Popconfirm title="确定取消敏感标记？" onConfirm={() => handleUnmark(record)}>
          <Button type="link" danger size="small" icon={<DeleteOutlined />}>取消</Button>
        </Popconfirm>
      ),
    },
  ];

  // ========== Tab配置 ==========
  const tabItems = [
    {
      key: 'rules',
      label: <span><SafetyOutlined /> 脱敏规则</span>,
      children: (
        <Card>
          <div style={{ marginBottom: 16, display: 'flex', justifyContent: 'space-between' }}>
            <Space>
              <Button icon={<ReloadOutlined />} onClick={loadMaskRules} loading={rulesLoading}>刷新</Button>
              <Button type="primary" icon={<PlusOutlined />} onClick={openCreateRule}>新建规则</Button>
            </Space>
          </div>
          <Table
            dataSource={maskRules}
            columns={maskRuleColumns}
            rowKey="id"
            loading={rulesLoading}
            pagination={{ pageSize: 15, showTotal: (t: number) => `共 ${t} 条规则` }}
            size="small"
            locale={{ emptyText: <Empty description="暂无脱敏规则" /> }}
          />
        </Card>
      ),
    },
    {
      key: 'columns',
      label: <span><EyeInvisibleOutlined /> 敏感列标识</span>,
      children: (
        <Row gutter={16}>
          <Col xs={24} md={10}>
            <Card title={<><DatabaseOutlined /> 选择数据源</>} size="small" style={{ marginBottom: 16 }}>
              <Space direction="vertical" style={{ width: '100%' }}>
                <div>
                  <Text type="secondary" style={{ fontSize: 12 }}>数据库实例</Text>
                  <Select
                    showSearch placeholder="选择数据库实例"
                    value={selectedDbId || undefined}
                    onChange={handleDbChange}
                    style={{ width: '100%' }}
                    filterOption={(input, option) =>
                      (option?.children as unknown as string)?.toLowerCase().includes(input.toLowerCase())
                    }
                  >
                    {databases.map((db: any) => (
                      <Option key={db.id} value={db.id}>{db.name || db.id} ({db.type || ''})</Option>
                    ))}
                  </Select>
                </div>
                <div>
                  <Text type="secondary" style={{ fontSize: 12 }}>数据表</Text>
                  <Select
                    showSearch placeholder="选择数据表"
                    value={selectedTable || undefined}
                    onChange={handleTableChange}
                    style={{ width: '100%' }}
                    disabled={!selectedDbId}
                  >
                    {tables.map((t: string) => (<Option key={t} value={t}>{t}</Option>))}
                  </Select>
                </div>
              </Space>
            </Card>
            <Card
              title={<><TableOutlined /> 表列清单</>}
              size="small"
              extra={selectedTable && (
                <Button size="small" icon={<ReloadOutlined />} onClick={() => handleTableChange(selectedTable)} />
              )}
            >
              {columnsLoading ? (
                <div style={{ textAlign: 'center', padding: 40 }}><Spin /></div>
              ) : !selectedTable ? (
                <Empty description="请先选择数据库和表" />
              ) : columns.length === 0 ? (
                <Empty description="该表无列信息" />
              ) : (
                <div style={{ maxHeight: 400, overflow: 'auto' }}>
                  {columns.map((col: string) => {
                    const marked = isColumnMarked(col);
                    const existing = sensitiveColumns.find((sc) => sc.columnName === col);
                    return (
                      <div
                        key={col}
                        style={{
                          padding: '8px 12px', borderBottom: '1px solid #f0f0f0',
                          display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                          cursor: !marked ? 'pointer' : 'default',
                          background: marked ? '#fff1f0' : undefined,
                        }}
                        onClick={() => !marked && handleMarkColumn(col)}
                      >
                        <Space>
                          <ColumnHeightOutlined />
                          <Text>{col}</Text>
                          {marked && existing && (
                            <Tag color={sensitivityLevelColors[existing.sensitivityLevel]}>
                              {existing.sensitivityLevel}
                            </Tag>
                          )}
                        </Space>
                        {marked ? (
                          <Tag color="red" icon={<EyeInvisibleOutlined />}>敏感</Tag>
                        ) : (
                          <Button type="link" size="small" icon={<PlusOutlined />}>标记</Button>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}
            </Card>
          </Col>
          <Col xs={24} md={14}>
            <Card
              title={<><EyeInvisibleOutlined /> 已标记敏感列</>}
              size="small"
              extra={<Tag color="red" style={{ margin: 0 }}>{sensitiveColumns.length}</Tag>}
            >
              {!selectedTable ? (
                <Empty description="请先选择表" />
              ) : (
                <Table
                  dataSource={sensitiveColumns}
                  columns={sensitiveColumnsTable}
                  rowKey="id"
                  loading={columnsLoading}
                  pagination={{ pageSize: 15, showTotal: (t: number) => `共 ${t} 列` }}
                  size="small"
                  locale={{ emptyText: <Empty description="该表暂无敏感列标记" /> }}
                />
              )}
            </Card>
          </Col>
        </Row>
      ),
    },
  ];

  return (
    <div>
      <h2 style={{ marginBottom: 16 }}>
        <EyeInvisibleOutlined /> 敏感数据管理
      </h2>
      <Tabs activeKey={activeTab} onChange={setActiveTab} items={tabItems} />

      {/* ===== 脱敏规则编辑 Modal ===== */}
      <Modal
        title={ruleEditing ? '编辑脱敏规则' : '新建脱敏规则'}
        open={ruleModalVisible}
        onCancel={() => setRuleModalVisible(false)}
        onOk={handleRuleSubmit}
        confirmLoading={ruleSubmitting}
        okText={ruleEditing ? '保存' : '创建'}
        destroyOnClose
        width={560}
      >
        <Form layout="vertical" size="small">
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item label="规则名称" required>
                <Input
                  placeholder="如：手机号脱敏"
                  value={ruleForm.name}
                  onChange={e => setRuleForm({ ...ruleForm, name: e.target.value })}
                />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="规则编码" required>
                <Input
                  placeholder="如：phone_custom"
                  value={ruleForm.code}
                  onChange={e => setRuleForm({ ...ruleForm, code: e.target.value })}
                  disabled={!!ruleEditing}
                />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={12}>
              <Form.Item label="脱敏类型">
                <Select
                  value={ruleForm.maskType}
                  onChange={v => setRuleForm({ ...ruleForm, maskType: v })}
                >
                  {Object.entries(maskTypeLabels).map(([k, v]) => (
                    <Option key={k} value={k}>{v}</Option>
                  ))}
                </Select>
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item label="脱敏算法" extra="指定Java处理算法">
                <Select
                  allowClear
                  placeholder="不选则用通用参数"
                  value={ruleForm.maskAlgorithm || undefined}
                  onChange={v => setRuleForm({ ...ruleForm, maskAlgorithm: v || '' })}
                  options={algorithmOptions}
                />
              </Form.Item>
            </Col>
          </Row>
          <Row gutter={16}>
            <Col span={8}>
              <Form.Item label="保留前缀长度">
                <Input
                  type="number" min={0}
                  value={ruleForm.keepPrefixLen}
                  onChange={e => setRuleForm({ ...ruleForm, keepPrefixLen: Number(e.target.value) })}
                />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label="保留后缀长度">
                <Input
                  type="number" min={0}
                  value={ruleForm.keepSuffixLen}
                  onChange={e => setRuleForm({ ...ruleForm, keepSuffixLen: Number(e.target.value) })}
                />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item label="遮蔽字符">
                <Input
                  maxLength={1}
                  value={ruleForm.maskCharacter}
                  onChange={e => setRuleForm({ ...ruleForm, maskCharacter: e.target.value })}
                />
              </Form.Item>
            </Col>
          </Row>
          <Form.Item label="描述">
            <Input.TextArea
              rows={2}
              placeholder="规则用途说明"
              value={ruleForm.description}
              onChange={e => setRuleForm({ ...ruleForm, description: e.target.value })}
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* ===== 标记敏感列 Modal ===== */}
      <Modal
        title={<><TagOutlined /> 标记敏感列</>}
        open={markVisible}
        onCancel={() => setMarkVisible(false)}
        onOk={handleSubmitMark}
        confirmLoading={markSubmitting}
        okText="确认标记"
        destroyOnClose
      >
        <Descriptions size="small" column={1} style={{ marginBottom: 16 }}>
          <Descriptions.Item label="Schema">{selectedDbName || selectedDbId}</Descriptions.Item>
          <Descriptions.Item label="表">{selectedTable}</Descriptions.Item>
          <Descriptions.Item label="列"><Text code>{markColumn}</Text></Descriptions.Item>
        </Descriptions>

        <div style={{ marginBottom: 16 }}>
          <div style={{ marginBottom: 8, fontWeight: 500 }}>敏感级别</div>
          <Radio.Group value={sensitivityLevel} onChange={(e) => setSensitivityLevel(e.target.value)}>
            <Space direction="vertical">
              <Radio value="L1"><Tag color="blue">L1</Tag> 低敏感（公开信息）</Radio>
              <Radio value="L2"><Tag color="green">L2</Tag> 中敏感（用户名、邮箱）</Radio>
              <Radio value="L3"><Tag color="orange">L3</Tag> 高敏感（手机号、身份证）</Radio>
              <Radio value="L4"><Tag color="red">L4</Tag> 极高敏感（密码、密钥）</Radio>
            </Space>
          </Radio.Group>
        </div>

        <div style={{ marginBottom: 16 }}>
          <div style={{ marginBottom: 8, fontWeight: 500 }}>分类标签（可选）</div>
          <Input
            placeholder="如：个人身份信息、财务数据"
            value={category}
            onChange={(e) => setCategory(e.target.value)}
          />
        </div>

        <div>
          <div style={{ marginBottom: 8, fontWeight: 500 }}>脱敏规则</div>
          <Select
            placeholder="选择脱敏规则（可选）"
            value={selectedMaskRuleId || undefined}
            onChange={(val) => setSelectedMaskRuleId(val)}
            style={{ width: '100%' }}
            allowClear
          >
            {maskRules.map((rule) => (
              <Option key={rule.id} value={rule.id}>
                <Space>
                  <span>{rule.name}</span>
                  {rule.maskAlgorithm && <Tag color="blue" style={{ fontSize: 10 }}>{algorithmLabels[rule.maskAlgorithm] || rule.maskAlgorithm}</Tag>}
                </Space>
              </Option>
            ))}
          </Select>
        </div>
      </Modal>
    </div>
  );
};

export default SensitiveDataPage;
