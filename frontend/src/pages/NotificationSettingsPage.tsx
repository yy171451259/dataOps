import React, { useEffect, useState } from 'react';
import { Table, Button, Modal, Form, Input, Select, Switch, Popconfirm, message, Card, Space, Tag } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, BellOutlined, SendOutlined } from '@ant-design/icons';
import { notificationApi } from '../utils/api';

const { TextArea } = Input;

const CHANNELS = [
  { label: '邮件', value: 'EMAIL' },
  { label: '钉钉', value: 'DINGTALK' },
  { label: '企业微信', value: 'WECHAT' },
  { label: 'Webhook', value: 'WEBHOOK' },
  { label: '短信', value: 'SMS' },
];

const NotificationSettingsPage: React.FC = () => {
  const [configs, setConfigs] = useState<any[]>([]);
  const [logs, setLogs] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingItem, setEditingItem] = useState<any>(null);
  const [tabActive, setTabActive] = useState<'configs' | 'logs'>('configs');
  const [form] = Form.useForm();

  const loadConfigs = async () => {
    setLoading(true);
    try {
      const res = await notificationApi.listConfigs({ page: 1, size: 100 });
      setConfigs(res.data.data?.records || []);
    } finally { setLoading(false); }
  };

  const loadLogs = async () => {
    setLoading(true);
    try {
      const res = await notificationApi.listLogs({ page: 1, size: 100 });
      setLogs(res.data.data?.records || []);
    } finally { setLoading(false); }
  };

  useEffect(() => { loadConfigs(); loadLogs(); }, []);

  const handleCreate = () => { setEditingItem(null); form.resetFields(); setModalVisible(true); };
  const handleEdit = (record: any) => { setEditingItem(record); form.setFieldsValue(record); setModalVisible(true); };
  const handleDelete = async (id: string) => { await notificationApi.deleteConfig(id); message.success('OK'); loadConfigs(); };
  const handleToggle = async (id: string, enabled: boolean) => { await notificationApi.toggleConfig(id, enabled); message.success('OK'); loadConfigs(); };
  const handleTest = async (id: string) => { await notificationApi.testConfig(id); message.success('OK'); loadLogs(); };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    if (editingItem) {
      await notificationApi.updateConfig(editingItem.id, values);
    } else {
      await notificationApi.createConfig(values);
    }
    message.success(editingItem ? '更新成功' : '创建成功');
    setModalVisible(false);
    loadConfigs();
  };

  const configColumns = [
    { title: '名称', dataIndex: 'name', key: 'name', width: 150 },
    {
      title: '渠道', dataIndex: 'channel', key: 'channel', width: 100,
      render: (v: string) => <Tag color="blue">{CHANNELS.find(c => c.value === v)?.label || v}</Tag>
    },
    {
      title: '状态', dataIndex: 'isEnabled', key: 'isEnabled', width: 70,
      render: (v: boolean, record: any) => (
        <Switch size="small" checked={v} onChange={(checked) => handleToggle(record.id, checked)} />
      )
    },
    { title: '触发事件', dataIndex: 'triggerEvents', key: 'triggerEvents', width: 120, ellipsis: true },
    { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 160 },
    {
      title: '操作', key: 'action', width: 240,
      render: (_: any, record: any) => (
        <Space>
          <Button size="small" icon={<SendOutlined />} onClick={() => handleTest(record.id)}>测试</Button>
          <Button size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)}>编辑</Button>
          <Popconfirm title="确定删除?" onConfirm={() => handleDelete(record.id)}>
            <Button size="small" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  const logColumns = [
    { title: '渠道', dataIndex: 'channel', key: 'channel', width: 80, render: (v: string) => <Tag>{v}</Tag> },
    { title: '接收人', dataIndex: 'recipient', key: 'recipient', width: 150 },
    { title: '标题', dataIndex: 'title', key: 'title', width: 200, ellipsis: true },
    {
      title: '状态', dataIndex: 'status', key: 'status', width: 80,
      render: (v: string) => v === 'success' ? <Tag color="green">成功</Tag> : v === 'failed' ? <Tag color="red">失败</Tag> : <Tag>待发送</Tag>
    },
    { title: '错误信息', dataIndex: 'errorMessage', key: 'errorMessage', width: 150, ellipsis: true },
    { title: '发送时间', dataIndex: 'sentAt', key: 'sentAt', width: 160 },
  ];

  return (
    <Card
      title={<><BellOutlined /> 通知管理</>}
      extra={<Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>新建配置</Button>}
      tabList={[
        { key: 'configs', tab: '通知配置' },
        { key: 'logs', tab: '发送记录' }
      ]}
      activeTabKey={tabActive}
      onTabChange={(k) => { setTabActive(k as any); if (k === 'logs') loadLogs(); else loadConfigs(); }}
    >
      {tabActive === 'configs' ? (
        <Table dataSource={configs} columns={configColumns} rowKey="id" loading={loading} size="small" />
      ) : (
        <Table dataSource={logs} columns={logColumns} rowKey="id" loading={loading} size="small" />
      )}

      <Modal title={editingItem ? '编辑通知配置' : '新建通知配置'} open={modalVisible} onOk={handleSubmit} onCancel={() => setModalVisible(false)} width={600}>
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="配置名称" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="channel" label="通知渠道" rules={[{ required: true }]}>
            <Select options={CHANNELS} />
          </Form.Item>
          <Form.Item name="config" label="配置JSON" rules={[{ required: true }]} tooltip='邮件示例: {"recipient":"xxx@qq.com","host":"smtp.qq.com","port":465}'>
            <TextArea rows={3} placeholder='{"recipient":"xxx@qq.com"}' />
          </Form.Item>
          <Form.Item name="triggerEvents" label="触发事件(JSON数组)" tooltip='["*"]表示所有事件'>
            <Input placeholder='["ticket.created","ticket.approved"]' />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default NotificationSettingsPage;
