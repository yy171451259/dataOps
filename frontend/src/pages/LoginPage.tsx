import React, { useState, useEffect, useRef } from 'react';
import { Form, Input, Button, Spin, message, Typography } from 'antd';
import { UserOutlined, LockOutlined, DatabaseOutlined, ReloadOutlined } from '@ant-design/icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { authApi } from '../utils/api';
import { useAuthStore } from '../store/useAuthStore';

const { Title, Text } = Typography;

const LoginPage: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [dingTalkLoading, setDingTalkLoading] = useState(false);
  const [qrCodeUrl, setQrCodeUrl] = useState<string>('');
  const [qrStatus, setQrStatus] = useState<string>('loading'); // loading | pending | scanned | expired | error
  const [qrErrorMsg, setQrErrorMsg] = useState<string>('');
  const sessionIdRef = useRef<string>('');
  const pollTimerRef = useRef<number | null>(null);
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const setAuth = useAuthStore((s) => s.setAuth);

  // 处理钉钉OAuth回调（兼容旧的redirect登录方式）
  useEffect(() => {
    const authCode = searchParams.get('authCode') || searchParams.get('code');
    if (authCode) {
      handleDingTalkCallback(authCode);
    }
  }, [searchParams]);

  // 加载钉钉扫码登录二维码 + 启动轮询
  useEffect(() => {
    loadDingTalkQrCode();
    return () => {
      // 组件卸载时清除轮询
      if (pollTimerRef.current) {
        clearInterval(pollTimerRef.current);
      }
    };
  }, []);

  const loadDingTalkQrCode = async () => {
    try {
      const res = await authApi.getDingTalkQrCode();
      const data = res?.data?.data;
      if (!data || !data.scanUrl || !data.sessionId) {
        console.error('扫码登录接口返回数据异常:', res);
        setQrErrorMsg('服务响应异常，请稍后重试');
        setQrStatus('error');
        return;
      }
      const scanUrl: string = data.scanUrl;
      const sid: string = data.sessionId;

      sessionIdRef.current = sid;
      const qrApiUrl = `https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=${encodeURIComponent(scanUrl)}`;
      setQrCodeUrl(qrApiUrl);
      setQrErrorMsg('');
      setQrStatus('pending');

      // 启动轮询
      startPolling(sid);
    } catch (err: any) {
      console.error('获取扫码二维码失败:', err);
      const status = err?.response?.status;
      if (status === 404) {
        setQrErrorMsg('扫码服务未启动，请联系管理员');
      } else if (status === 500) {
        setQrErrorMsg('服务器内部错误，请稍后重试');
      } else {
        setQrErrorMsg(err?.message || '网络异常，请检查连接后刷新');
      }
      setQrStatus('error');
    }
  };

  const startPolling = (sid: string) => {
    // 清除旧的轮询
    if (pollTimerRef.current) {
      clearInterval(pollTimerRef.current);
    }

    pollTimerRef.current = window.setInterval(async () => {
      try {
        const res = await authApi.getDingTalkQrStatus(sid);
        const statusData = res.data.data;
        const status = statusData.status;

        if (status === 'SUCCESS') {
          // 登录成功，停止轮询
          if (pollTimerRef.current) {
            clearInterval(pollTimerRef.current);
            pollTimerRef.current = null;
          }
          setQrStatus('success');

          const loginResult = statusData.loginResult;
          setAuth(
            loginResult.token,
            {
              userId: loginResult.userId,
              username: loginResult.username,
              nickname: loginResult.nickname,
              isAdmin: loginResult.isAdmin,
            },
            loginResult.permissions || [],
            loginResult.menus || []
          );
          message.success('钉钉扫码登录成功');
          navigate('/sql');
        } else if (status === 'SCANNED') {
          setQrStatus('scanned');
        } else if (status === 'EXPIRED') {
          setQrStatus('expired');
          if (pollTimerRef.current) {
            clearInterval(pollTimerRef.current);
            pollTimerRef.current = null;
          }
        }
        // PENDING : do nothing, keep polling
      } catch {
        // 轮询出错，继续重试
      }
    }, 2000);
  };

  const handleRefreshQrCode = () => {
    setQrCodeUrl('');
    setQrErrorMsg('');
    setQrStatus('loading');
    if (pollTimerRef.current) {
      clearInterval(pollTimerRef.current);
      pollTimerRef.current = null;
    }
    loadDingTalkQrCode();
  };

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
      message.success('登录成功');
      navigate('/sql');
    } catch (err: any) {
      message.error(err.response?.data?.message || '登录失败');
    } finally {
      setLoading(false);
    }
  };

  const renderQrContent = () => {
    switch (qrStatus) {
      case 'loading':
        return (
          <div style={{ textAlign: 'center', color: '#bbb' }}>
            <Spin size="large" />
            <div style={{ fontSize: 13, marginTop: 12 }}>加载二维码中...</div>
          </div>
        );
      case 'pending':
        return <img src={qrCodeUrl} alt="钉钉扫码登录" style={{ width: 180, height: 180 }} />;
      case 'scanned':
        return (
          <div style={{ textAlign: 'center', position: 'relative' }}>
            <img src={qrCodeUrl} alt="钉钉扫码登录" style={{ width: 180, height: 180, opacity: 0.5 }} />
            <div style={{
              position: 'absolute', top: 0, left: 0, right: 0, bottom: 0,
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              background: 'rgba(255,255,255,0.85)',
            }}>
              <div>
                <Spin size="default" />
                <div style={{ fontSize: 14, color: '#1677ff', marginTop: 8, fontWeight: 500 }}>已扫码</div>
                <div style={{ fontSize: 12, color: '#999' }}>请在手机上确认登录</div>
              </div>
            </div>
          </div>
        );
      case 'expired':
        return (
          <div
            style={{ textAlign: 'center', color: '#bbb', cursor: 'pointer' }}
            onClick={handleRefreshQrCode}
          >
            <ReloadOutlined style={{ fontSize: 48, marginBottom: 8 }} />
            <div style={{ fontSize: 13 }}>二维码已过期</div>
            <div style={{ fontSize: 12, color: '#1677ff' }}>点击刷新</div>
          </div>
        );
      case 'error':
        return (
          <div
            style={{ textAlign: 'center', color: '#bbb', cursor: 'pointer' }}
            onClick={handleRefreshQrCode}
          >
            <ReloadOutlined style={{ fontSize: 48, marginBottom: 8 }} />
            <div style={{ fontSize: 13, color: '#ff4d4f' }}>获取失败</div>
            {qrErrorMsg && <div style={{ fontSize: 12, color: '#999', marginTop: 4 }}>{qrErrorMsg}</div>}
            <div style={{ fontSize: 12, color: '#1677ff', marginTop: 4 }}>点击重试</div>
          </div>
        );
      case 'success':
        return (
          <div style={{ textAlign: 'center' }}>
            <Spin size="default" />
            <div style={{ fontSize: 14, color: '#52c41a', marginTop: 8 }}>登录成功，正在跳转...</div>
          </div>
        );
      default:
        return null;
    }
  };

  return (
    <div style={{
      minHeight: '100vh',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
      padding: 24,
    }}>
      <div style={{
        width: 820,
        background: '#fff',
        borderRadius: 12,
        boxShadow: '0 12px 40px rgba(0,0,0,0.18)',
        display: 'flex',
        overflow: 'hidden',
      }}>
        {/* 左侧 - 钉钉扫码登录 */}
        <div style={{
          width: 380,
          padding: '48px 40px',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          background: '#fafbfc',
          borderRight: '1px solid #f0f0f0',
        }}>
          <DatabaseOutlined style={{ fontSize: 36, color: '#667eea', marginBottom: 16 }} />
          <Title level={3} style={{ marginBottom: 4 }}>DataOps DMS</Title>
          <Text type="secondary" style={{ marginBottom: 32 }}>一站式数据管理平台</Text>

          <div style={{
            width: 200,
            height: 200,
            border: '1px solid #e8e8e8',
            borderRadius: 8,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            background: '#fff',
            marginBottom: 16,
          }}>
            {renderQrContent()}
          </div>

          {qrStatus === 'pending' && (
            <>
              <Text type="secondary" style={{ fontSize: 14, marginBottom: 4 }}>
                使用 <Text strong style={{ color: '#1677ff' }}>钉钉</Text> 扫码登录
              </Text>
              <Text type="secondary" style={{ fontSize: 12 }}>
                打开钉钉APP扫描二维码
              </Text>
            </>
          )}
          {qrStatus === 'scanned' && (
            <Text style={{ fontSize: 14, color: '#1677ff' }}>
              请在手机上确认登录
            </Text>
          )}
        </div>

        {/* 右侧 - 账号密码登录 */}
        <div style={{
          flex: 1,
          padding: '48px 40px',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center',
        }}>
          <div style={{ marginBottom: 32 }}>
            <Title level={4} style={{ marginBottom: 4 }}>账号登录</Title>
            <Text type="secondary">使用用户名和密码登录系统</Text>
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
            <Form.Item style={{ marginBottom: 0 }}>
              <Button type="primary" htmlType="submit" loading={loading} block>
                登录
              </Button>
            </Form.Item>
          </Form>
        </div>
      </div>
    </div>
  );
};

export default LoginPage;
