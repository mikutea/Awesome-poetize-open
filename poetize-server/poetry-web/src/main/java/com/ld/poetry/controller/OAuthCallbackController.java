package com.ld.poetry.controller;

import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.service.UserService;
import com.ld.poetry.vo.UserVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * OAuth回调控制器
 * 处理来自Python OAuth服务的回调请求
 * 
 * @author LeapYa
 * @since 2025-07-19
 */
@Slf4j
@RestController
@RequestMapping("/oauth")
public class OAuthCallbackController {

    @Autowired
    private UserService userService;

    /**
     * 处理Python OAuth服务的回调请求
     * 接收第三方平台的用户信息并完成登录流程
     * 
     * @param oauthData Python服务发送的OAuth用户数据
     * @return 登录结果，包含用户信息和访问token
     */
    @PostMapping("/callback")
    public PoetryResult<UserVO> handleOAuthCallback(@RequestBody Map<String, Object> oauthData) {
        try {
            log.info("收到OAuth回调请求: {}", oauthData);

            // 记录原始数据类型用于调试
            oauthData.forEach((key, value) -> {
                if (value != null) {
                    log.debug("OAuth数据字段: {} = {} (类型: {})", key, value, value.getClass().getSimpleName());
                } else {
                    log.debug("OAuth数据字段: {} = null", key);
                }
            });

            // 安全地提取和转换字段
            String provider = extractStringValue(oauthData, "provider");
            String uid = extractStringValue(oauthData, "uid");
            String username = extractStringValue(oauthData, "username");
            String email = extractStringValue(oauthData, "email");
            String avatar = extractStringValue(oauthData, "avatar");

            // 检查是否需要邮箱收集（主要针对Gitee）
            Boolean emailCollectionNeeded = extractBooleanValue(oauthData, "email_collection_needed");

            log.info("转换后的OAuth数据: provider={}, uid={}, username={}, email={}, avatar={}, emailCollectionNeeded={}",
                    provider, uid, username, email, avatar, emailCollectionNeeded);
            
            // 验证必要参数
            if (provider == null || provider.trim().isEmpty()) {
                log.warn("OAuth回调缺少provider参数");
                return PoetryResult.fail("缺少provider参数");
            }
            
            if (uid == null || uid.trim().isEmpty()) {
                log.warn("OAuth回调缺少uid参数");
                return PoetryResult.fail("缺少uid参数");
            }
            
            log.info("处理{}平台的OAuth登录: uid={}, username={}, email={}", 
                    provider, uid, username, email);
            
            // 调用用户服务处理第三方登录
            PoetryResult<UserVO> result = userService.thirdLogin(provider, uid, username, email, avatar);

            if (result.isSuccess()) {
                log.info("OAuth登录成功: provider={}, uid={}, userId={}",
                        provider, uid, result.getData().getId());

                // 如果需要邮箱收集，在返回数据中添加标记
                if (emailCollectionNeeded != null && emailCollectionNeeded) {
                    UserVO userVO = result.getData();
                    // 可以通过扩展UserVO或在返回消息中添加标记
                    log.info("用户需要补充邮箱信息: provider={}, uid={}", provider, uid);
                    // 这里可以设置一个特殊的标记，让前端知道需要收集邮箱
                    result.setMessage("EMAIL_COLLECTION_NEEDED");
                }
            } else {
                log.warn("OAuth登录失败: provider={}, uid={}, error={}",
                        provider, uid, result.getMessage());
            }

            return result;
            
        } catch (Exception e) {
            log.error("处理OAuth回调时发生异常", e);
            return PoetryResult.fail("OAuth回调处理失败: " + e.getMessage());
        }
    }
    
    /**
     * 健康检查端点
     * 用于验证OAuth回调服务是否正常运行
     * 
     * @return 服务状态
     */
    @GetMapping("/health")
    public PoetryResult<Map<String, Object>> health() {
        return PoetryResult.success(Map.of(
            "service", "oauth-callback",
            "status", "ok",
            "timestamp", System.currentTimeMillis()
        ));
    }
    
    /**
     * 获取支持的OAuth提供商列表
     *
     * @return 支持的提供商列表
     */
    @GetMapping("/providers")
    public PoetryResult<Map<String, Object>> getSupportedProviders() {
        return PoetryResult.success(Map.of(
            "providers", new String[]{"github", "google", "twitter", "yandex", "gitee"},
            "callback_endpoint", "/oauth/callback",
            "method", "POST"
        ));
    }

    /**
     * 安全地从Map中提取字符串值
     * 处理不同数据类型的转换（Integer、Long、String等）
     *
     * @param data 数据Map
     * @param key 键名
     * @return 转换后的字符串值，如果不存在或为null则返回null
     */
    private String extractStringValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) {
            return null;
        }

        // 如果已经是字符串，直接返回
        if (value instanceof String) {
            return (String) value;
        }

        // 如果是数字类型，转换为字符串
        if (value instanceof Number) {
            return value.toString();
        }

        // 如果是布尔类型，转换为字符串
        if (value instanceof Boolean) {
            return value.toString();
        }

        // 其他类型，使用toString()方法
        return value.toString();
    }

    /**
     * 安全地从Map中提取布尔值
     * 处理不同数据类型的转换
     *
     * @param data 数据Map
     * @param key 键名
     * @return 转换后的布尔值，如果不存在或为null则返回false
     */
    private Boolean extractBooleanValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null) {
            return false;
        }

        // 如果已经是布尔类型，直接返回
        if (value instanceof Boolean) {
            return (Boolean) value;
        }

        // 如果是字符串，解析为布尔值
        if (value instanceof String) {
            String strValue = ((String) value).toLowerCase().trim();
            return "true".equals(strValue) || "1".equals(strValue) || "yes".equals(strValue);
        }

        // 如果是数字类型，非零为true
        if (value instanceof Number) {
            return ((Number) value).doubleValue() != 0.0;
        }

        // 其他类型，默认为false
        return false;
    }
}
