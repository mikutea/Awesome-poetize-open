-- 添加动态标题设置字段
-- 执行时间：请在系统维护时间执行此脚本
-- 描述：为web_info表添加enable_dynamic_title字段，用于控制页面动态标题效果

USE `poetize`;

-- 添加动态标题开关字段（如果不存在）
SET @column_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS 
    WHERE TABLE_SCHEMA='poetize' AND TABLE_NAME='web_info' AND COLUMN_NAME='enable_dynamic_title');
SET @sql = IF(@column_exists = 0, 
    'ALTER TABLE `web_info` ADD COLUMN `enable_dynamic_title` tinyint(1) DEFAULT 1 COMMENT ''动态标题开关[0:否，1:是]'' AFTER `enable_gray_mode`', 
    'SELECT ''字段enable_dynamic_title已存在，跳过添加'' as message');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 验证字段是否添加成功
SELECT 
    COLUMN_NAME as '字段名',
    DATA_TYPE as '数据类型',
    IS_NULLABLE as '是否可空',
    COLUMN_DEFAULT as '默认值',
    COLUMN_COMMENT as '注释'
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = 'poetize' 
  AND TABLE_NAME = 'web_info' 
  AND COLUMN_NAME = 'enable_dynamic_title';
