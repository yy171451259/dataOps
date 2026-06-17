import React, { useState, useEffect } from 'react';
import { Form, Input, Button, Card, message, Typography, Divider } from 'antd';
import { UserOutlined, LockOutlined, DatabaseOutlined, WechatOutlined } from '@ant-design/icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { authApi } from '../utils/api';
import { useAuthStore } from '../store/useAuthStore';

const { Title, Text } = Typography;

const LoginPage: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [dingTalkLoading, setDingTalkLoading] = useState(false);
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const setAuth = useAuthStore((s) => s.setAuth);

  // 处理钉钉登录回调
  useEffect(() => {
    const authCode = searchParams.get('authCode');
    if (authCode) {
      handleDingTalkCallback(authCode);
    }
  }, [searchParams]);

  const handleDingTalkCallback = async (authCode: string) => {
    setDingTalkLoading(true);
    try {
      const res = await authApi.dingTalkLogin(authCode);
      const data = res.data.data;
      setAuth(
        data.token,
        {
          userId: data.userId,
          username: data.username,
          nickname: data.nickname,
          isAdmin: data.isAdmin,
        },
        data.permissions || [],
        data.menus || []
      );
      message.success('钉钉登录成功');
      navigate('/sql');
    } catch (err: any) {
      message.error(err.response?.data?.message || '钉钉登录失败');
    } finally {
      setDingTalkLoading(false);
    }
  };

  const handleDingTalkLogin = async () => {
    setDingTalkLoading(true);
    try {
      const res = await authApi.getDingTalkAuthUrl();
      const authUrl = res.data.data;
      // 跳转到钉钉授权页面
      window.location.href = authUrl;
    } catch (err: any) {
      message.error(err.response?.data?.message || '获取钉钉授权链接失败');
      setDingTalkLoading(false);
    }
  };

  const onFinish = async (values: { username: string; password: string }) => {
    setLoading(true);
    try {
      const res = await authApi.login(values.username, values.password);
      const data = res.data.data;
      setAuth(
        data.token,
        {
          userId: data.userId,
          username: data.username,
          nickname: data.nickname,
          isAdmin: data.isAdmin,
        },
        data.permissions || [],
        data.menus || []
      );
      message.success('OK');
      navigate('/sql');
    } catch (err: any) {
      message.error(err.response?.data?.message || '登录失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{
      minHeight: '100vh',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
    }}>
      <Card style={{ width: 420, borderRadius: 8, boxShadow: '0 8px 24px rgba(0,0,0,0.15)' }}>
        <div style={{ textAlign: 'center', marginBottom: 32 }}>
          <DatabaseOutlined style={{ fontSize: 48, color: '#667eea' }} />
          <Title level={3} style={{ marginTop: 12, marginBottom: 4 }}>DataOps DMS</Title>
          <Text type="secondary">一站式数据管理平台</Text>
        </div>
        <Form
          name="login"
          onFinish={onFinish}
          size="large"
          initialValues={{ username: 'admin' }}
        >
          <Form.Item name="username" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input prefix={<UserOutlined />} placeholder="用户名" />
          </Form.Item>
          <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password prefix={<LockOutlined />} placeholder="密码" />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={loading} block>
              登录
            </Button>
          </Form.Item>
        </Form>

        <Divider plain>或使用钉钉登录</Divider>

        <Button
          icon={<WechatOutlined />}
          loading={dingTalkLoading}
          onClick={handleDingTalkLogin}
          block
          style={{ marginBottom: 16 }}
        >
          钉钉扫码登录
        </Button>

      </Card>
    </div>
  );
};

export default LoginPage;
