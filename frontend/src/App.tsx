import React, { useEffect, useState, useMemo } from 'react';
import { Layout, Menu, theme, ConfigProvider, Avatar, Dropdown, Badge, message, Tabs, Button } from 'antd';
import {
  DatabaseOutlined, FileSearchOutlined, AuditOutlined, CheckSquareOutlined,
  UserOutlined, SettingOutlined, SafetyOutlined, LogoutOutlined,
  StarOutlined, BellOutlined, DashboardOutlined, TableOutlined,
  ThunderboltOutlined, BuildOutlined, ImportOutlined, LineChartOutlined,
  TeamOutlined, CrownOutlined, EyeInvisibleOutlined,
  SendOutlined, FilterOutlined, MenuFoldOutlined, MenuUnfoldOutlined,
  CloseOutlined,
} from '@ant-design/icons';
import { Routes, Route, useNavigate, useLocation, Navigate } from 'react-router-dom';
import { useAuthStore } from './store/useAuthStore';
import { ticketApi, permissionRequestApi } from './utils/api';
import SqlEditor from './components/SqlEditor';
import TicketList from './pages/TicketList';
import DatabaseList from './pages/DatabaseList';
import AuditList from './pages/AuditList';
import LoginPage from './pages/LoginPage';
import DashboardPage from './pages/DashboardPage';
import DatabaseMonitorPage from './pages/DatabaseMonitorPage';

import MetadataPage from './pages/MetadataPage';
import DataMaskingPage from './pages/DataMaskingPage';
import DataQualityPage from './pages/DataQualityPage';
import UserManagementPage from './pages/UserManagementPage';
import NotificationSettingsPage from './pages/NotificationSettingsPage';
import PipelinePage from './pages/PipelinePage';
import SystemSettingsPage from './pages/SystemSettingsPage';
import ResourceOwnerPage from './pages/ResourceOwnerPage';
import SensitiveDataPage from './pages/SensitiveDataPage';
import PermissionRequestPage from './pages/PermissionRequestPage';
import RowControlPage from './pages/RowControlPage';
import RoleManagementPage from './pages/RoleManagementPage';
import { getIcon } from './utils/iconMap';
import MenuManagementPage from './pages/MenuManagementPage';
import './index.css';

const { Sider, Content } = Layout;

const pageComponents: Record<string, React.FC> = {
  '/dashboard': DashboardPage,
  '/sql': SqlEditor,
  '/databases': DatabaseList,
  '/monitor': DatabaseMonitorPage,
  '/pipeline': PipelinePage,
  '/metadata': MetadataPage,
  '/tickets': TicketList,
  '/audit': AuditList,
  '/masking': DataMaskingPage,
  '/quality': DataQualityPage,
  '/resource-owners': ResourceOwnerPage,
  '/sensitive-data': SensitiveDataPage,
  '/permission-requests': PermissionRequestPage,
  '/row-controls': RowControlPage,
  '/users': UserManagementPage,
  '/roles': RoleManagementPage,
  '/notifications': NotificationSettingsPage,
  '/settings': SystemSettingsPage,
  '/menus': MenuManagementPage,
};

const pageLabels: Record<string, string> = {
  '/dashboard': '数据看板',
  '/sql': 'SQL查询',
  '/databases': '数据源管理',
  '/monitor': '性能监控',
  '/pipeline': '数据管道',
  '/metadata': '元数据管理',
  '/tickets': '数据变更工单',
  '/audit': '审计日志',
  '/masking': '数据脱敏',
  '/quality': '数据质量',
  '/resource-owners': '资源负责人',
  '/sensitive-data': '敏感数据管理',
  '/permission-requests': '权限申请',
  '/row-controls': '行级控制',
  '/users': '用户管理',
  '/roles': '角色管理',
  '/notifications': '通知设置',
  '/settings': '系统设置',
  '/menus': '菜单管理',
};

interface TabItem {
  key: string;
  label: string;
  path: string;
}

const ProtectedLayout: React.FC = () => {
  const [collapsed, setCollapsed] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const { user, logout, hasPermission, menus } = useAuthStore();
  const { token: { colorBgContainer, borderRadiusLG } } = theme.useToken();
  const [pendingCount, setPendingCount] = useState(0);

  const [tabs, setTabs] = useState<TabItem[]>([
    { key: '/dashboard', label: '数据看板', path: '/dashboard' },
  ]);
  const [activeTabKey, setActiveTabKey] = useState('/dashboard');

  useEffect(() => {
    if (!hasPermission('ticket:approve')) return;
    const fetchPendingCount = async () => {
      try {
        const [ticketRes, permRes] = await Promise.all([
          ticketApi.pending().catch(() => ({ data: { data: [] } })),
          permissionRequestApi.pending().catch(() => ({ data: { data: [] } })),
        ]);
        const tData = ticketRes.data?.data;
        const pData = permRes.data?.data;
        const tCount = Array.isArray(tData) ? tData.length : (tData?.list?.length || 0);
        const pCount = Array.isArray(pData) ? pData.length : (pData?.list?.length || 0);
        setPendingCount(tCount + pCount);
      } catch { /* ignore */ }
    };
    fetchPendingCount();
    const timer = setInterval(fetchPendingCount, 30000);
    return () => clearInterval(timer);
  }, [hasPermission]);

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
    return [];
  }, [menus]);

  const handleLogout = () => {
    logout();
    message.success('已退出登录');
    navigate('/login');
  };

  const userMenuItems = [
    { key: 'profile', label: `${user?.nickname || user?.username}`, icon: <UserOutlined />, disabled: true },
    { key: 'divider', type: 'divider' as const },
    { key: 'logout', label: '退出登录', icon: <LogoutOutlined />, danger: true },
  ];

  const handleMenuClick = ({ key }: { key: string }) => {
    if (!pageComponents[key]) {
      navigate(key);
      return;
    }

    const existingTab = tabs.find(tab => tab.key === key);
    if (existingTab) {
      setActiveTabKey(key);
    } else {
      const newTab: TabItem = {
        key,
        label: pageLabels[key] || key,
        path: key,
      };
      setTabs([...tabs, newTab]);
      setActiveTabKey(key);
    }
  };

  const handleTabChange = (key: string) => {
    setActiveTabKey(key);
  };

  const handleTabClose = (key: string) => {
    if (tabs.length === 1) {
      message.info('至少保留一个标签页');
      return;
    }

    const newTabs = tabs.filter(tab => tab.key !== key);
    setTabs(newTabs);

    if (activeTabKey === key) {
      const newActiveKey = newTabs[newTabs.length - 1].key;
      setActiveTabKey(newActiveKey);
    }
  };

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
              theme="dark" selectedKeys={[activeTabKey]} mode="inline"
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
                findParentKeys(menus, activeTabKey);
                return openKeys;
              }, [menus, activeTabKey])}
              items={visibleMenuItems} onClick={handleMenuClick}
            />
          </div>
          <div style={{ flexShrink: 0, borderTop: '1px solid rgba(255,255,255,0.1)' }}>
            <Dropdown menu={{ items: userMenuItems, onClick: ({ key }) => { if (key === 'logout') handleLogout(); } }} placement="topRight" trigger={['click']}>
              <div style={{
                cursor: 'pointer', padding: '12px 16px',
                display: 'flex', alignItems: 'center', justifyContent: 'space-between',
              }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
                  <Avatar size={collapsed ? 30 : 28} icon={<UserOutlined />} />
                  {!collapsed && <span style={{ color: 'rgba(255,255,255,0.75)', fontSize: 13, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{user?.nickname || user?.username || '用户'}</span>}
                </div>
                {!collapsed && pendingCount > 0 && hasPermission('ticket:approve') && (
                  <div onClick={(e) => { e.stopPropagation(); handleMenuClick({ key: '/dashboard' }); }} style={{ cursor: 'pointer', padding: '4px' }}>
                    <Badge count={pendingCount} size="small" offset={[4, 0]}>
                      <BellOutlined style={{ color: 'rgba(255,255,255,0.65)', fontSize: 18 }} />
                    </Badge>
                  </div>
                )}
              </div>
            </Dropdown>
          </div>
        </div>
      </Sider>
      <Layout>
        <Content style={{ margin: '16px', padding: 0, flex: 1, background: colorBgContainer, borderRadius: borderRadiusLG, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
          <Tabs
            activeKey={activeTabKey}
            onChange={handleTabChange}
            type="card"
            items={tabs.map(tab => ({
              key: tab.key,
              label: (
                <span>
                  {tab.label}
                  {tab.key !== '/dashboard' && (
                    <Button
                      type="text"
                      icon={<CloseOutlined />}
                      size="small"
                      onClick={(e) => {
                        e.stopPropagation();
                        handleTabClose(tab.key);
                      }}
                      style={{ marginLeft: 8, padding: 0 }}
                    />
                  )}
                </span>
              ),
            }))}
            style={{ borderBottom: '1px solid #f0f0f0' }}
          />
          <div style={{ flex: 1, overflow: 'auto', padding: '16px 24px' }}>
            {tabs.map(tab => {
              const Component = pageComponents[tab.key];
              if (!Component) return null;
              return (
                <div
                  key={tab.key}
                  style={{
                    display: activeTabKey === tab.key ? 'block' : 'none',
                    height: '100%',
                    overflow: 'auto',
                  }}
                >
                  <Component />
                </div>
              );
            })}
          </div>
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