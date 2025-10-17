-- ============================================================
-- 验证码配置表
-- 将Python端的JSON配置迁移到MySQL数据库
-- ============================================================

CREATE TABLE IF NOT EXISTS `sys_captcha_config` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `enable` tinyint(1) DEFAULT 0 COMMENT '是否启用验证码 (0:否 1:是)',
  `login` tinyint(1) DEFAULT 1 COMMENT '登录时启用 (0:否 1:是)',
  `register` tinyint(1) DEFAULT 1 COMMENT '注册时启用 (0:否 1:是)',
  `comment` tinyint(1) DEFAULT 0 COMMENT '评论时启用 (0:否 1:是)',
  `reset_password` tinyint(1) DEFAULT 1 COMMENT '重置密码时启用 (0:否 1:是)',
  `screen_size_threshold` int DEFAULT 768 COMMENT '屏幕宽度阈值(px)',
  `force_slide_for_mobile` tinyint(1) DEFAULT 1 COMMENT '移动端强制滑动 (0:否 1:是)',
  `slide_accuracy` int DEFAULT 5 COMMENT '滑动验证码精确度',
  `slide_success_threshold` decimal(3,2) DEFAULT 0.95 COMMENT '滑动成功阈值',
  `checkbox_track_sensitivity` decimal(3,2) DEFAULT 0.90 COMMENT '勾选轨迹敏感度',
  `checkbox_min_track_points` int DEFAULT 2 COMMENT '勾选最少轨迹点数',
  `checkbox_reply_sensitivity` decimal(3,2) DEFAULT 0.85 COMMENT '回复评论敏感度',
  `checkbox_max_retry_count` int DEFAULT 5 COMMENT '最大重试次数',
  `checkbox_retry_decrement` decimal(3,2) DEFAULT 0.02 COMMENT '重试降低敏感度',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注说明',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=Aria DEFAULT CHARSET=utf8mb4 COMMENT='验证码配置表';

-- 插入默认配置
INSERT INTO `sys_captcha_config` (
  `enable`, `login`, `register`, `comment`, `reset_password`,
  `screen_size_threshold`, `force_slide_for_mobile`,
  `slide_accuracy`, `slide_success_threshold`,
  `checkbox_track_sensitivity`, `checkbox_min_track_points`,
  `checkbox_reply_sensitivity`, `checkbox_max_retry_count`, `checkbox_retry_decrement`
) VALUES (
  0, 1, 1, 0, 1,
  768, 1,
  5, 0.95,
  0.90, 2, 0.85, 5, 0.02
) ON DUPLICATE KEY UPDATE id=id;

