package com.dataops.dms.service;

import com.dataops.dms.common.result.Result;

import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;

/**
 * 数据导出服务接口
 */
public interface ExportService {

    /**
     * 导出查询结果为CSV
     */
    void exportCsv(List<Map<String, Object>> data, List<String> columns, String fileName, HttpServletResponse response);

    /**
     * 导出查询结果为Excel
     */
    void exportExcel(List<Map<String, Object>> data, List<String> columns, String fileName, HttpServletResponse response);

    /**
     * 导出查询结果为JSON
     */
    void exportJson(List<Map<String, Object>> data, String fileName, HttpServletResponse response);

    /**
     * 导出审计日志
     */
    void exportAuditLog(HttpServletResponse response, String format);

    /**
     * 导出数据字典
     */
    Result<Void> exportDataDictionary(String databaseId, String format, HttpServletResponse response);

    /**
     * 导出查询结果为INSERT SQL语句
     */
    void exportSql(List<Map<String, Object>> data, List<String> columns, String tableName, String fileName, HttpServletResponse response);
}
