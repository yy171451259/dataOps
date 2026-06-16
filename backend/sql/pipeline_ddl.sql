-- ============================================
-- DDL部署流水线表
-- ============================================

-- 1. 流水线定义表
CREATE TABLE IF NOT EXISTS dms_pipeline (
    id VARCHAR(32) PRIMARY KEY COMMENT '流水线ID',
    name VARCHAR(100) NOT NULL COMMENT '流水线名称',
    description VARCHAR(500) COMMENT '描述',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE-启用, INACTIVE-禁用',
    created_by VARCHAR(64) COMMENT '创建人',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='DDL部署流水线';

-- 2. 流水线阶段表（环境链条）
CREATE TABLE IF NOT EXISTS dms_pipeline_stage (
    id VARCHAR(32) PRIMARY KEY COMMENT '阶段ID',
    pipeline_id VARCHAR(32) NOT NULL COMMENT '流水线ID',
    stage_name VARCHAR(50) NOT NULL COMMENT '阶段名称: DEV/TEST/STAGING/PROD',
    stage_order INT NOT NULL COMMENT '阶段顺序: 1,2,3...',
    database_instance_id VARCHAR(32) NOT NULL COMMENT '关联的数据库实例ID',
    require_approval TINYINT(1) DEFAULT 0 COMMENT '是否需要审批: 0-否, 1-是',
    approval_role VARCHAR(64) COMMENT '审批角色',
    auto_execute TINYINT(1) DEFAULT 1 COMMENT '上一阶段成功后自动执行: 0-否, 1-是',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_pipeline (pipeline_id),
    INDEX idx_order (pipeline_id, stage_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流水线阶段配置';

-- 3. 变更执行记录表
CREATE TABLE IF NOT EXISTS dms_pipeline_execution (
    id VARCHAR(32) PRIMARY KEY COMMENT '执行记录ID',
    pipeline_id VARCHAR(32) NOT NULL COMMENT '流水线ID',
    title VARCHAR(200) NOT NULL COMMENT '变更标题',
    description TEXT COMMENT '变更描述',
    sql_content LONGTEXT NOT NULL COMMENT 'DDL SQL内容',
    created_by VARCHAR(64) NOT NULL COMMENT '创建人',
    current_stage_id VARCHAR(32) COMMENT '当前阶段ID',
    current_stage_order INT COMMENT '当前阶段顺序',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '整体状态: PENDING-待开始, IN_PROGRESS-进行中, SUCCESS-全部成功, FAILED-失败, CANCELLED-已取消',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_pipeline (pipeline_id),
    INDEX idx_status (status),
    INDEX idx_creator (created_by)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流水线变更执行记录';

-- 4. 阶段执行详情表
CREATE TABLE IF NOT EXISTS dms_pipeline_stage_execution (
    id VARCHAR(32) PRIMARY KEY COMMENT '阶段执行ID',
    execution_id VARCHAR(32) NOT NULL COMMENT '执行记录ID',
    stage_id VARCHAR(32) NOT NULL COMMENT '阶段ID',
    stage_name VARCHAR(50) NOT NULL COMMENT '阶段名称',
    stage_order INT NOT NULL COMMENT '阶段顺序',
    database_instance_id VARCHAR(32) NOT NULL COMMENT '数据库实例ID',
    sql_content LONGTEXT NOT NULL COMMENT '执行的SQL',
    status VARCHAR(20) DEFAULT 'PENDING' COMMENT '状态: PENDING-待执行, APPROVING-审批中, EXECUTING-执行中, SUCCESS-成功, FAILED-失败, SKIPPED-跳过, ROLLBACKED-已回滚',
    executed_by VARCHAR(64) COMMENT '执行人',
    executed_at DATETIME COMMENT '执行时间',
    duration_seconds INT COMMENT '执行耗时(秒)',
    result_message TEXT COMMENT '执行结果消息',
    rollback_sql LONGTEXT COMMENT '回滚SQL',
    rollbacked_by VARCHAR(64) COMMENT '回滚人',
    rollbacked_at DATETIME COMMENT '回滚时间',
    approval_by VARCHAR(64) COMMENT '审批人',
    approval_at DATETIME COMMENT '审批时间',
    approval_comment VARCHAR(500) COMMENT '审批意见',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_execution (execution_id),
    INDEX idx_stage (stage_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='阶段执行详情';
