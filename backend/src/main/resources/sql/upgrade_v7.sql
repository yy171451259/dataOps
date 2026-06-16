-- =============================================
-- 升级脚本 V7：统一数据脱敏方案
-- 1. sys_sensitive_mask_rule 增加算法字段
-- 2. 给预置规则补充算法映射
-- =============================================

-- 增加算法字段
ALTER TABLE `sys_sensitive_mask_rule` 
ADD COLUMN `mask_algorithm` VARCHAR(64) COMMENT 'Java算法: PHONE/EMAIL/ID_CARD/BANK_CARD/FULL_MASK/NAME_MASK/CUSTOM' AFTER `mask_type`;

-- 更新预置规则的算法映射
UPDATE `sys_sensitive_mask_rule` SET `mask_algorithm` = 'PHONE'     WHERE `code` = 'phone';
UPDATE `sys_sensitive_mask_rule` SET `mask_algorithm` = 'EMAIL'     WHERE `code` = 'email';
UPDATE `sys_sensitive_mask_rule` SET `mask_algorithm` = 'ID_CARD'   WHERE `code` = 'idcard';
UPDATE `sys_sensitive_mask_rule` SET `mask_algorithm` = 'BANK_CARD' WHERE `code` = 'bankcard';
UPDATE `sys_sensitive_mask_rule` SET `mask_algorithm` = 'FULL_MASK' WHERE `code` = 'full';
UPDATE `sys_sensitive_mask_rule` SET `mask_algorithm` = 'NAME_MASK' WHERE `code` = 'name';
