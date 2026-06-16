package com.dataops.dms.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("notification_config")
public class NotificationConfig extends BaseEntity {
    private String name;
    private String channel;
    private String config;
    private Boolean isEnabled;
    private String triggerEvents;
}
