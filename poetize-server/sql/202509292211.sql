-- Baidu登录配置
INSERT INTO `poetize`.`third_party_oauth_config` 
(
  `platform_type`, 
  `platform_name`, 
  `client_id`, 
  `client_secret`, 
  `redirect_uri`, 
  `scope`, 
  `enabled`, 
  `global_enabled`, 
  `sort_order`, 
  `remark`
) 
VALUES 
(
  'baidu',
  'Baidu',
  '',
  '',
  '',
  'basic',
  0,
  0,
  7,
  'Baidu OAuth登录配置，需要在百度账号中心创建应用'
)
ON DUPLICATE KEY UPDATE 
  `platform_name` = 'Baidu',
  `scope` = 'basic',
  `sort_order` = 7,
  `remark` = 'Baidu OAuth登录配置，需要在百度账号中心创建应用';
