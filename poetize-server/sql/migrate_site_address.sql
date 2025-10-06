-- ================================================================
-- 网站地址字段迁移：从 seo_config 表迁移到 web_info 表
-- 执行时间：2025-10-06
-- 说明：统一在"网站设置"中管理网站地址，而不是分散在SEO配置中
-- 本脚本可以安全重复执行（幂等性）
-- ================================================================

-- 步骤1：为 web_info 表添加 site_address 字段（如果不存在）
SET @dbname = DATABASE();
SET @tablename = 'web_info';
SET @columnname = 'site_address';
SET @preparedStatement = (SELECT IF(
  (
    SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
    WHERE 
      TABLE_SCHEMA = @dbname
      AND TABLE_NAME = @tablename
      AND COLUMN_NAME = @columnname
  ) > 0,
  'SELECT 1', -- 字段已存在，不执行任何操作
  CONCAT('ALTER TABLE `', @tablename, '` ADD COLUMN `site_address` VARCHAR(255) NULL DEFAULT NULL COMMENT ''网站地址（完整URL）'' AFTER `web_title`')
));
PREPARE alterIfNotExists FROM @preparedStatement;
EXECUTE alterIfNotExists;
DEALLOCATE PREPARE alterIfNotExists;

-- 步骤2：数据迁移（如果 seo_config 表中有 site_address 字段且有数据，迁移到 web_info 表）
-- 检查 seo_config 表是否有 site_address 字段
SET @seo_column_exists = (SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS
  WHERE TABLE_SCHEMA = @dbname
    AND TABLE_NAME = 'seo_config'
    AND COLUMN_NAME = 'site_address');

-- 如果字段存在，执行数据迁移
SET @migrateStatement = IF(@seo_column_exists > 0,
  'UPDATE `web_info` w INNER JOIN `seo_config` s ON s.id = 1 SET w.`site_address` = s.`site_address` WHERE s.`site_address` IS NOT NULL AND s.`site_address` != '''' AND (w.`site_address` IS NULL OR w.`site_address` = '''')',
  'SELECT 1'  -- seo_config 表没有 site_address 字段，跳过迁移
);
PREPARE migrateIfExists FROM @migrateStatement;
EXECUTE migrateIfExists;
DEALLOCATE PREPARE migrateIfExists;

-- 步骤3：从 seo_config 表删除 site_address 字段（如果存在）
SET @dropStatement = IF(@seo_column_exists > 0,
  'ALTER TABLE `seo_config` DROP COLUMN `site_address`',
  'SELECT 1'  -- 字段不存在，不执行任何操作
);
PREPARE dropIfExists FROM @dropStatement;
EXECUTE dropIfExists;
DEALLOCATE PREPARE dropIfExists;

-- 步骤4：清空 resource_path 表中 siteInfo 类型记录的 url 字段
UPDATE `resource_path`
SET `url` = NULL
WHERE `type` = 'siteInfo'
  AND `url` IS NOT NULL;

-- 执行完成后，请重启后端服务以应用更改
-- 本脚本可以安全重复执行，不会报错

