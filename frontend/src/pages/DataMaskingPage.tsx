import React, { useEffect, useState } from 'react';
import { Table, Button, Modal, Form, Input, Select, Switch, Popconfirm, message, Card, Tag, Space } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, SafetyOutlined } from '@ant-design/icons';
import { maskingApi, instanceApi } from '../utils/api';

const MASK_ALGORITHMS = [
  { label: '完全替换(****)', value: 'REPLACE' },
  { label: '首尾保留(中间掩码)', value: 'MASK' },
  { label: '哈希摘要', value: 'HASH' },
  { label: '截断缩略', value: 'TRUNCATE' },
  { label: 'Base64编码', value: 'ENCRYPT' },
  { label: '手机号脱敏', value: 'PHONE' },
  { label: '邮箱脱敏', value: 'EMAIL' },
  { label: '身份证脱敏', value: 'ID_CARD' },
  { label: '银行卡脱敏', value: 'BANK_CARD' },
];

const DataMaskingPage: React.FC = () => {
  const [data, setData] = useState<any[]>([]);
  const [databases, setDatabases] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingItem, setEditingItem] = useState<any>(null);
  const [form] = Form.useForm();

  useEffect(() => {
    instanceApi.list().then(res => setDatabases(res.data.data || []));
  }, []);

  const loadData = async () => {
    setLoading(true);
    try {
      const res = await maskingApi.listRules({ page: 1, size: 100 });
      setData(res.data.data?.records || []);
    } finally { setLoading(false); }
  };

  useEffect(() => { loadData(); }, []);

  const getDbName = (id: string) => databases.find(d => d.id === id)?.name || id;

  const handleCreate = () => { setEditingItem(null); form.resetFields(); setModalVisible(true); };
  const handleEdit = (record: any) => { setEditingItem(record); form.setFieldsValue(record); setModalVisible(true); };
  const handleDelete = async (id: string) => { await maskingApi.deleteRule(id); message.success('OK'); loadData(); };
  const handleToggle = async (id: string, enabled: boolean) => { await maskingApi.toggleRule(id, enabled); message.success('OK'); loadData(); };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    if (editingItem) {
      await maskingApi.updateRule(editingItem.id, values);
    } else {
      await maskingApi.createRule(values);
    }
    message.success(editingItem ? '更新成功' : '创建成功');
    setModalVisible(false);
    loadData();
  };

  const columns = [
    { title: '规则名称', dataIndex: 'name', key: 'name', width: 150 },
    { title: '数据库', dataIndex: 'databaseId', key: 'databaseId', width: 120, render: getDbName },
    { title: '表名', dataIndex: 'tableName', key: 'tableName', width: 120 },
    { title: '字段', dataIndex: 'columnName', key: 'columnName', width: 120 },
    {
      title: '脱敏算法', dataIndex: 'maskAlgorithm', key: 'maskAlgorithm', width: 140,
      render: (v: string) => <Tag color="blue">{MASK_ALGORITHMS.find(a => a.value === v)?.label || v}</Tag>
    },
    {
      title: '状态', dataIndex: 'isEnabled', key: 'isEnabled', width: 80,
      render: (v: boolean, record: any) => (
        <Switch size="small" checked={v} onChange={(checked) => handleToggle(record.id, checked)} />
      )
    },
    { title: '优先级', dataIndex: 'priority', key: 'priority', width: 70 },
    {
      title: '操作', key: 'action', width: 160,
      render: (_: any, record: any) => (
        <Space>
          <Button size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)}>编辑</Button>
          <Popconfirm title="确定删除?" onConfirm={() => handleDelete(record.id)}>
            <Button size="small" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Card title={<><SafetyOutlined /> 数据脱敏管理</>} extra={<Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>新建规则</Button>}>
      <Table dataSource={data} columns={columns} rowKey="id" loading={loading} size="small" />
      <Modal title={editingItem ? '编辑脱敏规则' : '新建脱敏规则'} open={modalVisible} onOk={handleSubmit} onCancel={() => setModalVisible(false)} width={600}>
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="规则名称" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="databaseId" label="数据库实例">
            <Select allowClear options={databases.map(d => ({ label: d.name, value: d.id }))} />
          </Form.Item>
          <Form.Item name="tableName" label="表名">
            <Input placeholder="留空表示所有表" />
          </Form.Item>
          <Form.Item name="columnName" label="字段名" rules={[{ required: true }]}>
            <Input />
          </Form.Item>
          <Form.Item name="maskAlgorithm" label="脱敏算法" rules={[{ required: true }]}>
            <Select options={MASK_ALGORITHMS} />
          </Form.Item>
          <Form.Item name="priority" label="优先级">
            <Input type="number" />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default DataMaskingPage;
