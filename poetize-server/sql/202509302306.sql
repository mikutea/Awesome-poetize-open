-- 添加移动端侧边栏配置字段
-- 用于自定义移动端侧边栏的背景、颜色等样式

ALTER TABLE `web_info` 
ADD COLUMN `mobile_drawer_config` TEXT NULL COMMENT '移动端侧边栏配置(JSON格式)' AFTER `enable_gray_mode`;

-- 设置默认配置
UPDATE `web_info` 
SET `mobile_drawer_config` = JSON_OBJECT(
  'titleType', 'text',
  'titleText', '欢迎光临',
  'avatarSize', 100,
  'backgroundType', 'image',
  'backgroundImage', '/assets/toolbar.jpg',
  'backgroundColor', '#000000',
  'backgroundGradient', 'linear-gradient(60deg, #ffd7e4, #c8f1ff 95%)',
  'maskOpacity', 0.7,
  'menuFontColor', '#ffffff',
  'showBorder', true,
  'borderColor', 'rgba(255, 255, 255, 0.15)',
  'showSnowflake', true
)
WHERE `mobile_drawer_config` IS NULL;

-- 说明：
-- titleType: 标题类型 ['text', 'avatar']
--   - text: 显示文字标题
--   - avatar: 显示博客头像
-- titleText: 标题文字内容（当titleType为text时使用）
-- avatarSize: 头像大小（当titleType为avatar时使用，单位px，范围60-150）
-- backgroundType: 背景类型 ['image', 'color', 'gradient']
--   - image: 使用背景图片
--   - color: 使用纯色背景
--   - gradient: 使用渐变色背景
-- backgroundImage: 背景图片URL（当backgroundType为image时使用）
-- backgroundColor: 纯色背景颜色（当backgroundType为color时使用）
-- backgroundGradient: 渐变色CSS（当backgroundType为gradient时使用）
-- maskOpacity: 遮罩透明度 (0-1)
-- menuFontColor: 标题和菜单字体颜色（统一管理）
-- showBorder: 是否显示边框分隔线
-- borderColor: 边框颜色
-- showSnowflake: 是否显示雪花装饰（仅在头像模式下有效）

