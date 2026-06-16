package com.dataops.dms.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataops.dms.common.result.Result;
import com.dataops.dms.entity.NotificationConfig;
import com.dataops.dms.entity.NotificationLog;

/**
 * 通知服务接口
 */
public interface NotificationService {

    Result<Page<NotificationConfig>> listConfigs(Integer page, Integer size);

    Result<NotificationConfig> getConfigById(String id);

    Result<NotificationConfig> createConfig(NotificationConfig config);

    Result<NotificationConfig> updateConfig(NotificationConfig config);

    Result<Void> deleteConfig(String id);

    Result<Void> toggleConfig(String id, Boolean enabled);

    /**
     * 发送通知
     */
    Result<Void> send(String channel, String recipient, String title, String content);

    /**
     * 根据事件触发通知
     */
    Result<Void> triggerEvent(String eventType, String title, String content);

    /**
     * 获取通知历史
     */
    Result<Page<NotificationLog>> listLogs(Integer page, Integer size, String channel, String status);

    /**
     * 测试通知配置
     */
    Result<Void> testConfig(String id);
}
