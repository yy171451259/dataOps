package com.dataops.dms.service.impl;

import com.alibaba.fastjson2.JSON;
import com.dataops.dms.config.DingTalkConfig;
import com.dataops.dms.service.DingTalkOAuthService;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 钉钉OAuth服务实现
 */
@Service
public class DingTalkOAuthServiceImpl implements DingTalkOAuthService {

    @Resource
    private DingTalkConfig dingTalkConfig;

    private final RestTemplate restTemplate = new RestTemplate();

    @Override
    public String getAuthUrl(String state) {
        return getAuthUrl(state, null);
    }

    public String getAuthUrl(String state, String redirectUri) {
        String uri = redirectUri != null && !redirectUri.isEmpty() 
            ? redirectUri 
            : dingTalkConfig.getRedirectUri();
        return String.format("%s?redirect_uri=%s&response_type=code&client_id=%s&scope=openid&state=%s&prompt=consent",
                dingTalkConfig.getAuthUrl(),
                uri,
                dingTalkConfig.getAppKey(),
                state);
    }

    @Override
    public Map<String, Object> getUserInfoByAuthCode(String authCode) {
        // 1. 通过授权码获取 accessToken
        String accessToken = getAccessToken(authCode);
        if (accessToken == null) {
            throw new RuntimeException("获取钉钉访问令牌失败");
        }

        // 2. 通过 accessToken 获取用户信息
        return getUserInfo(accessToken);
    }

    @Override
    public Map<String, Object> getUserInfo(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-acs-dingtalk-access-token", accessToken);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                dingTalkConfig.getUserInfoUrl(),
                HttpMethod.GET,
                entity,
                String.class
        );

        if (response.getStatusCode() == HttpStatus.OK) {
            Map<String, Object> result = JSON.parseObject(response.getBody(), Map.class);
            return result;
        } else {
            throw new RuntimeException("获取钉钉用户信息失败");
        }
    }

    /**
     * 通过授权码获取访问令牌
     */
    private String getAccessToken(String authCode) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("clientId", dingTalkConfig.getAppKey());
        requestBody.put("clientSecret", dingTalkConfig.getAppSecret());
        requestBody.put("code", authCode);
        requestBody.put("grantType", "authorization_code");
        requestBody.put("refreshToken", "");

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> entity = new HttpEntity<>(JSON.toJSONString(requestBody), headers);

        ResponseEntity<String> response = restTemplate.exchange(
                dingTalkConfig.getTokenUrl(),
                HttpMethod.POST,
                entity,
                String.class
        );

        if (response.getStatusCode() == HttpStatus.OK) {
            Map<String, Object> result = JSON.parseObject(response.getBody(), Map.class);
            return (String) result.get("accessToken");
        } else {
            throw new RuntimeException("获取钉钉访问令牌失败");
        }
    }
}