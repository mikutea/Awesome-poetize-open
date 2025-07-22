package com.ld.poetry.utils;

import com.ld.poetry.constants.CommonConst;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

/**
 * Token验证工具类
 * 提供安全的token格式验证和类型检查功能
 * 
 * @author LeapYa
 * @since 2025-07-22
 */
@Slf4j
public class TokenValidationUtil {

    /**
     * HMAC签名token的最大合理长度
     * 基于新token格式计算：前缀(19) + Base64编码数据(约150-200) = 约170-220
     * 设置250作为安全上限，防止异常长的恶意token
     */
    private static final int MAX_SECURE_TOKEN_LENGTH = 250;

    /**
     * 验证是否为有效的用户token
     * 使用新的HMAC签名验证机制，提供防伪造和防篡改保护
     *
     * @param token 待验证的token
     * @return 是否为有效的用户token
     */
    public static boolean isUserToken(String token) {
        if (!StringUtils.hasText(token)) {
            log.debug("Token为空，验证失败");
            return false;
        }

        // 使用SecureTokenGenerator进行完整的签名验证
        SecureTokenGenerator.TokenValidationResult result = SecureTokenGenerator.validateToken(token);

        if (!result.isValid()) {
            log.debug("用户token验证失败: {}", result.getErrorMessage());
            return false;
        }

        // 验证是否为用户token类型
        if (!result.isUserToken()) {
            log.debug("Token类型不匹配，期望用户token但实际为: {}", result.getUserType());
            return false;
        }

        log.debug("用户token验证通过 - 用户ID: {}", result.getUserId());
        return true;
    }

    /**
     * 验证是否为有效的管理员token
     * 使用新的HMAC签名验证机制，提供防伪造和防篡改保护
     *
     * @param token 待验证的token
     * @return 是否为有效的管理员token
     */
    public static boolean isAdminToken(String token) {
        if (!StringUtils.hasText(token)) {
            log.debug("Token为空，验证失败");
            return false;
        }

        // 使用SecureTokenGenerator进行完整的签名验证
        SecureTokenGenerator.TokenValidationResult result = SecureTokenGenerator.validateToken(token);

        if (!result.isValid()) {
            log.debug("管理员token验证失败: {}", result.getErrorMessage());
            return false;
        }

        // 验证是否为管理员token类型
        if (!result.isAdminToken()) {
            log.debug("Token类型不匹配，期望管理员token但实际为: {}", result.getUserType());
            return false;
        }

        log.debug("管理员token验证通过 - 用户ID: {}", result.getUserId());
        return true;
    }

    /**
     * 验证token是否为有效格式（用户或管理员）
     * 使用新的HMAC签名验证机制
     *
     * @param token 待验证的token
     * @return 是否为有效token
     */
    public static boolean isValidToken(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }

        // 使用SecureTokenGenerator进行完整的签名验证
        SecureTokenGenerator.TokenValidationResult result = SecureTokenGenerator.validateToken(token);
        return result.isValid();
    }

    /**
     * 获取token的类型
     * 使用新的HMAC签名验证机制
     *
     * @param token 待检查的token
     * @return token类型：USER、ADMIN或INVALID
     */
    public static TokenType getTokenType(String token) {
        if (!StringUtils.hasText(token)) {
            return TokenType.INVALID;
        }

        // 使用SecureTokenGenerator进行验证
        SecureTokenGenerator.TokenValidationResult result = SecureTokenGenerator.validateToken(token);

        if (!result.isValid()) {
            return TokenType.INVALID;
        }

        if (result.isUserToken()) {
            return TokenType.USER;
        } else if (result.isAdminToken()) {
            return TokenType.ADMIN;
        } else {
            return TokenType.INVALID;
        }
    }

    /**
     * 安全地获取token前缀用于日志记录
     * 只返回前缀部分，不暴露完整token
     * 
     * @param token 待检查的token
     * @return token前缀，如果token无效则返回"INVALID"
     */
    public static String getTokenPrefix(String token) {
        if (!StringUtils.hasText(token)) {
            return "EMPTY";
        }
        
        if (token.startsWith(CommonConst.USER_ACCESS_TOKEN)) {
            return CommonConst.USER_ACCESS_TOKEN;
        } else if (token.startsWith(CommonConst.ADMIN_ACCESS_TOKEN)) {
            return CommonConst.ADMIN_ACCESS_TOKEN;
        } else {
            // 只返回前10个字符用于调试，避免泄露完整token
            return token.length() > 10 ? token.substring(0, 10) + "..." : token;
        }
    }



    /**
     * 验证token长度是否在合理范围内
     * 适配新的HMAC签名token格式，防止过长的token导致内存问题
     *
     * @param token 待验证的token
     * @return 是否在合理长度范围内
     */
    public static boolean isReasonableLength(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }

        // 使用新的HMAC签名token最大长度限制
        if (token.length() > MAX_SECURE_TOKEN_LENGTH) {
            log.warn("Token长度超出合理范围 - 最大允许: {}, 实际: {}", MAX_SECURE_TOKEN_LENGTH, token.length());
            return false;
        }

        return true;
    }

    /**
     * Token类型枚举
     */
    public enum TokenType {
        USER("用户token"),
        ADMIN("管理员token"),
        INVALID("无效token");
        
        private final String description;
        
        TokenType(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
    }

    /**
     * 从token中提取用户ID
     *
     * @param token 有效的token
     * @return 用户ID，如果token无效则返回null
     */
    public static Integer extractUserId(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }

        SecureTokenGenerator.TokenValidationResult result = SecureTokenGenerator.validateToken(token);
        return result.isValid() ? result.getUserId() : null;
    }

    /**
     * 记录token验证失败的安全日志
     * 委托给SecureTokenGenerator的安全日志方法
     *
     * @param token 失败的token
     * @param reason 失败原因
     * @param clientIp 客户端IP
     */
    public static void logTokenValidationFailure(String token, String reason, String clientIp) {
        // 使用SecureTokenGenerator的安全日志方法
        SecureTokenGenerator.logTokenValidationFailure(token, reason, clientIp);
    }

    /**
     * 记录可疑的token活动
     * 
     * @param token 可疑的token
     * @param suspiciousActivity 可疑活动描述
     * @param clientIp 客户端IP
     */
    public static void logSuspiciousTokenActivity(String token, String suspiciousActivity, String clientIp) {
        log.warn("检测到可疑token活动 - 活动: {}, IP: {}, token前缀: {}", 
            suspiciousActivity, clientIp, getTokenPrefix(token));
    }
}
