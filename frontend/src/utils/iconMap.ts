import {
  DashboardOutlined, FileSearchOutlined, DatabaseOutlined, BuildOutlined,
  ImportOutlined, LineChartOutlined, TableOutlined, CheckSquareOutlined,
  AuditOutlined, LockOutlined, SettingOutlined, SendOutlined,
  SafetyOutlined, CrownOutlined, EyeInvisibleOutlined, FilterOutlined,
  UserOutlined, TeamOutlined, BellOutlined, MenuOutlined,
  ThunderboltOutlined, StarOutlined,
} from '@ant-design/icons';
import React from 'react';

const iconMap: Record<string, React.ReactNode> = {
  DashboardOutlined: React.createElement(DashboardOutlined),
  FileSearchOutlined: React.createElement(FileSearchOutlined),
  DatabaseOutlined: React.createElement(DatabaseOutlined),
  BuildOutlined: React.createElement(BuildOutlined),
  ImportOutlined: React.createElement(ImportOutlined),
  LineChartOutlined: React.createElement(LineChartOutlined),
  TableOutlined: React.createElement(TableOutlined),
  CheckSquareOutlined: React.createElement(CheckSquareOutlined),
  AuditOutlined: React.createElement(AuditOutlined),
  LockOutlined: React.createElement(LockOutlined),
  SettingOutlined: React.createElement(SettingOutlined),
  SendOutlined: React.createElement(SendOutlined),
  SafetyOutlined: React.createElement(SafetyOutlined),
  CrownOutlined: React.createElement(CrownOutlined),
  EyeInvisibleOutlined: React.createElement(EyeInvisibleOutlined),
  FilterOutlined: React.createElement(FilterOutlined),
  UserOutlined: React.createElement(UserOutlined),
  TeamOutlined: React.createElement(TeamOutlined),
  BellOutlined: React.createElement(BellOutlined),
  MenuOutlined: React.createElement(MenuOutlined),
  ThunderboltOutlined: React.createElement(ThunderboltOutlined),
  StarOutlined: React.createElement(StarOutlined),
};

export const getIcon = (iconName: string | null | undefined): React.ReactNode => {
  if (!iconName) return React.createElement(SettingOutlined); // 默认图标
  return iconMap[iconName] || React.createElement(SettingOutlined);
};

export default iconMap;
