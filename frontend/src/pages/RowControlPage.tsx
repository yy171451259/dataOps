import React, { useState, useEffect } from 'react';
import {
  Table, Button, Modal, Form, Input, Select, message, Card, Space,
  Popconfirm, Tag, Switch, Tooltip
} from 'antd';
import {
  PlusOutlined, DeleteOutlined, EditOutlined, FilterOutlined,
  DatabaseOutlined, TableOutlined, UserOutlined, TeamOutlined
} from '@ant-design/icons';
import { rowControlApi, instanceApi } from '../utils/api';
import dayjs from 'dayjs';

const { TextArea } = Input;
const { Option } = Select;

interface RowControlRule {
  id: string;
  name: string;
  databaseId: string;
  databaseName: string;
  tableName: string;
  filterCondition: string;
  filterDescription: string;
  targetUserIds: string;
  targetRoleIds: string;
  isActive: boolean;
  priority: number;
  createdAt: string;
  updatedAt: string;
  createdBy: string;
}

const RowControlPage: React.FC = () => {
  const [rules, setRules] = useState<RowControlRule[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingRule, setEditingRule] = useState<RowControlRule | null>(null);
  const [databases, setDatabases] = useState<any[]>([]);
  const [form] = Form.useForm();
  const [saving, setSaving] = useState(false);

  const loadRules = async () => {
    setLoading(true);
    try {
      const res = await rowControlApi.list();
      setRules(res.data?.data || []);
    } catch {
      message.error('加载规则失败');
    } finally {
      setLoading(false);
    }
  };

  const loadDatabases = async () => {
    try {
      const res = await instanceApi.list();
      setDatabases(res.data?.data || []);
    } catch { /* ignore */ }
  };

  useEffect(() => { loadRules(); loadDatabases(); }, []);

  const handleOpenModal = (rule?: RowControlRule) => {
    setEditingRule(rule || null);
    form.resetFields();
    if (rule) {
      form.setFieldsValue(rule);
    }
    setModalVisible(true);
  };

  const handleSubmit = async () => {
    try {
      const values = await form.validateFields();
      setSaving(true);
      if (editingRule) {
        await rowControlApi.update(editingRule.id, values);
        message.success('规则已更新');
      } else {
        await rowControlApi.create(values);
        message.success('规则已创建');
      }
      setModalVisible(false);
      loadRules();
    } catch (err: any) {
      if (err?.errorFields) return;
      message.error('操作失败');
    } finally {
      setSaving(false);
    }
  };

  const handleDelete = async (id: string) => {
    try {
      await rowControlApi.delete(id);
      message.success('规则已删除');
      loadRules();
    } catch {
      message.error('删除失败');
    }
  };

  const handleToggleActive = async (rule: RowControlRule) => {
    try {
      await rowControlApi.update(rule.id, { ...rule, isActive: !rule.isActive });
      message.success(`规则已${rule.isActive ? '停用' : '启用'}`);
      loadRules();
    } catch { message.error('操作失败'); }
  };

  const columns = [
    {
      title: '规则名称', dataIndex: 'name', key: 'name', width: 160,
      render: (v: string, r: RowControlRule) => (
        <Space>
          <FilterOutlined style={{ color: r.isActive ? '#1677ff' : '#ccc' }} />
          <span>{v}</span>
        </Space>
      ),
    },
    {
      title: '目标表', key: 'table', width: 200,
      render: (_: any, r: RowControlRule) => (
        <Space size={4}>
          <DatabaseOutlined style={{ color: '#666' }} />
          <span style={{ fontSize: 12 }}>{r.databaseName}</span>
          <span style={{ color: '#ccc' }}>·</span>
          <TableOutlined style={{ color: '#666' }} />
          <span style={{ fontSize: 12 }}>{r.tableName}</span>
        </Space>
      ),
    },
    {
      title: '过滤条件', dataIndex: 'filterCondition', key: 'filterCondition',
      ellipsis: true, width: 280,
      render: (v: string) => (
        <Tooltip title={v}>
          <code style={{ fontSize: 11, background: '#f5f5f5', padding: '2px 6px', borderRadius: 3 }}>{v}</code>
        </Tooltip>
      ),
    },
    {
      title: '目标用户/角色', key: 'target', width: 160,
      render: (_: any, r: RowControlRule) => (
        <Space size={4} wrap>
          {r.targetUserIds?.split(',').filter(Boolean).map((id: string) => (
            <Tag key={id} color="blue" icon={<UserOutlined />} style={{ fontSize: 10 }}>{id}</Tag>
          ))}
          {r.targetRoleIds?.split(',').filter(Boolean).map((id: string) => (
            <Tag key={id} color="green" icon={<TeamOutlined />} style={{ fontSize: 10 }}>{id}</Tag>
          ))}
          {!r.targetUserIds && !r.targetRoleIds && <span style={{ color: '#999', fontSize: 11 }}>所有用户</span>}
        </Space>
      ),
    },
    {
      title: '优先级', dataIndex: 'priority', key: 'priority', width: 70, align: 'center' as const,
    },
    {
      title: '状态', key: 'status', width: 80, align: 'center' as const,
      render: (_: any, r: RowControlRule) => (
        <Switch checked={r.isActive} size="small" onChange={() => handleToggleActive(r)} />
      ),
    },
    {
      title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 160,
      render: (v: string) => v ? dayjs(v).format('YYYY-MM-DD HH:mm') : '-',
    },
    {
      title: '操作', key: 'actions', width: 120, align: 'center' as const,
      render: (_: any, r: RowControlRule) => (
        <Space size={4}>
          <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleOpenModal(r)} />
          <Popconfirm title="确认删除该规则？" onConfirm={() => handleDelete(r.id)} okText="删除" cancelText="取消">
            <Button type="link" size="small" danger icon={<DeleteOutlined />} />
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <div style={{ padding: 24, height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Card
        title={
          <Space>
            <FilterOutlined />
            <span>行级管控规则</span>
            <Tag color="orange">细粒度数据权限</Tag>
          </Space>
        }
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={() => handleOpenModal()}>
            新建规则
          </Button>
        }
        style={{ flex: 1, display: 'flex', flexDirection: 'column' }}
        bodyStyle={{ flex: 1, overflow: 'auto', padding: 0 }}
      >
        <Table
          dataSource={rules}
          columns={columns}
          rowKey="id"
          loading={loading}
          pagination={{ pageSize: 15, showSizeChanger: true }}
          scroll={{ x: 1200 }}
          locale={{ emptyText: (
            <div style={{ padding: 40, color: '#999' }}>
              <FilterOutlined style={{ fontSize: 32, marginBottom: 8, color: '#ddd' }} />
              <div>暂无行级管控规则</div>
              <div style={{ fontSize: 12, color: '#ccc' }}>创建规则可限制用户只能访问特定数据行</div>
            </div>
          )}}
        />
      </Card>

      {/* 创建/编辑规则 Modal */}
      <Modal
        title={
          <Space>
            <FilterOutlined />
            {editingRule ? '编辑行级管控规则' : '新建行级管控规则'}
          </Space>
        }
        open={modalVisible}
        onOk={handleSubmit}
        onCancel={() => setModalVisible(false)}
        okText={editingRule ? '保存' : '创建'}
        cancelText="取消"
        confirmLoading={saving}
        width={640}
        destroyOnClose
      >
        <Form form={form} layout="vertical" style={{ marginTop: 8 }}>
          <Form.Item name="name" label="规则名称" rules={[{ required: true, message: '请输入规则名称' }]}>
            <Input placeholder="例如：研发部数据行权限" />
          </Form.Item>

          <Space style={{ width: '100%' }} size={16}>
            <Form.Item name="databaseId" label="数据库实例" rules={[{ required: true }]} style={{ width: 250 }}>
              <Select placeholder="选择数据库" onChange={(val) => form.setFieldValue('databaseName', databases.find(d => d.id === val)?.name)}>
                {databases.map(db => (
                  <Option key={db.id} value={db.id}>{db.name}</Option>
                ))}
              </Select>
            </Form.Item>
            <Form.Item name="databaseName" label="数据库名" rules={[{ required: true }]} style={{ width: 200 }}>
              <Input placeholder="数据库名" />
            </Form.Item>
            <Form.Item name="tableName" label="表名" rules={[{ required: true }]} style={{ width: 150 }}>
              <Input placeholder="表名" />
            </Form.Item>
          </Space>

          <Form.Item
            name="filterCondition"
            label="行过滤条件(SQL WHERE)"
            rules={[{ required: true, message: '请输入过滤条件' }]}
            extra="输入 SQL WHERE 子句，如：department='研发部' AND status='active'"
          >
            <TextArea
              rows={3}
              placeholder="department='研发部' AND status='active'"
              style={{ fontFamily: 'monospace', fontSize: 12 }}
            />
          </Form.Item>

          <Form.Item name="filterDescription" label="条件描述">
            <Input placeholder="用中文简述过滤条件，便于其他管理员理解" />
          </Form.Item>

          <Space style={{ width: '100%' }} size={16}>
            <Form.Item name="targetUserIds" label="目标用户" style={{ width: 300 }}
              extra="留空表示适用于所有用户">
              <Input placeholder="用户ID，逗号分隔" />
            </Form.Item>
            <Form.Item name="targetRoleIds" label="目标角色" style={{ width: 300 }}
              extra="留空表示适用于所有角色">
              <Input placeholder="角色ID，逗号分隔" />
            </Form.Item>
          </Space>

          <Form.Item name="priority" label="优先级" initialValue={0} extra="数值越大优先级越高，多条规则时按优先级合并">
            <Input type="number" placeholder="0" style={{ width: 120 }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default RowControlPage;
