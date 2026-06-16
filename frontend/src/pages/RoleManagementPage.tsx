import React, { useEffect, useState } from 'react';
import {
  Table, Button, Modal, Form, Input, Popconfirm, message, Card, Space, Tag,
  Drawer, List, Typography, Tooltip, Tree,
} from 'antd';
import {
  PlusOutlined, EditOutlined, DeleteOutlined, TeamOutlined,
  SafetyOutlined, UserOutlined, ReloadOutlined, FolderOutlined,
} from '@ant-design/icons';
import { roleApi, menuApi } from '../utils/api';
import { getIcon } from '../utils/iconMap';

const { Text } = Typography;

interface RoleItem {
  id: string; name: string; code: string; description: string;
  isSystem: boolean; createTime: string; updateTime: string;
}

const RoleManagementPage: React.FC = () => {
  const [data, setData] = useState<RoleItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingItem, setEditingItem] = useState<RoleItem | null>(null);
  const [form] = Form.useForm();

  // 菜单权限分配
  const [permDrawerVisible, setPermDrawerVisible] = useState(false);
  const [selectedRole, setSelectedRole] = useState<RoleItem | null>(null);
  const [menuTree, setMenuTree] = useState<any[]>([]);
  const [checkedMenuIds, setCheckedMenuIds] = useState<string[]>([]);
  const [permSaving, setPermSaving] = useState(false);

  // 查看用户
  const [usersModalVisible, setUsersModalVisible] = useState(false);
  const [usersRole, setUsersRole] = useState<RoleItem | null>(null);
  const [userList, setUserList] = useState<string[]>([]);
  const [usersLoading, setUsersLoading] = useState(false);

  const loadData = async () => {
    setLoading(true);
    try {
      const res = await roleApi.list({ page: 1, size: 100 });
      setData(res.data.data?.records || []);
    } finally { setLoading(false); }
  };

  const loadMenuTree = async () => {
    try {
      const res = await menuApi.getTree();
      setMenuTree(res.data.data || []);
    } catch { /* ignore */ }
  };

  useEffect(() => { loadData(); loadMenuTree(); }, []);

  // ==== 角色 CRUD ====
  const handleCreate = () => { setEditingItem(null); form.resetFields(); setModalVisible(true); };
  const handleEdit = (record: RoleItem) => { setEditingItem(record); form.setFieldsValue(record); setModalVisible(true); };
  const handleDelete = async (id: string) => {
    try {
      await roleApi.delete(id);
      message.success('删除成功'); loadData();
    } catch (e: any) {
      message.error(e.response?.data?.message || '删除失败');
    }
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    try {
      if (editingItem) {
        await roleApi.update(editingItem.id, values);
        message.success('更新成功');
      } else {
        await roleApi.create(values);
        message.success('创建成功');
      }
      setModalVisible(false); loadData();
    } catch (e: any) {
      message.error(e.response?.data?.message || '保存失败');
    }
  };

  // ==== 权限分配 ====
  const openPermDrawer = async (role: RoleItem) => {
    setSelectedRole(role);
    setPermDrawerVisible(true);
    try {
      const res = await menuApi.getRoleMenus(role.id);
      const menuIds: string[] = res.data.data || [];
      setCheckedMenuIds(menuIds);
    } catch {
      setCheckedMenuIds([]);
    }
  };

  const handleSavePermissions = async () => {
    if (!selectedRole) return;
    setPermSaving(true);
    try {
      await menuApi.assignRoleMenus(selectedRole.id, checkedMenuIds);
      message.success('菜单权限分配成功');
      setPermDrawerVisible(false);
    } catch (e: any) {
      message.error(e.response?.data?.message || '保存失败');
    } finally { setPermSaving(false); }
  };

  // ==== 查看用户 ====
  const openUsers = async (role: RoleItem) => {
    setUsersRole(role);
    setUsersModalVisible(true);
    setUsersLoading(true);
    try {
      const res = await roleApi.getUsers(role.id);
      setUserList(res.data.data || []);
    } catch { setUserList([]); }
    finally { setUsersLoading(false); }
  };

  // 将后端菜单树转为 Ant Design Tree 需要的格式
  const convertToTreeNode = (node: any): any => {
    const hasChildren = node.children && node.children.length > 0;
    return {
      key: node.id,
      title: (
        <Space>
          {node.icon ? getIcon(node.icon) : <FolderOutlined />}
          <span>{node.name}</span>
          {node.permissionCode ? <Text code style={{ fontSize: 11 }}>{node.permissionCode}</Text> : null}
        </Space>
      ),
      children: hasChildren ? node.children.map((child: any) => convertToTreeNode(child)) : undefined,
      checkable: true,
      selectable: false,
    };
  };

  const columns = [
    {
      title: '角色名称', dataIndex: 'name', key: 'name', width: 140,
      render: (name: string, r: RoleItem) => (
        <Space>
          <Text strong>{name}</Text>
          {r.isSystem && <Tag color="blue" style={{ fontSize: 10 }}>系统</Tag>}
        </Space>
      ),
    },
    { title: '编码', dataIndex: 'code', key: 'code', width: 120 },
    {
      title: '描述', dataIndex: 'description', key: 'description', ellipsis: true,
      render: (v: string) => <Tooltip title={v}>{v || '-'}</Tooltip>,
    },
    {
      title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 160,
    },
    {
      title: '操作', key: 'action', width: 320,
      render: (_: any, record: RoleItem) => (
        <Space>
          <Button size="small" icon={<SafetyOutlined />} onClick={() => openPermDrawer(record)}>权限</Button>
          <Button size="small" icon={<UserOutlined />} onClick={() => openUsers(record)}>用户</Button>
          <Button size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)}>编辑</Button>
          {!record.isSystem && (
            <Popconfirm title="确定删除此角色?" onConfirm={() => handleDelete(record.id)}>
              <Button size="small" danger icon={<DeleteOutlined />}>删除</Button>
            </Popconfirm>
          )}
        </Space>
      ),
    },
  ];

  return (
    <Card
      title={<><TeamOutlined /> 角色管理</>}
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} onClick={loadData}>刷新</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>新建角色</Button>
        </Space>
      }
    >
      <Table dataSource={data} columns={columns} rowKey="id" loading={loading} size="small"
        pagination={{ pageSize: 20 }} />

      {/* 创建/编辑角色 Modal */}
      <Modal
        title={editingItem ? '编辑角色' : '新建角色'}
        open={modalVisible}
        onOk={handleSubmit}
        onCancel={() => setModalVisible(false)}
        width={500}
      >
        <Form form={form} layout="vertical">
          <Form.Item name="name" label="角色名称" rules={[{ required: true, message: '请输入角色名称' }]}>
            <Input />
          </Form.Item>
          <Form.Item name="code" label="角色编码" rules={[{ required: true, message: '请输入角色编码' }]}>
            <Input placeholder="如: developer" disabled={!!editingItem} />
          </Form.Item>
          <Form.Item name="description" label="描述">
            <Input.TextArea rows={3} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 菜单权限分配 Drawer */}
      <Drawer
        title={<><SafetyOutlined /> 菜单权限分配 - {selectedRole?.name}</>}
        open={permDrawerVisible}
        onClose={() => setPermDrawerVisible(false)}
        width={480}
        extra={
          <Space>
            <Button type="primary" loading={permSaving} onClick={handleSavePermissions}>保存</Button>
          </Space>
        }
      >
        {menuTree.length > 0 ? (
          <Tree
            checkable
            defaultExpandAll
            checkedKeys={checkedMenuIds}
            onCheck={(checkedKeys) => {
              setCheckedMenuIds(checkedKeys as string[]);
            }}
            treeData={menuTree.map((node: any) => convertToTreeNode(node))}
          />
        ) : (
          <Text type="secondary">暂无菜单数据，请先初始化菜单</Text>
        )}
      </Drawer>

      {/* 查看角色用户 Modal */}
      <Modal
        title={<><UserOutlined /> {usersRole?.name} - 拥有该角色的用户</>}
        open={usersModalVisible}
        onCancel={() => setUsersModalVisible(false)}
        footer={null}
        width={400}
      >
        <List
          loading={usersLoading}
          dataSource={userList}
          locale={{ emptyText: '暂无用户拥有此角色' }}
          renderItem={(uid: string) => (
            <List.Item>
              <Space><UserOutlined /><Text code>{uid}</Text></Space>
            </List.Item>
          )}
        />
      </Modal>
    </Card>
  );
};

export default RoleManagementPage;
