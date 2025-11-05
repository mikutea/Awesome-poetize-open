-- 聊天最后查看时间表（私聊+群聊）- 用于未读消息和聊天列表同步
-- 创建时间: 2025-10-09
-- 说明: 支持私聊和群聊的未读消息追踪，以及多端聊天列表同步

CREATE TABLE IF NOT EXISTS `poetize`.`im_chat_last_read` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT 'id',
  `user_id` int NOT NULL COMMENT '用户ID',
  `chat_type` tinyint NOT NULL COMMENT '聊天类型[1:私聊，2:群聊]',
  `chat_id` int NOT NULL COMMENT '聊天ID（私聊为friendId，群聊为groupId）',
  `last_read_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后查看时间',
  `is_hidden` tinyint NOT NULL DEFAULT 0 COMMENT '是否隐藏[0:否，1:是]',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_chat` (`user_id`, `chat_type`, `chat_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_chat` (`chat_type`, `chat_id`),
  KEY `idx_hidden` (`user_id`, `is_hidden`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天最后查看时间（私聊+群聊）';

