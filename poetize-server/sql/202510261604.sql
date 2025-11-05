-- ============================================================
-- 添加翻译文章的文章摘要字段
-- ============================================================
ALTER TABLE `article_translation`
ADD COLUMN `summary` TEXT NULL COMMENT '文章摘要' AFTER `content`;
