package com.dataops.dms.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataops.dms.common.result.Result;
import com.dataops.dms.entity.MetadataColumn;
import com.dataops.dms.entity.MetadataTable;
import com.dataops.dms.service.MetadataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/metadata")
@Tag(name = "元数据管理")
public class MetadataController {

    @Resource
    private MetadataService metadataService;

    @PostMapping("/collect/{databaseId}")
    @Operation(summary = "采集数据库元数据")
    public Result<Integer> collect(@PathVariable String databaseId,
                                   @RequestBody(required = false) Map<String, String> body) {
        String schemaName = (body != null) ? body.get("schemaName") : null;
        return metadataService.collectMetadata(databaseId, schemaName);
    }

    @GetMapping("/tables")
    @Operation(summary = "获取表列表")
    public Result<Page<MetadataTable>> listTables(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String databaseId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String schemaName) {
        return metadataService.listTables(page, size, databaseId, keyword, schemaName);
    }

    @GetMapping("/tables/{tableId}")
    @Operation(summary = "获取表详情")
    public Result<MetadataTable> getTable(@PathVariable String tableId) {
        return metadataService.getTableDetail(tableId);
    }

    @PutMapping("/tables/{tableId}")
    @Operation(summary = "更新表元数据")
    public Result<MetadataTable> updateTable(@PathVariable String tableId,
                                              @RequestParam(required = false) String tableComment,
                                              @RequestParam(required = false) String businessTags,
                                              @RequestParam(required = false) String owner) {
        return metadataService.updateTableMeta(tableId, tableComment, businessTags, owner);
    }

    @GetMapping("/tables/{tableId}/columns")
    @Operation(summary = "获取表字段列表")
    public Result<List<MetadataColumn>> listColumns(@PathVariable String tableId) {
        return metadataService.listColumns(tableId);
    }

    @PutMapping("/columns/{columnId}")
    @Operation(summary = "更新字段元数据")
    public Result<MetadataColumn> updateColumn(@PathVariable String columnId,
                                                @RequestParam(required = false) String columnComment,
                                                @RequestParam(required = false) String businessName,
                                                @RequestParam(required = false) Boolean isSensitive,
                                                @RequestParam(required = false) String businessDesc,
                                                @RequestParam(required = false) String dataSteward) {
        return metadataService.updateColumnMeta(columnId, columnComment, businessName, isSensitive);
    }

    @GetMapping("/search")
    @Operation(summary = "搜索元数据")
    public Result<Page<MetadataTable>> search(
            @RequestParam String keyword,
            @RequestParam(required = false) String databaseId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return metadataService.search(keyword, databaseId, page, size);
    }

    @GetMapping("/stats/{databaseId}")
    @Operation(summary = "数据库统计")
    public Result<Object> stats(@PathVariable String databaseId) {
        return metadataService.getDatabaseStats(databaseId);
    }

    /**
     * 导出数据字典为CSV
     */
    @GetMapping("/export-dictionary/{databaseId}")
    @Operation(summary = "导出数据字典")
    public void exportDictionary(@PathVariable String databaseId,
                                 @RequestParam(required = false) String schemaName,
                                 HttpServletResponse response) {
        try {
            response.setContentType("text/csv;charset=UTF-8");
            response.setHeader("Content-Disposition", "attachment;filename=data_dictionary.csv");
            response.setCharacterEncoding("UTF-8");

            PrintWriter writer = response.getWriter();
            // BOM for Excel
            writer.write("\uFEFF");
            writer.println("表名,表注释,字段名,字段类型,字段长度,是否可空,是否主键,字段注释,业务名称,是否敏感");

            Result<Page<MetadataTable>> tablesResult = metadataService.listTables(1, 1000, databaseId, null, schemaName);
            if (tablesResult.getData() != null && tablesResult.getData().getRecords() != null) {
                for (MetadataTable table : tablesResult.getData().getRecords()) {
                    Result<List<MetadataColumn>> colsResult = metadataService.listColumns(table.getId());
                    if (colsResult.getData() != null) {
                        for (MetadataColumn col : colsResult.getData()) {
                            writer.println(String.format("%s,%s,%s,%s,%s,%s,%s,%s,%s,%s",
                                    csvEscape(table.getTableName()),
                                    csvEscape(table.getTableComment()),
                                    csvEscape(col.getColumnName()),
                                    csvEscape(col.getDataType()),
                                    col.getColumnLength() != null ? col.getColumnLength() : "",
                                    Boolean.TRUE.equals(col.getIsNullable()) ? "是" : "否",
                                    Boolean.TRUE.equals(col.getIsPrimaryKey()) ? "是" : "否",
                                    csvEscape(col.getColumnComment()),
                                    csvEscape(col.getBusinessName()),
                                    Boolean.TRUE.equals(col.getIsSensitive()) ? "是" : "否"
                            ));
                        }
                    }
                }
            }
            writer.flush();
        } catch (Exception e) {
            throw new RuntimeException("导出数据字典失败: " + e.getMessage());
        }
    }

    private String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
