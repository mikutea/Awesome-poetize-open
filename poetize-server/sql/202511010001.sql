-- ============================================================
-- 删除文章点赞功能
-- 移除article表的like_count字段
-- ============================================================

-- 删除article表的like_count字段
ALTER TABLE `poetize`.`article` DROP COLUMN IF EXISTS `like_count`;

