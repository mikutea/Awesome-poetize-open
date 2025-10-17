package com.ld.poetry.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ld.poetry.entity.SysCaptchaConfig;

import java.util.Map;

/**
 * 验证码配置服务接口
 */
public interface SysCaptchaConfigService extends IService<SysCaptchaConfig> {
    
    /**
     * 获取验证码配置
     * @return 配置Map
     */
    Map<String, Object> getCaptchaConfig();
    
    /**
     * 保存验证码配置
     * @param config 配置Map
     * @return 保存是否成功
     */
    boolean saveCaptchaConfig(Map<String, Object> config);
}

