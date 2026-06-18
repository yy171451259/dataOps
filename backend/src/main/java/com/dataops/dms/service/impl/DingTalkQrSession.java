package com.dataops.dms.service.impl;

import java.util.Map;

/**
 * 钉钉扫码登录会话
 */
public class DingTalkQrSession {

    private String sessionId;
    private String status; // PENDING, SCANNED, SUCCESS, EXPIRED
    private String token;
    private Map<String, Object> loginResult;
    private long createdAt;
    private long expireAt;

    public DingTalkQrSession() {}

    public DingTalkQrSession(String sessionId) {
        this.sessionId = sessionId;
        this.status = "PENDING";
        this.createdAt = System.currentTimeMillis();
        this.expireAt = this.createdAt + 5 * 60 * 1000; // 5分钟过期
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > expireAt;
    }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }

    public Map<String, Object> getLoginResult() { return loginResult; }
    public void setLoginResult(Map<String, Object> loginResult) { this.loginResult = loginResult; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getExpireAt() { return expireAt; }
    public void setExpireAt(long expireAt) { this.expireAt = expireAt; }
}
