-- 为评论表添加IP地址和地理位置字段
-- 执行时间：请在系统维护时间执行此脚本

USE `poetize`;

-- 添加IP地址字段（如果不存在）
SET @column_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA='poetize' AND TABLE_NAME='comment' AND COLUMN_NAME='ip_address');
SET @sql = IF(@column_exists = 0, 
    'ALTER TABLE `comment` ADD COLUMN `ip_address` varchar(45) DEFAULT NULL COMMENT ''IP地址'' AFTER `comment_info`', 
    'SELECT ''Column ip_address already exists'' as message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加地理位置字段（如果不存在）
SET @column_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA='poetize' AND TABLE_NAME='comment' AND COLUMN_NAME='location');
SET @sql = IF(@column_exists = 0, 
    'ALTER TABLE `comment` ADD COLUMN `location` varchar(100) DEFAULT NULL COMMENT ''地理位置'' AFTER `ip_address`', 
    'SELECT ''Column location already exists'' as message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 验证字段是否添加成功
DESCRIBE `comment`;

-- 可选：为现有评论添加默认位置信息（如果需要）
-- UPDATE `comment` SET `location` = '未知' WHERE `location` IS NULL;

-- 创建索引以提高查询性能（可选）
-- CREATE INDEX idx_comment_location ON `comment`(`location`);
-- CREATE INDEX idx_comment_ip ON `comment`(`ip_address`);
