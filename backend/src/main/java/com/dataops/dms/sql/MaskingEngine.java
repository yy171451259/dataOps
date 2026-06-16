package com.dataops.dms.sql;

import com.dataops.dms.entity.SensitiveColumn;
import com.dataops.dms.entity.SensitiveMaskRule;
import com.dataops.dms.service.SensitiveColumnService;
import com.dataops.dms.service.SensitiveMaskRuleService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;

/**
 * 数据脱敏引擎
 * 基于 sys_sensitive_column + sys_sensitive_mask_rule 对查询结果自动应用脱敏算法
 */
@Slf4j
@Component
public class MaskingEngine {

    @Resource
    private SensitiveColumnService sensitiveColumnService;

    @Resource
    private SensitiveMaskRuleService maskRuleService;

    /**
     * 对查询结果应用脱敏
     * @param databaseId   数据库实例ID
     * @param databaseName 数据库名称
     * @param tableName    表名
     * @param rows         查询结果行数据
     * @return 脱敏后的数据
     */
    public List<Map<String, Object>> applyMasking(
            String databaseId, String databaseName,
            String tableName, List<Map<String, Object>> rows) {

        if (rows == null || rows.isEmpty()) return rows;

        // 1. 查该表所有启用的敏感列
        List<SensitiveColumn> cols = sensitiveColumnService.getByTable(databaseId, databaseName, tableName);
        if (cols.isEmpty()) return rows;

        // 2. 加载关联的脱敏规则
        Map<String, SensitiveMaskRule> ruleMap = loadRules(cols);

        // 3. 构建 列名 → 脱敏规则 映射
        Map<String, SensitiveMaskRule> columnRuleMap = new HashMap<>();
        for (SensitiveColumn col : cols) {
            if (col.getMaskRuleId() != null && !col.getMaskRuleId().isEmpty()) {
                SensitiveMaskRule rule = ruleMap.get(col.getMaskRuleId());
                if (rule != null) {
                    columnRuleMap.put(col.getColumnName(), rule);
                }
            }
        }
        if (columnRuleMap.isEmpty()) return rows;

        // 4. 逐行逐列脱敏
        for (Map<String, Object> row : rows) {
            for (Map.Entry<String, SensitiveMaskRule> entry : columnRuleMap.entrySet()) {
                String colName = entry.getKey();
                SensitiveMaskRule rule = entry.getValue();
                Object value = row.get(colName);
                if (value != null) {
                    row.put(colName, maskValue(value.toString(), rule));
                }
            }
        }

        log.debug("脱敏完成: {}.{} @ {} 列", databaseName, tableName, columnRuleMap.size());
        return rows;
    }

    /**
     * 加载脱敏规则到Map
     */
    private Map<String, SensitiveMaskRule> loadRules(List<SensitiveColumn> cols) {
        Map<String, SensitiveMaskRule> map = new HashMap<>();
        for (SensitiveColumn col : cols) {
            if (col.getMaskRuleId() != null && !col.getMaskRuleId().isEmpty()
                    && !map.containsKey(col.getMaskRuleId())) {
                SensitiveMaskRule rule = maskRuleService.getById(col.getMaskRuleId());
                if (rule != null) {
                    map.put(col.getMaskRuleId(), rule);
                }
            }
        }
        return map;
    }

    /**
     * 对单个值执行脱敏
     */
    private String maskValue(String value, SensitiveMaskRule rule) {
        String algorithm = rule.getMaskAlgorithm();
        if (algorithm == null || algorithm.isEmpty()) {
            // 回退：使用通用参数脱敏
            return maskByConfig(value, rule);
        }

        switch (algorithm.toUpperCase()) {
            case "PHONE":     return maskPhone(value);
            case "EMAIL":     return maskEmail(value);
            case "ID_CARD":   return maskIdCard(value);
            case "BANK_CARD": return maskBankCard(value);
            case "FULL_MASK": return "****";
            case "NAME_MASK": return maskName(value);
            case "CUSTOM":    return maskByConfig(value, rule);
            default:          return maskByConfig(value, rule);
        }
    }

    /**
     * 通用参数脱敏
     */
    private String maskByConfig(String value, SensitiveMaskRule rule) {
        int prefix = rule.getKeepPrefixLen() != null ? rule.getKeepPrefixLen() : 0;
        int suffix = rule.getKeepSuffixLen() != null ? rule.getKeepSuffixLen() : 0;
        char maskChar = (rule.getMaskCharacter() != null && !rule.getMaskCharacter().isEmpty())
                ? rule.getMaskCharacter().charAt(0) : '*';

        if (value.length() <= prefix + suffix) {
            return repeatChar(maskChar, value.length());
        }
        return value.substring(0, prefix)
                + repeatChar(maskChar, value.length() - prefix - suffix)
                + value.substring(value.length() - suffix);
    }

    /**
     * Java 8 兼容的字符重复
     */
    private static String repeatChar(char c, int count) {
        if (count <= 0) return "";
        char[] arr = new char[count];
        Arrays.fill(arr, c);
        return new String(arr);
    }

    // ===== 9种内置脱敏算法 =====

    private String maskPhone(String value) {
        if (value.length() == 11) {
            return value.substring(0, 3) + "****" + value.substring(7);
        }
        return maskCharMiddle(value, '*');
    }

    private String maskEmail(String value) {
        int atIndex = value.indexOf('@');
        if (atIndex > 0) {
            String prefix = value.substring(0, atIndex);
            String suffix = value.substring(atIndex);
            if (prefix.length() <= 2) return "*" + suffix;
            return prefix.charAt(0) + "***" + prefix.charAt(prefix.length() - 1) + suffix;
        }
        return maskCharMiddle(value, '*');
    }

    private String maskIdCard(String value) {
        if (value.length() == 18) {
            return value.substring(0, 6) + "********" + value.substring(14);
        }
        if (value.length() == 15) {
            return value.substring(0, 6) + "******" + value.substring(12);
        }
        return maskCharMiddle(value, '*');
    }

    private String maskBankCard(String value) {
        if (value.length() >= 16) {
            return value.substring(0, 4) + "******" + value.substring(value.length() - 4);
        }
        return maskCharMiddle(value, '*');
    }

    private String maskName(String value) {
        if (value.length() <= 1) return "*";
        StringBuilder sb = new StringBuilder();
        sb.append(value.charAt(0));
        for (int i = 1; i < value.length(); i++) sb.append('*');
        return sb.toString();
    }

    private String maskCharMiddle(String value, char c) {
        if (value.length() <= 2) return "**";
        return value.charAt(0) + repeatChar(c, value.length() - 2) + value.charAt(value.length() - 1);
    }
}
