package com.dataops.dms.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataops.dms.entity.RowControlRule;
import com.dataops.dms.mapper.RowControlRuleMapper;
import com.dataops.dms.service.RowControlService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 行级控制规则服务实现
 */
@Slf4j
@Service
public class RowControlServiceImpl extends ServiceImpl<RowControlRuleMapper, RowControlRule> implements RowControlService {

}
