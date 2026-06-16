package com.dataops.dms.controller;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.dataops.dms.common.result.Result;
import com.dataops.dms.service.ExportService;
import com.dataops.dms.service.MetadataAccessControlService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/export")
@Tag(name = "数据导出")
public class ExportController {

    @Resource
    private ExportService exportService;

    @Resource
    private MetadataAccessControlService accessControlService;

    /** 提取并校验导出权限 */
    private void checkExportPermission(Map<String, Object> params, HttpServletRequest request) {
        String instanceId = (String) params.getOrDefault("instanceId", params.get("databaseId"));
        String userId = (String) request.getAttribute("userId");
        if (userId == null || userId.isEmpty()) {
            throw new RuntimeException("用户未登录");
        }
        if (instanceId != null && !instanceId.isEmpty()) {
            if (!accessControlService.canUserAccess(userId, "schema", instanceId, "export")) {
                throw new RuntimeException("您没有导出数据的权限");
            }
        }
    }

    @PostMapping("/csv")
    @Operation(summary = "导出CSV")
    public void exportCsv(@RequestBody Map<String, Object> params, HttpServletResponse response,
                          HttpServletRequest request) {
        checkExportPermission(params, request);
        List<Map<String, Object>> data = JSON.parseObject(
                JSON.toJSONString(params.get("data")),
                new TypeReference<List<Map<String, Object>>>() {});
        List<String> columns = JSON.parseObject(
                JSON.toJSONString(params.get("columns")),
                new TypeReference<List<String>>() {});
        String fileName = params.getOrDefault("fileName", "export.csv").toString();
        exportService.exportCsv(data, columns, fileName, response);
    }

    @PostMapping("/excel")
    @Operation(summary = "导出Excel")
    public void exportExcel(@RequestBody Map<String, Object> params, HttpServletResponse response,
                            HttpServletRequest request) {
        checkExportPermission(params, request);
        List<Map<String, Object>> data = JSON.parseObject(
                JSON.toJSONString(params.get("data")),
                new TypeReference<List<Map<String, Object>>>() {});
        List<String> columns = JSON.parseObject(
                JSON.toJSONString(params.get("columns")),
                new TypeReference<List<String>>() {});
        String fileName = params.getOrDefault("fileName", "export.xlsx").toString();
        exportService.exportExcel(data, columns, fileName, response);
    }

    @PostMapping("/json")
    @Operation(summary = "导出JSON")
    public void exportJson(@RequestBody Map<String, Object> params, HttpServletResponse response,
                           HttpServletRequest request) {
        checkExportPermission(params, request);
        List<Map<String, Object>> data = JSON.parseObject(
                JSON.toJSONString(params.get("data")),
                new TypeReference<List<Map<String, Object>>>() {});
        String fileName = params.getOrDefault("fileName", "export.json").toString();
        exportService.exportJson(data, fileName, response);
    }

    @PostMapping("/sql")
    @Operation(summary = "导出INSERT SQL语句")
    public void exportSql(@RequestBody Map<String, Object> params, HttpServletResponse response,
                          HttpServletRequest request) {
        checkExportPermission(params, request);
        List<Map<String, Object>> data = JSON.parseObject(
                JSON.toJSONString(params.get("data")),
                new TypeReference<List<Map<String, Object>>>() {});
        List<String> columns = JSON.parseObject(
                JSON.toJSONString(params.get("columns")),
                new TypeReference<List<String>>() {});
        String tableName = params.getOrDefault("tableName", "export_table").toString();
        String fileName = params.getOrDefault("fileName", "export.sql").toString();
        exportService.exportSql(data, columns, tableName, fileName, response);
    }

    @GetMapping("/audit")
    @Operation(summary = "导出审计日志")
    public void exportAuditLog(@RequestParam(defaultValue = "csv") String format, HttpServletResponse response) {
        exportService.exportAuditLog(response, format);
    }
}
