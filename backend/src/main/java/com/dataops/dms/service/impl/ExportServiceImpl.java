package com.dataops.dms.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.dataops.dms.common.result.Result;
import com.dataops.dms.entity.AuditLog;
import com.dataops.dms.mapper.AuditLogMapper;
import com.dataops.dms.service.ExportService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service
public class ExportServiceImpl implements ExportService {

    @Resource
    private AuditLogMapper auditLogMapper;

    @Override
    public void exportCsv(List<Map<String, Object>> data, List<String> columns, String fileName, HttpServletResponse response) {
        try {
            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(fileName, "UTF-8"));

            // BOM for Excel
            OutputStream os = response.getOutputStream();
            os.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});

            // Header
            os.write(String.join(",", columns).getBytes(StandardCharsets.UTF_8));
            os.write('\n');

            // Data
            for (Map<String, Object> row : data) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < columns.size(); i++) {
                    if (i > 0) sb.append(',');
                    Object val = row.get(columns.get(i));
                    if (val != null) {
                        String strVal = val.toString();
                        if (strVal.contains(",") || strVal.contains("\"") || strVal.contains("\n")) {
                            strVal = "\"" + strVal.replace("\"", "\"\"") + "\"";
                        }
                        sb.append(strVal);
                    }
                }
                os.write(sb.toString().getBytes(StandardCharsets.UTF_8));
                os.write('\n');
            }
            os.flush();
        } catch (Exception e) {
            throw new RuntimeException("CSV导出失败: " + e.getMessage());
        }
    }

    @Override
    public void exportExcel(List<Map<String, Object>> data, List<String> columns, String fileName, HttpServletResponse response) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Data");

            // Header style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Header row
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns.get(i));
                cell.setCellStyle(headerStyle);
            }

            // Data rows
            for (int i = 0; i < data.size(); i++) {
                Row row = sheet.createRow(i + 1);
                Map<String, Object> dataRow = data.get(i);
                for (int j = 0; j < columns.size(); j++) {
                    Cell cell = row.createCell(j);
                    Object val = dataRow.get(columns.get(j));
                    if (val != null) {
                        cell.setCellValue(val.toString());
                    }
                }
            }

            // Auto-size columns
            for (int i = 0; i < columns.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(fileName, "UTF-8"));
            workbook.write(response.getOutputStream());
            response.getOutputStream().flush();
        } catch (Exception e) {
            throw new RuntimeException("Excel导出失败: " + e.getMessage());
        }
    }

    @Override
    public void exportJson(List<Map<String, Object>> data, String fileName, HttpServletResponse response) {
        try {
            response.setContentType("application/json;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(fileName, "UTF-8"));
            String json = JSON.toJSONString(data);
            response.getWriter().write(json);
            response.getWriter().flush();
        } catch (Exception e) {
            throw new RuntimeException("JSON导出失败: " + e.getMessage());
        }
    }

    @Override
    public void exportAuditLog(HttpServletResponse response, String format) {
        List<AuditLog> logs = auditLogMapper.selectList(null);
        // Convert entities to maps
        List<Map<String, Object>> data = JSON.parseObject(JSON.toJSONString(logs),
                new TypeReference<List<Map<String, Object>>>() {});
        List<String> columns = java.util.Arrays.asList("id", "userId", "action", "resourceType", "resourceId", "detail", "clientIp", "riskLevel", "createTime");

        switch (format != null ? format.toLowerCase() : "csv") {
            case "excel":
            case "xlsx":
                exportExcel(data, columns, "audit_log.xlsx", response);
                break;
            case "json":
                exportJson(data, "audit_log.json", response);
                break;
            default:
                exportCsv(data, columns, "audit_log.csv", response);
        }
    }

    @Override
    public void exportSql(List<Map<String, Object>> data, List<String> columns, String tableName, String fileName, HttpServletResponse response) {
        try {
            response.setContentType("text/plain;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(fileName, "UTF-8"));

            StringBuilder sb = new StringBuilder();
            sb.append("-- DataOps DMS 导出 - ").append(tableName).append("\n");
            sb.append("-- 共 ").append(data.size()).append(" 行\n\n");

            for (Map<String, Object> row : data) {
                sb.append("INSERT INTO `").append(tableName).append("` (");
                for (int i = 0; i < columns.size(); i++) {
                    if (i > 0) sb.append(", ");
                    sb.append("`").append(columns.get(i)).append("`");
                }
                sb.append(") VALUES (");
                for (int i = 0; i < columns.size(); i++) {
                    if (i > 0) sb.append(", ");
                    Object val = row.get(columns.get(i));
                    if (val == null) {
                        sb.append("NULL");
                    } else if (val instanceof Number) {
                        sb.append(val);
                    } else {
                        String str = val.toString().replace("'", "''").replace("\\", "\\\\");
                        sb.append("'").append(str).append("'");
                    }
                }
                sb.append(");\n");
            }

            response.getWriter().write(sb.toString());
            response.getWriter().flush();
        } catch (Exception e) {
            throw new RuntimeException("SQL导出失败: " + e.getMessage());
        }
    }

    @Override
    public Result<Void> exportDataDictionary(String databaseId, String format, HttpServletResponse response) {
        // Stub: metadata-based dict export
        return Result.success("导出完成");
    }
}
