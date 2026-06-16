package com.dataops.dms.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataops.dms.entity.TicketApproval;
import com.dataops.dms.mapper.TicketApprovalMapper;
import com.dataops.dms.service.TicketApprovalService;
import org.springframework.stereotype.Service;

/**
 * 工单审批服务实现
 */
@Service
public class TicketApprovalServiceImpl 
    extends ServiceImpl<TicketApprovalMapper, TicketApproval> 
    implements TicketApprovalService {
}
