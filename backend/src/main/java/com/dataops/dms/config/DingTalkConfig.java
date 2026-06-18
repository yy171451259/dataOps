package com.dataops.dms.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 钉钉配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "dingtalk")
public class DingTalkConfig {

    /**
     * 钉钉AppKey
     */
    private String appKey;

    /**
     * 钉钉AppSecret
     */
    private String appSecret;

    /**
     * 钉钉企业ID
     */
    private String corpId;

    /**
     * 回调地址
     */
    private String redirectUri;

    /**
     * 后端公网地址（扫码登录用，需配置为前端可访问的后端地址）
     */
    private String backendBaseUrl;

    /**
     * OAuth授权地址
     */
    private String authUrl = "https://login.dingtalk.com/oauth2/auth";

    /**
     * 获取Token地址
     */
    private String tokenUrl = "https://api.dingtalk.com/v1.0/oauth2/userAccessToken";

    /**
     * 获取用户信息地址
     */
    private String userInfoUrl = "https://api.dingtalk.com/v1.0/contact/users/me";

    /**
     * 发送消息地址
     */
    private String sendMessageUrl = "https://api.dingtalk.com/v1.0/im/messages/conversations/send";
}