package com.dataops.dms.controller;

import com.dataops.dms.common.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/monitor")
@Tag(name = "系统监控")
public class MonitoringController {

    @GetMapping("/system")
    @Operation(summary = "系统资源监控")
    public Result<Map<String, Object>> systemInfo() {
        Map<String, Object> info = new HashMap<>();

        // JVM信息
        Runtime runtime = Runtime.getRuntime();
        info.put("totalMemory", runtime.totalMemory() / 1024 / 1024 + " MB");
        info.put("freeMemory", runtime.freeMemory() / 1024 / 1024 + " MB");
        info.put("maxMemory", runtime.maxMemory() / 1024 / 1024 + " MB");
        info.put("usedMemory", (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024 + " MB");
        info.put("availableProcessors", runtime.availableProcessors());

        // Memory详情
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        info.put("heapMemoryUsed", memoryMXBean.getHeapMemoryUsage().getUsed() / 1024 / 1024 + " MB");
        info.put("heapMemoryMax", memoryMXBean.getHeapMemoryUsage().getMax() / 1024 / 1024 + " MB");

        // OS信息
        OperatingSystemMXBean osMXBean = ManagementFactory.getOperatingSystemMXBean();
        info.put("osName", osMXBean.getName());
        info.put("osArch", osMXBean.getArch());
        info.put("osVersion", osMXBean.getVersion());
        info.put("systemLoadAverage", String.format("%.2f", osMXBean.getSystemLoadAverage()));

        // JVM运行时间
        RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        long uptime = runtimeMXBean.getUptime();
        info.put("jvmUptime", uptime / 1000 + " seconds");

        return Result.success(info);
    }

    @GetMapping("/health")
    @Operation(summary = "健康检查")
    public Result<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", System.currentTimeMillis());
        health.put("javaVersion", System.getProperty("java.version"));
        health.put("springBootVersion", "2.7.18");
        return Result.success(health);
    }
}
