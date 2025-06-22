-- 为article表添加summary字段的迁移脚本
-- 执行前请备份数据库！

-- 1. 添加summary字段
ALTER TABLE `article` ADD COLUMN `summary` varchar(500) DEFAULT NULL COMMENT '文章摘要' AFTER `article_content`;

-- 2. 为现有文章生成简单摘要（从文章内容前100个字符生成）
-- 注意：这只是临时的简单摘要，建议后续使用Java代码重新生成智能摘要
UPDATE `article` 
SET `summary` = CASE 
    WHEN LENGTH(`article_content`) > 100 THEN 
        CONCAT(
            TRIM(
                REPLACE(
                    REPLACE(
                        REPLACE(
                            REPLACE(
                                REPLACE(SUBSTRING(`article_content`, 1, 100), '#', ''),
                                '*', ''
                            ),
                            '`', ''
                        ),
                        '>', ''
                    ),
                    '\n', ' '
                )
            ),
            '...'
        )
    ELSE 
        TRIM(
            REPLACE(
                REPLACE(
                    REPLACE(
                        REPLACE(
                            REPLACE(`article_content`, '#', ''),
                            '*', ''
                        ),
                        '`', ''
                    ),
                    '>', ''
                ),
                '\n', ' '
            )
        )
END
WHERE `summary` IS NULL AND `article_content` IS NOT NULL AND `article_content` != '';

-- 3. 验证更新结果
SELECT 
    id, 
    article_title, 
    SUBSTRING(article_content, 1, 50) as content_preview,
    summary,
    LENGTH(summary) as summary_length
FROM `article` 
WHERE `summary` IS NOT NULL 
LIMIT 10;

-- 4. 统计信息
SELECT 
    COUNT(*) as total_articles,
    COUNT(summary) as articles_with_summary,
    COUNT(*) - COUNT(summary) as articles_without_summary
FROM `article`; 