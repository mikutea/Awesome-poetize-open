package com.ld.poetry.service;

import java.util.Map;

/**
 * OAuth客户端服务接口
 */
public interface OAuthClientService {
    
    /**
     * 构建OAuth授权URL
     * @param platformType 平台类型
     * @param state 状态参数
     * @return 授权URL
     */
    String buildAuthUrl(String platformType, String state);
    
    /**
     * 使用授权码获取访问令牌
     * @param platformType 平台类型
     * @param code 授权码
     * @return 访问令牌信息
     */
    Map<String, Object> getAccessToken(String platformType, String code);
    
    /**
     * 使用访问令牌获取用户信息
     * @param platformType 平台类型
     * @param accessToken 访问令牌
     * @return 用户信息
     */
    Map<String, Object> getUserInfo(String platformType, String accessToken);
    
    /**
     * 检查平台配置是否完整
     * @param platformType 平台类型
     * @return 是否配置完整
     */
    boolean isPlatformConfigured(String platformType);
}
