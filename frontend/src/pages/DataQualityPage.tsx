import React, { useEffect, useState } from 'react';
import { Table, Button, Modal, Form, Input, Select, Switch, Popconfirm, message, Card, Tag, Space, Row, Col, Statistic } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, PlayCircleOutlined, CheckCircleOutlined, CloseCircleOutlined } from '@ant-design/icons';
import { qualityApi, instanceApi } from '../utils/api';

const DataQualityPage: React.FC = () => {
  const [rules, setRules] = useState<any[]>([]);
  const [results, setResults] = useState<any[]>([]);
  const [databases, setDatabases] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingItem, setEditingItem] = useState<any>(null);
  const [tabActive, setTabActive] = useState<'rules' | 'results'>('rules');
  const [form] = Form.useForm();

  useEffect(() => {
    instanceApi.list().then(res => setDatabases(res.data.data || []));
  }, []);

  const loadRules = async () => {
    setLoading(true);
    try {
      const res = await qualityApi.listRules({ page: 1, size: 100 });
      setRules(res.data.data?.records || []);
    } finally { setLoading(false); }
  };

  const loadResults = async () => {
    setLoading(true);
    try {
      const res = await qualityApi.listResults({ page: 1, size: 100 });
      setResults(res.data.data?.records || []);
    } finally { setLoading(false); }
  };

  useEffect(() => { loadRules(); loadResults(); }, []);

  const getDbName = (id: string) => databases.find(d => d.id === id)?.name || id;

  const handleCreate = () => { setEditingItem(null); form.resetFields(); setModalVisible(true); };
  const handleEdit = (record: any) => { setEditingItem(record); form.setFieldsValue(record); setModalVisible(true); };
  const handleDelete = async (id: string) => { await qualityApi.deleteRule(id); message.success('OK'); loadRules(); };
  const handleToggle = async (id: string, enabled: boolean) => { await qualityApi.toggleRule(id, enabled); message.success('OK'); loadRules(); };
  const handleExecute = async (id: string) => { await qualityApi.executeRule(id); message.success('OK'); loadResults(); };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    if (editingItem) {
      await qualityApi.updateRule(editingItem.id, values);
    } else {
      await qualityApi.createRule(values);
    }
    message.success(editingItem ? '更新成功' : '创建成功');
    setModalVisible(false);
    loadRules();
  };

  const ruleColumns = [
    { title: '名称', dataIndex: 'name', key: 'name', width: 160 },
    { title: '类型', dataIndex: 'ruleType', key: 'ruleType', width: 100, render: (v: string) => <Tag>{v}</Tag> },
    { title: '数据库', dataIndex: 'databaseId', key: 'databaseId', width: 120, render: getDbName },
    { title: '表名', dataIndex: 'tableName', key: 'tableName', width: 120 },
    { title: '字段', dataIndex: 'columnName', key: 'columnName', width: 120 },
    { title: '期望值', dataIndex: 'expectedValue', key: 'expectedValue', width: 100 },
    {
      title: '状态', dataIndex: 'isEnabled', key: 'isEnabled', width: 70,
      render: (v: boolean, record: any) => (
        <Switch size="small" checked={v} onChange={(checked) => handleToggle(record.id, checked)} />
      )
    },
    {
      title: '操作', key: 'action', width: 200,
      render: (_: any, record: any) => (
        <Space>
          <Button size="small" icon={<PlayCircleOutlined />} onClick={() => handleExecute(record.id)}>执行</Button>
          <Button size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)}>编辑</Button>
          <Popconfirm title="确定删除?" onConfirm={() => handleDelete(record.id)}>
            <Button size="small" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const resultColumns = [
    { title: '规则ID', dataIndex: 'ruleId', key: 'ruleId', width: 120, ellipsis: true },
    { title: '数据库', dataIndex: 'databaseId', key: 'databaseId', width: 100, render: getDbName },
    { title: '检查值', dataIndex: 'checkValue', key: 'checkValue', width: 100 },
    { title: '期望值', dataIndex: 'expectedValue', key: 'expectedValue', width: 100 },
    {
      title: '结果', dataIndex: 'isPass', key: 'isPass', width: 80,
      render: (v: boolean) => v ? <Tag icon={<CheckCircleOutlined />} color="success">通过</Tag> : <Tag icon={<CloseCircleOutlined />} color="error">失败</Tag>
    },
    { title: '错误', dataIndex: 'errorMessage', key: 'errorMessage', ellipsis: true },
    { title: '耗时(ms)', dataIndex: 'executionTime', key: 'executionTime', width: 80 },
    { title: '检查时间', dataIndex: 'checkedAt', key: 'checkedAt', width: 160 },
  ];

  const passCount = results.filter(r => r.isPass).length;
  const failCount = results.length - passCount;

  return (
    <div>
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col span={6}><Card><Statistic title="总检查次数" value={results.length} /></Card></Col>
        <Col span={6}><Card><Statistic title="通过" value={passCount} valueStyle={{ color: '#3f8600' }} prefix={<CheckCircleOutlined />} /></Card></Col>
        <Col span={6}><Card><Statistic title="失败" value={failCount} valueStyle={{ color: '#cf1322' }} prefix={<CloseCircleOutlined />} /></Card></Col>
        <Col span={6}><Card><Statistic title="质量规则" value={rules.length} /></Card></Col>
      </Row>

      <Card
        title="数据质量管理"
        extra={<Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>新建规则</Button>}
        tabList={[
          { key: 'rules', tab: '质量规则' },
          { key: 'results', tab: '检查结果' },
        ]}
        activeTabKey={tabActive}
        onTabChange={(k) => { setTabActive(k as any); if (k === 'results') loadResults(); else loadRules(); }}
      >
        {tabActive === 'rules' ? (
          <Table dataSource={rules} columns={ruleColumns} rowKey="id" loading={loading} size="small" />
        ) : (
          <Table dataSource={results} columns={resultColumns} rowKey="id" loading={loading} size="small" />
        )}
      </Card>

      <Modal title={editingItem ? '编辑质量规则' : '新建质量规则'} open={modalVisible} onOk={handleSubmit} onCancel={() => setModalVisible(false)} width={600}>
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="规则名称" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="ruleType" label="规则类型" rules={[{ required: true }]}>
            <Select options={[{ label: '完整性', value: 'COMPLETENESS' }, { label: '唯一性', value: 'UNIQUENESS' }, { label: '准确性', value: 'ACCURACY' }, { label: '一致性', value: 'CONSISTENCY' }]} />
          </Form.Item>
          <Form.Item name="databaseId" label="数据库实例" rules={[{ required: true }]}>
            <Select options={databases.map(d => ({ label: d.name, value: d.id }))} />
          </Form.Item>
          <Form.Item name="tableName" label="表名" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="columnName" label="字段名"><Input /></Form.Item>
          <Form.Item name="checkSql" label="检查SQL" rules={[{ required: true }]}><Input.TextArea rows={3} /></Form.Item>
          <Form.Item name="expectedValue" label="期望值"><Input /></Form.Item>
          <Form.Item name="severity" label="严重级别"><Select options={[{ label: '错误', value: 'error' }, { label: '警告', value: 'warning' }, { label: '提示', value: 'info' }]} /></Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default DataQualityPage;
