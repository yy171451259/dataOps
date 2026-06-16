package com.dataops.dms.sql;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * SQL执行结果
 */
@Data
public class SqlExecuteResult {

    /**
     * 列名列表
     */
    private List<String> columns;

    /**
     * 数据行
     */
    private List<Map<String, Object>> data;

    /**
     * 影响行数
     */
    private Integer affectRows;

    /**
     * 总行数（查询结果）
     */
    private Integer totalRows;

    /**
     * 执行时间（毫秒）
     */
    private Long executionTime;

    /**
     * 是否成功
     */
    private Boolean success;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 是否还有更多数据（分页加载）
     */
    private Boolean hasMore;
}
