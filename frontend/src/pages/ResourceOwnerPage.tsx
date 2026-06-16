import React, { useState, useEffect } from 'react';
import {
  Tree, Table, Button, Modal, Select, message, Card, Space, Popconfirm,
  Tag, Empty, Spin, Row, Col, Descriptions, Typography, Switch, Alert,
} from 'antd';
import {
  LockOutlined, UnlockOutlined, UserAddOutlined, UserDeleteOutlined,
  CrownOutlined, FolderOutlined, DatabaseOutlined, TableOutlined,
  ReloadOutlined, SafetyOutlined,
} from '@ant-design/icons';
import { ownerApi, instanceApi, userApi, accessControlApi } from '../utils/api';
import dayjs from 'dayjs';

const { Text } = Typography;

interface OwnerVO {
  id: string;
  ownerUserId: string;
  ownerUsername: string;
  resourceName?: string;
  resourceType: string;
  resourceId: string;
  createdAt: string;
  createdBy?: string;
}

interface AccessControlVO {
  id: string;
  resourceType: string;
  resourceId: string;
  resourceName: string;
  enabled: boolean;
}

interface TreeNodeData {
  title: string;
  key: string;
  icon: React.ReactNode;
  children?: TreeNodeData[];
  isLeaf?: boolean;
  data: { type: string; id: string; name?: string; parentId?: string; databaseName?: string };
}

const typeLabels: Record<string, string> = { instance: '实例', database: 'Schema', table: '数据表' };

const ResourceOwnerPage: React.FC = () => {
  // Tree
  const [treeData, setTreeData] = useState<TreeNodeData[]>([]);
  const [treeLoading, setTreeLoading] = useState(false);
  const [selectedNode, setSelectedNode] = useState<TreeNodeData | null>(null);

  // Access control
  const [accessRules, setAccessRules] = useState<AccessControlVO[]>([]);
  const [currentRule, setCurrentRule] = useState<AccessControlVO | null>(null);
  const [isRestricted, setIsRestricted] = useState(false);

  // Owners
  const [owners, setOwners] = useState<OwnerVO[]>([]);
  const [ownersLoading, setOwnersLoading] = useState(false);

  // Add user
  const [addVisible, setAddVisible] = useState(false);
  const [users, setUsers] = useState<any[]>([]);
  const [selectedUser, setSelectedUser] = useState<string>('');
  const [userSearchLoading, setUserSearchLoading] = useState(false);

  useEffect(() => { loadTree(); loadAccessRules(); }, []);

  // 检查父级Instance是否已开启访问控制（层级继承约束）
  const isParentInstanceRestricted = (): boolean => {
    if (!selectedNode || selectedNode.data.type !== 'database') return false;
    const parentInstanceId = selectedNode.data.parentId;
    if (!parentInstanceId) return false;
    return accessRules.some(r => r.resourceType === 'instance' && r.resourceId === parentInstanceId && r.enabled);
  };

  // When selection changes, update right panel
  useEffect(() => {
    if (selectedNode) {
      const rule = accessRules.find(r => r.resourceType === selectedNode.data.type && r.resourceId === selectedNode.data.id && r.enabled);
      // 如果父级Instance受限，Schema自动视为受限
      const effectiveRestricted = !!rule || isParentInstanceRestricted();
      setIsRestricted(effectiveRestricted);
      setCurrentRule(rule || null);
      loadOwners(selectedNode.data.type, selectedNode.data.id);
    }
  }, [selectedNode, accessRules]);

  const loadTree = async () => {
    setTreeLoading(true);
    try {
      const res = await instanceApi.list();
      const instances = Array.isArray(res?.data?.data) ? res.data.data : [];
      setTreeData(instances.map((inst: any) => ({
        title: `${inst.name || inst.id} (${inst.type || 'mysql'})`,
        key: `instance:${inst.id}`,
        icon: <FolderOutlined style={{ color: '#722ed1' }} />,
        isLeaf: false,
        data: { type: 'instance', id: inst.id, name: inst.name || inst.id },
      })));
    } catch { setTreeData([]); } finally { setTreeLoading(false); }
  };

  const loadAccessRules = async () => {
    try {
      const res = await accessControlApi.list();
      setAccessRules(Array.isArray(res?.data?.data) ? res.data.data : []);
    } catch { setAccessRules([]); }
  };

  const onLoadData = async (node: any): Promise<void> => {
    const { data: nodeData, key } = node;
    if (node.children && node.children.length > 0) return;
    if (nodeData.type === 'instance') {
      try {
        const res = await instanceApi.getSchemas(nodeData.id);
        const dbs = Array.isArray(res?.data?.data) ? res.data.data : [];
        const children = dbs.map((db: string) => ({
          title: db, key: `database:${nodeData.id}:${db}`,
          icon: <DatabaseOutlined style={{ color: '#1677ff' }} />, isLeaf: false,
          data: { type: 'database', id: db, parentId: nodeData.id, databaseName: db },
        }));
        setTreeData(prev => updateTreeChildren(prev, key, children));
      } catch { message.error('加载数据库列表失败'); }
    } else if (nodeData.type === 'database') {
      try {
        const res = await instanceApi.getTableNames(nodeData.parentId!, nodeData.databaseName);
        const tables = Array.isArray(res?.data?.data) ? res.data.data : [];
        const children = tables.map((tbl: string) => ({
          title: tbl, key: `table:${nodeData.parentId}:${nodeData.databaseName}:${tbl}`,
          icon: <TableOutlined style={{ color: '#52c41a' }} />, isLeaf: true,
          data: { type: 'table', id: tbl, parentId: nodeData.parentId, databaseName: nodeData.databaseName },
        }));
        setTreeData(prev => updateTreeChildren(prev, key, children));
      } catch { message.error('加载表列表失败'); }
    }
  };

  const updateTreeChildren = (list: TreeNodeData[], key: string, children: TreeNodeData[]): TreeNodeData[] =>
    list.map(item => {
      if (item.key === key) return { ...item, children };
      if (item.children) return { ...item, children: updateTreeChildren(item.children, key, children) };
      return item;
    });

  const handleNodeSelect = (_keys: React.Key[], info: any) => {
    if (info?.node?.data) setSelectedNode(info.node);
  };

  const loadOwners = async (resourceType: string, resourceId: string) => {
    setOwnersLoading(true);
    try {
      const res = await ownerApi.listByResource(resourceType, resourceId);
      setOwners(Array.isArray(res?.data?.data) ? res.data.data : []);
    } catch { setOwners([]); } finally { setOwnersLoading(false); }
  };

  // Toggle access control
  const handleToggleAccess = async (checked: boolean) => {
    if (!selectedNode) return;
    const { type, id } = selectedNode.data;
    try {
      if (checked) {
        await accessControlApi.enable({ resourceType: type, resourceId: id, resourceName: id });
        message.success('已开启访问控制');
      } else {
        // Schema 层级：关闭时需传入 parentResourceId 供后端校验
        if (currentRule) {
          const parentResourceId = type === 'database' ? selectedNode.data.parentId : undefined;
          await accessControlApi.disable(currentRule.id, { parentResourceId });
        }
        message.success('已关闭访问控制（公开访问）');
      }
      await loadAccessRules();
    } catch (e: any) { message.error(e?.response?.data?.message || '操作失败'); }
  };

  const searchUsers = async (keyword: string) => {
    if (!keyword || keyword.length < 2) { setUsers([]); return; }
    setUserSearchLoading(true);
    try {
      const res = await userApi.list({ keyword, size: 20 });
      setUsers(Array.isArray(res?.data?.data?.records) ? res.data.data.records : []);
    } catch { setUsers([]); } finally { setUserSearchLoading(false); }
  };

  // 打开添加人员弹窗时预加载用户列表
  const openAddUserModal = async () => {
    setAddVisible(true);
    setSelectedUser('');
    setUserSearchLoading(true);
    try {
      const res = await userApi.list({ size: 100 });
      setUsers(Array.isArray(res?.data?.data?.records) ? res.data.data.records : []);
    } catch { setUsers([]); } finally { setUserSearchLoading(false); }
  };

  // 生成可读的资源名称
  const getResourceDisplayName = (node: TreeNodeData): string => {
    const { type, id, name, parentId, databaseName } = node.data;
    if (type === 'instance') return name || id;
    if (type === 'database') {
      const parentInst = treeData.find(n => n.key === `instance:${parentId}`);
      const instName = parentInst?.data?.name || parentId;
      return `${instName} / ${databaseName || id}`;
    }
    if (type === 'table') {
      const parentInst = treeData.find(n => n.key === `instance:${parentId}`);
      const instName = parentInst?.data?.name || parentId;
      return `${instName} / ${databaseName} / ${id}`;
    }
    return id;
  };

  const handleAddOwner = async () => {
    if (!selectedUser || !selectedNode) { message.warning('请选择用户'); return; }
    // 从用户列表中取出选中用户的昵称
    const selectedUserObj = users.find((u: any) => (u.userId || u.id) === selectedUser);
    try {
      await ownerApi.assign({
        ownerUserId: selectedUser,
        ownerUsername: selectedUserObj?.nickname || selectedUserObj?.username || selectedUser,
        resourceType: selectedNode.data.type,
        resourceId: selectedNode.data.id, resourceName: getResourceDisplayName(selectedNode),
      });
      message.success('已添加');
      setAddVisible(false); setSelectedUser('');
      loadOwners(selectedNode.data.type, selectedNode.data.id);
    } catch (e: any) { message.error(e?.response?.data?.message || '添加失败'); }
  };

  const handleRemoveOwner = async (ownerId: string) => {
    try {
      await ownerApi.revoke(ownerId);
      message.success('已移除');
      if (selectedNode) loadOwners(selectedNode.data.type, selectedNode.data.id);
    } catch (e: any) { message.error(e?.response?.data?.message || '移除失败'); }
  };

  const ownerColumns = [
    {
      title: '用户', dataIndex: 'ownerUsername', width: 180,
      render: (n: string, r: OwnerVO) => (
        <Space><CrownOutlined style={{ color: '#faad14' }} /><span>{n || r.ownerUserId}</span></Space>
      ),
    },
    {
      title: '添加时间', dataIndex: 'createdAt', width: 180,
      render: (t: string) => t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : '-',
    },
    {
      title: '操作', width: 80,
      render: (_: any, record: OwnerVO) => (
        <Popconfirm title="确定移除？" onConfirm={() => handleRemoveOwner(record.id)}>
          <Button type="link" danger size="small" icon={<UserDeleteOutlined />}>移除</Button>
        </Popconfirm>
      ),
    },
  ];

  return (
    <div>
      <h2 style={{ marginBottom: 16 }}><SafetyOutlined /> 资源权限管理</h2>
      <Row gutter={16}>
        {/* Left: Resource Tree */}
        <Col xs={24} md={8}>
          <Card title={<span><FolderOutlined /> 资源列表</span>}
            extra={<Button type="text" size="small" icon={<ReloadOutlined />} onClick={loadTree} loading={treeLoading} />}
            style={{ height: 'calc(100vh - 160px)', overflow: 'auto' }}>
            {treeLoading ? <div style={{ textAlign: 'center', padding: 40 }}><Spin /></div> :
              treeData.length === 0 ? <Empty description="暂无资源" /> :
                <Tree showIcon loadData={onLoadData} treeData={treeData} onSelect={handleNodeSelect} blockNode />}
          </Card>
        </Col>

        {/* Right: Permission Panel */}
        <Col xs={24} md={16}>
          <Card title={selectedNode ? (
            <Space>
              <Tag color="purple">{typeLabels[selectedNode.data.type] || selectedNode.data.type}</Tag>
              <Text strong>{getResourceDisplayName(selectedNode)}</Text>
            </Space>
          ) : <Text type="secondary">请在左侧选择资源</Text>}
            style={{ height: 'calc(100vh - 160px)', overflow: 'auto' }}>
            {!selectedNode ? (
              <div style={{ textAlign: 'center', padding: 60 }}><Empty description="点击左侧资源管理其权限" /></div>
            ) : (
              <>
                {/* Access Level Toggle */}
                <Card size="small" style={{ marginBottom: 16, background: isRestricted ? '#fff7e6' : '#f6ffed' }}>
                  <Row align="middle" justify="space-between">
                    <Col>
                      <Space size="large">
                        <span style={{ fontWeight: 500, fontSize: 14 }}>访问级别</span>
                        <Switch
                          checked={isRestricted}
                          onChange={handleToggleAccess}
                          disabled={
                            // Schema节点且父级Instance受限 → 不可切换为公开
                            selectedNode.data.type === 'database' && isParentInstanceRestricted()
                          }
                          checkedChildren={<><LockOutlined /> 受限</>}
                          unCheckedChildren={<><UnlockOutlined /> 公开</>}
                        />
                        <Tag color={isRestricted ? 'orange' : 'green'} style={{ fontSize: 13 }}>
                          {isRestricted 
                            ? (selectedNode.data.type === 'database' && isParentInstanceRestricted()
                                ? '受实例限制，不可公开' 
                                : '仅授权用户可访问')
                            : '所有用户可访问'}
                        </Tag>
                      </Space>
                    </Col>
                  </Row>
                </Card>

                {/* 父级Instance受限提示 */}
                {selectedNode.data.type === 'database' && isParentInstanceRestricted() && (
                  <Alert
                    message="层级访问约束"
                    description={`父级实例 "${(() => { const p = treeData.find(n => n.key === `instance:${selectedNode.data.parentId}`); return p?.data?.name || selectedNode.data.parentId; })()}" 已开启访问控制，该 Schema 自动受保护，不可设为公开访问。`}
                    type="warning"
                    showIcon
                    style={{ marginBottom: 16 }}
                  />
                )}

                {/* Owner List (only when restricted) */}
                {isRestricted && (
                  <Card size="small" title={<span><CrownOutlined /> 可访问人员 ({owners.length})</span>}
                    extra={<Button type="primary" size="small" icon={<UserAddOutlined />} onClick={openAddUserModal}>添加人员</Button>}>
                    {owners.length === 0 ? (
                      <Alert message="当前无授权用户，请添加可访问人员" type="warning" showIcon />
                    ) : (
                      <Table dataSource={owners} columns={ownerColumns} rowKey="id" loading={ownersLoading}
                        pagination={false} size="small" />
                    )}
                  </Card>
                )}

                {!isRestricted && (
                  <Alert message="该资源当前为公开访问，所有系统用户均可访问。如需限制，请开启上方的访问控制开关。"
                    type="info" showIcon />
                )}
              </>
            )}
          </Card>
        </Col>
      </Row>

      {/* Add Owner Modal */}
      <Modal title="添加可访问人员" open={addVisible}
        onCancel={() => { setAddVisible(false); setSelectedUser(''); }}
        onOk={handleAddOwner} okText="确认添加" destroyOnClose>
        {selectedNode && (
          <Descriptions size="small" column={2} style={{ marginBottom: 16 }}>
            <Descriptions.Item label="资源类型"><Tag>{typeLabels[selectedNode.data.type]}</Tag></Descriptions.Item>
            <Descriptions.Item label="资源名称">{getResourceDisplayName(selectedNode)}</Descriptions.Item>
          </Descriptions>
        )}
        <div style={{ marginBottom: 8, fontWeight: 500 }}>选择用户</div>
        <Select showSearch value={selectedUser || undefined} placeholder="搜索或选择用户"
          filterOption={(input, option) => (option?.label as string || '').toLowerCase().includes(input.toLowerCase())}
          onSearch={searchUsers} onChange={val => setSelectedUser(val)}
          notFoundContent={userSearchLoading ? <Spin size="small" /> : '无匹配用户'}
          style={{ width: '100%' }}
          loading={userSearchLoading}
          options={users.map((u: any) => ({
            label: u.nickname || u.username,
            value: u.userId || u.id,
          }))} />
      </Modal>
    </div>
  );
};

export default ResourceOwnerPage;
