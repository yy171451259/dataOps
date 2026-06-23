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

    /**
     * 发送 ActionCard 消息给指定用户（支持点击跳转免登）
     * 钉钉内置浏览器打开 singleUrl 时会自动追加 ?code=xxx 实现免登
     *
     * @param userId     钉钉用户ID
     * @param title      消息标题
     * @param markdown   Markdown内容正文
     * @param singleTitle 按钮文字
     * @param singleUrl   点击跳转URL（钉钉会自动追加 ?code=xxx）
     * @return 是否发送成功
     */
    boolean sendActionCardMessage(String userId, String title, String markdown, String singleTitle, String singleUrl);
}