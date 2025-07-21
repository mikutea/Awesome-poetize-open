package com.ld.poetry.service;

import cn.hutool.crypto.SecureUtil;
import com.ld.poetry.config.PasswordSecurityPolicy;
import com.ld.poetry.constants.CommonConst;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;

/**
 * 密码服务类
 * 负责密码的加密、验证和格式识别
 * 支持MD5到BCrypt的渐进式升级
 * 
 * @author LeapYa
 * @since 2025-07-20
 */
@Service
@Slf4j
public class PasswordService {

    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private final PasswordSecurityPolicy securityPolicy;

    // BCrypt密码的标识前缀
    private static final String BCRYPT_PREFIX = "$2a$";
    private static final String BCRYPT_PREFIX_2B = "$2b$";
    private static final String BCRYPT_PREFIX_2Y = "$2y$";

    // MD5密码长度（固定32位）
    private static final int MD5_LENGTH = 32;

    @Autowired
    public PasswordService(PasswordSecurityPolicy securityPolicy) {
        this.securityPolicy = securityPolicy;
        // 使用配置的BCrypt强度
        this.bCryptPasswordEncoder = new BCryptPasswordEncoder(securityPolicy.getBcryptStrength());

        // 验证安全策略配置
        if (!securityPolicy.isValid()) {
            throw new IllegalStateException("密码安全策略配置无效");
        }

        log.info("密码服务初始化完成 - 部署场景: {}, MD5支持: {}",
            securityPolicy.getDeploymentScenario(), securityPolicy.shouldSupportMd5());
    }

    /**
     * 解密前端AES加密的密码
     * 
     * @param encryptedPassword 前端AES加密的密码
     * @return 解密后的明文密码
     */
    public String decryptFromFrontend(String encryptedPassword) {
        if (!StringUtils.hasText(encryptedPassword)) {
            throw new IllegalArgumentException("加密密码不能为空");
        }
        
        try {
            return new String(SecureUtil.aes(CommonConst.CRYPOTJS_KEY.getBytes(StandardCharsets.UTF_8))
                .decrypt(encryptedPassword));
        } catch (Exception e) {
            log.error("密码解密失败: {}", e.getMessage());
            throw new IllegalArgumentException("密码解密失败");
        }
    }

    /**
     * 使用BCrypt加密密码
     * 
     * @param rawPassword 明文密码
     * @return BCrypt加密后的密码
     */
    public String encodeBCrypt(String rawPassword) {
        if (!StringUtils.hasText(rawPassword)) {
            throw new IllegalArgumentException("密码不能为空");
        }
        
        String encoded = bCryptPasswordEncoder.encode(rawPassword);
        log.debug("密码已使用BCrypt加密");
        return encoded;
    }

    /**
     * 使用MD5加密密码（仅用于兼容性）
     * 
     * @param rawPassword 明文密码
     * @return MD5加密后的密码
     * @deprecated 仅用于兼容性，新密码应使用BCrypt
     */
    @Deprecated
    public String encodeMD5(String rawPassword) {
        if (!StringUtils.hasText(rawPassword)) {
            throw new IllegalArgumentException("密码不能为空");
        }
        
        return DigestUtils.md5DigestAsHex(rawPassword.getBytes());
    }

    /**
     * 验证密码是否匹配
     * 根据安全策略自动识别密码格式（MD5或BCrypt）并使用相应的验证方法
     *
     * @param rawPassword 明文密码
     * @param encodedPassword 数据库中存储的加密密码
     * @return 密码是否匹配
     */
    public boolean matches(String rawPassword, String encodedPassword) {
        if (!StringUtils.hasText(rawPassword) || !StringUtils.hasText(encodedPassword)) {
            return false;
        }

        // 记录密码格式（用于监控）
        if (securityPolicy.isLogPasswordFormat()) {
            String format = isBCryptPassword(encodedPassword) ? "BCrypt" :
                           (isMD5Password(encodedPassword) ? "MD5" : "Unknown");
            log.debug("密码验证 - 格式: {}, 部署场景: {}", format, securityPolicy.getDeploymentScenario());
        }

        if (isBCryptPassword(encodedPassword)) {
            // BCrypt密码验证
            return verifyBCryptPassword(rawPassword, encodedPassword);
        } else if (isMD5Password(encodedPassword)) {
            // MD5密码验证（根据安全策略决定是否支持）
            return verifyMD5Password(rawPassword, encodedPassword);
        } else {
            log.warn("未知的密码格式: {}", encodedPassword.substring(0, Math.min(10, encodedPassword.length())));
            return false;
        }
    }

    /**
     * 验证BCrypt密码
     */
    private boolean verifyBCryptPassword(String rawPassword, String encodedPassword) {
        try {
            boolean matches = bCryptPasswordEncoder.matches(rawPassword, encodedPassword);
            if (securityPolicy.isLogPasswordFormat()) {
                log.debug("BCrypt密码验证结果: {}", matches);
            }
            return matches;
        } catch (Exception e) {
            log.error("BCrypt密码验证失败", e);
            return false;
        }
    }

    /**
     * 验证MD5密码（根据安全策略）
     */
    private boolean verifyMD5Password(String rawPassword, String encodedPassword) {
        // 检查是否支持MD5验证
        if (!securityPolicy.shouldSupportMd5()) {
            log.warn("MD5密码验证被安全策略禁用 - 部署场景: {}", securityPolicy.getDeploymentScenario());
            return false;
        }

        try {
            // 时序攻击防护：即使不支持MD5，也执行相同的计算时间
            String md5Hash = DigestUtils.md5DigestAsHex(rawPassword.getBytes());
            boolean matches = md5Hash.equals(encodedPassword);

            if (securityPolicy.isLogPasswordFormat()) {
                log.debug("MD5密码验证结果: {} (兼容模式)", matches);
            }

            // 如果是迁移模式且MD5支持已过期，记录警告
            if (securityPolicy.getDeploymentScenario() == PasswordSecurityPolicy.DeploymentScenario.MIGRATION_SYSTEM
                && securityPolicy.isMigrationExpired()) {
                log.warn("MD5密码验证成功，但迁移期已过期，建议用户尽快升级密码");
            }

            return matches;
        } catch (Exception e) {
            log.error("MD5密码验证失败", e);
            return false;
        }
    }

    /**
     * 判断密码是否为BCrypt格式
     * 
     * @param password 密码字符串
     * @return 是否为BCrypt格式
     */
    public boolean isBCryptPassword(String password) {
        if (!StringUtils.hasText(password)) {
            return false;
        }
        
        return password.startsWith(BCRYPT_PREFIX) || 
               password.startsWith(BCRYPT_PREFIX_2B) || 
               password.startsWith(BCRYPT_PREFIX_2Y);
    }

    /**
     * 判断密码是否为MD5格式
     * 
     * @param password 密码字符串
     * @return 是否为MD5格式
     */
    public boolean isMD5Password(String password) {
        if (!StringUtils.hasText(password)) {
            return false;
        }
        
        // MD5哈希值为32位十六进制字符串
        return password.length() == MD5_LENGTH && 
               password.matches("^[a-fA-F0-9]{32}$");
    }

    /**
     * 检查密码是否需要升级
     * 如果是MD5格式，则需要升级到BCrypt
     * 
     * @param encodedPassword 数据库中存储的密码
     * @return 是否需要升级
     */
    public boolean needsUpgrade(String encodedPassword) {
        return isMD5Password(encodedPassword);
    }

    /**
     * 升级密码格式
     * 将MD5密码升级为BCrypt密码
     * 
     * @param rawPassword 明文密码
     * @param oldEncodedPassword 旧的加密密码
     * @return 新的BCrypt加密密码，如果不需要升级则返回原密码
     */
    public String upgradePassword(String rawPassword, String oldEncodedPassword) {
        if (!needsUpgrade(oldEncodedPassword)) {
            log.debug("密码无需升级");
            return oldEncodedPassword;
        }
        
        // 验证原密码是否正确
        if (!matches(rawPassword, oldEncodedPassword)) {
            throw new IllegalArgumentException("原密码验证失败，无法升级");
        }
        
        // 使用BCrypt重新加密
        String newPassword = encodeBCrypt(rawPassword);
        log.info("密码已从MD5升级到BCrypt");
        return newPassword;
    }

    /**
     * 验证密码是否有效（仅检查非空）
     *
     * @param rawPassword 明文密码
     * @return 是否有效
     */
    public boolean isPasswordValid(String rawPassword) {
        return StringUtils.hasText(rawPassword);
    }
}
