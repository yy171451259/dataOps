package com.dataops.dms.sql;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * SQL回滚语句生成器
 * 在执行数据变更前自动备份数据并生成回滚SQL
 */
@Slf4j
@Component
public class SqlRollbackGenerator {

    /**
     * 根据变更类型和备份数据生成回滚SQL
     */
    public String generateRollbackSql(String originalSql, String changeType, String tableName, String backupData) {
        if (backupData == null || backupData.isEmpty()) {
            return null;
        }

        try {
            List<Map<String, Object>> dataList = JSON.parseObject(backupData, new TypeReference<List<Map<String, Object>>>() {});
            
            if (dataList == null || dataList.isEmpty()) {
                log.warn("备份数据为空，无法生成回滚SQL");
                return null;
            }

            if ("DELETE".equalsIgnoreCase(changeType)) {
                return generateInsertRollback(tableName, dataList);
            } else if ("UPDATE".equalsIgnoreCase(changeType)) {
                return generateUpdateRollback(tableName, dataList);
            } else if ("INSERT".equalsIgnoreCase(changeType)) {
                return generateDeleteRollback(originalSql, tableName, dataList);
            }

        } catch (Exception e) {
            log.error("生成回滚SQL失败: {}", e.getMessage(), e);
        }

        return null;
    }

    /**
     * 为DELETE操作生成INSERT回滚SQL
     */
    private String generateInsertRollback(String tableName, List<Map<String, Object>> dataList) {
        if (dataList.isEmpty()) {
            return null;
        }

        Map<String, Object> firstRow = dataList.get(0);
        List<String> columns = new ArrayList<>(firstRow.keySet());
        
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO ").append(tableName).append(" (");
        sql.append(String.join(", ", columns));
        sql.append(") VALUES ");

        // 为每一行生成VALUES
        List<String> valueRows = new ArrayList<>();
        for (Map<String, Object> row : dataList) {
            List<String> values = new ArrayList<>();
            for (String col : columns) {
                values.add(formatValue(row.get(col)));
            }
            valueRows.add("(" + String.join(", ", values) + ")");
        }

        sql.append(String.join(", ", valueRows));
        
        log.info("DELETE操作回滚SQL生成完成: {} 条记录", dataList.size());
        return sql.toString();
    }

    /**
     * 为UPDATE操作生成UPDATE回滚SQL
     * 使用主键逐行更新
     */
    private String generateUpdateRollback(String tableName, List<Map<String, Object>> dataList) {
        StringBuilder sql = new StringBuilder();
        
        // 简单实现：生成多条UPDATE语句
        // 实际项目中需要识别主键字段
        for (Map<String, Object> row : dataList) {
            List<String> setClauses = new ArrayList<>();
            List<String> whereClauses = new ArrayList<>();
            
            // 假设第一个字段是主键（简化实现）
            boolean firstColumn = true;
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String col = entry.getKey();
                String value = formatValue(entry.getValue());
                
                if (firstColumn) {
                    whereClauses.add(col + " = " + value);
                    firstColumn = false;
                } else {
                    setClauses.add(col + " = " + value);
                }
            }

            if (!setClauses.isEmpty() && !whereClauses.isEmpty()) {
                sql.append("UPDATE ").append(tableName)
                   .append(" SET ").append(String.join(", ", setClauses))
                   .append(" WHERE ").append(String.join(" AND ", whereClauses))
                   .append(";\n");
            }
        }

        log.info("UPDATE操作回滚SQL生成完成");
        return sql.toString();
    }

    /**
     * 为INSERT操作生成DELETE回滚SQL
     */
    private String generateDeleteRollback(String originalSql, String tableName, List<Map<String, Object>> dataList) {
        if (dataList.isEmpty()) {
            return null;
        }

        Map<String, Object> firstRow = dataList.get(0);
        List<String> whereClauses = new ArrayList<>();
        
        // 使用主键或唯一键生成WHERE条件
        for (Map.Entry<String, Object> entry : firstRow.entrySet()) {
            String col = entry.getKey();
            String value = formatValue(entry.getValue());
            whereClauses.add(col + " = " + value);
            break; // 简化：只用第一个字段
        }

        String sql = "DELETE FROM " + tableName + " WHERE " + String.join(" AND ", whereClauses);
        log.info("INSERT操作回滚SQL生成完成: {}", sql);
        return sql;
    }

    /**
     * 格式化SQL值
     */
    private String formatValue(Object value) {
        if (value == null) {
            return "NULL";
        }
        
        // 数字类型直接返回
        if (value instanceof Number) {
            return value.toString();
        }
        
        // 布尔类型
        if (value instanceof Boolean) {
            return (Boolean) value ? "1" : "0";
        }
        
        // 日期/字符串类型：转义单引号
        String str = value.toString();
        str = str.replace("'", "''");
        return "'" + str + "'";
    }
}