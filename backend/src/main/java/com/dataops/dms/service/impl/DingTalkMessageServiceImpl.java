package com.dataops.dms.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.dataops.dms.config.DingTalkConfig;
import com.dataops.dms.service.DingTalkMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * 钉钉消息服务实现
 */
@Service
public class DingTalkMessageServiceImpl implements DingTalkMessageService {

    private static final Logger log = LoggerFactory.getLogger(DingTalkMessageServiceImpl.class);

    @Resource
    private DingTalkConfig dingTalkConfig;

    private final RestTemplate restTemplate = new RestTemplate();

    /** 缓存 oapi accessToken */
    private String cachedOapiToken;
    /** oapi accessToken 过期时间戳 */
    private long oapiTokenExpireAt;
    /** Token 提前刷新余量（秒） */
    private static final long TOKEN_REFRESH_MARGIN_SECONDS = 200;

    @Override
    public boolean sendTextMessage(String userId, String content) {
        Map<String, Object> message = new HashMap<>();
        message.put("msgtype", "text");

        Map<String, String> text = new HashMap<>();
        text.put("content", content);
        message.put("text", text);

        return sendMessage(userId, message);
    }

    @Override
    public boolean sendMarkdownMessage(String userId, String title, String content) {
        Map<String, Object> message = new HashMap<>();
        message.put("msgtype", "markdown");

        Map<String, String> markdown = new HashMap<>();
        markdown.put("title", title);
        markdown.put("text", content);
        message.put("markdown", markdown);

        return sendMessage(userId, message);
    }

    @Override
    public boolean sendCardMessage(String userId, String card) {
        Map<String, Object> message = new HashMap<>();
        message.put("msgtype", "interactive");
        message.put("card", JSON.parseObject(card));

        return sendMessage(userId, message);
    }

    /**
     * 发送消息（使用旧版 oapi.dingtalk.com 工作通知 API）
     */
    private boolean sendMessage(String userId, Map<String, Object> message) {
        try {
            String oapiToken = getOapiToken();
            if (oapiToken == null) {
                log.error("钉钉 oapi accessToken 为空，无法发送消息");
                return false;
            }

            Long agentId = dingTalkConfig.getAgentId();
            if (agentId == null) {
                log.error("钉钉 agentId 未配置，请检查 application.yml dingtalk.agent-id");
                return false;
            }

            if (userId == null || userId.isEmpty()) {
                log.error("钉钉消息发送失败：userId为空");
                return false;
            }

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("agent_id", agentId);
            requestBody.put("userid_list", userId);
            requestBody.put("msg", message);

            String url = dingTalkConfig.getSendMessageUrl() + "?access_token=" + oapiToken;

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(JSON.toJSONString(requestBody), headers);

            log.info("发送钉钉消息: agentId={}, userId={}", agentId, userId);
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JSONObject json = JSON.parseObject(response.getBody());
                int errCode = json.getIntValue("errcode");
                String errmsg = json.getString("errmsg");
                
                log.info("钉钉消息发送响应: errcode={}, errmsg={}, body={}", errCode, errmsg, response.getBody());
                
                if (errCode == 0) {
                    return true;
                } else {
                    log.warn("钉钉消息发送失败: errcode={}, errmsg={}", errCode, errmsg);
                    if (json.containsKey("task_id")) {
                        log.info("钉钉任务ID: {}", json.getLong("task_id"));
                    }
                }
            } else {
                log.warn("钉钉消息发送失败: status={}, body={}", response.getStatusCode(), response.getBody());
            }
            return false;
        } catch (Exception e) {
            log.error("钉钉消息发送异常 userId={}", userId, e);
            return false;
        }
    }

    /**
     * 获取 oapi.dingtalk.com 的 accessToken（旧版，用于发送工作通知）
     * API: GET https://oapi.dingtalk.com/gettoken?appkey=xxx&appsecret=xxx
     */
    private String getOapiToken() {
        if (cachedOapiToken != null && Instant.now().toEpochMilli() < oapiTokenExpireAt) {
            return cachedOapiToken;
        }

        synchronized (this) {
            if (cachedOapiToken != null && Instant.now().toEpochMilli() < oapiTokenExpireAt) {
                return cachedOapiToken;
            }

            try {
                String url = dingTalkConfig.getOapiTokenUrl()
                    + "?appkey=" + dingTalkConfig.getAppKey()
                    + "&appsecret=" + dingTalkConfig.getAppSecret();

                ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

                if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                    JSONObject json = JSON.parseObject(response.getBody());
                    if (json.getIntValue("errcode") == 0) {
                        cachedOapiToken = json.getString("access_token");
                        int expireIn = json.getIntValue("expires_in");
                        oapiTokenExpireAt = Instant.now().toEpochMilli() + (expireIn > 0 ? expireIn : 7200) * 1000L
                            - TOKEN_REFRESH_MARGIN_SECONDS * 1000L;
                        log.info("钉钉 oapi accessToken 获取成功，expires_in={}s", expireIn);
                        return cachedOapiToken;
                    } else {
                        log.warn("钉钉 oapi gettoken 失败: errcode={}, errmsg={}",
                            json.getIntValue("errcode"), json.getString("errmsg"));
                    }
                }
            } catch (Exception e) {
                log.error("钉钉 oapi accessToken 获取异常", e);
            }
            if (cachedOapiToken != null) {
                log.warn("钉钉 oapi accessToken 获取失败，使用缓存的旧 token");
                return cachedOapiToken;
            }
            return null;
        }
    }
}
