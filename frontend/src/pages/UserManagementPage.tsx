import React, { useEffect, useState } from 'react';
import { Table, Button, Modal, Form, Input, Popconfirm, message, Card, Space, Tag, Select } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined, UserOutlined, KeyOutlined } from '@ant-design/icons';
import { userApi, roleApi } from '../utils/api';

interface RoleOption { id: string; name: string; code: string; }

const UserManagementPage: React.FC = () => {
  const [data, setData] = useState<any[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingItem, setEditingItem] = useState<any>(null);
  const [form] = Form.useForm();
  const [allRoles, setAllRoles] = useState<RoleOption[]>([]);
  const [userRolesMap, setUserRolesMap] = useState<Record<string, RoleOption[]>>({});

  // 加载用户列表和所有角色
  const loadData = async () => {
    setLoading(true);
    try {
      const [userRes, roleRes] = await Promise.all([
        userApi.list({ page: 1, size: 100 }),
        roleApi.listAll(),
      ]);
      const users = userRes.data.data?.records || [];
      setData(users);
      const roles: RoleOption[] = roleRes.data.data || [];
      setAllRoles(roles);

      // 批量加载每个用户的角色
      const map: Record<string, RoleOption[]> = {};
      await Promise.all(users.map(async (u: any) => {
        try {
          const rRes = await userApi.getRoles(u.id);
          const roleData = rRes.data.data || [];
          map[u.id] = Array.isArray(roleData) ? roleData : [];
        } catch { map[u.id] = []; }
      }));
      setUserRolesMap(map);
    } finally { setLoading(false); }
  };

  useEffect(() => { loadData(); }, []);

  const handleCreate = () => { setEditingItem(null); form.resetFields(); form.setFieldsValue({ roleIds: [] }); setModalVisible(true); };
  const handleEdit = async (record: any) => {
    setEditingItem(record);
    form.setFieldsValue(record);
    // 加载当前用户角色
    try {
      const res = await userApi.getRoles(record.id);
      const roles = res.data.data || [];
      const roleIds = Array.isArray(roles) ? roles.map((r: any) => r.id || r) : [];
      form.setFieldsValue({ ...record, roleIds });
    } catch {
      form.setFieldsValue({ ...record, roleIds: [] });
    }
    setModalVisible(true);
  };
  const handleDelete = async (id: string) => { await userApi.delete(id); message.success('OK'); loadData(); };
  const handleResetPwd = async (id: string) => {
    const pwd = prompt('请输入新密码:');
    if (pwd) {
      await userApi.resetPassword(id, pwd);
      message.success('OK');
    }
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    const { roleIds, ...userData } = values;
    try {
      if (editingItem) {
        await userApi.update(editingItem.id, userData);
        // 更新角色分配
        if (roleIds !== undefined) {
          await userApi.assignRoles(editingItem.id, roleIds || []);
        }
      } else {
        const createRes = await userApi.create(userData);
        // 新建用户后分配角色
        const newUserId = createRes.data.data?.id;
        if (newUserId && roleIds && roleIds.length > 0) {
          await userApi.assignRoles(newUserId, roleIds);
        }
      }
      message.success(editingItem ? '更新成功' : '创建成功');
      setModalVisible(false);
      loadData();
    } catch (e: any) {
      message.error(e.response?.data?.message || '操作失败');
    }
  };

  const columns = [
    { title: '用户名', dataIndex: 'username', key: 'username', width: 120 },
    { title: '昵称', dataIndex: 'nickname', key: 'nickname', width: 100 },
    { title: '邮箱', dataIndex: 'email', key: 'email', width: 180, ellipsis: true },
    {
      title: '角色', key: 'roles', width: 200,
      render: (_: any, record: any) => {
        const roles = userRolesMap[record.id] || [];
        if (roles.length === 0) return <Tag color="default">无角色</Tag>;
        return (
          <Space size={2} wrap>
            {roles.map((r: any) => (
              <Tag key={r.id || r} color="blue">{r.name || r}</Tag>
            ))}
          </Space>
        );
      },
    },
    {
      title: '状态', dataIndex: 'isActive', key: 'isActive', width: 70,
      render: (v: boolean) => v ? <Tag color="green">启用</Tag> : <Tag color="red">禁用</Tag>,
    },
    {
      title: '管理员', dataIndex: 'isAdmin', key: 'isAdmin', width: 70,
      render: (v: boolean) => v ? <Tag color="gold">是</Tag> : <Tag>否</Tag>,
    },
    { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 160 },
    {
      title: '操作', key: 'action', width: 240,
      render: (_: any, record: any) => (
        <Space>
          <Button size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)}>编辑</Button>
          <Button size="small" icon={<KeyOutlined />} onClick={() => handleResetPwd(record.id)}>重置密码</Button>
          <Popconfirm title="确定删除?" onConfirm={() => handleDelete(record.id)}>
            <Button size="small" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Card title={<><UserOutlined /> 用户管理</>} extra={<Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>新建用户</Button>}>
      <Table dataSource={data} columns={columns} rowKey="id" loading={loading} size="small"
        pagination={{ pageSize: 20 }} />
      <Modal
        title={editingItem ? '编辑用户' : '新建用户'}
        open={modalVisible}
        onOk={handleSubmit}
        onCancel={() => setModalVisible(false)}
        width={520}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="username" label="用户名" rules={[{ required: true }]}><Input /></Form.Item>
          {!editingItem && (
            <Form.Item name="passwordHash" label="密码" rules={[{ required: true }]}><Input.Password /></Form.Item>
          )}
          <Form.Item name="nickname" label="昵称"><Input /></Form.Item>
          <Form.Item name="email" label="邮箱"><Input /></Form.Item>
          <Form.Item name="roleIds" label="角色">
            <Select
              mode="multiple"
              placeholder="选择角色（可选）"
              optionFilterProp="label"
              options={allRoles.map(r => ({ label: `${r.name} (${r.code})`, value: r.id }))}
            />
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default UserManagementPage;
