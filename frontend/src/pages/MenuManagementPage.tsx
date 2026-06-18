import React, { useEffect, useState, useCallback } from 'react';
import {
  Table, Button, Modal, Form, Input, Select, InputNumber, Switch,
  Popconfirm, message, Card, Space, Tag,
} from 'antd';
import {
  PlusOutlined, EditOutlined, DeleteOutlined, ReloadOutlined,
  MenuOutlined, FolderOutlined,
} from '@ant-design/icons';
import { menuApi } from '../utils/api';
import { getIcon } from '../utils/iconMap';

const { Option } = Select;

interface MenuNode {
  id: string;
  parentId: string | null;
  name: string;
  type: string;
  path?: string;
  icon?: string;
  permissionCode?: string;
  sortOrder: number;
  visible: number;
  status: string;
  children?: MenuNode[];
}

const iconOptions = [
  'DashboardOutlined', 'FileSearchOutlined', 'DatabaseOutlined',
  'BuildOutlined', 'ImportOutlined', 'LineChartOutlined',
  'TableOutlined', 'CheckSquareOutlined', 'AuditOutlined',
  'LockOutlined', 'SettingOutlined', 'SendOutlined',
  'SafetyOutlined', 'CrownOutlined', 'EyeInvisibleOutlined',
  'FilterOutlined', 'UserOutlined', 'TeamOutlined',
  'BellOutlined', 'MenuOutlined',
];

const MenuManagementPage: React.FC = () => {
  const [data, setData] = useState<MenuNode[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingItem, setEditingItem] = useState<MenuNode | null>(null);
  const [parentOptions, setParentOptions] = useState<MenuNode[]>([]);
  const [form] = Form.useForm();

  const loadData = async () => {
    setLoading(true);
    try {
      const res = await menuApi.getTree();
      const treeData = res.data.data || [];
      setData(treeData);
      // 扁平化菜单作为父级选项（只列出 menu 类型）
      const allFlat: MenuNode[] = [];
      const walk = (items: MenuNode[]) => {
        items.forEach(item => {
          if (item.type === 'menu') {
            allFlat.push(item);
          }
          if (item.children) walk(item.children);
        });
      };
      walk(treeData);
      setParentOptions(allFlat);
    } catch (e: any) {
      message.error('加载菜单失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadData(); }, []);

  const handleCreate = (parentId?: string | null) => {
    setEditingItem(null);
    form.resetFields();
    if (parentId) {
      form.setFieldsValue({ parentId, type: 'button' });
    } else {
      form.setFieldsValue({ type: 'menu', sortOrder: 0 });
    }
    setModalVisible(true);
  };

  const handleEdit = (record: MenuNode) => {
    setEditingItem(record);
    form.setFieldsValue({
      ...record,
      parentId: record.parentId || null,
    });
    setModalVisible(true);
  };

  const handleDelete = async (id: string) => {
    try {
      await menuApi.delete(id);
      message.success('删除成功');
      loadData();
    } catch (e: any) {
      message.error(e.response?.data?.message || '删除失败');
    }
  };

  const handleSubmit = async () => {
    const values = await form.validateFields();
    try {
      if (editingItem) {
        await menuApi.update(editingItem.id, values);
        message.success('更新成功');
      } else {
        await menuApi.create(values);
        message.success('创建成功');
      }
      setModalVisible(false);
      loadData();
    } catch (e: any) {
      message.error(e.response?.data?.message || '保存失败');
    }
  };

  const columns = [
    {
      title: '菜单名称',
      dataIndex: 'name',
      key: 'name',
      width: 200,
      render: (name: string, record: MenuNode) => (
        <Space>
          {record.type === 'menu' ? 
            (record.icon ? getIcon(record.icon) : <FolderOutlined />) : 
            <Tag color="blue" style={{ fontSize: 10 }}>按钮</Tag>
          }
          <span style={{ fontWeight: record.type === 'menu' ? 'bold' : 'normal' }}>
            {name}
          </span>
        </Space>
      ),
    },
    {
      title: '类型',
      dataIndex: 'type',
      key: 'type',
      width: 80,
      render: (type: string) => (
        <Tag color={type === 'menu' ? 'green' : 'blue'}>{type === 'menu' ? '菜单' : '按钮'}</Tag>
      ),
    },
    {
      title: '路由路径',
      dataIndex: 'path',
      key: 'path',
      width: 160,
      render: (v: string | undefined) => v ? <Tag>{v}</Tag> : '-',
    },
    {
      title: '权限码',
      dataIndex: 'permissionCode',
      key: 'permissionCode',
      width: 140,
      render: (v: string | undefined) => v ? <Tag color="orange">{v}</Tag> : '-',
    },
    {
      title: '排序',
      dataIndex: 'sortOrder',
      key: 'sortOrder',
      width: 70,
    },
    {
      title: '可见',
      dataIndex: 'visible',
      key: 'visible',
      width: 70,
      render: (v: number) => <Switch checked={v === 1} disabled size="small" />,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 80,
      render: (v: string) => (
        <Tag color={v === 'active' ? 'green' : 'red'}>{v === 'active' ? '启用' : '禁用'}</Tag>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 220,
      render: (_: any, record: MenuNode) => (
        <Space>
          {record.type === 'menu' && (
            <Button size="small" icon={<PlusOutlined />} onClick={() => handleCreate(record.id)}>
              子项
            </Button>
          )}
          <Button size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)}>编辑</Button>
          <Popconfirm title="确定删除?" onConfirm={() => handleDelete(record.id)}>
            <Button size="small" danger icon={<DeleteOutlined />}>删除</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ];

  return (
    <Card
      title={<><MenuOutlined /> 菜单管理</>}
      extra={
        <Space>
          <Button icon={<ReloadOutlined />} onClick={loadData}>刷新</Button>
          <Button type="primary" icon={<PlusOutlined />} onClick={() => handleCreate(null)}>
            新建菜单
          </Button>
        </Space>
      }
    >
      <Table
        dataSource={data}
        columns={columns}
        rowKey="id"
        loading={loading}
        size="small"
        pagination={false}
        defaultExpandAllRows
        expandable={{
          childrenColumnName: 'children',
        }}
      />

      <Modal
        title={editingItem ? '编辑菜单/按钮' : '新建菜单/按钮'}
        open={modalVisible}
        onOk={handleSubmit}
        onCancel={() => setModalVisible(false)}
        width={560}
        destroyOnClose
      >
        <Form form={form} layout="vertical">
          <Form.Item name="parentId" label="父级菜单">
            <Select allowClear placeholder="留空表示顶级菜单">
              {parentOptions
                .filter(p => p.id !== editingItem?.id)
                .map(p => (
                  <Option key={p.id} value={p.id}>
                    {p.icon ? getIcon(p.icon) : null} {p.name}
                  </Option>
                ))}
            </Select>
          </Form.Item>
          <Form.Item name="name" label="名称" rules={[{ required: true, message: '请输入名称' }]}>
            <Input placeholder="菜单/按钮名称" />
          </Form.Item>
          <Form.Item name="type" label="类型" rules={[{ required: true }]}>
            <Select>
              <Option value="menu">菜单</Option>
              <Option value="button">按钮/操作</Option>
            </Select>
          </Form.Item>
          <Form.Item name="path" label="路由路径">
            <Input placeholder="如 /tickets（按钮类型可不填）" />
          </Form.Item>
          <Form.Item name="icon" label="图标">
            <Select allowClear placeholder="选择图标">
              {iconOptions.map(icon => (
                <Option key={icon} value={icon}>
                  <Space>{getIcon(icon)} {icon}</Space>
                </Option>
              ))}
            </Select>
          </Form.Item>
          <Form.Item name="permissionCode" label="权限码">
            <Input placeholder="如 menu:manage" />
          </Form.Item>
          <Form.Item name="sortOrder" label="排序号">
            <InputNumber min={0} max={999} style={{ width: '100%' }} />
          </Form.Item>
          <Form.Item name="visible" label="是否可见" valuePropName="checked">
            <Switch checkedChildren="可见" unCheckedChildren="隐藏" />
          </Form.Item>
          <Form.Item name="status" label="状态">
            <Select>
              <Option value="active">启用</Option>
              <Option value="inactive">禁用</Option>
            </Select>
          </Form.Item>
        </Form>
      </Modal>
    </Card>
  );
};

export default MenuManagementPage;
