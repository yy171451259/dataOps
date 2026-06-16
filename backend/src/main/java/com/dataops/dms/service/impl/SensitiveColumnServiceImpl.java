package com.dataops.dms.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dataops.dms.entity.SensitiveColumn;
import com.dataops.dms.mapper.SensitiveColumnMapper;
import com.dataops.dms.service.SensitiveColumnService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class SensitiveColumnServiceImpl extends ServiceImpl<SensitiveColumnMapper, SensitiveColumn> implements SensitiveColumnService {

    @Override
    @Transactional
    public SensitiveColumn markSensitive(SensitiveColumn col, String userId) {
        // 检查是否已存在
        LambdaQueryWrapper<SensitiveColumn> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SensitiveColumn::getInstanceId, col.getInstanceId())
               .eq(SensitiveColumn::getTableName, col.getTableName())
               .eq(SensitiveColumn::getColumnName, col.getColumnName());
        if (col.getSchemaName() != null) {
            wrapper.eq(SensitiveColumn::getSchemaName, col.getSchemaName());
        }
        SensitiveColumn existing = this.getOne(wrapper);
        if (existing != null) {
            existing.setSensitivityLevel(col.getSensitivityLevel());
            existing.setCategory(col.getCategory());
            existing.setMaskRuleId(col.getMaskRuleId());
            existing.setDescription(col.getDescription());
            existing.setUpdatedAt(LocalDateTime.now());
            this.updateById(existing);
            log.info("更新敏感列标记: {}.{}.{}", col.getSchemaName(), col.getTableName(), col.getColumnName());
            return existing;
        }
        col.setCreatedAt(LocalDateTime.now());
        col.setCreatedBy(userId);
        col.setIsActive(true);
        this.save(col);
        log.info("新增敏感列标记: {}.{}.{}", col.getSchemaName(), col.getTableName(), col.getColumnName());
        return col;
    }

    @Override
    @Transactional
    public int batchMark(List<SensitiveColumn> cols, String userId) {
        int count = 0;
        for (SensitiveColumn col : cols) {
            try { markSensitive(col, userId); count++; }
            catch (Exception e) { log.error("标记敏感列失败: {}", col.getColumnName(), e); }
        }
        return count;
    }

    @Override
    public List<SensitiveColumn> getByDatabase(String databaseId, String databaseName) {
        LambdaQueryWrapper<SensitiveColumn> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SensitiveColumn::getInstanceId, databaseId)
               .eq(SensitiveColumn::getIsActive, true);
        if (databaseName != null) {
            wrapper.eq(SensitiveColumn::getSchemaName, databaseName);
        }
        return this.list(wrapper);
    }

    @Override
    public List<SensitiveColumn> getByTable(String databaseId, String databaseName, String tableName) {
        LambdaQueryWrapper<SensitiveColumn> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SensitiveColumn::getInstanceId, databaseId)
               .eq(SensitiveColumn::getTableName, tableName)
               .eq(SensitiveColumn::getIsActive, true);
        if (databaseName != null) {
            wrapper.eq(SensitiveColumn::getSchemaName, databaseName);
        }
        return this.list(wrapper);
    }

    @Override
    @Transactional
    public boolean deleteSensitive(String id) {
        SensitiveColumn col = this.getById(id);
        if (col != null) {
            col.setIsActive(false);
            col.setUpdatedAt(LocalDateTime.now());
            return this.updateById(col);
        }
        return false;
    }
}
