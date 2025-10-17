-- ============================================================
-- 邮件配置表迁移脚本
-- 将Python端的JSON配置迁移到MySQL数据库
-- ============================================================

-- 创建邮件配置表
CREATE TABLE IF NOT EXISTS `sys_mail_config` (
  `id` int(11) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `config_name` varchar(100) NOT NULL COMMENT '配置名称',
  `host` varchar(255) NOT NULL COMMENT '邮箱服务器地址',
  `port` int(11) NOT NULL DEFAULT 25 COMMENT '邮箱服务器端口',
  `username` varchar(255) NOT NULL COMMENT '邮箱账号',
  `password` varchar(500) DEFAULT NULL COMMENT '邮箱密码或授权码（加密存储）',
  `sender_name` varchar(100) DEFAULT NULL COMMENT '发件人名称',
  `use_ssl` tinyint(1) DEFAULT 0 COMMENT '是否启用SSL (0:否 1:是)',
  `use_starttls` tinyint(1) DEFAULT 0 COMMENT '是否启用STARTTLS (0:否 1:是)',
  `auth` tinyint(1) DEFAULT 1 COMMENT '是否需要认证 (0:否 1:是)',
  `enabled` tinyint(1) DEFAULT 1 COMMENT '是否启用 (0:禁用 1:启用)',
  `is_default` tinyint(1) DEFAULT 0 COMMENT '是否为默认配置 (0:否 1:是)',
  `connection_timeout` int(11) DEFAULT 25000 COMMENT '连接超时(毫秒)',
  `timeout` int(11) DEFAULT 25000 COMMENT '读取超时(毫秒)',
  `trust_all_certs` tinyint(1) DEFAULT 0 COMMENT '是否信任所有证书 (0:否 1:是)',
  `protocol` varchar(50) DEFAULT 'smtp' COMMENT '协议(smtp, smtps等)',
  `auth_mechanism` varchar(50) DEFAULT NULL COMMENT '认证机制(LOGIN, PLAIN, DIGEST-MD5等)',
  `debug` tinyint(1) DEFAULT 0 COMMENT '是否启用调试模式 (0:否 1:是)',
  `use_proxy` tinyint(1) DEFAULT 0 COMMENT '是否使用代理 (0:否 1:是)',
  `proxy_host` varchar(255) DEFAULT NULL COMMENT '代理服务器地址',
  `proxy_port` int(11) DEFAULT NULL COMMENT '代理服务器端口',
  `proxy_user` varchar(100) DEFAULT NULL COMMENT '代理服务器用户名',
  `proxy_password` varchar(500) DEFAULT NULL COMMENT '代理服务器密码',
  `custom_properties` text DEFAULT NULL COMMENT '自定义属性(JSON格式)',
  `sort_order` int(11) DEFAULT 0 COMMENT '排序顺序',
  `remark` varchar(500) DEFAULT NULL COMMENT '备注说明',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_enabled` (`enabled`),
  KEY `idx_is_default` (`is_default`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='邮件配置表';
