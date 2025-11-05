-- ============================================================
-- 添加看板娘显示模式配置字段
-- 支持切换Live2D看板娘模式和简洁按钮模式
-- ============================================================

-- 为 web_info 表添加 waifu_display_mode 字段
ALTER TABLE `web_info`
ADD COLUMN `waifu_display_mode` VARCHAR(20) DEFAULT 'live2d' COMMENT '看板娘显示模式 [live2d:Live2D看板娘, button:简洁按钮]' AFTER `enable_waifu`;

-- 更新现有记录，默认使用 live2d 模式
UPDATE `web_info` 
SET `waifu_display_mode` = 'live2d' 
WHERE `waifu_display_mode` IS NULL;

-- ============================================================
-- 字段说明
-- ============================================================
-- waifu_display_mode 可选值：
--   'live2d': 完整的Live2D动画角色（默认）
--   'button': 简洁的圆形AI聊天按钮
-- ============================================================
