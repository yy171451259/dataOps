package com.dataops.dms.controller;

import com.alibaba.fastjson2.JSON;
import com.dataops.dms.common.result.Result;
import com.dataops.dms.entity.DatabaseInstance;
import com.dataops.dms.service.DatabaseInstanceService;
import com.dataops.dms.sql.SqlExecutor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.*;
import java.sql.*;
import java.util.*;

/**
 * 数据导入控制器
 * 支持CSV/Excel/JSON/SQL文件导入，字段映射，进度追踪
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/import")
@Tag(name = "数据导入")
public class DataImportController {

    @Resource
    private DatabaseInstanceService databaseInstanceService;

    /**
     * 预览导入文件（不实际导入，返回字段映射建议）
     */
    @PostMapping("/preview")
    @Operation(summary = "预览导入文件内容")
    public Result<ImportPreview> previewFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(defaultValue = "csv") String format) {
        ImportPreview preview = new ImportPreview();
        preview.setFileName(file.getOriginalFilename());
        preview.setFileSize(file.getSize());
        preview.setFormat(format);

        try {
            List<Map<String, Object>> rows = parseFile(file, format, 20); // 预览前20行
            if (rows.isEmpty()) {
                return Result.error(400, "文件内容为空");
            }

            preview.setColumns(new ArrayList<>(rows.get(0).keySet()));
            preview.setSampleData(rows);
            preview.setTotalPreviewRows(rows.size());

            // 自动推断字段类型
            Map<String, String> typeHints = inferColumnTypes(rows);
            preview.setColumnTypeHints(typeHints);

            return Result.success(preview);
        } catch (Exception e) {
            log.error("预览文件失败: {}", e.getMessage());
            return Result.error("解析文件失败: " + e.getMessage());
        }
    }

    /**
     * 执行数据导入
     */
    @PostMapping("/execute")
    @Operation(summary = "执行数据导入")
    public Result<ImportResult> executeImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam String databaseId,
            @RequestParam(required = false) String databaseName,
            @RequestParam String tableName,
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(defaultValue = "500") int batchSize,
            @RequestParam(required = false) String columnMapping,
            @RequestParam(defaultValue = "false") boolean createTable,
            @RequestParam(required = false) String truncateFirst) {

        DatabaseInstance db = databaseInstanceService.getById(databaseId);
        if (db == null) return Result.error(400, "数据库实例不存在");

        ImportResult result = new ImportResult();
        result.setTableName(tableName);
        result.setStartTime(System.currentTimeMillis());

        Connection conn = null;
        try {
            conn = getConnection(db, databaseName);
            conn.setAutoCommit(false);

            // 解析完整文件
            List<Map<String, Object>> allRows = parseFile(file, format, -1);
            if (allRows.isEmpty()) {
                return Result.error(400, "文件内容为空");
            }

            result.setTotalRows(allRows.size());

            // 解析字段映射
            Map<String, String> mapping = new LinkedHashMap<>();
            if (columnMapping != null && !columnMapping.isEmpty()) {
                mapping = JSON.parseObject(columnMapping, Map.class);
            } else {
                // 默认1:1映射
                for (String col : allRows.get(0).keySet()) {
                    mapping.put(col, col);
                }
            }

            // 如果需要先建表
            if (createTable) {
                String createSql = generateCreateTableSql(tableName, allRows.get(0), mapping);
                Statement stmt = conn.createStatement();
                stmt.execute(createSql);
                stmt.close();
                result.setTableCreated(true);
                log.info("自动创建表: {}", tableName);
            }

            // 如果需要先清空表
            if ("true".equalsIgnoreCase(truncateFirst)) {
                Statement stmt = conn.createStatement();
                stmt.execute("TRUNCATE TABLE " + tableName);
                stmt.close();
                result.setTableTruncated(true);
            }

            // 分批插入
            List<String> targetColumns = new ArrayList<>(mapping.values());
            String insertSql = buildInsertSql(tableName, targetColumns);
            PreparedStatement pstmt = conn.prepareStatement(insertSql);

            int batchCount = 0;
            int successRows = 0;
            int errorRows = 0;
            List<String> errors = new ArrayList<>();

            for (int i = 0; i < allRows.size(); i++) {
                Map<String, Object> row = allRows.get(i);
                try {
                    int paramIdx = 1;
                    for (Map.Entry<String, String> entry : mapping.entrySet()) {
                        String sourceCol = entry.getKey();
                        Object value = row.get(sourceCol);
                        pstmt.setObject(paramIdx++, value);
                    }
                    pstmt.addBatch();
                    batchCount++;

                    if (batchCount >= batchSize) {
                        int[] batchResults = pstmt.executeBatch();
                        successRows += batchResults.length;
                        conn.commit();
                        batchCount = 0;
                    }
                } catch (Exception e) {
                    errorRows++;
                    if (errors.size() < 10) {
                        errors.add("Row " + (i + 1) + ": " + e.getMessage());
                    }
                }
            }

            // 处理最后一批
            if (batchCount > 0) {
                int[] batchResults = pstmt.executeBatch();
                successRows += batchResults.length;
                conn.commit();
            }

            pstmt.close();
            result.setSuccessRows(successRows);
            result.setErrorRows(errorRows);
            result.setErrors(errors);
            result.setSuccess(errorRows == 0);
            result.setMessage(String.format("导入完成: 成功 %d 行, 失败 %d 行, 共 %d 行",
                    successRows, errorRows, allRows.size()));

            log.info("数据导入完成: table={}, success={}, errors={}", tableName, successRows, errorRows);

        } catch (Exception e) {
            if (conn != null) {
                try { conn.rollback(); } catch (Exception ignored) {}
            }
            result.setSuccess(false);
            result.setMessage("导入失败: " + e.getMessage());
            log.error("数据导入失败: {}", e.getMessage(), e);
        } finally {
            if (conn != null) {
                try { conn.setAutoCommit(true); } catch (Exception ignored) {}
                try { conn.close(); } catch (Exception ignored) {}
            }
            result.setEndTime(System.currentTimeMillis());
        }

        return Result.success(result.getMessage(), result);
    }

    /**
     * 获取导入支持的格式列表
     */
    @GetMapping("/formats")
    @Operation(summary = "支持的导入格式")
    public Result<List<Map<String, String>>> getSupportedFormats() {
        List<Map<String, String>> formats = new ArrayList<>();
        Map<String, String> csv = new LinkedHashMap<>(); csv.put("key", "csv"); csv.put("label", "CSV文件"); csv.put("ext", ".csv"); formats.add(csv);
        Map<String, String> json = new LinkedHashMap<>(); json.put("key", "json"); json.put("label", "JSON文件"); json.put("ext", ".json"); formats.add(json);
        Map<String, String> sql = new LinkedHashMap<>(); sql.put("key", "sql"); sql.put("label", "SQL文件"); sql.put("ext", ".sql"); formats.add(sql);
        Map<String, String> tsv = new LinkedHashMap<>(); tsv.put("key", "tsv"); tsv.put("label", "TSV文件"); tsv.put("ext", ".tsv"); formats.add(tsv);
        return Result.success(formats);
    }

    // ========== 文件解析 ==========

    private List<Map<String, Object>> parseFile(MultipartFile file, String format, int maxRows) throws Exception {
        switch (format.toLowerCase()) {
            case "csv":
            case "tsv":
                return parseCsv(file, maxRows, format.equals("tsv") ? "\t" : ",");
            case "json":
                return parseJson(file, maxRows);
            case "sql":
                return parseSqlInserts(file, maxRows);
            default:
                throw new RuntimeException("不支持的格式: " + format);
        }
    }

    private List<Map<String, Object>> parseCsv(MultipartFile file, int maxRows, String delimiter) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), "UTF-8"))) {
            String headerLine = reader.readLine();
            if (headerLine == null) return rows;

            // 处理BOM
            if (headerLine.startsWith("\uFEFF")) {
                headerLine = headerLine.substring(1);
            }

            String[] headers = splitCsvLine(headerLine, delimiter);

            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                if (maxRows > 0 && count >= maxRows) break;
                String[] values = splitCsvLine(line, delimiter);
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    row.put(headers[i].trim(), i < values.length ? values[i].trim() : null);
                }
                rows.add(row);
                count++;
            }
        }
        return rows;
    }

    private String[] splitCsvLine(String line, String delimiter) {
        // 简单CSV解析（支持引号）
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (String.valueOf(c).equals(delimiter) && !inQuotes) {
                fields.add(field.toString());
                field = new StringBuilder();
            } else {
                field.append(c);
            }
        }
        fields.add(field.toString());
        return fields.toArray(new String[0]);
    }

    private List<Map<String, Object>> parseJson(MultipartFile file, int maxRows) throws Exception {
        String content = new String(file.getBytes(), "UTF-8");
        List<Map> rawList = JSON.parseArray(content, Map.class);
        List<Map<String, Object>> result = new ArrayList<>();
        for (int i = 0; i < rawList.size(); i++) {
            if (maxRows > 0 && i >= maxRows) break;
            result.add(rawList.get(i));
        }
        return result;
    }

    private List<Map<String, Object>> parseSqlInserts(MultipartFile file, int maxRows) throws Exception {
        List<Map<String, Object>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), "UTF-8"))) {
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                if (maxRows > 0 && count >= maxRows) break;
                line = line.trim();
                if (line.toUpperCase().startsWith("INSERT")) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("sql", line);
                    rows.add(row);
                    count++;
                }
            }
        }
        return rows;
    }

    // ========== 辅助方法 ==========

    private Map<String, String> inferColumnTypes(List<Map<String, Object>> rows) {
        Map<String, String> hints = new LinkedHashMap<>();
        if (rows.isEmpty()) return hints;

        for (String col : rows.get(0).keySet()) {
            boolean allNumbers = true;
            boolean allIntegers = true;
            boolean allDates = true;

            for (Map<String, Object> row : rows) {
                Object val = row.get(col);
                if (val == null || val.toString().isEmpty()) continue;
                String str = val.toString();
                try {
                    Double.parseDouble(str);
                    if (!str.matches("-?\\d+")) allIntegers = false;
                } catch (NumberFormatException e) {
                    allNumbers = false;
                    allIntegers = false;
                }
                if (!str.matches("\\d{4}-\\d{2}-\\d{2}.*")) allDates = false;
            }

            if (allIntegers) hints.put(col, "INT");
            else if (allNumbers) hints.put(col, "DECIMAL");
            else if (allDates) hints.put(col, "DATETIME");
            else hints.put(col, "VARCHAR(255)");
        }
        return hints;
    }

    private String generateCreateTableSql(String tableName, Map<String, Object> sampleRow, Map<String, String> mapping) {
        StringBuilder sql = new StringBuilder();
        sql.append("CREATE TABLE IF NOT EXISTS `").append(tableName).append("` (\n");
        sql.append("  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,\n");

        List<String> cols = new ArrayList<>();
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            String targetCol = entry.getValue();
            Object sampleVal = sampleRow.get(entry.getKey());
            String type = "VARCHAR(255)";
            if (sampleVal instanceof Number) {
                type = ((Number) sampleVal).doubleValue() == ((Number) sampleVal).longValue() ? "BIGINT" : "DECIMAL(10,2)";
            }
            cols.add("  `" + targetCol + "` " + type);
        }
        sql.append(String.join(",\n", cols));
        sql.append("\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
        return sql.toString();
    }

    private String buildInsertSql(String tableName, List<String> columns) {
        StringBuilder sql = new StringBuilder();
        sql.append("INSERT INTO `").append(tableName).append("` (");
        List<String> quotedCols = new ArrayList<>();
        for (String col : columns) {
            quotedCols.add("`" + col + "`");
        }
        sql.append(String.join(", ", quotedCols));
        sql.append(") VALUES (");
        List<String> placeholders = new ArrayList<>();
        for (int i = 0; i < columns.size(); i++) placeholders.add("?");
        sql.append(String.join(", ", placeholders));
        sql.append(")");
        return sql.toString();
    }

    private Connection getConnection(DatabaseInstance db, String databaseName) throws Exception {
        String dbName = (databaseName != null && !databaseName.isEmpty()) ? databaseName : db.getDefaultSchemaName();
        String url = String.format("jdbc:mysql://%s:%d/%s?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&zeroDateTimeBehavior=convertToNull",
                db.getHost(), db.getPort() != null ? db.getPort() : 3306, dbName);
        Properties props = new Properties();
        props.put("user", db.getUsername());
        props.put("password", db.getPassword());
        return DriverManager.getConnection(url, props);
    }

    // ========== DTO ==========

    @Data
    public static class ImportPreview {
        private String fileName;
        private long fileSize;
        private String format;
        private List<String> columns;
        private List<Map<String, Object>> sampleData;
        private int totalPreviewRows;
        private Map<String, String> columnTypeHints;
    }

    @Data
    public static class ImportResult {
        private String tableName;
        private boolean success;
        private String message;
        private int totalRows;
        private int successRows;
        private int errorRows;
        private boolean tableCreated;
        private boolean tableTruncated;
        private List<String> errors;
        private long startTime;
        private long endTime;
    }
}
