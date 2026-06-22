package com.dataops.dms.service.impl;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.dataops.dms.config.DingTalkConfig;
import com.dataops.dms.service.DingTalkOAuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 钉钉OAuth服务实现
 */
@Service
public class DingTalkOAuthServiceImpl implements DingTalkOAuthService {

    @Resource
    private DingTalkConfig dingTalkConfig;

    private final RestTemplate restTemplate = new RestTemplate();

    private static final Logger log = LoggerFactory.getLogger(DingTalkOAuthServiceImpl.class);

    /**
     * 扫码登录会话缓存（sessionId -> session）
     */
    private final ConcurrentHashMap<String, DingTalkQrSession> qrSessions = new ConcurrentHashMap<>();

    @Override
    public String getAuthUrl(String state) {
        return getAuthUrl(state, null);
    }

    public String getAuthUrl(String state, String redirectUri) {
        String uri = redirectUri != null && !redirectUri.isEmpty() 
            ? redirectUri 
            : dingTalkConfig.getRedirectUri();
        String encodedUri;
        try {
            encodedUri = java.net.URLEncoder.encode(uri, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            encodedUri = uri;
        }
        return String.format("%s?redirect_uri=%s&response_type=code&client_id=%s&scope=openid+profile&state=%s&prompt=consent",
                dingTalkConfig.getAuthUrl(),
                encodedUri,
                dingTalkConfig.getAppKey(),
                state);
    }

    @Override
    public Map<String, Object> getUserInfoByAuthCode(String authCode) {
        // 1. 通过授权码获取新版 accessToken
        String accessToken = getAccessToken(authCode);
        if (accessToken == null) {
            throw new RuntimeException("获取钉钉访问令牌失败");
        }

        // 2. 通过新版 accessToken 获取用户信息（包含 openId）
        Map<String, Object> userInfo = getUserInfo(accessToken);

        // 3. 通过 unionId 获取 userId（最可靠，用于发送工作通知）
        String unionId = (String) userInfo.get("unionId");
        if (unionId != null) {
            String userId = getUserIdByUnionId(unionId);
            if (userId != null && !userId.isEmpty()) {
                userInfo.put("userId", userId);
                return userInfo;
            }
        }

        // 4. 如果 unionId 方式失败，尝试通过 openId + 应用级 token 获取 userId
        String openId = (String) userInfo.get("openId");
        if (openId != null) {
            String userId = getUserIdByOpenId(openId);
            if (userId != null && !userId.isEmpty()) {
                userInfo.put("userId", userId);
            }
        }

        return userInfo;
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
            @SuppressWarnings("unchecked")
            Map<String, Object> result = JSON.parseObject(response.getBody(), Map.class);
            return result;
        } else {
            throw new RuntimeException("获取钉钉用户信息失败");
        }
    }

    // ========== 扫码登录会话管理 ==========

    @Override
    public String createQrSession() {
        String sessionId = UUID.randomUUID().toString().replace("-", "");
        DingTalkQrSession session = new DingTalkQrSession(sessionId);
        qrSessions.put(sessionId, session);
        return sessionId;
    }

    @Override
    public DingTalkQrSession getQrSession(String sessionId) {
        DingTalkQrSession session = qrSessions.get(sessionId);
        if (session != null && session.isExpired()) {
            session.setStatus("EXPIRED");
            qrSessions.remove(sessionId);
            return session;
        }
        return session;
    }

    @Override
    public void updateQrSessionStatus(String sessionId, String status) {
        DingTalkQrSession session = qrSessions.get(sessionId);
        if (session != null) {
            session.setStatus(status);
        }
    }

    @Override
    public void completeQrSession(String sessionId, String token, Map<String, Object> loginResult) {
        DingTalkQrSession session = qrSessions.get(sessionId);
        if (session != null) {
            session.setStatus("SUCCESS");
            session.setToken(token);
            session.setLoginResult(loginResult);
        }
    }

    /**
     * 定时清理过期会话（每30秒执行一次）
     */
    @Scheduled(fixedDelay = 30000)
    public void cleanExpiredSessions() {
        qrSessions.entrySet().removeIf(entry -> entry.getValue().isExpired());
    }

    // ========== 私有方法 ==========

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
            @SuppressWarnings("unchecked")
            Map<String, Object> result = JSON.parseObject(response.getBody(), Map.class);
            return (String) result.get("accessToken");
        } else {
            throw new RuntimeException("获取钉钉访问令牌失败");
        }
    }

    // ========== userId 获取方法 ==========

    /**
     * 通过 unionId 获取 userId（适用于企业内部应用）
     * API: POST https://oapi.dingtalk.com/topapi/user/getbyunionid?access_token=xxx
     * 使用旧版 oapi token 调用，需要企业内部应用权限
     */
    @Override
    public String getUserIdByUnionId(String unionId) {
        if (unionId == null || unionId.isEmpty()) {
            return null;
        }

        try {
            String oapiToken = getOapiToken();
            if (oapiToken == null) {
                log.warn("oapi token 获取失败，无法转换 unionId");
                return null;
            }

            String url = "https://oapi.dingtalk.com/topapi/user/getbyunionid?access_token=" + oapiToken;

            Map<String, String> body = new HashMap<>();
            body.put("unionid", unionId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(JSON.toJSONString(body), headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JSONObject json = JSON.parseObject(response.getBody());
                int errCode = json.getIntValue("errcode");
                if (errCode == 0 && json.containsKey("result")) {
                    JSONObject result = json.getJSONObject("result");
                    if (result != null) {
                        String userId = result.getString("userid");
                        log.info("unionId 转 userId 成功: unionId={} -> userId={}", maskUnionId(unionId), userId);
                        return userId;
                    }
                } else {
                    log.warn("unionId 转 userId 失败: errcode={}, errmsg={}",
                        errCode, json.getString("errmsg"));
                }
            }
        } catch (Exception e) {
            log.warn("unionId 转 userId 异常", e);
        }
        return null;
    }

    /**
     * 通过 openId 获取 userId（使用新版API + 应用级 token）
     * API: POST https://api.dingtalk.com/v1.0/contact/users/getUserIdByOpenId
     * 需要应用级 accessToken
     */
    private String getUserIdByOpenId(String openId) {
        if (openId == null || openId.isEmpty()) {
            return null;
        }

        try {
            String appToken = getAppAccessToken();
            if (appToken == null) {
                log.warn("应用级 accessToken 获取失败，无法转换 openId");
                return null;
            }

            Map<String, String> body = new HashMap<>();
            body.put("openId", openId);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-acs-dingtalk-access-token", appToken);

            HttpEntity<String> entity = new HttpEntity<>(JSON.toJSONString(body), headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    "https://api.dingtalk.com/v1.0/contact/users/getUserIdByOpenId",
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> result = JSON.parseObject(response.getBody(), Map.class);
                String userId = (String) result.get("userId");
                if (userId != null && !userId.isEmpty()) {
                    log.info("openId 转 userId 成功");
                    return userId;
                }
            }
        } catch (Exception e) {
            log.warn("openId 转 userId 异常", e);
        }
        return null;
    }

    /**
     * 获取旧版 oapi.dingtalk.com accessToken（企业内部应用）
     * API: GET https://oapi.dingtalk.com/gettoken?appkey=xxx&appsecret=xxx
     */
    private String cachedOapiToken;
    private long oapiTokenExpireAt;

    private String getOapiToken() {
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
                    oapiTokenExpireAt = Instant.now().toEpochMilli() + (expireIn > 0 ? expireIn : 7200) * 1000L - 200_000L;
                    return cachedOapiToken;
                } else {
                    log.warn("钉钉 oapi gettoken 失败: errcode={}, errmsg={}",
                        json.getIntValue("errcode"), json.getString("errmsg"));
                }
            }
        } catch (Exception e) {
            log.warn("钉钉 oapi accessToken 获取异常", e);
        }
        return null;
    }

    /**
     * 获取新版应用级 accessToken（调用新版API）
     * API: POST https://api.dingtalk.com/v1.0/oauth2/accessToken
     */
    private String cachedAppToken;
    private long appTokenExpireAt;

    private String getAppAccessToken() {
        if (cachedAppToken != null && Instant.now().toEpochMilli() < appTokenExpireAt) {
            return cachedAppToken;
        }

        try {
            Map<String, String> body = new HashMap<>();
            body.put("appKey", dingTalkConfig.getAppKey());
            body.put("appSecret", dingTalkConfig.getAppSecret());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(JSON.toJSONString(body), headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    dingTalkConfig.getGetAccessTokenUrl(),
                    entity,
                    String.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                JSONObject json = JSON.parseObject(response.getBody());
                String token = json.getString("accessToken");
                Long expireIn = json.getLong("expireIn");
                if (token != null) {
                    cachedAppToken = token;
                    long expireMs = expireIn != null ? expireIn * 1000L : 7200_000L;
                    appTokenExpireAt = Instant.now().toEpochMilli() + expireMs - 200_000L;
                    return cachedAppToken;
                }
            }
        } catch (Exception e) {
            log.warn("钉钉应用级 accessToken 获取异常", e);
        }
        return null;
    }

    /** 脱敏 unionId（日志用） */
    private String maskUnionId(String unionId) {
        if (unionId == null) return null;
        if (unionId.length() <= 8) return unionId.substring(0, Math.min(unionId.length(), 4)) + "***";
        return unionId.substring(0, 4) + "***" + unionId.substring(unionId.length() - 4);
    }
}