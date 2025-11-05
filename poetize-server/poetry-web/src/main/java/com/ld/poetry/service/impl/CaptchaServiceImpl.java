package com.ld.poetry.service.impl;

import com.ld.poetry.service.CaptchaService;
import com.ld.poetry.service.SysCaptchaConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 验证码验证服务实现
 */
@Slf4j
@Service
public class CaptchaServiceImpl implements CaptchaService {
    
    @Autowired
    private SysCaptchaConfigService captchaConfigService;
    
    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    
    private static final String CAPTCHA_TOKEN_PREFIX = "captcha:token:";
    private static final String IP_VERIFY_COUNT_PREFIX = "captcha:ip:count:";
    private static final String IP_BLOCK_PREFIX = "captcha:ip:block:";
    private static final String FINGERPRINT_PREFIX = "captcha:fingerprint:";
    private static final long TOKEN_EXPIRY = 5; // 5分钟过期
    private static final long IP_COUNT_WINDOW = 5; // IP统计窗口：5分钟
    private static final long IP_BLOCK_DURATION = 30; // IP封禁时长：30分钟
    private static final int MAX_VERIFY_PER_IP = 15; // 5分钟内最多验证15次
    private static final int MAX_FINGERPRINT_SWITCHES = 3; // 允许的最大指纹切换次数
    
    @Override
    public boolean isCaptchaRequired(String action) {
        try {
            Map<String, Object> config = captchaConfigService.getCaptchaConfig();
            
            // 检查是否启用验证码
            Boolean enable = (Boolean) config.get("enable");
            if (!Boolean.TRUE.equals(enable)) {
                return false;
            }
            
            // 检查特定操作是否需要验证码
            Object actionValue = config.get(action);
            boolean required = actionValue instanceof Boolean ? (Boolean) actionValue : false;
            
            return required;
        } catch (Exception e) {
            log.error("检查验证码需求失败", e);
            // 出错时默认不需要验证码，确保用户可以正常操作
            return false;
        }
    }
    
    @Override
    public Map<String, Object> verifyCheckboxCaptcha(
            List<Map<String, Object>> mouseTrack,
            Double straightRatio,
            Boolean isReplyComment,
            Integer retryCount,
            Double frontendSensitivity,
            Integer frontendMinPoints,
            Long clickDelay,
            String browserFingerprint,
            String clientIp) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 0. IP频率检查和封禁检查
            if (clientIp != null && !clientIp.isEmpty()) {
                // 检查IP是否被封禁
                if (isIpBlocked(clientIp)) {
                    long remainingMinutes = getIpBlockRemainingTime(clientIp);
                    log.warn("IP已被封禁: {}, 剩余{}分钟", clientIp, remainingMinutes);
                    result.put("success", false);
                    result.put("token", "");
                    result.put("message", String.format("验证失败次数过多，已被临时限制 %d 分钟，请稍后再试", remainingMinutes));
                    result.put("blocked", true);
                    result.put("remainingMinutes", remainingMinutes);
                    return result;
                }
                
                // 获取当前验证次数
                int currentCount = getIpVerifyCount(clientIp);
                int remainingAttempts = MAX_VERIFY_PER_IP - currentCount;
                
                // 检查IP验证频率
                if (!checkIpRateLimit(clientIp)) {
                    log.warn("IP验证频率过高: {}, 已达{}次", clientIp, currentCount);
                    blockIp(clientIp);
                    result.put("success", false);
                    result.put("token", "");
                    result.put("message", String.format("验证次数过多（%d次/%d分钟），已被临时限制 %d 分钟", 
                            MAX_VERIFY_PER_IP, (int)IP_COUNT_WINDOW, (int)IP_BLOCK_DURATION));
                    result.put("blocked", true);
                    result.put("remainingMinutes", IP_BLOCK_DURATION);
                    return result;
                }
                
                // 如果剩余次数较少，添加警告信息
                if (remainingAttempts <= 3 && remainingAttempts > 0) {
                    result.put("warning", String.format("提示：您还剩 %d 次验证机会（%d分钟内）", 
                            remainingAttempts, (int)IP_COUNT_WINDOW));
                }
            }
            
            // 获取验证码配置
            Map<String, Object> config = captchaConfigService.getCaptchaConfig();
            Map<String, Object> checkboxConfig = (Map<String, Object>) config.getOrDefault("checkbox", new HashMap<>());
            
            boolean isValid = true;
            List<String> validationDetails = new ArrayList<>();
            
            // 回复评论模式：使用宽松的验证规则
            if (Boolean.TRUE.equals(isReplyComment)) {
            }
            
            // 1. 点击时间分析
            if (clickDelay != null) {
                if (clickDelay < 500) {
                    isValid = false;
                    validationDetails.add(String.format("点击过快: %dms < 500ms (疑似机器人)", clickDelay));
                } else if (clickDelay > 60000) {
                    isValid = false;
                    validationDetails.add(String.format("点击过慢: %dms > 60000ms (页面可能失效)", clickDelay));
                }
            }
            
            // 2. 轨迹点数量检查（回复评论时降低要求）
            int minTrackPoints = frontendMinPoints != null ? frontendMinPoints : 
                getIntValue(checkboxConfig, "minTrackPoints", 3);
            
            // 回复评论时只要求最少1个轨迹点
            if (Boolean.TRUE.equals(isReplyComment)) {
                minTrackPoints = Math.min(minTrackPoints, 1);
            }
            
            if (mouseTrack == null || mouseTrack.size() < minTrackPoints) {
                isValid = false;
                validationDetails.add(String.format("轨迹点数不足: %d < %d", 
                    mouseTrack != null ? mouseTrack.size() : 0, minTrackPoints));
            }
            
            // 3. 直线率检查（回复评论时跳过此检查）
            if (!Boolean.TRUE.equals(isReplyComment)) {
                double trackSensitivity = frontendSensitivity != null ? frontendSensitivity :
                    getDoubleValue(checkboxConfig, "trackSensitivity", 0.98);
                
                if (straightRatio != null && straightRatio > trackSensitivity) {
                    isValid = false;
                    validationDetails.add(String.format("轨迹过于直线: %.3f > %.3f", straightRatio, trackSensitivity));
                }
            } else {
            }
            
            // 4. 浏览器指纹检测
            if (browserFingerprint != null && !browserFingerprint.isEmpty() && clientIp != null) {
                // 检查该IP是否频繁切换浏览器指纹（可疑行为）
                if (!checkFingerprintConsistency(clientIp, browserFingerprint)) {
                    isValid = false;
                    validationDetails.add("检测到频繁切换设备/浏览器（疑似自动化）");
                }
            }
            
            // 5. 轨迹特征增强分析（回复评论时跳过此检查）
            if (!Boolean.TRUE.equals(isReplyComment) && mouseTrack != null && mouseTrack.size() >= 2) {
                // 分析轨迹速度分布
                double avgSpeed = calculateAverageSpeed(mouseTrack);
                double speedVariance = calculateSpeedVariance(mouseTrack, avgSpeed);
                
                // 人类操作：速度有变化（加速、减速）
                // 机器操作：速度恒定（方差接近0）
                if (speedVariance < 0.1 && avgSpeed > 100) {
                    isValid = false;
                    validationDetails.add(String.format("速度过于恒定: variance=%.3f (疑似脚本)", speedVariance));
                }
                
                // 分析方向变化
                int directionChanges = calculateDirectionChanges(mouseTrack);
                if (directionChanges < 2 && mouseTrack.size() > 5) {
                    isValid = false;
                    validationDetails.add(String.format("方向变化过少: %d (疑似直线移动)", directionChanges));
                }
                
            } else if (Boolean.TRUE.equals(isReplyComment)) {
            }
            
            log.info("验证结果: {}, IP: {}, 详情: {}", isValid, clientIp, validationDetails);
            
            // 生成验证令牌
            String token = "";
            if (isValid) {
                token = generateVerificationToken();
                // 存储令牌到Redis，5分钟过期
                try {
                    redisTemplate.opsForValue().set(
                        CAPTCHA_TOKEN_PREFIX + token, 
                        "1", 
                        TOKEN_EXPIRY, 
                        TimeUnit.MINUTES
                    );
                } catch (Exception e) {
                    log.error("存储验证令牌到Redis失败", e);
                }
            }
            
            result.put("success", isValid);
            result.put("token", token);
            
        } catch (Exception e) {
            log.error("复选框验证出错", e);
            result.put("success", false);
            result.put("token", "");
        }
        
        return result;
    }
    
    @Override
    public boolean verifyToken(String token) {
        if (token == null || token.isEmpty()) {
            return false;
        }
        
        try {
            String key = CAPTCHA_TOKEN_PREFIX + token;
            String value = redisTemplate.opsForValue().get(key);
            
            if (value != null) {
                // 验证后删除令牌（一次性）
                redisTemplate.delete(key);
                return true;
            }
            
            return false;
        } catch (Exception e) {
            log.error("验证令牌失败", e);
            return false;
        }
    }
    
    @Override
    public Map<String, Object> verifySlideCaptcha(
            List<Map<String, Object>> slideTrack,
            Long totalTime,
            Double maxDistance,
            Double finalPosition,
            String browserFingerprint,
            String clientIp) {
        
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 0. IP频率检查
            if (clientIp != null && !clientIp.isEmpty()) {
                // 检查IP是否被封禁
                if (isIpBlocked(clientIp)) {
                    long remainingMinutes = getIpBlockRemainingTime(clientIp);
                    log.warn("IP已被封禁: {}, 剩余{}分钟", clientIp, remainingMinutes);
                    result.put("success", false);
                    result.put("message", String.format("验证失败次数过多，已被临时限制 %d 分钟，请稍后再试", remainingMinutes));
                    result.put("blocked", true);
                    result.put("remainingMinutes", remainingMinutes);
                    return result;
                }
                
                // 获取当前验证次数
                int currentCount = getIpVerifyCount(clientIp);
                int remainingAttempts = MAX_VERIFY_PER_IP - currentCount;
                
                // 检查IP验证频率
                if (!checkIpRateLimit(clientIp)) {
                    log.warn("IP滑动验证频率过高: {}, 已达{}次", clientIp, currentCount);
                    blockIp(clientIp);
                    result.put("success", false);
                    result.put("message", String.format("验证次数过多（%d次/%d分钟），已被临时限制 %d 分钟", 
                            MAX_VERIFY_PER_IP, (int)IP_COUNT_WINDOW, (int)IP_BLOCK_DURATION));
                    result.put("blocked", true);
                    result.put("remainingMinutes", IP_BLOCK_DURATION);
                    return result;
                }
                
                // 如果剩余次数较少，添加警告信息
                if (remainingAttempts <= 3 && remainingAttempts > 0) {
                    result.put("warning", String.format("提示：您还剩 %d 次验证机会（%d分钟内）", 
                            remainingAttempts, (int)IP_COUNT_WINDOW));
                }
            }
            
            boolean isValid = true;
            List<String> validationDetails = new ArrayList<>();
            
            // 1. 时间检测
            if (totalTime != null) {
                if (totalTime < 500) {
                    isValid = false;
                    validationDetails.add(String.format("滑动过快: %dms < 500ms (疑似机器人)", totalTime));
                } else if (totalTime > 10000) {
                    isValid = false;
                    validationDetails.add(String.format("滑动过慢: %dms > 10000ms", totalTime));
                }
            }
            
            // 2. 浏览器指纹检测
            if (browserFingerprint != null && !browserFingerprint.isEmpty() && clientIp != null) {
                if (!checkFingerprintConsistency(clientIp, browserFingerprint)) {
                    isValid = false;
                    validationDetails.add("检测到频繁切换设备/浏览器");
                }
            }
            
            // 3. 轨迹特征分析
            if (slideTrack != null && slideTrack.size() >= 2) {
                // 计算平均速度
                double avgSpeed = calculateSlideAverageSpeed(slideTrack);
                
                // 计算速度方差
                double speedVariance = calculateSlideSpeedVariance(slideTrack, avgSpeed);
                
                // 检测恒速滑动（机器人特征）
                if (speedVariance < 0.05 && avgSpeed > 0.5) {
                    isValid = false;
                    validationDetails.add(String.format("速度过于恒定: variance=%.3f (疑似脚本)", speedVariance));
                }
                
                // 检测回退次数（人类会有微调）
                int backtrackCount = calculateBacktrackCount(slideTrack);
                
                // 检测加速度变化
                double avgAcceleration = calculateAverageAcceleration(slideTrack);
                
                
                // 完全匀速且无回退 = 可疑
                if (speedVariance < 0.1 && backtrackCount == 0 && slideTrack.size() > 5) {
                    isValid = false;
                    validationDetails.add("滑动轨迹过于完美（疑似程序控制）");
                }
            }
            
            log.info("滑动验证结果: {}, IP: {}, 耗时: {}ms, 详情: {}", 
                isValid, clientIp, totalTime, validationDetails);
            
            // 生成令牌
            String token = "";
            if (isValid) {
                token = generateVerificationToken();
                try {
                    redisTemplate.opsForValue().set(
                        CAPTCHA_TOKEN_PREFIX + token, 
                        "1", 
                        TOKEN_EXPIRY, 
                        TimeUnit.MINUTES
                    );
                } catch (Exception e) {
                    log.error("存储滑动验证令牌失败", e);
                }
            }
            
            result.put("success", isValid);
            result.put("token", token);
            if (!isValid && !validationDetails.isEmpty()) {
                result.put("message", validationDetails.get(0));
            }
            
        } catch (Exception e) {
            log.error("滑动验证异常", e);
            result.put("success", false);
            result.put("message", "验证失败");
        }
        
        return result;
    }
    
    @Override
    public Map<String, Object> getPublicCaptchaConfig() {
        try {
            Map<String, Object> config = captchaConfigService.getCaptchaConfig();
            
            // 只返回前端验证组件必要的配置信息
            Map<String, Object> publicConfig = new HashMap<>();
            publicConfig.put("enable", config.get("enable"));
            publicConfig.put("screenSizeThreshold", config.get("screenSizeThreshold"));
            publicConfig.put("forceSlideForMobile", config.get("forceSlideForMobile"));
            publicConfig.put("slide", config.get("slide"));
            publicConfig.put("checkbox", config.get("checkbox"));
            
            return publicConfig;
        } catch (Exception e) {
            log.error("获取公共验证码配置失败", e);
            
            // 返回默认配置
            Map<String, Object> defaultConfig = new HashMap<>();
            defaultConfig.put("enable", false);
            defaultConfig.put("screenSizeThreshold", 768);
            defaultConfig.put("forceSlideForMobile", true);
            
            Map<String, Object> slide = new HashMap<>();
            slide.put("accuracy", 5);
            slide.put("successThreshold", 0.95);
            defaultConfig.put("slide", slide);
            
            Map<String, Object> checkbox = new HashMap<>();
            checkbox.put("trackSensitivity", 0.90);
            checkbox.put("minTrackPoints", 2);
            checkbox.put("replyCommentSensitivity", 0.85);
            checkbox.put("maxRetryCount", 5);
            checkbox.put("retryDecrement", 0.02);
            defaultConfig.put("checkbox", checkbox);
            
            return defaultConfig;
        }
    }
    
    @Override
    public String generateVerificationToken() {
        try {
            String uniqueStr = UUID.randomUUID().toString() + "-" + System.currentTimeMillis() + "-" + 
                new Random().nextInt(100000);
            
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(uniqueStr.getBytes());
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            
            return hexString.toString();
        } catch (Exception e) {
            log.error("生成验证令牌失败", e);
            return UUID.randomUUID().toString();
        }
    }
    
    /**
     * 检查IP是否被封禁
     */
    private boolean isIpBlocked(String ip) {
        try {
            String key = IP_BLOCK_PREFIX + ip;
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.error("检查IP封禁状态失败", e);
            return false;
        }
    }
    
    /**
     * 获取IP封禁剩余时间（分钟）
     */
    private long getIpBlockRemainingTime(String ip) {
        try {
            String key = IP_BLOCK_PREFIX + ip;
            Long ttl = redisTemplate.getExpire(key, TimeUnit.MINUTES);
            return ttl != null && ttl > 0 ? ttl : 0;
        } catch (Exception e) {
            log.error("获取IP封禁剩余时间失败", e);
            return 0;
        }
    }
    
    /**
     * 获取IP当前验证次数
     */
    private int getIpVerifyCount(String ip) {
        try {
            String key = IP_VERIFY_COUNT_PREFIX + ip;
            String countStr = redisTemplate.opsForValue().get(key);
            return countStr != null ? Integer.parseInt(countStr) : 0;
        } catch (Exception e) {
            log.error("获取IP验证次数失败", e);
            return 0;
        }
    }
    
    /**
     * 检查IP验证频率限制
     * @return true表示在限制内，false表示超出限制
     */
    private boolean checkIpRateLimit(String ip) {
        try {
            String key = IP_VERIFY_COUNT_PREFIX + ip;
            String countStr = redisTemplate.opsForValue().get(key);
            
            int count = countStr != null ? Integer.parseInt(countStr) : 0;
            
            if (count >= MAX_VERIFY_PER_IP) {
                return false;
            }
            
            // 增加计数
            redisTemplate.opsForValue().increment(key);
            redisTemplate.expire(key, IP_COUNT_WINDOW, TimeUnit.MINUTES);
            
            return true;
        } catch (Exception e) {
            log.error("检查IP频率失败", e);
            return true; // 出错时允许通过
        }
    }
    
    /**
     * 封禁IP
     */
    private void blockIp(String ip) {
        try {
            String key = IP_BLOCK_PREFIX + ip;
            redisTemplate.opsForValue().set(key, "1", IP_BLOCK_DURATION, TimeUnit.MINUTES);
            log.warn("IP已被封禁{}分钟: {}", IP_BLOCK_DURATION, ip);
        } catch (Exception e) {
            log.error("封禁IP失败", e);
        }
    }
    
    /**
     * 检查浏览器指纹一致性
     * 检测同一IP是否频繁切换浏览器指纹（疑似自动化攻击）
     * @param ip 客户端IP
     * @param fingerprint 当前浏览器指纹
     * @return true表示正常，false表示可疑
     */
    private boolean checkFingerprintConsistency(String ip, String fingerprint) {
        try {
            String key = FINGERPRINT_PREFIX + ip;
            
            // 获取该IP历史使用的指纹列表（用Set存储，JSON序列化）
            String fingerprintsJson = redisTemplate.opsForValue().get(key);
            
            Set<String> fingerprints = new HashSet<>();
            if (fingerprintsJson != null && !fingerprintsJson.isEmpty()) {
                // 简单解析：用逗号分隔
                String[] arr = fingerprintsJson.split(",");
                fingerprints.addAll(Arrays.asList(arr));
            }
            
            // 添加当前指纹
            fingerprints.add(fingerprint);
            
            // 检查是否超过允许的切换次数
            if (fingerprints.size() > MAX_FINGERPRINT_SWITCHES) {
                log.warn("IP {} 使用了过多不同的浏览器指纹: {} 个", ip, fingerprints.size());
                return false;
            }
            
            // 更新指纹列表，保存24小时
            String newFingerprintsJson = String.join(",", fingerprints);
            redisTemplate.opsForValue().set(key, newFingerprintsJson, 24, TimeUnit.HOURS);
            
            return true;
        } catch (Exception e) {
            log.error("检查指纹一致性失败", e);
            return true; // 出错时允许通过
        }
    }
    
    /**
     * 计算平均速度
     */
    private double calculateAverageSpeed(List<Map<String, Object>> mouseTrack) {
        if (mouseTrack == null || mouseTrack.size() < 2) {
            return 0.0;
        }
        
        double totalSpeed = 0.0;
        int validSegments = 0;
        
        for (int i = 1; i < mouseTrack.size(); i++) {
            Map<String, Object> prev = mouseTrack.get(i - 1);
            Map<String, Object> curr = mouseTrack.get(i);
            
            double x1 = getDoubleFromMap(prev, "x", 0.0);
            double y1 = getDoubleFromMap(prev, "y", 0.0);
            long t1 = getLongFromMap(prev, "timestamp", 0L);
            
            double x2 = getDoubleFromMap(curr, "x", 0.0);
            double y2 = getDoubleFromMap(curr, "y", 0.0);
            long t2 = getLongFromMap(curr, "timestamp", 0L);
            
            double distance = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
            long timeDiff = t2 - t1;
            
            if (timeDiff > 0) {
                double speed = distance / timeDiff; // px/ms
                totalSpeed += speed;
                validSegments++;
            }
        }
        
        return validSegments > 0 ? totalSpeed / validSegments : 0.0;
    }
    
    /**
     * 计算速度方差
     */
    private double calculateSpeedVariance(List<Map<String, Object>> mouseTrack, double avgSpeed) {
        if (mouseTrack == null || mouseTrack.size() < 2) {
            return 0.0;
        }
        
        double sumSquaredDiff = 0.0;
        int validSegments = 0;
        
        for (int i = 1; i < mouseTrack.size(); i++) {
            Map<String, Object> prev = mouseTrack.get(i - 1);
            Map<String, Object> curr = mouseTrack.get(i);
            
            double x1 = getDoubleFromMap(prev, "x", 0.0);
            double y1 = getDoubleFromMap(prev, "y", 0.0);
            long t1 = getLongFromMap(prev, "timestamp", 0L);
            
            double x2 = getDoubleFromMap(curr, "x", 0.0);
            double y2 = getDoubleFromMap(curr, "y", 0.0);
            long t2 = getLongFromMap(curr, "timestamp", 0L);
            
            double distance = Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
            long timeDiff = t2 - t1;
            
            if (timeDiff > 0) {
                double speed = distance / timeDiff;
                sumSquaredDiff += Math.pow(speed - avgSpeed, 2);
                validSegments++;
            }
        }
        
        return validSegments > 0 ? Math.sqrt(sumSquaredDiff / validSegments) : 0.0;
    }
    
    /**
     * 计算方向变化次数
     */
    private int calculateDirectionChanges(List<Map<String, Object>> mouseTrack) {
        if (mouseTrack == null || mouseTrack.size() < 3) {
            return 0;
        }
        
        int changes = 0;
        double prevAngle = 0.0;
        
        for (int i = 2; i < mouseTrack.size(); i++) {
            Map<String, Object> p1 = mouseTrack.get(i - 2);
            Map<String, Object> p2 = mouseTrack.get(i - 1);
            Map<String, Object> p3 = mouseTrack.get(i);
            
            double x1 = getDoubleFromMap(p1, "x", 0.0);
            double y1 = getDoubleFromMap(p1, "y", 0.0);
            double x2 = getDoubleFromMap(p2, "x", 0.0);
            double y2 = getDoubleFromMap(p2, "y", 0.0);
            double x3 = getDoubleFromMap(p3, "x", 0.0);
            double y3 = getDoubleFromMap(p3, "y", 0.0);
            
            // 计算两段的角度
            double angle1 = Math.atan2(y2 - y1, x2 - x1);
            double angle2 = Math.atan2(y3 - y2, x3 - x2);
            
            // 角度差异
            double angleDiff = Math.abs(angle2 - angle1);
            
            // 归一化到0-π
            if (angleDiff > Math.PI) {
                angleDiff = 2 * Math.PI - angleDiff;
            }
            
            // 角度变化超过30度认为是方向改变
            if (angleDiff > Math.PI / 6) {
                changes++;
            }
        }
        
        return changes;
    }
    
    /**
     * 计算滑动平均速度
     */
    private double calculateSlideAverageSpeed(List<Map<String, Object>> slideTrack) {
        if (slideTrack == null || slideTrack.size() < 2) {
            return 0.0;
        }
        
        double totalSpeed = 0.0;
        int validSegments = 0;
        
        for (int i = 1; i < slideTrack.size(); i++) {
            Map<String, Object> prev = slideTrack.get(i - 1);
            Map<String, Object> curr = slideTrack.get(i);
            
            double x1 = getDoubleFromMap(prev, "x", 0.0);
            double x2 = getDoubleFromMap(curr, "x", 0.0);
            long t1 = getLongFromMap(prev, "timestamp", 0L);
            long t2 = getLongFromMap(curr, "timestamp", 0L);
            
            double distance = Math.abs(x2 - x1);
            long timeDiff = t2 - t1;
            
            if (timeDiff > 0) {
                double speed = distance / timeDiff; // px/ms
                totalSpeed += speed;
                validSegments++;
            }
        }
        
        return validSegments > 0 ? totalSpeed / validSegments : 0.0;
    }
    
    /**
     * 计算滑动速度方差
     */
    private double calculateSlideSpeedVariance(List<Map<String, Object>> slideTrack, double avgSpeed) {
        if (slideTrack == null || slideTrack.size() < 2) {
            return 0.0;
        }
        
        double sumSquaredDiff = 0.0;
        int validSegments = 0;
        
        for (int i = 1; i < slideTrack.size(); i++) {
            Map<String, Object> prev = slideTrack.get(i - 1);
            Map<String, Object> curr = slideTrack.get(i);
            
            double x1 = getDoubleFromMap(prev, "x", 0.0);
            double x2 = getDoubleFromMap(curr, "x", 0.0);
            long t1 = getLongFromMap(prev, "timestamp", 0L);
            long t2 = getLongFromMap(curr, "timestamp", 0L);
            
            double distance = Math.abs(x2 - x1);
            long timeDiff = t2 - t1;
            
            if (timeDiff > 0) {
                double speed = distance / timeDiff;
                sumSquaredDiff += Math.pow(speed - avgSpeed, 2);
                validSegments++;
            }
        }
        
        return validSegments > 0 ? Math.sqrt(sumSquaredDiff / validSegments) : 0.0;
    }
    
    /**
     * 计算回退次数（向后滑动的次数）
     */
    private int calculateBacktrackCount(List<Map<String, Object>> slideTrack) {
        if (slideTrack == null || slideTrack.size() < 2) {
            return 0;
        }
        
        int backtrackCount = 0;
        
        for (int i = 1; i < slideTrack.size(); i++) {
            double prevX = getDoubleFromMap(slideTrack.get(i - 1), "x", 0.0);
            double currX = getDoubleFromMap(slideTrack.get(i), "x", 0.0);
            
            // 向左滑动（回退）
            if (currX < prevX) {
                backtrackCount++;
            }
        }
        
        return backtrackCount;
    }
    
    /**
     * 计算平均加速度
     */
    private double calculateAverageAcceleration(List<Map<String, Object>> slideTrack) {
        if (slideTrack == null || slideTrack.size() < 3) {
            return 0.0;
        }
        
        double totalAcceleration = 0.0;
        int validSegments = 0;
        
        for (int i = 2; i < slideTrack.size(); i++) {
            Map<String, Object> p1 = slideTrack.get(i - 2);
            Map<String, Object> p2 = slideTrack.get(i - 1);
            Map<String, Object> p3 = slideTrack.get(i);
            
            double x1 = getDoubleFromMap(p1, "x", 0.0);
            double x2 = getDoubleFromMap(p2, "x", 0.0);
            double x3 = getDoubleFromMap(p3, "x", 0.0);
            long t1 = getLongFromMap(p1, "timestamp", 0L);
            long t2 = getLongFromMap(p2, "timestamp", 0L);
            long t3 = getLongFromMap(p3, "timestamp", 0L);
            
            // 计算两段的速度
            double v1 = (t2 - t1) > 0 ? (x2 - x1) / (t2 - t1) : 0;
            double v2 = (t3 - t2) > 0 ? (x3 - x2) / (t3 - t2) : 0;
            
            // 计算加速度
            long timeDiff = t3 - t1;
            if (timeDiff > 0) {
                double acceleration = (v2 - v1) / timeDiff;
                totalAcceleration += Math.abs(acceleration);
                validSegments++;
            }
        }
        
        return validSegments > 0 ? totalAcceleration / validSegments : 0.0;
    }
    
    /**
     * 从Map获取Double值
     */
    private double getDoubleFromMap(Map<String, Object> map, String key, double defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }
    
    /**
     * 从Map获取Long值
     */
    private long getLongFromMap(Map<String, Object> map, String key, long defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return defaultValue;
    }
    
    /**
     * 从Map获取整数值
     */
    private int getIntValue(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }
    
    /**
     * 从Map获取双精度值
     */
    private double getDoubleValue(Map<String, Object> map, String key, double defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof BigDecimal) {
            return ((BigDecimal) value).doubleValue();
        }
        return defaultValue;
    }
    
    /**
     * 解除IP封禁
     */
    @Override
    public boolean unblockIp(String ip) {
        try {
            String blockKey = IP_BLOCK_PREFIX + ip;
            String countKey = IP_VERIFY_COUNT_PREFIX + ip;
            
            // 删除封禁记录
            Boolean blockDeleted = redisTemplate.delete(blockKey);
            // 删除计数记录
            Boolean countDeleted = redisTemplate.delete(countKey);
            
            if (Boolean.TRUE.equals(blockDeleted)) {
                log.info("IP封禁已解除: {}, 计数记录也已清除: {}", ip, countDeleted);
                return true;
            } else {
                log.info("IP未被封禁: {}", ip);
                return false;
            }
        } catch (Exception e) {
            log.error("解除IP封禁失败: {}", ip, e);
            return false;
        }
    }
    
    /**
     * 获取所有被封禁的IP列表
     */
    @Override
    public List<Map<String, Object>> getBlockedIpList() {
        List<Map<String, Object>> blockedList = new ArrayList<>();
        
        try {
            // 获取所有封禁IP的key
            Set<String> keys = redisTemplate.keys(IP_BLOCK_PREFIX + "*");
            
            if (keys != null && !keys.isEmpty()) {
                for (String key : keys) {
                    String ip = key.replace(IP_BLOCK_PREFIX, "");
                    
                    // 获取剩余时间（秒）
                    Long ttlSeconds = redisTemplate.getExpire(key, TimeUnit.SECONDS);
                    
                    if (ttlSeconds != null && ttlSeconds > 0) {
                        Map<String, Object> ipInfo = new HashMap<>();
                        ipInfo.put("ip", ip);
                        ipInfo.put("remainingSeconds", ttlSeconds);
                        ipInfo.put("remainingMinutes", (ttlSeconds + 59) / 60); // 向上取整
                        
                        // 获取验证失败次数（如果还在计数窗口内）
                        String countKey = IP_VERIFY_COUNT_PREFIX + ip;
                        String countStr = redisTemplate.opsForValue().get(countKey);
                        int failCount = countStr != null ? Integer.parseInt(countStr) : 0;
                        ipInfo.put("failCount", failCount);
                        
                        blockedList.add(ipInfo);
                    }
                }
                
                // 按剩余时间降序排序（时间长的在前）
                blockedList.sort((a, b) -> {
                    Long timeA = (Long) a.get("remainingSeconds");
                    Long timeB = (Long) b.get("remainingSeconds");
                    return timeB.compareTo(timeA);
                });
            }
            
            return blockedList;
        } catch (Exception e) {
            log.error("获取封禁IP列表失败", e);
            return blockedList;
        }
    }
}

