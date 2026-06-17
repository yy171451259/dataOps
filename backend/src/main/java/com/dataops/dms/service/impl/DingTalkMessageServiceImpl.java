package com.dataops.dms.service.impl;

import com.alibaba.fastjson2.JSON;
import com.dataops.dms.config.DingTalkConfig;
import com.dataops.dms.service.DingTalkMessageService;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 钉钉消息服务实现
 */
@Service
public class DingTalkMessageServiceImpl implements DingTalkMessageService {

    @Resource
    private DingTalkConfig dingTalkConfig;

    private final RestTemplate restTemplate = new RestTemplate();

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
     * 发送消息
     */
    private boolean sendMessage(String userId, Map<String, Object> message) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("msg", message);
            requestBody.put("robotCode", dingTalkConfig.getAppKey());

            // 构建接收者
            Map<String, String> receiver = new HashMap<>();
            receiver.put("userId", userId);
            requestBody.put("receiver", receiver);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-acs-dingtalk-access-token", getAccessToken());

            HttpEntity<String> entity = new HttpEntity<>(JSON.toJSONString(requestBody), headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    dingTalkConfig.getSendMessageUrl(),
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * 获取访问令牌（简化版，实际应该缓存）
     */
    private String getAccessToken() {
        // 这里应该实现获取企业内部应用的accessToken
        // 暂时返回空，需要后续实现
        return "";
    }
}