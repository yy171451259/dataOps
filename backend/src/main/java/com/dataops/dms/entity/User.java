package com.dataops.dms.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class User extends BaseEntity {

    /**
     * 用户名
     */
    private String username;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 密码哈希
     */
    private String passwordHash;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像
     */
    private String avatar;

    /**
     * 是否启用
     */
    private Boolean isActive;

    /**
     * 是否管理员
     */
    private Boolean isAdmin;

    /**
     * 钉钉UnionID（跨应用唯一）
     */
    private String dingtalkUnionId;

    /**
     * 钉钉用户ID（内部userId，用于发送工作通知）
     */
    private String dingtalkUserId;

    /**
     * 钉钉开放ID（openId，新版API使用）
     */
    private String dingtalkOpenId;
}
