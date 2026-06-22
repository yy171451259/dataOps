package com.dataops.dms.service;

import com.dataops.dms.service.impl.DingTalkQrSession;

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

    /**
     * 通过 unionId 获取钉钉 userId（用于发送工作通知）
     *
     * @param unionId 钉钉 unionId
     * @return 钉钉 userId，获取失败返回 null
     */
    String getUserIdByUnionId(String unionId);

    /**
     * 创建扫码登录会话
     *
     * @return 会话ID
     */
    String createQrSession();

    /**
     * 获取扫码登录会话状态
     *
     * @param sessionId 会话ID
     * @return 会话对象，可能为null（过期或不存在）
     */
    DingTalkQrSession getQrSession(String sessionId);

    /**
     * 更新会话状态
     *
     * @param sessionId 会话ID
     * @param status 新状态
     */
    void updateQrSessionStatus(String sessionId, String status);

    /**
     * 完成会话登录
     *
     * @param sessionId 会话ID
     * @param token JWT令牌
     * @param loginResult 登录结果
     */
    void completeQrSession(String sessionId, String token, Map<String, Object> loginResult);
}