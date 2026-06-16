package com.dataops.dms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataops.dms.entity.TicketApproval;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工单审批记录Mapper
 */
@Mapper
public interface TicketApprovalMapper extends BaseMapper<TicketApproval> {
}
