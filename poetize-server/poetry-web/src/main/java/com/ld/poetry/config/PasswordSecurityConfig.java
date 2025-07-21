package com.ld.poetry.config;

import com.ld.poetry.service.PasswordService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

/**
 * 密码安全配置类
 * 负责密码安全策略的配置和管理
 * 
 * @author LeapYa
 * @since 2025-07-20
 */
@Configuration
@Slf4j
public class PasswordSecurityConfig {

    @Autowired
    private PasswordService passwordService;

    /**
     * 应用启动完成后的初始化操作
     */
    @EventListener(ApplicationReadyEvent.class)
    @Async
    public void onApplicationReady() {
        log.info("=== 密码安全系统初始化 ===");
        log.info("BCrypt密码编码器已启用，强度级别: 12");
        log.info("支持MD5到BCrypt的渐进式升级");
        log.info("密码强度验证已启用");
        log.info("=== 密码安全系统初始化完成 ===");
    }

    /**
     * 获取密码安全配置信息
     * 
     * @return 配置信息字符串
     */
    public String getSecurityInfo() {
        StringBuilder info = new StringBuilder();
        info.append("密码安全配置:\n");
        info.append("- BCrypt强度级别: 12\n");
        info.append("- 支持格式: MD5(兼容), BCrypt(推荐)\n");
        info.append("- 自动升级: 启用\n");
        info.append("- 密码强度验证: 启用\n");
        return info.toString();
    }

    /**
     * 验证密码安全配置是否正确
     * 
     * @return 配置是否正确
     */
    public boolean validateConfiguration() {
        try {
            // 测试BCrypt编码
            String testPassword = "TestPassword123!";
            String encoded = passwordService.encodeBCrypt(testPassword);
            boolean matches = passwordService.matches(testPassword, encoded);
            
            if (!matches) {
                log.error("BCrypt密码编码验证失败");
                return false;
            }
            
            // 测试MD5兼容性
            String md5Hash = "5d41402abc4b2a76b9719d911017c592"; // "hello"的MD5
            boolean md5Matches = passwordService.matches("hello", md5Hash);
            
            if (!md5Matches) {
                log.error("MD5密码兼容性验证失败");
                return false;
            }
            
            // 测试密码格式识别
            if (!passwordService.isBCryptPassword(encoded)) {
                log.error("BCrypt密码格式识别失败");
                return false;
            }
            
            if (!passwordService.isMD5Password(md5Hash)) {
                log.error("MD5密码格式识别失败");
                return false;
            }
            
            log.info("密码安全配置验证通过");
            return true;
            
        } catch (Exception e) {
            log.error("密码安全配置验证失败: {}", e.getMessage());
            return false;
        }
    }
}
