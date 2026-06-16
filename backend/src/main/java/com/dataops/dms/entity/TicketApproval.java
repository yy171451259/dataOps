package com.dataops.dms.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 工单审批记录实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ticket_approval")
public class TicketApproval extends BaseEntity {

    /**
     * 工单ID
     */
    private String ticketId;

    /**
     * 审批人ID
     */
    private String approverId;

    /**
     * 审批状态: approved, rejected
     */
    private String status;

    /**
     * 审批意见
     */
    private String comment;

    /**
     * 审批时间
     */
    private LocalDateTime approvedAt;
}
