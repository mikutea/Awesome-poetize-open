package com.ld.poetry.utils;

import com.ld.poetry.constants.CommonConst;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 安全Token生成器
 * 使用HMAC-SHA256签名算法生成和验证token，提供防伪造和防篡改保护
 * 
 * Token格式：前缀 + Base64(userId:userType:timestamp:nonce:signature)
 * 
 * @author LeapYa
 * @since 2025-07-22
 */
@Slf4j
public class SecureTokenGenerator {

    /**
     * HMAC算法名称
     */
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    
    /**
     * Token密钥环境变量名
     */
    private static final String TOKEN_SECRET_ENV = "TOKEN_SECRET_KEY";
    
    /**
     * 默认密钥（仅用于开发环境，生产环境必须使用环境变量）
     */
    private static final String DEFAULT_SECRET = "poetize_default_secret_key_change_in_production";
    
    /**
     * Token有效时间窗口（毫秒）- 用于防止重放攻击
     * 设置为1小时，超过此时间的token将被拒绝
     */
    private static final long TOKEN_TIME_WINDOW = 7 * 24 * 60 * 60 * 1000; // 7天
    
    /**
     * 安全随机数生成器
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 获取Token签名密钥
     * 优先从环境变量获取，如果没有则使用默认密钥（并记录警告）
     * 
     * @return Token签名密钥
     */
    private static String getTokenSecret() {
        String secret = System.getenv(TOKEN_SECRET_ENV);
        if (!StringUtils.hasText(secret)) {
            log.warn("未配置TOKEN_SECRET_KEY环境变量，使用默认密钥（生产环境不安全）");
            return DEFAULT_SECRET;
        }
        return secret;
    }

    /**
     * 生成安全的用户token
     * 
     * @param userId 用户ID
     * @return 签名后的安全token
     */
    public static String generateUserToken(Integer userId) {
        return generateSecureToken(userId, "user");
    }

    /**
     * 生成安全的管理员token
     * 
     * @param userId 用户ID
     * @return 签名后的安全token
     */
    public static String generateAdminToken(Integer userId) {
        return generateSecureToken(userId, "admin");
    }

    /**
     * 生成安全token的核心方法
     * 
     * @param userId 用户ID
     * @param userType 用户类型（user/admin）
     * @return 完整的安全token
     */
    private static String generateSecureToken(Integer userId, String userType) {
        try {
            // 当前时间戳
            long timestamp = System.currentTimeMillis();
            
            // 生成随机nonce防止相同参数生成相同token
            String nonce = generateNonce();
            
            // 构建payload
            String payload = userId + ":" + userType + ":" + timestamp + ":" + nonce;
            
            // 生成HMAC-SHA256签名
            String signature = generateHmacSignature(payload);
            
            // 组合完整的token数据
            String tokenData = payload + ":" + signature;
            
            // Base64编码
            String encodedToken = Base64.getEncoder().encodeToString(tokenData.getBytes(StandardCharsets.UTF_8));
            
            // 添加前缀
            String prefix = "admin".equals(userType) ? CommonConst.ADMIN_ACCESS_TOKEN : CommonConst.USER_ACCESS_TOKEN;
            String finalToken = prefix + encodedToken;
            
            log.debug("生成安全token成功 - 用户ID: {}, 类型: {}, token长度: {}", userId, userType, finalToken.length());
            
            return finalToken;
            
        } catch (Exception e) {
            log.error("生成安全token失败 - 用户ID: {}, 类型: {}, 错误: {}", userId, userType, e.getMessage(), e);
            throw new RuntimeException("Token生成失败", e);
        }
    }

    /**
     * 验证token的完整性和有效性
     * 
     * @param token 待验证的token
     * @return 验证结果
     */
    public static TokenValidationResult validateToken(String token) {
        if (!StringUtils.hasText(token)) {
            return TokenValidationResult.failure("Token为空");
        }

        try {
            // 提取token类型和数据
            String prefix;
            String encodedData;
            
            if (token.startsWith(CommonConst.USER_ACCESS_TOKEN)) {
                prefix = CommonConst.USER_ACCESS_TOKEN;
                encodedData = token.substring(CommonConst.USER_ACCESS_TOKEN.length());
            } else if (token.startsWith(CommonConst.ADMIN_ACCESS_TOKEN)) {
                prefix = CommonConst.ADMIN_ACCESS_TOKEN;
                encodedData = token.substring(CommonConst.ADMIN_ACCESS_TOKEN.length());
            } else {
                return TokenValidationResult.failure("无效的token前缀");
            }

            // Base64解码
            String decodedData;
            try {
                decodedData = new String(Base64.getDecoder().decode(encodedData), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                return TokenValidationResult.failure("Token Base64解码失败");
            }

            // 解析token组件
            String[] parts = decodedData.split(":");
            if (parts.length != 5) {
                return TokenValidationResult.failure("Token格式错误，组件数量不正确");
            }

            String userId = parts[0];
            String userType = parts[1];
            String timestampStr = parts[2];
            String nonce = parts[3];
            String providedSignature = parts[4];

            // 验证时间戳格式
            long timestamp;
            try {
                timestamp = Long.parseLong(timestampStr);
            } catch (NumberFormatException e) {
                return TokenValidationResult.failure("时间戳格式错误");
            }

            // 验证时间窗口（防止重放攻击）
            long currentTime = System.currentTimeMillis();
            if (currentTime - timestamp > TOKEN_TIME_WINDOW) {
                return TokenValidationResult.failure("登录已过期，请重新登录");
            }

            // 重新计算签名
            String payload = userId + ":" + userType + ":" + timestampStr + ":" + nonce;
            String expectedSignature = generateHmacSignature(payload);

            // 验证签名（使用常量时间比较防止时序攻击）
            if (!MessageDigest.isEqual(providedSignature.getBytes(), expectedSignature.getBytes())) {
                return TokenValidationResult.failure("Token签名验证失败");
            }

            // 验证用户类型与前缀的一致性
            boolean isAdminToken = token.startsWith(CommonConst.ADMIN_ACCESS_TOKEN);
            boolean isAdminUserType = "admin".equals(userType);
            if (isAdminToken != isAdminUserType) {
                return TokenValidationResult.failure("Token类型与前缀不匹配");
            }

            log.debug("Token验证成功 - 用户ID: {}, 类型: {}", userId, userType);

            // 构建验证结果
            return TokenValidationResult.success(
                Integer.parseInt(userId),
                userType,
                timestamp,
                nonce
            );

        } catch (Exception e) {
            log.error("Token验证过程中发生异常: {}", e.getMessage(), e);
            return TokenValidationResult.failure("Token验证异常: " + e.getMessage());
        }
    }

    /**
     * 生成HMAC-SHA256签名
     * 
     * @param data 待签名的数据
     * @return 十六进制格式的签名字符串
     */
    private static String generateHmacSignature(String data) {
        try {
            String secret = getTokenSecret();
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(secretKeySpec);
            
            byte[] signatureBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            
            // 转换为十六进制字符串
            StringBuilder hexString = new StringBuilder();
            for (byte b : signatureBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            
            return hexString.toString();
            
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("生成HMAC签名失败: {}", e.getMessage(), e);
            throw new RuntimeException("签名生成失败", e);
        }
    }

    /**
     * 生成随机nonce
     * 
     * @return 16位十六进制随机字符串
     */
    private static String generateNonce() {
        byte[] nonceBytes = new byte[8]; // 8字节 = 16位十六进制
        SECURE_RANDOM.nextBytes(nonceBytes);
        
        StringBuilder nonce = new StringBuilder();
        for (byte b : nonceBytes) {
            nonce.append(String.format("%02x", b));
        }
        
        return nonce.toString();
    }

    /**
     * 记录token验证失败的安全事件
     * 
     * @param token 失败的token
     * @param reason 失败原因
     * @param clientIp 客户端IP
     */
    public static void logTokenValidationFailure(String token, String reason, String clientIp) {
        // 安全地记录token信息（不记录完整token）
        String tokenPrefix = "UNKNOWN";
        int tokenLength = 0;
        
        if (StringUtils.hasText(token)) {
            tokenLength = token.length();
            if (token.startsWith(CommonConst.USER_ACCESS_TOKEN)) {
                tokenPrefix = "USER_TOKEN";
            } else if (token.startsWith(CommonConst.ADMIN_ACCESS_TOKEN)) {
                tokenPrefix = "ADMIN_TOKEN";
            } else {
                tokenPrefix = token.length() > 10 ? token.substring(0, 10) + "..." : token;
            }
        }
        
        log.warn("安全Token验证失败 - 原因: {}, IP: {}, token类型: {}, token长度: {}", 
            reason, clientIp, tokenPrefix, tokenLength);
    }

    /**
     * Token验证结果类
     */
    public static class TokenValidationResult {
        private final boolean valid;
        private final String errorMessage;
        private final Integer userId;
        private final String userType;
        private final Long timestamp;
        private final String nonce;

        private TokenValidationResult(boolean valid, String errorMessage, Integer userId, 
                                    String userType, Long timestamp, String nonce) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.userId = userId;
            this.userType = userType;
            this.timestamp = timestamp;
            this.nonce = nonce;
        }

        public static TokenValidationResult success(Integer userId, String userType, Long timestamp, String nonce) {
            return new TokenValidationResult(true, null, userId, userType, timestamp, nonce);
        }

        public static TokenValidationResult failure(String errorMessage) {
            return new TokenValidationResult(false, errorMessage, null, null, null, null);
        }

        // Getters
        public boolean isValid() { return valid; }
        public String getErrorMessage() { return errorMessage; }
        public Integer getUserId() { return userId; }
        public String getUserType() { return userType; }
        public Long getTimestamp() { return timestamp; }
        public String getNonce() { return nonce; }
        public boolean isAdminToken() { return "admin".equals(userType); }
        public boolean isUserToken() { return "user".equals(userType); }
    }

    // ==================== WebSocket专用Token方法 ====================
    
    /**
     * WebSocket token前缀
     */
    private static final String WS_TOKEN_PREFIX = "ws_token_";
    
    /**
     * WebSocket token有效期：30分钟（毫秒）
     */
    private static final long WS_TOKEN_TIME_WINDOW = 30 * 60 * 1000L;

    /**
     * 生成WebSocket专用临时token（30分钟有效期）
     * 
     * @param userId 用户ID
     * @param userType 用户类型
     * @return WebSocket临时token
     */
    public static String generateWebSocketToken(Integer userId, String userType) {
        try {
            long timestamp = System.currentTimeMillis();
            String nonce = generateNonce();
            
            // 构建payload
            String payload = userId + ":" + userType + ":" + timestamp + ":" + nonce;
            
            // 生成签名
            String signature = generateHmacSignature(payload);
            
            // 组合完整数据
            String tokenData = payload + ":" + signature;
            
            // Base64编码
            String encodedData = Base64.getEncoder().encodeToString(tokenData.getBytes(StandardCharsets.UTF_8));
            
            String wsToken = WS_TOKEN_PREFIX + encodedData;
            
            log.debug("生成WebSocket token成功 - 用户ID: {}, 类型: {}, 有效期: 30分钟", userId, userType);
            
            return wsToken;
            
        } catch (Exception e) {
            log.error("生成WebSocket token失败 - 用户ID: {}, 类型: {}, 错误: {}", userId, userType, e.getMessage(), e);
            throw new RuntimeException("WebSocket Token生成失败", e);
        }
    }

    /**
     * 验证WebSocket token并返回用户ID
     * 
     * @param wsToken WebSocket token
     * @return 用户ID，验证失败返回null
     */
    public static Integer validateWebSocketToken(String wsToken) {
        if (!StringUtils.hasText(wsToken) || !wsToken.startsWith(WS_TOKEN_PREFIX)) {
            log.warn("WebSocket token格式错误或前缀不匹配");
            return null;
        }

        try {
            String encodedData = wsToken.substring(WS_TOKEN_PREFIX.length());
            
            // Base64解码
            String decodedData;
            try {
                decodedData = new String(Base64.getDecoder().decode(encodedData), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException e) {
                log.warn("WebSocket token Base64解码失败");
                return null;
            }
            
            // 解析数据：userId:userType:timestamp:nonce:signature
            String[] parts = decodedData.split(":");
            if (parts.length != 5) {
                log.warn("WebSocket token格式错误，组件数量不正确");
                return null;
            }

            Integer userId = Integer.parseInt(parts[0]);
            String userType = parts[1];
            long timestamp = Long.parseLong(parts[2]);
            String nonce = parts[3];
            String providedSignature = parts[4];

            // 验证时间戳（30分钟有效期）
            long currentTime = System.currentTimeMillis();
            if (currentTime - timestamp > WS_TOKEN_TIME_WINDOW) {
                log.warn("WebSocket token已过期 - 用户ID: {}, 过期时间: {}分钟前", 
                    userId, (currentTime - timestamp) / (60 * 1000));
                return null;
            }

            // 验证签名
            String payload = userId + ":" + userType + ":" + timestamp + ":" + nonce;
            String expectedSignature = generateHmacSignature(payload);
            
            if (!MessageDigest.isEqual(providedSignature.getBytes(), expectedSignature.getBytes())) {
                log.warn("WebSocket token签名验证失败 - 用户ID: {}", userId);
                return null;
            }

            log.debug("WebSocket token验证成功 - 用户ID: {}, 类型: {}", userId, userType);
            return userId;
            
        } catch (Exception e) {
            log.error("WebSocket token验证异常: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 验证WebSocket token并返回用户ID
     * 
     * @param wsToken WebSocket token
     * @param allowExpiring 是否允许即将过期的token（用于续签）
     * @return 用户ID，验证失败返回null
     */
    public static Integer validateWebSocketToken(String wsToken, boolean allowExpiring) {
        if (!StringUtils.hasText(wsToken) || !wsToken.startsWith(WS_TOKEN_PREFIX)) {
            log.warn("WebSocket token格式错误或前缀不匹配");
            return null;
        }

        try {
            String encodedData = wsToken.substring(WS_TOKEN_PREFIX.length());
            String decodedData = new String(Base64.getDecoder().decode(encodedData), StandardCharsets.UTF_8);
            String[] parts = decodedData.split(":");
            
            if (parts.length != 5) {
                log.warn("WebSocket token格式错误，组件数量不正确");
                return null;
            }

            Integer userId = Integer.parseInt(parts[0]);
            String userType = parts[1];
            long timestamp = Long.parseLong(parts[2]);
            String nonce = parts[3];
            String providedSignature = parts[4];

            // 验证时间戳
            long currentTime = System.currentTimeMillis();
            long elapsed = currentTime - timestamp;
            
            if (!allowExpiring && elapsed > WS_TOKEN_TIME_WINDOW) {
                log.warn("WebSocket token已过期 - 用户ID: {}", userId);
                return null;
            }
            
            // 续签模式：允许额外5分钟宽限期
            if (allowExpiring && elapsed > WS_TOKEN_TIME_WINDOW + 5 * 60 * 1000) {
                log.warn("WebSocket token过期太久，无法续签 - 用户ID: {}", userId);
                return null;
            }

            // 验证签名
            String payload = userId + ":" + userType + ":" + timestamp + ":" + nonce;
            String expectedSignature = generateHmacSignature(payload);
            
            if (!MessageDigest.isEqual(providedSignature.getBytes(), expectedSignature.getBytes())) {
                log.warn("WebSocket token签名验证失败 - 用户ID: {}", userId);
                return null;
            }

            return userId;
            
        } catch (Exception e) {
            log.error("WebSocket token验证异常: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 从WebSocket token中获取用户类型
     * 
     * @param wsToken WebSocket token
     * @return 用户类型，获取失败返回null
     */
    public static String getUserTypeFromWebSocketToken(String wsToken) {
        if (!StringUtils.hasText(wsToken) || !wsToken.startsWith(WS_TOKEN_PREFIX)) {
            return null;
        }

        try {
            String encodedData = wsToken.substring(WS_TOKEN_PREFIX.length());
            String decodedData = new String(Base64.getDecoder().decode(encodedData), StandardCharsets.UTF_8);
            String[] parts = decodedData.split(":");
            
            if (parts.length != 5) {
                return null;
            }

            return parts[1]; // userType
            
        } catch (Exception e) {
            log.error("从WebSocket token获取用户类型失败: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 获取WebSocket token剩余有效时间（分钟）
     * 
     * @param wsToken WebSocket token
     * @return 剩余分钟数，token无效返回-1
     */
    public static int getWebSocketTokenRemainingMinutes(String wsToken) {
        if (!StringUtils.hasText(wsToken) || !wsToken.startsWith(WS_TOKEN_PREFIX)) {
            return -1;
        }

        try {
            String encodedData = wsToken.substring(WS_TOKEN_PREFIX.length());
            String decodedData = new String(Base64.getDecoder().decode(encodedData), StandardCharsets.UTF_8);
            String[] parts = decodedData.split(":");
            
            if (parts.length != 5) {
                return -1;
            }

            long timestamp = Long.parseLong(parts[2]);
            long currentTime = System.currentTimeMillis();
            long elapsed = currentTime - timestamp;
            long remaining = WS_TOKEN_TIME_WINDOW - elapsed;

            if (remaining <= 0) {
                return 0;
            }

            return (int) (remaining / (60 * 1000)); // 转换为分钟
            
        } catch (Exception e) {
            log.error("获取WebSocket token剩余时间失败: {}", e.getMessage(), e);
            return -1;
        }
    }
}
