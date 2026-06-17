import React from 'react';
import { Tag, Badge, Timeline, Row, Col } from 'antd';
import { CheckCircleFilled, CloseCircleFilled } from '@ant-design/icons';
import dayjs from 'dayjs';

export interface PermissionRequest {
  id: string;
  resourceType: string;
  resourceId: string;
  resourceName: string;
  permissions: string[];
  reason: string;
  status: 'pending' | 'approved' | 'rejected' | 'cancelled';
  applicantId: string;
  applicantName: string;
  approverId?: string;
  approverName?: string;
  approverComment?: string;
  createdAt: string;
  approvedAt?: string;
}

export const statusMap: Record<string, { color: string; text: string }> = {
  pending: { color: 'processing', text: '待审批' },
  approved: { color: 'success', text: '已通过' },
  rejected: { color: 'error', text: '已拒绝' },
  cancelled: { color: 'default', text: '已撤销' },
};

export const resourceTypeLabels: Record<string, string> = {
  instance: '实例',
  database: 'Schema',
  table: '数据表',
  column: '字段',
};

interface DetailResourceInfo {
  instanceId?: string;
  instanceName?: string;
  databaseId?: string;
  databaseName?: string;
  environment?: string;
  tables?: string[];
}

function parseReasonExtra(reason: string): { info: DetailResourceInfo | null; plainText: string } {
  if (!reason) return { info: null, plainText: '' };
  if (reason.startsWith('[JSON]')) {
    const end = reason.indexOf('}', 6);
    if (end > 0) {
      try {
        const jsonStr = reason.substring(6, end + 1);
        const obj = JSON.parse(jsonStr);
        const info: DetailResourceInfo = {
          instanceId: obj.instanceId || obj.databaseId,
          instanceName: obj.instanceName || obj.databaseName,
          databaseName: obj.databaseName || obj.schemaName,
          environment: obj.environment,
          tables: obj.tables,
        };
        const rest = reason.substring(end + 1).trim();
        return { info, plainText: rest };
      } catch {
        return { info: null, plainText: reason };
      }
    }
  }
  return { info: null, plainText: reason };
}

const RequestDetail: React.FC<{ request: PermissionRequest }> = ({ request }) => {
  const { info, plainText } = parseReasonExtra(request.reason || '');
  const createTime = request.createdAt;
  const updateTime = request.approvedAt;
  const perms = request.permissions && request.permissions.length > 0
    ? request.permissions
    : (request as any).requestedPermissions
      ? String((request as any).requestedPermissions).split(',').map((s: string) => s.trim()).filter(Boolean)
      : [];
  const permText = perms
    .map((p: string) => ({
      read: '查询', query: '查询',
      export: '导出', write: '变更', update: '变更',
      ddl: '结构变更'
    } as any)[p] || p)
    .join(' / ');
  const typeText = (resourceTypeLabels as any)[request.resourceType] || request.resourceType || '-';
  const resourceText = request.resourceName
    ? request.resourceName
    : (info?.instanceName || request.resourceId || '-');

  const sectionStyle: React.CSSProperties = {
    background: '#fff', border: '1px solid #e8e8e8', borderRadius: 4
  };
  const sectionHeader: React.CSSProperties = {
    padding: '8px 16px', borderBottom: '1px solid #f0f0f0', fontSize: 13, fontWeight: 500,
    display: 'flex', alignItems: 'center', justifyContent: 'space-between'
  };

  let approveBody: React.ReactNode;
  let approveDotColor: string = 'gray';
  if (request.status === 'pending') {
    const approver = (request as any).approverName || (request as any).approverId;
    approveBody = (
      <div>
        <Tag color="processing">待审批</Tag>
        <span style={{ color: '#666', marginLeft: 8 }}>
          {approver ? `等待 ${approver} 审批中...` : '等待资源 Owner 或管理员审批...'}
        </span>
      </div>
    );
    approveDotColor = 'blue';
  } else if (request.status === 'approved') {
    approveBody = (
      <div>
        <Tag color="success">审批通过</Tag>
        <span style={{ color: '#222', marginLeft: 8 }}>
          审批人：{request.approverName || request.approverId || '-'}
        </span>
        {updateTime && (
          <span style={{ color: '#999', marginLeft: 12 }}>
            ({dayjs(updateTime).format('YYYY-MM-DD HH:mm:ss')})
          </span>
        )}
        {request.approverComment && (
          <div style={{ marginTop: 6, color: '#555', background: '#fafafa', padding: '6px 10px', borderRadius: 4 }}>
            审批意见：{request.approverComment}
          </div>
        )}
      </div>
    );
    approveDotColor = 'green';
  } else if (request.status === 'rejected') {
    approveBody = (
      <div>
        <Tag color="error">审批拒绝</Tag>
        <span style={{ color: '#222', marginLeft: 8 }}>
          审批人：{request.approverName || request.approverId || '-'}
        </span>
        {updateTime && (
          <span style={{ color: '#999', marginLeft: 12 }}>
            ({dayjs(updateTime).format('YYYY-MM-DD HH:mm:ss')})
          </span>
        )}
        {request.approverComment && (
          <div style={{ marginTop: 6, color: '#555', background: '#fff2f0', padding: '6px 10px', borderRadius: 4 }}>
            审批意见：{request.approverComment}
          </div>
        )}
      </div>
    );
    approveDotColor = 'red';
  } else {
    approveBody = (
      <div>
        <Tag>已撤销</Tag>
        <span style={{ color: '#666', marginLeft: 8 }}>申请人主动撤销了该申请</span>
      </div>
    );
    approveDotColor = 'gray';
  }

  let finishText = '';
  let finishColor = '#999';
  if (request.status === 'approved') { finishText = '✓ 分配权限成功'; finishColor = '#52c41a'; }
  else if (request.status === 'rejected') { finishText = '✗ 申请已拒绝，未分配权限'; finishColor = '#f5222d'; }
  else if (request.status === 'pending') { finishText = '等待审批完成...'; }
  else if (request.status === 'cancelled') { finishText = '申请已撤销'; }

  return (
    <div style={{ background: '#f5f7fa', padding: 16, borderRadius: 4 }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', marginBottom: 8 }}>
        <Badge
          status={(statusMap[request.status]?.color || 'default') as any}
          text={statusMap[request.status]?.text || request.status}
        />
      </div>

      <Timeline
        mode="left"
        style={{ paddingLeft: 4 }}
        items={[
          {
            color: 'green',
            dot: <CheckCircleFilled style={{ color: '#52c41a', fontSize: 16 }} />,
            children: (
              <div style={sectionStyle}>
                <div style={sectionHeader}><span>基本信息</span></div>
                <div style={{ padding: 12, fontSize: 12, lineHeight: 1.9 }}>
                  <Row>
                    <Col span={6} style={{ color: '#666' }}>工单编号</Col>
                    <Col span={18} style={{ color: '#222', fontWeight: 500 }}>{request.id}</Col>
                  </Row>
                  <Row>
                    <Col span={6} style={{ color: '#666' }}>申请人</Col>
                    <Col span={18} style={{ color: '#222' }}>{request.applicantName || request.applicantId}</Col>
                  </Row>
                  <Row>
                    <Col span={6} style={{ color: '#666' }}>资源类型</Col>
                    <Col span={18} style={{ color: '#222' }}><Tag>{typeText}</Tag></Col>
                  </Row>
                  <Row>
                    <Col span={6} style={{ color: '#666' }}>资源名称</Col>
                    <Col span={18} style={{ color: '#222' }}>{resourceText}</Col>
                  </Row>
                  <Row>
                    <Col span={6} style={{ color: '#666' }}>操作类型</Col>
                    <Col span={18} style={{ color: '#222' }}>{permText || '-'}</Col>
                  </Row>
                  {info?.environment && (
                    <Row>
                      <Col span={6} style={{ color: '#666' }}>环境</Col>
                      <Col span={18} style={{ color: '#222' }}><Tag color="orange">{info.environment}</Tag></Col>
                    </Row>
                  )}
                  <Row>
                    <Col span={6} style={{ color: '#666' }}>申请原因</Col>
                    <Col span={18} style={{ color: '#222' }}>{plainText || '-'}</Col>
                  </Row>
                  <Row>
                    <Col span={6} style={{ color: '#666' }}>提交时间</Col>
                    <Col span={18} style={{ color: '#222' }}>
                      {createTime ? dayjs(createTime).format('YYYY-MM-DD HH:mm:ss') : '-'}
                    </Col>
                  </Row>
                  <Row>
                    <Col span={6} style={{ color: '#666' }}>审批人</Col>
                    <Col span={18} style={{ color: '#222' }}>{request.approverName || request.approverId || '-'}</Col>
                  </Row>
                </div>
              </div>
            )
          },
          {
            color: approveDotColor,
            dot: (request.status === 'approved' || request.status === 'rejected') ? (
              request.status === 'approved'
                ? <CheckCircleFilled style={{ color: '#52c41a', fontSize: 16 }} />
                : <CloseCircleFilled style={{ color: '#f5222d', fontSize: 16 }} />
            ) : undefined,
            children: (
              <div style={sectionStyle}>
                <div style={sectionHeader}><span>审批</span></div>
                <div style={{ padding: 12, fontSize: 12, lineHeight: 1.8 }}>{approveBody}</div>
              </div>
            )
          },
          {
            color: request.status === 'approved' ? 'green' : (request.status === 'rejected' ? 'red' : 'gray'),
            dot: request.status === 'approved'
              ? <CheckCircleFilled style={{ color: '#52c41a', fontSize: 16 }} />
              : request.status === 'rejected'
                ? <CloseCircleFilled style={{ color: '#f5222d', fontSize: 16 }} />
                : undefined,
            children: (
              <div style={sectionStyle}>
                <div style={sectionHeader}><span>完成</span></div>
                <div style={{ padding: 12, fontSize: 12, lineHeight: 1.8 }}>
                  <span style={{ color: finishColor }}>{finishText}</span>
                </div>
              </div>
            )
          }
        ]}
      />
    </div>
  );
};

export default RequestDetail;