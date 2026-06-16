package com.dataops.dms.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dataops.dms.entity.Ticket;
import org.apache.ibatis.annotations.Mapper;

/**
 * 工单Mapper
 */
@Mapper
public interface TicketMapper extends BaseMapper<Ticket> {
}
