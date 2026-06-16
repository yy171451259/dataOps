import React, { useEffect, useState, useMemo } from 'react';
import { Layout, Menu, theme, ConfigProvider, Avatar, Dropdown, message } from 'antd';
import {
  DatabaseOutlined, FileSearchOutlined, AuditOutlined, CheckSquareOutlined,
  UserOutlined, SettingOutlined, SafetyOutlined, LogoutOutlined,
  StarOutlined, BellOutlined, DashboardOutlined, TableOutlined,
  ThunderboltOutlined, BuildOutlined, ImportOutlined, LineChartOutlined,
  TeamOutlined, CrownOutlined, EyeInvisibleOutlined,
  SendOutlined, FilterOutlined, MenuFoldOutlined, MenuUnfoldOutlined,
} from '@ant-design/icons';
import { Routes, Route, useNavigate, useLocation, Navigate } from 'react-router-dom';
import { useAuthStore } from './store/useAuthStore';
import SqlEditor from './components/SqlEditor';
import TicketList from './pages/TicketList';
import DatabaseList from './pages/DatabaseList';
import AuditList from './pages/AuditList';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';

import MetadataPage from './pages/MetadataPage';
import DataMaskingPage from './pages/DataMaskingPage';
import DataQualityPage from './pages/DataQualityPage';
import UserManagementPage from './pages/UserManagementPage';
import NotificationSettingsPage from './pages/NotificationSettingsPage';
import PipelinePage from './pages/PipelinePage';
// import SchemaDesignerPage from './pages/SchemaDesignerPage'; // TODO: 修复编码后恢复
import SystemSettingsPage from './pages/SystemSettingsPage';
// import DatabaseMonitorPage from './pages/DatabaseMonitorPage'; // TODO: 修复编码后恢复
// import DataImportPage from './pages/DataImportPage'; // TODO: 修复编码后恢复
import ResourceOwnerPage from './pages/ResourceOwnerPage';
import SensitiveDataPage from './pages/SensitiveDataPage';
import PermissionRequestPage from './pages/PermissionRequestPage';
import RowControlPage from './pages/RowControlPage';
import RoleManagementPage from './pages/RoleManagementPage';
import { getIcon } from './utils/iconMap';
import MenuManagementPage from './pages/MenuManagementPage';
import './index.css';

const { Sider, Content } = Layout;

const ProtectedLayout: React.FC = () => {
  const [collapsed, setCollapsed] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const { user, logout, hasPermission, menus } = useAuthStore();
  const { token: { colorBgContainer, borderRadiusLG } } = theme.useToken();

  // ����˲˵������ݽṹת��Ϊ Ant Design Menu items ��ʽ
  const convertToMenuItems = (menuTree: any[]): any[] => {
    if (!menuTree || !Array.isArray(menuTree)) return [];
    return menuTree
      .filter((item: any) => item.visible !== 0)
      .map((item: any) => ({
        key: item.path || item.id,
        icon: getIcon(item.icon),
        label: item.name,
        children: item.children ? convertToMenuItems(item.children) : undefined,
      }));
  };

  const visibleMenuItems = useMemo(() => {
    if (menus && menus.length > 0) {
      return convertToMenuItems(menus);
    }
    // ���ף�����˵����ݻ�δ���أ����ؿ�����
    return [];
  }, [menus]);

  const handleLogout = () => {
    logout();
    message.success('���˳���¼');
    navigate('/login');
  };

  const userMenuItems = [
    { key: 'profile', label: `${user?.nickname || user?.username}`, icon: <UserOutlined />, disabled: true },
    { key: 'divider', type: 'divider' as const },
    { key: 'logout', label: '�˳���¼', icon: <LogoutOutlined />, danger: true },
  ];

  return (
    <Layout style={{ height: '100vh', overflow: 'hidden' }}>
      <Sider collapsed={collapsed} className="custom-sider" style={{ position: 'relative', overflow: 'hidden' }}>
        <div style={{ height: '100vh', display: 'flex', flexDirection: 'column' }}>
          <div style={{
            height: 32, margin: '16px 16px 16px 16px',
            display: 'flex', alignItems: 'center', justifyContent: 'space-between',
            color: 'white', fontWeight: 'bold', fontSize: collapsed ? 12 : 16,
            padding: '0 12px',
          }}>
            <span>{collapsed ? 'DMS' : 'DataOps DMS'}</span>
            <span
              onClick={() => setCollapsed(!collapsed)}
              style={{ cursor: 'pointer', fontSize: 14, flexShrink: 0, display: 'flex', alignItems: 'center' }}
            >
              {collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
            </span>
          </div>
          <div style={{ flex: 1, overflow: 'auto', minHeight: 0 }}>
            <Menu
              theme="dark" selectedKeys={[location.pathname]} mode="inline"
              defaultOpenKeys={useMemo(() => {
                const openKeys: string[] = [];
                const findParentKeys = (tree: any[], targetPath: string): boolean => {
                  for (const item of tree) {
                    if (item.path === targetPath) return true;
                    if (item.children) {
                      for (const child of item.children) {
                        if (child.path === targetPath) {
                          openKeys.push(item.path || item.id);
                          return true;
                        }
                      }
                    }
                  }
                  return false;
                };
                findParentKeys(menus, location.pathname);
                return openKeys;
              }, [menus, location.pathname])}
              items={visibleMenuItems} onClick={({ key }) => navigate(key)}
            />
          </div>
          {/* �û���Ϣ - �̶��ڲ������ײ� */}
          <div style={{ flexShrink: 0, borderTop: '1px solid rgba(255,255,255,0.1)' }}>
            <Dropdown menu={{ items: userMenuItems, onClick: ({ key }) => { if (key === 'logout') handleLogout(); } }} placement="topRight" trigger={['click']}>
              <div style={{
                cursor: 'pointer', padding: '12px 16px',
                display: 'flex', alignItems: 'center', gap: 8,
              }}>
                <Avatar size={collapsed ? 30 : 28} icon={<UserOutlined />} />
                {!collapsed && <span style={{ color: 'rgba(255,255,255,0.75)', fontSize: 13, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{user?.nickname || user?.username || '�û�'}</span>}
              </div>
            </Dropdown>
          </div>
        </div>
      </Sider>
      <Layout>
        <Content style={{ margin: '16px', padding: 24, flex: 1, background: colorBgContainer, borderRadius: borderRadiusLG, overflow: 'hidden' }}>
          <Routes>
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/sql" element={<SqlEditor />} />
            <Route path="/databases" element={<DatabaseList />} />
            {/* <Route path="/schema-designer" element={<SchemaDesignerPage />} /> */}
            {/* <Route path="/import" element={<DataImportPage />} /> */}
            {/* <Route path="/monitor" element={<DatabaseMonitorPage />} /> */}
            <Route path="/pipeline" element={<PipelinePage />} />
            <Route path="/metadata" element={<MetadataPage />} />
            <Route path="/tickets" element={<TicketList />} />

            <Route path="/audit" element={<AuditList />} />
            <Route path="/masking" element={<DataMaskingPage />} />
            <Route path="/quality" element={<DataQualityPage />} />
            <Route path="/resource-owners" element={<ResourceOwnerPage />} />
            <Route path="/sensitive-data" element={<SensitiveDataPage />} />
            <Route path="/permission-requests" element={<PermissionRequestPage />} />
            <Route path="/row-controls" element={<RowControlPage />} />
            <Route path="/users" element={<UserManagementPage />} />
            <Route path="/roles" element={<RoleManagementPage />} />
            <Route path="/notifications" element={<NotificationSettingsPage />} />
            <Route path="/settings" element={<SystemSettingsPage />} />
            <Route path="/menus" element={<MenuManagementPage />} />
            <Route path="/" element={<Navigate to="/dashboard" replace />} />
          </Routes>
        </Content>
      </Layout>
    </Layout>
  );
};

const App: React.FC = () => {
  const [ready, setReady] = useState(false);
  const { isLoggedIn, loadFromStorage } = useAuthStore();

  useEffect(() => {
    loadFromStorage();
    setReady(true);
  }, []);

  if (!ready) return null;

  return (
    <ConfigProvider theme={{
      token: {
        colorPrimary: '#1677ff',
        borderRadius: 6,
      },
    }}>
      <Routes>
        <Route path="/login" element={isLoggedIn ? <Navigate to="/dashboard" replace /> : <LoginPage />} />
        <Route path="/*" element={isLoggedIn ? <ProtectedLayout /> : <Navigate to="/login" replace />} />
      </Routes>
    </ConfigProvider>
  );
};

export default App;
