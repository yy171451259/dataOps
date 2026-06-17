package com.dataops.dms.service;

import java.util.Map;

/**
 * 钉钉OAuth服务
 */
public interface DingTalkOAuthService {

    /**
     * 获取钉钉授权URL
     *
     * @param state 状态参数
     * @return 授权URL
     */
    String getAuthUrl(String state);

    /**
     * 获取钉钉授权URL（支持自定义回调地址）
     *
     * @param state 状态参数
     * @param redirectUri 自定义回调地址
     * @return 授权URL
     */
    String getAuthUrl(String state, String redirectUri);

    /**
     * 通过授权码获取用户信息
     *
     * @param authCode 授权码
     * @return 用户信息（包含 unionId、userId、nickname、avatar）
     */
    Map<String, Object> getUserInfoByAuthCode(String authCode);

    /**
     * 通过 accessToken 获取用户信息
     *
     * @param accessToken 访问令牌
     * @return 用户信息
     */
    Map<String, Object> getUserInfo(String accessToken);
}