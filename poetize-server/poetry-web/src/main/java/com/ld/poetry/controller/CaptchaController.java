package com.ld.poetry.controller;

import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.service.CaptchaService;
import com.ld.poetry.utils.IpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 验证码验证Controller
 */
@Slf4j
@RestController
@RequestMapping("/captcha")
public class CaptchaController {
    
    @Autowired
    private CaptchaService captchaService;
    
    /**
     * 检查是否需要验证码
     * GET /captcha/validate?action=login
     */
    @GetMapping("/validate")
    public PoetryResult<Map<String, Boolean>> validateCaptcha(@RequestParam("action") String action) {
        try {
            boolean required = captchaService.isCaptchaRequired(action);
            
            Map<String, Boolean> data = new HashMap<>();
            data.put("required", required);
            
            log.debug("验证码检查 - 操作: {}, 需要验证: {}", action, required);
            return PoetryResult.success(data);
        } catch (Exception e) {
            log.error("检查验证码需求失败", e);
            // 出错时默认不需要验证码
            Map<String, Boolean> data = new HashMap<>();
            data.put("required", false);
            return PoetryResult.success(data);
        }
    }
    
    /**
     * 验证复选框验证码
     * POST /captcha/verify-checkbox
     */
    @PostMapping("/verify-checkbox")
    public PoetryResult<Map<String, Object>> verifyCheckboxCaptcha(
            @RequestBody Map<String, Object> data,
            HttpServletRequest request) {
        try {
            // 获取验证数据
            List<Map<String, Object>> mouseTrack = (List<Map<String, Object>>) data.get("mouseTrack");
            Double straightRatio = getDoubleValue(data, "straightRatio", 1.0);
            Boolean isReplyComment = getBooleanValue(data, "isReplyComment", false);
            Integer retryCount = getIntValue(data, "retryCount", 0);
            Double frontendSensitivity = getDoubleValue(data, "trackSensitivity", null);
            Integer frontendMinPoints = getIntValue(data, "minTrackPoints", null);
            Long clickDelay = getLongValue(data, "clickDelay", null);
            String browserFingerprint = (String) data.get("browserFingerprint");
            
            // 获取客户端IP
            String clientIp = IpUtil.getClientRealIp(request);
            
            log.info("验证请求 - IP: {}, 指纹: {}, 回复评论: {}, 重试: {}, 轨迹点: {}, 点击延迟: {}ms", 
                    clientIp, browserFingerprint != null ? browserFingerprint.substring(0, Math.min(8, browserFingerprint.length())) + "..." : "null",
                    isReplyComment, retryCount, 
                    mouseTrack != null ? mouseTrack.size() : 0, clickDelay);
            
            // 执行验证
            Map<String, Object> result = captchaService.verifyCheckboxCaptcha(
                mouseTrack, straightRatio, isReplyComment, retryCount, 
                frontendSensitivity, frontendMinPoints, clickDelay, browserFingerprint, clientIp
            );
            
            return PoetryResult.success(result);
        } catch (Exception e) {
            log.error("复选框验证失败", e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("token", "");
            return PoetryResult.success(errorResult);
        }
    }
    
    /**
     * 验证令牌
     * POST /captcha/verify-token
     */
    @PostMapping("/verify-token")
    public PoetryResult<Map<String, Boolean>> verifyToken(@RequestBody Map<String, Object> data) {
        try {
            String token = (String) data.get("token");
            boolean valid = captchaService.verifyToken(token);
            
            Map<String, Boolean> result = new HashMap<>();
            result.put("valid", valid);
            
            return PoetryResult.success(result);
        } catch (Exception e) {
            log.error("验证令牌失败", e);
            Map<String, Boolean> result = new HashMap<>();
            result.put("valid", false);
            return PoetryResult.success(result);
        }
    }
    
    /**
     * 验证滑动验证码
     * POST /captcha/verify-slide
     */
    @PostMapping("/verify-slide")
    public PoetryResult<Map<String, Object>> verifySlideCaptcha(
            @RequestBody Map<String, Object> data,
            HttpServletRequest request) {
        try {
            // 获取验证数据
            List<Map<String, Object>> slideTrack = (List<Map<String, Object>>) data.get("slideTrack");
            Long totalTime = getLongValue(data, "totalTime", null);
            Double maxDistance = getDoubleValue(data, "maxDistance", null);
            Double finalPosition = getDoubleValue(data, "finalPosition", null);
            String browserFingerprint = (String) data.get("browserFingerprint");
            
            // 获取客户端IP
            String clientIp = IpUtil.getClientRealIp(request);
            
            log.info("滑动验证请求 - IP: {}, 指纹: {}, 轨迹点: {}, 总耗时: {}ms, 距离: {}/{}", 
                    clientIp, 
                    browserFingerprint != null ? browserFingerprint.substring(0, Math.min(8, browserFingerprint.length())) + "..." : "null",
                    slideTrack != null ? slideTrack.size() : 0, 
                    totalTime, finalPosition, maxDistance);
            
            // 执行验证
            Map<String, Object> result = captchaService.verifySlideCaptcha(
                slideTrack, totalTime, maxDistance, finalPosition, browserFingerprint, clientIp
            );
            
            return PoetryResult.success(result);
        } catch (Exception e) {
            log.error("滑动验证失败", e);
            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("success", false);
            errorResult.put("message", "验证失败");
            return PoetryResult.success(errorResult);
        }
    }
    
    /**
     * 获取公共验证码配置
     * GET /captcha/getConfig
     */
    @GetMapping("/getConfig")
    public PoetryResult<Map<String, Object>> getPublicConfig() {
        try {
            Map<String, Object> config = captchaService.getPublicCaptchaConfig();
            return PoetryResult.success(config);
        } catch (Exception e) {
            log.error("获取公共验证码配置失败", e);
            return PoetryResult.fail("获取配置失败: " + e.getMessage());
        }
    }
    
    // 工具方法
    private Double getDoubleValue(Map<String, Object> map, String key, Double defaultValue) {
        if (!map.containsKey(key)) return defaultValue;
        Object value = map.get(key);
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    private Long getLongValue(Map<String, Object> map, String key, Long defaultValue) {
        if (!map.containsKey(key)) return defaultValue;
        Object value = map.get(key);
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return Long.parseLong(value.toString());
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    private Integer getIntValue(Map<String, Object> map, String key, Integer defaultValue) {
        if (!map.containsKey(key)) return defaultValue;
        Object value = map.get(key);
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    private Boolean getBooleanValue(Map<String, Object> map, String key, boolean defaultValue) {
        if (!map.containsKey(key)) return defaultValue;
        Object value = map.get(key);
        if (value instanceof Boolean) return (Boolean) value;
        if (value != null) {
            String str = value.toString().toLowerCase();
            return "true".equals(str) || "1".equals(str);
        }
        return defaultValue;
    }
}

