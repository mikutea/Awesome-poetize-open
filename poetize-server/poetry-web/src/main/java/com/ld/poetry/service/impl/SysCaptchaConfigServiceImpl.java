package com.ld.poetry.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ld.poetry.dao.SysCaptchaConfigMapper;
import com.ld.poetry.entity.SysCaptchaConfig;
import com.ld.poetry.service.SysCaptchaConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 验证码配置服务实现
 */
@Slf4j
@Service
public class SysCaptchaConfigServiceImpl extends ServiceImpl<SysCaptchaConfigMapper, SysCaptchaConfig> 
        implements SysCaptchaConfigService {
    
    @Override
    public Map<String, Object> getCaptchaConfig() {
        // 获取配置（只有一条记录，取ID=1或第一条）
        SysCaptchaConfig entity = getById(1);
        if (entity == null) {
            entity = lambdaQuery().last("LIMIT 1").one();
        }
        
        if (entity == null) {
            log.warn("验证码配置不存在，返回默认配置");
            return getDefaultConfig();
        }
        
        return convertToMap(entity);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean saveCaptchaConfig(Map<String, Object> config) {
        try {
            SysCaptchaConfig entity = convertToEntity(config);
            
            // 获取现有配置
            SysCaptchaConfig existing = getById(1);
            
            if (existing != null) {
                entity.setId(1);
                updateById(entity);
            } else {
                entity.setId(1);
                save(entity);
            }
            
            log.info("验证码配置保存成功");
            return true;
        } catch (Exception e) {
            log.error("保存验证码配置失败", e);
            throw new RuntimeException("保存验证码配置失败: " + e.getMessage());
        }
    }
    
    /**
     * 转换Entity到Map
     */
    private Map<String, Object> convertToMap(SysCaptchaConfig entity) {
        Map<String, Object> config = new HashMap<>();
        
        config.put("enable", entity.getEnable());
        config.put("login", entity.getLogin());
        config.put("register", entity.getRegister());
        config.put("comment", entity.getComment());
        config.put("reset_password", entity.getResetPassword());
        config.put("screenSizeThreshold", entity.getScreenSizeThreshold());
        config.put("forceSlideForMobile", entity.getForceSlideForMobile());
        
        // 滑动验证码配置
        Map<String, Object> slide = new HashMap<>();
        slide.put("accuracy", entity.getSlideAccuracy());
        slide.put("successThreshold", entity.getSlideSuccessThreshold());
        config.put("slide", slide);
        
        // 勾选验证码配置
        Map<String, Object> checkbox = new HashMap<>();
        checkbox.put("trackSensitivity", entity.getCheckboxTrackSensitivity());
        checkbox.put("minTrackPoints", entity.getCheckboxMinTrackPoints());
        checkbox.put("replyCommentSensitivity", entity.getCheckboxReplySensitivity());
        checkbox.put("maxRetryCount", entity.getCheckboxMaxRetryCount());
        checkbox.put("retryDecrement", entity.getCheckboxRetryDecrement());
        config.put("checkbox", checkbox);
        
        return config;
    }
    
    /**
     * 转换Map到Entity
     */
    private SysCaptchaConfig convertToEntity(Map<String, Object> config) {
        SysCaptchaConfig entity = new SysCaptchaConfig();
        
        entity.setEnable(getBoolean(config, "enable", false));
        entity.setLogin(getBoolean(config, "login", true));
        entity.setRegister(getBoolean(config, "register", true));
        entity.setComment(getBoolean(config, "comment", false));
        entity.setResetPassword(getBoolean(config, "reset_password", true));
        entity.setScreenSizeThreshold(getInteger(config, "screenSizeThreshold", 768));
        entity.setForceSlideForMobile(getBoolean(config, "forceSlideForMobile", true));
        
        // 滑动验证码配置
        if (config.containsKey("slide") && config.get("slide") instanceof Map) {
            Map<String, Object> slide = (Map<String, Object>) config.get("slide");
            entity.setSlideAccuracy(getInteger(slide, "accuracy", 5));
            entity.setSlideSuccessThreshold(getBigDecimal(slide, "successThreshold", "0.95"));
        } else {
            entity.setSlideAccuracy(5);
            entity.setSlideSuccessThreshold(new BigDecimal("0.95"));
        }
        
        // 勾选验证码配置
        if (config.containsKey("checkbox") && config.get("checkbox") instanceof Map) {
            Map<String, Object> checkbox = (Map<String, Object>) config.get("checkbox");
            entity.setCheckboxTrackSensitivity(getBigDecimal(checkbox, "trackSensitivity", "0.90"));
            entity.setCheckboxMinTrackPoints(getInteger(checkbox, "minTrackPoints", 2));
            entity.setCheckboxReplySensitivity(getBigDecimal(checkbox, "replyCommentSensitivity", "0.85"));
            entity.setCheckboxMaxRetryCount(getInteger(checkbox, "maxRetryCount", 5));
            entity.setCheckboxRetryDecrement(getBigDecimal(checkbox, "retryDecrement", "0.02"));
        } else {
            entity.setCheckboxTrackSensitivity(new BigDecimal("0.90"));
            entity.setCheckboxMinTrackPoints(2);
            entity.setCheckboxReplySensitivity(new BigDecimal("0.85"));
            entity.setCheckboxMaxRetryCount(5);
            entity.setCheckboxRetryDecrement(new BigDecimal("0.02"));
        }
        
        return entity;
    }
    
    /**
     * 获取默认配置
     */
    private Map<String, Object> getDefaultConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("enable", false);
        config.put("login", true);
        config.put("register", true);
        config.put("comment", false);
        config.put("reset_password", true);
        config.put("screenSizeThreshold", 768);
        config.put("forceSlideForMobile", true);
        
        Map<String, Object> slide = new HashMap<>();
        slide.put("accuracy", 5);
        slide.put("successThreshold", 0.95);
        config.put("slide", slide);
        
        Map<String, Object> checkbox = new HashMap<>();
        checkbox.put("trackSensitivity", 0.90);
        checkbox.put("minTrackPoints", 2);
        checkbox.put("replyCommentSensitivity", 0.85);
        checkbox.put("maxRetryCount", 5);
        checkbox.put("retryDecrement", 0.02);
        config.put("checkbox", checkbox);
        
        return config;
    }
    
    // 工具方法
    private Boolean getBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        if (!map.containsKey(key)) return defaultValue;
        Object value = map.get(key);
        if (value instanceof Boolean) return (Boolean) value;
        if (value != null) {
            String str = value.toString().toLowerCase();
            return "true".equals(str) || "1".equals(str);
        }
        return defaultValue;
    }
    
    private Integer getInteger(Map<String, Object> map, String key, int defaultValue) {
        if (!map.containsKey(key)) return defaultValue;
        Object value = map.get(key);
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception e) {
            return defaultValue;
        }
    }
    
    private BigDecimal getBigDecimal(Map<String, Object> map, String key, String defaultValue) {
        if (!map.containsKey(key)) return new BigDecimal(defaultValue);
        Object value = map.get(key);
        if (value instanceof Number) return new BigDecimal(value.toString());
        try {
            return new BigDecimal(value.toString());
        } catch (Exception e) {
            return new BigDecimal(defaultValue);
        }
    }
}

