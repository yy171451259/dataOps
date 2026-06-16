import React, { useState, useEffect } from 'react';
import {
  Card,
  Table,
  Button,
  Modal,
  Form,
  Input,
  Select,
  message,
  Popconfirm,
  Tag,
  Space,
  Statistic,
  Row,
  Col
} from 'antd';
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  CheckCircleOutlined,
  DisconnectOutlined,
  SyncOutlined
} from '@ant-design/icons';
import { instanceApi } from '../utils/api';

const { Option } = Select;

interface DatabaseInstance {
  id: string;
  name: string;
  dbType: string;
  host: string;
  port: number;
  username: string;
  environment: string;
  status: string;
  createTime: string;
}

const DatabaseList: React.FC = () => {
  const [databases, setDatabases] = useState<DatabaseInstance[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [testingId, setTestingId] = useState<string | null>(null);
  const [form] = Form.useForm();

  useEffect(() => {
    fetchDatabases();
  }, []);

  const fetchDatabases = async () => {
    setLoading(true);
    try {
      const res = await instanceApi.list();
      setDatabases(res.data.data || []);
    } catch (error) {
      message.error('OK');
    } finally {
      setLoading(false);
    }
  };

  const handleTestConnection = async (id: string) => {
    setTestingId(id);
    try {
      const res = await instanceApi.test(id);
      if (res.data.data) {
        message.success('OK');
      } else {
        message.error('OK');
      }
    } catch (error) {
      message.error('OK');
    } finally {
      setTestingId(null);
    }
    fetchDatabases();
  };

  const handleSubmit = async (values: any) => {
    try {
      if (editingId) {
        // 编辑
        await instanceApi.update(editingId, values);
        message.success('OK');
      } else {
        // 新增
        await instanceApi.create(values);
        message.success('OK');
      }
      setModalVisible(false);
      form.resetFields();
      fetchDatabases();
    } catch (error) {
      message.error('OK');
    }
  };

  const handleDelete = async (id: string) => {
    try {
      await instanceApi.delete(id);
      message.success('OK');
      fetchDatabases();
    } catch (error) {
      message.error('OK');
    }
  };

  const dbTypeColors: Record<string, string> = {
    mysql: 'blue',
    postgresql: 'cyan',
    oracle: 'orange',
    sqlserver: 'purple'
  };

  const envColors: Record<string, string> = {
    dev: 'default',
    test: 'blue',
    prod: 'red'
  };

  const columns = [
    {
      title: '实例名称',
      dataIndex: 'name',
      key: 'name',
      render: (text: string) => <strong>{text}</strong>
    },
    {
      title: '数据库类垀',
      dataIndex: 'dbType',
      key: 'dbType',
      width: 120,
      render: (type: string) => (
        <Tag color={dbTypeColors[type] || 'default'}>{type.toUpperCase()}</Tag>
      )
    },
    {
      title: '连接地址',
      dataIndex: 'host',
      key: 'host',
      render: (host: string, record: DatabaseInstance) => `${host}:${record.port}`
    },
    {
      title: '用户名',
      dataIndex: 'username',
      key: 'username',
      width: 120
    },
    {
      title: '环境',
      dataIndex: 'environment',
      key: 'environment',
      width: 100,
      render: (env: string) => (
        <Tag color={envColors[env] || 'default'}>
          {env === 'prod' ? '生产' : env === 'test' ? '测试' : '开发'}
        </Tag>
      )
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: string) => (
        <Tag icon={status === 'active' ? <CheckCircleOutlined /> : <DisconnectOutlined />} 
             color={status === 'active' ? 'success' : 'error'}>
          {status === 'active' ? '在线' : '离线'}
        </Tag>
      )
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 180
    },
    {
      title: '操作',
      key: 'actions',
      width: 200,
      render: (_: any, record: DatabaseInstance) => (
        <Space>
          <Button 
            type="link" 
            size="small" 
            icon={<SyncOutlined spin={testingId === record.id} />}
            onClick={() => handleTestConnection(record.id)}
          >
            测试连接
          </Button>
          <Button 
            type="link" 
            size="small" 
            icon={<EditOutlined />}
            onClick={() => {
              setEditingId(record.id);
              form.setFieldsValue(record);
              setModalVisible(true);
            }}
          >
            编辑
          </Button>
          <Popconfirm title="确认删除此数据库实例＀" onConfirm={() => handleDelete(record.id)}>
            <Button type="link" danger size="small" icon={<DeleteOutlined />}>
              删除
            </Button>
          </Popconfirm>
        </Space>
      )
    }
  ];

  return (
    <div>
      <Card 
        title="数据库实例管琀"
        extra={
          <Button 
            type="primary" 
            icon={<PlusOutlined />} 
            onClick={() => {
              setEditingId(null);
              form.resetFields();
              setModalVisible(true);
            }}
          >
            添加实例
          </Button>
        }
      >
        <Row gutter={16} style={{ marginBottom: 16 }}>
          <Col span={6}>
            <Statistic title="实例总数" value={databases.length} />
          </Col>
          <Col span={6}>
            <Statistic 
              title="在线实例" 
              value={databases.filter(d => d.status === 'active').length} 
              valueStyle={{ color: '#3f8600' }}
            />
          </Col>
          <Col span={6}>
            <Statistic title="MySQL实例" value={databases.filter(d => d.dbType === 'mysql').length} />
          </Col>
          <Col span={6}>
            <Statistic title="生产环境" value={databases.filter(d => d.environment === 'prod').length} />
          </Col>
        </Row>

        <Table
          columns={columns}
          dataSource={databases}
          rowKey="id"
          loading={loading}
          pagination={{ pageSize: 10 }}
        />
      </Card>

      <Modal
        title={editingId ? '编辑数据库实例' : '添加数据库实例'}
        open={modalVisible}
        onCancel={() => setModalVisible(false)}
        onOk={() => form.submit()}
        width={600}
      >
        <Form
          form={form}
          layout="vertical"
          onFinish={handleSubmit}
        >
          <Form.Item
            label="实例名称"
            name="name"
            rules={[{ required: true, message: '请输入实例名秀' }]}
          >
            <Input placeholder="例如：用户中忀生产庀" />
          </Form.Item>

          <Form.Item
            label="数据库类垀"
            name="dbType"
            rules={[{ required: true, message: '请选择数据库类垀' }]}
          >
            <Select placeholder="选择数据库类型">
              <Option value="mysql">MySQL</Option>
              <Option value="postgresql">PostgreSQL</Option>
              <Option value="oracle">Oracle</Option>
              <Option value="sqlserver">SQL Server</Option>
            </Select>
          </Form.Item>

          <Form.Item label="环境" name="environment">
            <Select placeholder="选择环境">
              <Option value="dev">开发环境</Option>
              <Option value="test">测试环境</Option>
              <Option value="prod">生产环境</Option>
            </Select>
          </Form.Item>

          <Row gutter={16}>
            <Col span={16}>
              <Form.Item
                label="主机地址"
                name="host"
                rules={[{ required: true, message: '请输入主机地址' }]}
              >
                <Input placeholder="localhost / IP地址" />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item
                label="端口"
                name="port"
                rules={[{ required: true, message: '请输入端叀' }]}
              >
                <Input type="number" placeholder="3306" />
              </Form.Item>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={12}>
              <Form.Item
                label="用户名"
                name="username"
                rules={[{ required: true, message: '请输入用户名' }]}
              >
                <Input placeholder="root" />
              </Form.Item>
            </Col>
            <Col span={12}>
              <Form.Item
                label="密码"
                name="password"
                rules={[{ required: true, message: '请输入密码' }]}
              >
                <Input.Password placeholder="数据库密码" />
              </Form.Item>
            </Col>
          </Row>

          <Form.Item label="默认数据库" name="databaseName">
            <Input placeholder="可选，连接后自动获叀" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default DatabaseList;
