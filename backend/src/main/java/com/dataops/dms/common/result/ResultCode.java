package com.dataops.dms.common.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 返回状态码枚举
 */
@Getter
@AllArgsConstructor
public enum ResultCode {
    
    SUCCESS(200, "操作成功"),
    ERROR(500, "服务器内部错误"),
    
    // 用户相关
    USER_NOT_FOUND(1001, "用户不存在"),
    USER_PASSWORD_ERROR(1002, "密码错误"),
    USER_ACCOUNT_DISABLED(1003, "账号已被禁用"),
    USER_NOT_LOGIN(1004, "用户未登录"),
    USER_TOKEN_INVALID(1005, "Token无效或已过期"),
    USER_PERMISSION_DENIED(1006, "权限不足"),
    USER_USERNAME_EXISTS(1007, "用户名已存在"),
    USER_EMAIL_EXISTS(1008, "邮箱已被注册"),
    
    // 数据库相关
    DATABASE_NOT_FOUND(2001, "数据库实例不存在"),
    DATABASE_CONNECTION_FAILED(2002, "数据库连接失败"),
    DATABASE_EXECUTE_FAILED(2003, "SQL执行失败"),
    DATABASE_NOT_SUPPORTED(2004, "不支持的数据库类型"),
    
    // 工单相关
    TICKET_NOT_FOUND(3001, "工单不存在"),
    TICKET_STATUS_ERROR(3002, "工单状态错误"),
    TICKET_NOT_APPROVER(3003, "您不是当前审批人"),
    
    // 参数相关
    PARAM_ERROR(4000, "参数错误"),
    PARAM_BLANK(4001, "参数不能为空"),
    PARAM_INVALID(4002, "参数格式不正确");
    
    private final Integer code;
    private final String message;
}
