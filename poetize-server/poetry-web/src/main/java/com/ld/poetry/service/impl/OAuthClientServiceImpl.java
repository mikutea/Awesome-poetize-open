package com.ld.poetry.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ld.poetry.entity.ThirdPartyOauthConfig;
import com.ld.poetry.service.OAuthClientService;
import com.ld.poetry.service.ThirdPartyOauthConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * OAuth客户端服务实现
 */
@Slf4j
@Service
public class OAuthClientServiceImpl implements OAuthClientService {
    
    @Autowired
    private ThirdPartyOauthConfigService configService;
    
    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("oauthRestTemplate")
    private RestTemplate restTemplate;
    
    @Override
    public String buildAuthUrl(String platformType, String state) {
        try {
            ThirdPartyOauthConfig config = configService.getByPlatformType(platformType);
            if (config == null || !config.getEnabled() || !config.getGlobalEnabled()) {
                throw new RuntimeException("平台未配置或未启用: " + platformType);
            }
            
            String authUrl = getAuthUrl(platformType);
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(authUrl);
            
            // 添加通用参数
            if ("twitter".equals(platformType)) {
                builder.queryParam("client_id", config.getClientKey());
            } else {
                builder.queryParam("client_id", config.getClientId());
            }
            
            builder.queryParam("redirect_uri", config.getRedirectUri())
                   .queryParam("state", state)
                   .queryParam("response_type", "code");
            
            // 添加平台特定参数
            addPlatformSpecificParams(platformType, builder);
            
            String finalUrl = builder.build().toUriString();
            log.info("构建OAuth授权URL: platformType={}, url={}", platformType, finalUrl);
            
            return finalUrl;
        } catch (Exception e) {
            log.error("构建OAuth授权URL失败: platformType={}", platformType, e);
            throw new RuntimeException("构建授权URL失败", e);
        }
    }
    
    @Override
    public Map<String, Object> getAccessToken(String platformType, String code) {
        String tokenUrl = null;
        long startTime = System.currentTimeMillis();

        // 重试配置
        int maxRetries = 2;
        int retryDelay = 1000; // 1秒

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.info("🕐 开始获取访问令牌: platformType={}, attempt={}/{}, timestamp={}",
                        platformType, attempt, maxRetries, System.currentTimeMillis());

                ThirdPartyOauthConfig config = configService.getByPlatformType(platformType);
                if (config == null) {
                    throw new RuntimeException("平台配置不存在: " + platformType);
                }

                tokenUrl = getTokenUrl(platformType);
            
            // 构建请求参数
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("grant_type", "authorization_code");
            params.add("code", code);
            params.add("redirect_uri", config.getRedirectUri());
            
            if ("twitter".equals(platformType)) {
                params.add("client_id", config.getClientKey());
            } else {
                params.add("client_id", config.getClientId());
            }
            params.add("client_secret", config.getClientSecret());
            
            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.add("Accept", "application/json");
            
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);
            
            // 发送请求 - 添加详细的请求参数日志
            log.info("🚀 发送OAuth token请求详情:");
            log.info("   - URL: {}", tokenUrl);
            log.info("   - Platform: {}", platformType);
            log.info("   - Grant Type: {}", params.getFirst("grant_type"));
            log.info("   - Client ID: {}", params.getFirst("client_id"));
            log.info("   - Client Secret: {}***",
                    params.getFirst("client_secret") != null ?
                    params.getFirst("client_secret").substring(0, Math.min(8, params.getFirst("client_secret").length())) : "null");
            log.info("   - Redirect URI: {}", params.getFirst("redirect_uri"));
            log.info("   - Code Length: {}", code != null ? code.length() : 0);
            log.info("   - Code: {}***", code != null ? code.substring(0, Math.min(8, code.length())) : "null");
            log.info("   - Headers: {}", headers);

            ResponseEntity<String> response = restTemplate.postForEntity(tokenUrl, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                String responseBody = response.getBody();
                log.info("OAuth token响应原始数据: platformType={}, responseBody={}", platformType, responseBody);

                Map<String, Object> tokenData;

                // 尝试解析JSON格式
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> jsonData = objectMapper.readValue(responseBody, Map.class);
                    tokenData = jsonData;
                    log.info("OAuth token解析为JSON成功: platformType={}, tokenData={}", platformType, tokenData);
                } catch (Exception jsonException) {
                    log.info("OAuth token不是JSON格式，尝试解析URL编码格式: platformType={}", platformType);

                    // 尝试解析URL编码格式（GitHub可能返回这种格式）
                    tokenData = parseUrlEncodedResponse(responseBody);
                    log.info("OAuth token解析为URL编码成功: platformType={}, tokenData={}", platformType, tokenData);
                }

                // 检查响应中是否包含错误信息
                if (tokenData.containsKey("error")) {
                    String error = (String) tokenData.get("error");
                    String errorDescription = (String) tokenData.get("error_description");
                    String errorUri = (String) tokenData.get("error_uri");

                    log.error("❌ OAuth token请求返回错误: platformType={}, error={}, description={}, uri={}",
                             platformType, error, errorDescription, errorUri);

                    // 特别处理授权码相关错误
                    if ("bad_verification_code".equals(error)) {
                        log.error("🔍 授权码错误详情: code长度={}, error_description={}",
                                code != null ? code.length() : 0, errorDescription);
                        log.error("🔍 这可能是因为: 1)授权码已被使用过 2)授权码已过期 3)授权码格式错误");
                    }

                    // 根据错误类型提供更友好的错误信息
                    String userFriendlyMessage = getOAuthErrorMessage(error, errorDescription);
                    throw new RuntimeException(userFriendlyMessage);
                }

                // 检查是否包含access_token
                String accessToken = (String) tokenData.get("access_token");
                if (accessToken == null || accessToken.trim().isEmpty()) {
                    log.error("OAuth token响应中缺少access_token: platformType={}, tokenData={}", platformType, tokenData);
                    throw new RuntimeException("OAuth服务器响应格式错误，缺少访问令牌");
                }

                long endTime = System.currentTimeMillis();
                long elapsedTime = endTime - startTime;
                log.info("🕐 获取访问令牌成功: platformType={}, attempt={}, 耗时={}ms, timestamp={}",
                        platformType, attempt, elapsedTime, endTime);
                return tokenData;
            } else {
                log.error("获取访问令牌HTTP错误: platformType={}, attempt={}, statusCode={}, responseBody={}",
                         platformType, attempt, response.getStatusCode(), response.getBody());
                throw new RuntimeException("获取访问令牌失败: HTTP " + response.getStatusCode());
            }
        } catch (ResourceAccessException e) {
            // 网络连接异常（超时、连接被拒绝等）
            log.error("OAuth token请求网络连接失败: platformType={}, attempt={}, url={}, error={}",
                     platformType, attempt, tokenUrl, e.getMessage());

            if (attempt < maxRetries) {
                log.info("⏳ 网络连接失败，{}ms后进行第{}次重试", retryDelay, attempt + 1);
                try {
                    Thread.sleep(retryDelay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("重试被中断", ie);
                }
                continue; // 重试
            } else {
                throw new RuntimeException("网络连接失败，已重试" + maxRetries + "次: " + e.getMessage(), e);
            }
        } catch (RestClientException e) {
            // 其他REST客户端异常
            log.error("OAuth token请求客户端错误: platformType={}, attempt={}, url={}, error={}",
                     platformType, attempt, tokenUrl, e.getMessage());
            throw new RuntimeException("请求失败: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("获取访问令牌失败: platformType={}, attempt={}, code={}, url={}",
                     platformType, attempt, code, tokenUrl, e);
            throw new RuntimeException("获取访问令牌失败: " + e.getMessage(), e);
        }
        }

        // 如果所有重试都失败了（理论上不会到达这里）
        throw new RuntimeException("获取访问令牌失败: 已重试" + maxRetries + "次");
    }

    /**
     * 解析URL编码格式的响应（如GitHub OAuth返回的格式）
     */
    private Map<String, Object> parseUrlEncodedResponse(String responseBody) {
        Map<String, Object> result = new HashMap<>();

        if (responseBody == null || responseBody.trim().isEmpty()) {
            return result;
        }

        try {
            String[] pairs = responseBody.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2) {
                    String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                    String value = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                    result.put(key, value);
                }
            }
        } catch (Exception e) {
            log.error("解析URL编码响应失败: responseBody={}", responseBody, e);
        }

        return result;
    }

    /**
     * 根据OAuth错误类型返回用户友好的错误信息
     */
    private String getOAuthErrorMessage(String error, String errorDescription) {
        if (error == null) {
            return "OAuth授权失败，请重新尝试";
        }

        switch (error) {
            case "bad_verification_code":
                // 授权码过期是最常见的问题，提供更详细的说明
                return "授权码已过期或无效。OAuth授权码通常在10分钟内有效，请重新进行授权";
            case "incorrect_client_credentials":
                return "OAuth客户端配置错误，请联系管理员";
            case "redirect_uri_mismatch":
                return "回调地址配置错误，请联系管理员";
            case "access_denied":
                return "用户拒绝了授权请求";
            case "unsupported_grant_type":
                return "不支持的授权类型";
            case "invalid_scope":
                return "请求的权限范围无效";
            case "server_error":
                return "OAuth服务器内部错误，请稍后重试";
            case "temporarily_unavailable":
                return "OAuth服务暂时不可用，请稍后重试";
            case "invalid_grant":
                return "授权码无效或已被使用，请重新进行授权";
            default:
                // 如果有详细描述，使用描述；否则使用通用错误信息
                if (errorDescription != null && !errorDescription.trim().isEmpty()) {
                    return "OAuth授权失败：" + errorDescription;
                } else {
                    return "OAuth授权失败：" + error;
                }
        }
    }

    @Override
    public Map<String, Object> getUserInfo(String platformType, String accessToken) {
        String userInfoUrl = null;
        try {
            userInfoUrl = getUserInfoUrl(platformType);

            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.add("Authorization", "Bearer " + accessToken);
            headers.add("Accept", "application/json");

            HttpEntity<String> request = new HttpEntity<>(headers);

            // 发送请求
            log.info("发送OAuth用户信息请求: url={}, platformType={}", userInfoUrl, platformType);
            ResponseEntity<String> response = restTemplate.exchange(userInfoUrl, HttpMethod.GET, request, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
                @SuppressWarnings("unchecked")
                Map<String, Object> rawUserInfo = objectMapper.readValue(response.getBody(), Map.class);

                // 标准化用户信息
                Map<String, Object> userInfo = normalizeUserInfo(platformType, rawUserInfo);
                log.info("获取用户信息成功: platformType={}, uid={}", platformType, userInfo.get("uid"));
                return userInfo;
            } else {
                log.error("获取用户信息HTTP错误: platformType={}, statusCode={}, responseBody={}",
                         platformType, response.getStatusCode(), response.getBody());
                throw new RuntimeException("获取用户信息失败: HTTP " + response.getStatusCode());
            }
        } catch (ResourceAccessException e) {
            // 网络连接异常（超时、连接被拒绝等）
            log.error("OAuth用户信息请求网络连接失败: platformType={}, url={}, error={}",
                     platformType, userInfoUrl, e.getMessage(), e);
            throw new RuntimeException("网络连接失败，请检查网络设置或稍后重试: " + e.getMessage(), e);
        } catch (RestClientException e) {
            // 其他REST客户端异常
            log.error("OAuth用户信息请求客户端错误: platformType={}, url={}, error={}",
                     platformType, userInfoUrl, e.getMessage(), e);
            throw new RuntimeException("请求失败: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("获取用户信息失败: platformType={}, url={}", platformType, userInfoUrl, e);
            throw new RuntimeException("获取用户信息失败: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean isPlatformConfigured(String platformType) {
        try {
            ThirdPartyOauthConfig config = configService.getByPlatformType(platformType);
            if (config == null || !config.getEnabled() || !config.getGlobalEnabled()) {
                return false;
            }
            
            // 检查必要的配置项
            if ("twitter".equals(platformType)) {
                return StringUtils.hasText(config.getClientKey()) && 
                       StringUtils.hasText(config.getClientSecret()) &&
                       StringUtils.hasText(config.getRedirectUri());
            } else {
                return StringUtils.hasText(config.getClientId()) && 
                       StringUtils.hasText(config.getClientSecret()) &&
                       StringUtils.hasText(config.getRedirectUri());
            }
        } catch (Exception e) {
            log.error("检查平台配置失败: platformType={}", platformType, e);
            return false;
        }
    }
    
    // 私有辅助方法
    private String getAuthUrl(String platformType) {
        switch (platformType.toLowerCase()) {
            case "github":
                return "https://github.com/login/oauth/authorize";
            case "google":
                return "https://accounts.google.com/o/oauth2/v2/auth";
            case "twitter":
            case "x":
                return "https://twitter.com/i/oauth2/authorize";
            case "yandex":
                return "https://oauth.yandex.com/authorize";
            case "gitee":
                return "https://gitee.com/oauth/authorize";
            default:
                throw new RuntimeException("不支持的平台: " + platformType);
        }
    }
    
    private String getTokenUrl(String platformType) {
        switch (platformType.toLowerCase()) {
            case "github":
                return "https://github.com/login/oauth/access_token";
            case "google":
                return "https://oauth2.googleapis.com/token";
            case "twitter":
            case "x":
                return "https://api.twitter.com/2/oauth2/token";
            case "yandex":
                return "https://oauth.yandex.com/token";
            case "gitee":
                return "https://gitee.com/oauth/token";
            default:
                throw new RuntimeException("不支持的平台: " + platformType);
        }
    }
    
    private String getUserInfoUrl(String platformType) {
        switch (platformType.toLowerCase()) {
            case "github":
                return "https://api.github.com/user";
            case "google":
                return "https://www.googleapis.com/oauth2/v2/userinfo";
            case "twitter":
            case "x":
                return "https://api.twitter.com/2/users/me";
            case "yandex":
                return "https://login.yandex.ru/info";
            case "gitee":
                return "https://gitee.com/api/v5/user";
            default:
                throw new RuntimeException("不支持的平台: " + platformType);
        }
    }
    
    private void addPlatformSpecificParams(String platformType, UriComponentsBuilder builder) {
        switch (platformType.toLowerCase()) {
            case "github":
                builder.queryParam("scope", "user:email");
                break;
            case "google":
                builder.queryParam("scope", "openid email profile");
                builder.queryParam("access_type", "offline");
                break;
            case "twitter":
            case "x":
                builder.queryParam("scope", "tweet.read users.read");
                builder.queryParam("code_challenge", "challenge");
                builder.queryParam("code_challenge_method", "plain");
                break;
            case "yandex":
                builder.queryParam("scope", "login:email login:info");
                break;
            case "gitee":
                builder.queryParam("scope", "user_info emails");
                break;
        }
    }
    
    private Map<String, Object> normalizeUserInfo(String platformType, Map<String, Object> rawUserInfo) {
        Map<String, Object> userInfo = new HashMap<>();
        
        switch (platformType.toLowerCase()) {
            case "github":
                userInfo.put("uid", String.valueOf(rawUserInfo.get("id")));
                userInfo.put("username", rawUserInfo.get("login"));
                userInfo.put("email", rawUserInfo.get("email"));
                userInfo.put("avatar", rawUserInfo.get("avatar_url"));
                break;
            case "google":
                userInfo.put("uid", rawUserInfo.get("id"));
                userInfo.put("username", rawUserInfo.get("name"));
                userInfo.put("email", rawUserInfo.get("email"));
                userInfo.put("avatar", rawUserInfo.get("picture"));
                break;
            case "twitter":
            case "x":
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) rawUserInfo.get("data");
                if (data != null) {
                    userInfo.put("uid", data.get("id"));
                    userInfo.put("username", data.get("username"));
                    userInfo.put("email", null); // Twitter API v2 不直接提供邮箱
                    userInfo.put("avatar", data.get("profile_image_url"));
                }
                break;
            case "yandex":
                userInfo.put("uid", rawUserInfo.get("id"));
                userInfo.put("username", rawUserInfo.get("login"));
                userInfo.put("email", rawUserInfo.get("default_email"));
                userInfo.put("avatar", rawUserInfo.get("default_avatar_id"));
                break;
            case "gitee":
                userInfo.put("uid", String.valueOf(rawUserInfo.get("id")));
                userInfo.put("username", rawUserInfo.get("login"));
                userInfo.put("email", rawUserInfo.get("email"));
                userInfo.put("avatar", rawUserInfo.get("avatar_url"));
                break;
            default:
                // 默认处理
                userInfo.putAll(rawUserInfo);
        }

        return userInfo;
    }
}
