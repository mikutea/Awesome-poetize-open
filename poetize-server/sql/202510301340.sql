-- ============================================================
-- 添加评论开关配置
-- 用于全局控制文章评论区的显示/隐藏
-- ============================================================

-- 先删除可能存在的重复记录（保留ID最小的）
DELETE t1 FROM `sys_config` t1
INNER JOIN `sys_config` t2
WHERE t1.config_key = 'enableComment'
  AND t2.config_key = 'enableComment'
  AND t1.config_type = t2.config_type
  AND t1.id > t2.id;

-- 使用安全的 INSERT ... SELECT 方式，只在不存在时插入
INSERT INTO `sys_config` (`config_name`, `config_key`, `config_value`, `config_type`)
SELECT '全局评论开关', 'enableComment', 'true', '2'
FROM DUAL
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_config` 
    WHERE `config_key` = 'enableComment' 
    AND `config_type` = '2'
);

-- 如果记录已存在，更新其值（确保配置正确）
UPDATE `sys_config` 
SET `config_name` = '全局评论开关',
    `config_value` = 'true'
WHERE `config_key` = 'enableComment' AND `config_type` = '2';

