package com.dataops.dms.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.dataops.dms.common.result.Result;
import com.dataops.dms.entity.NotificationConfig;
import com.dataops.dms.entity.NotificationLog;
import com.dataops.dms.mapper.NotificationConfigMapper;
import com.dataops.dms.mapper.NotificationLogMapper;
import com.dataops.dms.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class NotificationServiceImpl implements NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceImpl.class);

    @Resource
    private NotificationConfigMapper configMapper;

    @Resource
    private NotificationLogMapper logMapper;

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Override
    public Result<Page<NotificationConfig>> listConfigs(Integer page, Integer size) {
        LambdaQueryWrapper<NotificationConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(NotificationConfig::getCreateTime);
        return Result.success(configMapper.selectPage(new Page<>(page, size), wrapper));
    }

    @Override
    public Result<NotificationConfig> getConfigById(String id) {
        return Result.success(configMapper.selectById(id));
    }

    @Override
    public Result<NotificationConfig> createConfig(NotificationConfig config) {
        config.setIsEnabled(config.getIsEnabled() != null ? config.getIsEnabled() : true);
        configMapper.insert(config);
        return Result.success("创建成功", config);
    }

    @Override
    public Result<NotificationConfig> updateConfig(NotificationConfig config) {
        configMapper.updateById(config);
        return Result.success("更新成功", config);
    }

    @Override
    public Result<Void> deleteConfig(String id) {
        configMapper.deleteById(id);
        return Result.success("删除成功");
    }

    @Override
    public Result<Void> toggleConfig(String id, Boolean enabled) {
        NotificationConfig config = configMapper.selectById(id);
        if (config != null) {
            config.setIsEnabled(enabled);
            configMapper.updateById(config);
        }
        return Result.success();
    }

    @Override
    public Result<Void> send(String channel, String recipient, String title, String content) {
        NotificationLog log = new NotificationLog();
        log.setChannel(channel);
        log.setRecipient(recipient);
        log.setTitle(title);
        log.setContent(content);
        log.setStatus("pending");
        logMapper.insert(log);

        try {
            switch (channel.toUpperCase()) {
                case "EMAIL":
                    sendEmail(recipient, title, content);
                    break;
                case "WEBHOOK":
                    sendWebhook(recipient, title, content);
                    break;
                case "DINGTALK":
                case "WECHAT":
                case "SMS":
                    // 占位：接入第三方SDK
                    break;
                default:
                    log.setStatus("failed");
                    log.setErrorMessage("不支持的渠道: " + channel);
                    logMapper.updateById(log);
                    return Result.error("不支持的渠道: " + channel);
            }

            log.setStatus("success");
            log.setSentAt(LocalDateTime.now());
            logMapper.updateById(log);
            return Result.success("发送成功");
        } catch (Exception e) {
            log.setStatus("failed");
            log.setErrorMessage(e.getMessage());
            logMapper.updateById(log);
            return Result.error("发送失败: " + e.getMessage());
        }
    }

    private void sendEmail(String recipient, String title, String content) {
        if (mailSender == null) {
            log.warn("邮件服务未配置，跳过邮件发送");
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(recipient);
        message.setSubject(title);
        message.setText(content);
        mailSender.send(message);
    }

    private void sendWebhook(String url, String title, String content) {
        // 占位：HTTP POST webhook
        log.info("Webhook to {}: {} - {}", url, title, content);
    }

    @Override
    public Result<Void> triggerEvent(String eventType, String title, String content) {
        LambdaQueryWrapper<NotificationConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(NotificationConfig::getIsEnabled, true);
        List<NotificationConfig> configs = configMapper.selectList(wrapper);

        for (NotificationConfig config : configs) {
            if (config.getTriggerEvents() != null) {
                List<String> events = JSON.parseArray(config.getTriggerEvents(), String.class);
                if (events == null) continue;
                if (events.contains(eventType) || events.contains("*")) {
                    Map<String, Object> configMap = JSON.parseObject(config.getConfig(), Map.class);
                    if (configMap != null && configMap.containsKey("recipient")) {
                        String recipient = configMap.get("recipient").toString();
                        send(config.getChannel(), recipient, title, content);
                    }
                }
            }
        }
        return Result.success();
    }

    @Override
    public Result<Page<NotificationLog>> listLogs(Integer page, Integer size, String channel, String status) {
        LambdaQueryWrapper<NotificationLog> wrapper = new LambdaQueryWrapper<>();
        if (channel != null && !channel.isEmpty()) {
            wrapper.eq(NotificationLog::getChannel, channel);
        }
        if (status != null && !status.isEmpty()) {
            wrapper.eq(NotificationLog::getStatus, status);
        }
        wrapper.orderByDesc(NotificationLog::getCreateTime);
        return Result.success(logMapper.selectPage(new Page<>(page, size), wrapper));
    }

    @Override
    public Result<Void> testConfig(String id) {
        NotificationConfig config = configMapper.selectById(id);
        if (config == null) {
            return Result.error("配置不存在");
        }
        Map<String, Object> configMap = JSON.parseObject(config.getConfig(), Map.class);
        String recipient = configMap != null ? String.valueOf(configMap.getOrDefault("recipient", "")) : "";
        return send(config.getChannel(), recipient,
                "[DataOps DMS] 通知测试",
                "这是一条来自DataOps DMS的测试通知，收到此消息说明配置正确。");
    }
}
