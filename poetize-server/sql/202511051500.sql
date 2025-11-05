-- 修复 sys_config 表重复键问题
-- 日期: 2025-11-05 15:00

-- 第一步：先清理已有的重复数据
-- 保留每个 config_key 的最早记录（ID最小的），删除后来重复插入的记录
DELETE t1 FROM sys_config t1
INNER JOIN sys_config t2
WHERE t1.config_key = t2.config_key
  AND t1.config_type = t2.config_type
  AND t1.id > t2.id;

-- 第二步：为 config_key 和 config_type 组合添加唯一索引
-- 同一类型（公开/私有）下的 config_key 不允许重复
ALTER TABLE `sys_config` 
ADD UNIQUE INDEX `uk_config_key_type` (`config_key`, `config_type`) 
COMMENT '配置键和类型联合唯一索引';

-- 说明：
-- 1. 删除语句会保留ID最小（最早）的记录，删除后来重复插入的记录
-- 2. 唯一索引确保同一config_type下的config_key不会重复
-- 3. 不同config_type可以有相同的config_key（例如：公开配置和私有配置可以有同名的key）

