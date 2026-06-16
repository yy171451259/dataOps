#!/usr/bin/env python
# -*- coding: utf-8 -*-
import os

script_dir = os.path.dirname(os.path.abspath(__file__))
proj = os.path.dirname(script_dir)
path = os.path.join(proj, 'frontend/src/pages/SchemaDesignerPage.tsx')

with open(path, 'r', encoding='utf-8') as f:
    c = f.read()

# 1. State: viewMode + ticket list filters (after databaseList state)
old = "  const [databaseList, setDatabaseList] = useState<any[]>([]);"
new = """  const [viewMode, setViewMode] = useState<'list' | 'editor'>('list');
  const [ticketList, setTicketList] = useState<any[]>([]);
  const [loadingTicketList, setLoadingTicketList] = useState(false);
  const [ticketFilterStatus, setTicketFilterStatus] = useState<string>('');
  const [ticketFilterKeyword, setTicketFilterKeyword] = useState<string>('');
  const [selectedTicket, setSelectedTicket] = useState<any | null>(null);
  const [databaseList, setDatabaseList] = useState<any[]>([]);"""
c = c.replace(old, new)
print("1. State vars added")

# 2. Add loadTicketList function + handleNewTicket (after loadDatabaseList)
old = "  const loadDatabaseList = async () => {"
new = """  // ========== 加载工单列表 ==========
  const loadTicketList = async () => {
    setLoadingTicketList(true);
    try {
      const params: any = { changeType: 'DDL' };
      if (ticketFilterStatus) params.status = ticketFilterStatus;
      if (ticketFilterKeyword) params.keyword = ticketFilterKeyword;
      const res = await ticketApi.list(params);
      setTicketList(res.data?.data || []);
    } catch (e) {
      console.error('加载工单列表失败', e);
    } finally {
      setLoadingTicketList(false);
    }
  };

  // ========== 新建工单 ==========
  const handleNewTicket = () => {
    setSelectedTicket(null);
    setViewMode('editor');
  };

  const handleEditTicket = (ticket: any) => {
    setSelectedTicket(ticket);
    // 选中工单的数据库
    if (ticket.databaseId) {
      setSelectedDatabase(ticket.databaseId);
    }
    if (ticket.sqlContent) {
      setGeneratedSql(ticket.sqlContent);
    }
    setViewMode('editor');
    // 加载数据库列表（如未加载）
    if (databaseList.length === 0) loadDatabaseList();
  };

  const handleBackToList = () => {
    setViewMode('list');
    loadTicketList();
  };

  const loadDatabaseList = async () => {"""
c = c.replace(old, new)
print("2. Ticket list functions added")

# 3. After the main render's div, add view mode routing
# Replace the outermost return structure
old = """  // ========== 主渲染 ==========
  return (
    <div style={{ height: 'calc(100vh - 64px)', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>"""
new = """  // ========== 渲染：工单列表 ==========
  const renderTicketList = () => {
    const statusOptions = [
      { value: '', label: '全部' },
      { value: 'pending', label: '待审批' },
      { value: 'approved', label: '已通过' },
      { value: 'executing', label: '执行中' },
      { value: 'done', label: '已完成' },
      { value: 'failed', label: '失败' },
      { value: 'rejected', label: '已拒绝' },
    ];
    const statusColors: Record<string, string> = {
      pending: 'orange', approved: 'blue', executing: 'cyan',
      done: 'green', failed: 'red', rejected: 'red', cancelled: 'default',
    };
    const statusLabels: Record<string, string> = {
      pending: '待审批', approved: '已通过', executing: '执行中',
      done: '已完成', failed: '失败', rejected: '已拒绝', cancelled: '已取消',
    };

    return (
      <div style={{ height: '100%', display: 'flex', flexDirection: 'column', padding: 16, overflow: 'hidden' }}>
        <Card size="small" style={{ marginBottom: 12, flexShrink: 0 }}>
          <Row gutter={16} align="middle">
            <Col><span style={{ fontWeight: 500, fontSize: 15 }}>结构设计工单</span></Col>
            <Col flex="auto">
              <Space>
                <Input.Search placeholder="搜索工单号/标题/变更原因" allowClear style={{ width: 260 }}
                  value={ticketFilterKeyword}
                  onChange={e => setTicketFilterKeyword(e.target.value)}
                  onSearch={() => loadTicketList()} />
                <Select style={{ width: 120 }} value={ticketFilterStatus} onChange={v => { setTicketFilterStatus(v); setTimeout(loadTicketList, 100); }}>
                  {statusOptions.map(o => <Option key={o.value} value={o.value}>{o.label}</Option>)}
                </Select>
              </Space>
            </Col>
            <Col>
              <Space>
                <Button icon={<ReloadOutlined />} onClick={loadTicketList}>刷新</Button>
                <Button type="primary" icon={<PlusOutlined />} onClick={handleNewTicket}>新建工单</Button>
              </Space>
            </Col>
          </Row>
        </Card>
        <div style={{ flex: 1, overflow: 'auto' }}>
          <Spin spinning={loadingTicketList}>
            <Table dataSource={ticketList} rowKey="id" size="small" pagination={{ pageSize: 20 }}
              columns={[
                { title: '工单号', dataIndex: 'id', width: 100, ellipsis: true, render: (t: string) => <span style={{ fontFamily: 'monospace', fontSize: 12 }}>#{t.substring(0, 8)}</span> },
                { title: '标题', dataIndex: 'title', ellipsis: true, width: 200 },
                { title: '数据库', dataIndex: 'databaseName', width: 140 },
                { title: '变更原因', dataIndex: 'description', ellipsis: true, width: 200 },
                { title: '工单类型', dataIndex: 'changeType', width: 90, render: (t: string) => <Tag color={t === 'DDL' ? 'blue' : 'default'}>{t}</Tag> },
                { title: '当前状态', dataIndex: 'status', width: 100, render: (s: string) => <Tag color={statusColors[s] || 'default'}>{statusLabels[s] || s}</Tag> },
                { title: '发起人', dataIndex: 'creatorId', width: 100 },
                { title: '当前处理人', dataIndex: 'currentApproverId', width: 100, render: (t: string) => t || '-' },
                { title: '创建时间', dataIndex: 'createTime', width: 160, render: (t: string) => t || '-' },
                { title: '最后操作', dataIndex: 'updateTime', width: 160, render: (t: string) => t || '-' },
                { title: '操作', width: 140, fixed: 'right', render: (_: any, record: any) => (
                  <Space>
                    {record.status !== 'rejected' && record.status !== 'cancelled' && (
                      <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleEditTicket(record)}>编辑</Button>
                    )}
                    {record.status === 'done' && record.sqlContent && (
                      <Popconfirm title="确认执行此DDL？" onConfirm={async () => {
                        setGeneratedSql(record.sqlContent);
                        setSelectedTicket(record);
                        if (record.databaseId) setSelectedDatabase(record.databaseId);
                        setViewMode('editor');
                      }}>
                        <Button type="link" size="small" icon={<PlayCircleOutlined />}>执行</Button>
                      </Popconfirm>
                    )}
                  </Space>
                )},
              ]}
            />
          </Spin>
        </div>
      </div>
    );
  };

  // ========== 主渲染 ==========
  return (
    <div style={{ height: 'calc(100vh - 64px)', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      {viewMode === 'list' ? renderTicketList() : (<>"""
c = c.replace(old, new)
print("3. Ticket list render added")

# 4. Close the wrapper div that we opened
# The original return ends with `</div>` (closing the main div)
# We need to add `</>)` before that to close the editor fragment
# Find the renderPipelineSteps - it's inside the editor view now
# The structure is:
#   {viewMode === 'list' ? renderTicketList() : (<>
#     ...existing content...
#   </>)}
# The original </div> at the end closes the outer div

# Add closing of the editor fragment before the SQL preview modal comment
# Actually, looking at the render structure, the modals (SQL preview, ticket submit, etc.)
# are inside the main return but outside the dev/integration/production tab sections.
# Since we're wrapping in (<>...</>), we just need to close </> before the modals.

# Let me find the first modal after the content - that's where editor content ends
old = """      {/* SQL预览弹窗 - 增强版 */}"""
new = """      </>)}
      {/* SQL预览弹窗 - 增强版 */}"""
c = c.replace(old, new)
print("4. Editor fragment closed before modals")

with open(path, 'w', encoding='utf-8') as f:
    f.write(c)
print('\nDone!')