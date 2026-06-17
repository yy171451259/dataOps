package com.dataops.dms.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.dataops.dms.common.result.PageResult;
import com.dataops.dms.dto.TicketCreateDTO;
import com.dataops.dms.entity.Ticket;
import com.dataops.dms.sql.LockFreeDmlEngine;
import com.dataops.dms.sql.OnlineDdlEngine;

import java.util.List;
import java.util.Map;

/**
 * 工单服务接口
 */
public interface TicketService extends IService<Ticket> {

    /**
     * 创建数据变更工单
     */
    Ticket createDataChangeTicket(TicketCreateDTO dto, String creatorId);

    /**
     * 检测SQL是否需要无锁数据变更（DML）
     */
    LockFreeDmlEngine.DmlCheckResult checkLockFreeDml(String instanceId, String schemaName, String sql) throws Exception;

    /**
     * 检测SQL是否需要无锁结构变更（DDL）
     */
    OnlineDdlEngine.DdlCheckResult checkOnlineDdl(String instanceId, String schemaName, String sql) throws Exception;

    /**
     * 审批工单
     */
    boolean approveTicket(String ticketId, String approverId, boolean approved, String comment);

    /**
     * 回滚工单
     */
    boolean rollbackTicket(String ticketId, String operatorId) throws Exception;

    /**
     * 取消工单
     */
    boolean cancelTicket(String ticketId, String userId);

    /**
     * 获取我的待审批工单
     */
    List<Ticket> getMyPendingTickets(String approverId);

    /**
     * 分页获取我的待审批工单
     */
    PageResult<Ticket> getMyPendingTicketsPage(String approverId, Integer page, Integer size);

    /**
     * 获取我创建的工单
     */
    List<Ticket> getMyCreatedTickets(String creatorId);

    /**
     * 分页获取我创建的工单
     */
    PageResult<Ticket> getMyCreatedTicketsPage(String creatorId, String status, Integer page, Integer size);

    /**
     * 获取工单详情
     */
    Ticket getTicketDetail(String ticketId);

    /**
     * 获取所有工单列表
     */
    List<Ticket> getAllTickets();

    /**
     * 分页查询工单列表
     */
    PageResult<Ticket> queryTicketsPage(String changeType, String status, String keyword, String databaseId, Integer page, Integer size);

    /**
     * 按条件查询工单
     */
    List<Ticket> queryTickets(String changeType, String status, String keyword, String databaseId);

    // ============ 对标阿里云DMS新增接口 ============

    /**
     * 暂停无锁DML执行
     */
    boolean pauseDmlExecution(String ticketId, String operatorId);

    /**
     * 恢复无锁DML执行
     */
    boolean resumeDmlExecution(String ticketId, String operatorId);

    /**
     * 终止无锁DML执行
     */
    boolean stopDmlExecution(String ticketId, String operatorId);

    /**
     * 查询无锁DML执行进度
     */
    Map<String, Object> getDmlProgress(String ticketId);

    /**
     * 检查并处理审批超时工单（定时任务调用）
     */
    int processApprovalTimeout();

    /**
     * 获取工单审批记录列表
     */
    List<Map<String, Object>> getApprovalRecords(String ticketId);
}