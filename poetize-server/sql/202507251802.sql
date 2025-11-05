-- QQ登录配置
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
  'qq', 
  'QQ', 
  '', 
  '', 
  '', 
  'get_user_info', 
  0, 
  0, 
  6, 
  'QQ OAuth登录配置，需要在QQ互联平台中创建应用'
) 
ON DUPLICATE KEY UPDATE 
  `platform_name` = 'QQ',
  `scope` = 'get_user_info'; 