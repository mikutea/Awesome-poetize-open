-- =============================================
-- 导航栏配置完整迁移脚本
-- 功能：
-- 1. 将"百宝箱"替换为：友人帐、曲乐、收藏夹
-- 2. 将"记录"更新为"分类"（📒 → 📑）
-- 创建时间：2025-10-07
-- =============================================

-- 更新包含"百宝箱"的配置（直接更新为最新版本）
UPDATE web_info 
SET nav_config = '[
  {"name":"首页","icon":"🏡","link":"/","type":"internal","order":1,"enabled":true},
  {"name":"分类","icon":"📑","link":"#","type":"dropdown","order":2,"enabled":true},
  {"name":"家","icon":"❤️‍🔥","link":"/love","type":"internal","order":3,"enabled":true},
  {"name":"友人帐","icon":"🤝","link":"/friends","type":"internal","order":4,"enabled":true},
  {"name":"曲乐","icon":"🎵","link":"/music","type":"internal","order":5,"enabled":true},
  {"name":"收藏夹","icon":"📁","link":"/favorites","type":"internal","order":6,"enabled":true},
  {"name":"留言","icon":"📪","link":"/message","type":"internal","order":7,"enabled":true},
  {"name":"联系我","icon":"💬","link":"#chat","type":"special","order":8,"enabled":true}
]'
WHERE nav_config IS NOT NULL 
  AND nav_config != '' 
  AND nav_config != '[]'
  AND nav_config LIKE '%百宝箱%';

-- 更新包含"记录"的配置（改为"分类"）
UPDATE web_info 
SET nav_config = REPLACE(
    REPLACE(
        nav_config,
        '"name":"记录"',
        '"name":"分类"'
    ),
    '"icon":"📒"',
    '"icon":"📑"'
)
WHERE nav_config LIKE '%"name":"记录"%'
   OR nav_config LIKE '%"icon":"📒"%';

-- 对于空的导航配置，设置为最新默认配置
UPDATE web_info 
SET nav_config = '[
  {"name":"首页","icon":"🏡","link":"/","type":"internal","order":1,"enabled":true},
  {"name":"分类","icon":"📑","link":"#","type":"dropdown","order":2,"enabled":true},
  {"name":"家","icon":"❤️‍🔥","link":"/love","type":"internal","order":3,"enabled":true},
  {"name":"友人帐","icon":"🤝","link":"/friends","type":"internal","order":4,"enabled":true},
  {"name":"曲乐","icon":"🎵","link":"/music","type":"internal","order":5,"enabled":true},
  {"name":"收藏夹","icon":"📁","link":"/favorites","type":"internal","order":6,"enabled":true},
  {"name":"留言","icon":"📪","link":"/message","type":"internal","order":7,"enabled":true},
  {"name":"联系我","icon":"💬","link":"#chat","type":"special","order":8,"enabled":true}
]'
WHERE nav_config IS NULL 
  OR nav_config = '' 
  OR nav_config = '[]';
