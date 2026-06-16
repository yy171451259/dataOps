package com.dataops.dms.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataops.dms.common.result.Result;
import com.dataops.dms.entity.NotificationConfig;
import com.dataops.dms.entity.NotificationLog;
import com.dataops.dms.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "通知管理")
public class NotificationController {

    @Resource
    private NotificationService notificationService;

    @GetMapping("/configs")
    @Operation(summary = "获取通知配置列表")
    public Result<Page<NotificationConfig>> listConfigs(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        return notificationService.listConfigs(page, size);
    }

    @GetMapping("/configs/{id}")
    @Operation(summary = "获取配置详情")
    public Result<NotificationConfig> getConfig(@PathVariable String id) {
        return notificationService.getConfigById(id);
    }

    @PostMapping("/configs")
    @Operation(summary = "创建通知配置")
    public Result<NotificationConfig> createConfig(@RequestBody NotificationConfig config) {
        return notificationService.createConfig(config);
    }

    @PutMapping("/configs/{id}")
    @Operation(summary = "更新通知配置")
    public Result<NotificationConfig> updateConfig(@PathVariable String id, @RequestBody NotificationConfig config) {
        config.setId(id);
        return notificationService.updateConfig(config);
    }

    @DeleteMapping("/configs/{id}")
    @Operation(summary = "删除通知配置")
    public Result<Void> deleteConfig(@PathVariable String id) {
        return notificationService.deleteConfig(id);
    }

    @PostMapping("/configs/{id}/toggle")
    @Operation(summary = "启用/禁用配置")
    public Result<Void> toggleConfig(@PathVariable String id, @RequestParam Boolean enabled) {
        return notificationService.toggleConfig(id, enabled);
    }

    @PostMapping("/configs/{id}/test")
    @Operation(summary = "测试通知配置")
    public Result<Void> testConfig(@PathVariable String id) {
        return notificationService.testConfig(id);
    }

    @PostMapping("/send")
    @Operation(summary = "手动发送通知")
    public Result<Void> send(@RequestBody NotificationLog log) {
        return notificationService.send(log.getChannel(), log.getRecipient(), log.getTitle(), log.getContent());
    }

    @GetMapping("/logs")
    @Operation(summary = "获取通知历史")
    public Result<Page<NotificationLog>> listLogs(
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer size,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) String status) {
        return notificationService.listLogs(page, size, channel, status);
    }
}
