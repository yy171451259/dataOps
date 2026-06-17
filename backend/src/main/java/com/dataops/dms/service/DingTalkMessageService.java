package com.dataops.dms.service;

/**
 * 钉钉消息服务
 */
public interface DingTalkMessageService {

    /**
     * 发送文本消息给指定用户
     *
     * @param userId 钉钉用户ID
     * @param content 消息内容
     * @return 是否发送成功
     */
    boolean sendTextMessage(String userId, String content);

    /**
     * 发送Markdown消息给指定用户
     *
     * @param userId 钉钉用户ID
     * @param title 消息标题
     * @param content Markdown内容
     * @return 是否发送成功
     */
    boolean sendMarkdownMessage(String userId, String title, String content);

    /**
     * 发送卡片消息给指定用户
     *
     * @param userId 钉钉用户ID
     * @param card 卡片JSON
     * @return 是否发送成功
     */
    boolean sendCardMessage(String userId, String card);
}