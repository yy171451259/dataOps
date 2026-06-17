package com.dataops.dms.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 工单编号生成器
 * 格式：年月日时分秒（14位）+ 毫秒（3位）= 17位纯数字
 * 例如：20260617112542001
 */
public class TicketIdGenerator {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 生成工单编号
     * @return 17位纯数字的工单编号
     */
    public static String generate() {
        return LocalDateTime.now().format(FORMATTER) + String.format("%03d", System.currentTimeMillis() % 1000);
    }
}