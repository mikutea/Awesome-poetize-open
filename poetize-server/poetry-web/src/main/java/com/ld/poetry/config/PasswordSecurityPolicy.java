package com.ld.poetry.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 密码安全策略配置
 * 基于部署场景的简化配置设计，消除配置冲突和混淆
 *
 * @author LeapYa
 * @since 2025-07-20
 */
@Component
@ConfigurationProperties(prefix = "poetize.security.password")
@Data
@Slf4j
public class PasswordSecurityPolicy {

    /**
     * 部署场景枚举
     * 每种场景都有明确的安全策略和行为定义
     */
    public enum DeploymentScenario {
        /**
         * 全新系统部署
         * - 仅支持BCrypt加密
         * - 完全禁用MD5支持
         * - 无需密码升级功能
         * - 最高安全级别
         */
        NEW_SYSTEM("new-system"),

        /**
         * 现有系统升级
         * - 支持MD5和BCrypt混合验证
         * - 自动升级MD5密码到BCrypt
         * - 升级失败允许登录（保证用户体验）
         * - 记录升级统计
         */
        EXISTING_SYSTEM("existing-system"),

        /**
         * 迁移系统
         * - 支持MD5和BCrypt混合验证
         * - 有时间限制的MD5支持
         * - 自动升级MD5密码到BCrypt
         * - 过期后禁用MD5支持
         */
        MIGRATION_SYSTEM("migration-system");

        private final String configValue;

        DeploymentScenario(String configValue) {
            this.configValue = configValue;
        }

        public String getConfigValue() {
            return configValue;
        }

        public static DeploymentScenario fromConfigValue(String configValue) {
            for (DeploymentScenario scenario : values()) {
                if (scenario.configValue.equals(configValue)) {
                    return scenario;
                }
            }
            throw new IllegalArgumentException("未知的部署场景: " + configValue);
        }
    }

    /**
     * 部署场景，默认为新系统部署
     */
    private String deploymentScenario = "new-system";

    /**
     * BCrypt强度级别（4-31）
     */
    private int bcryptStrength = 12;

    /**
     * 迁移系统的MD5支持截止时间（仅在migration-system模式下有效）
     * 格式：yyyy-MM-dd HH:mm:ss
     */
    private String migrationEndTime;



    /**
     * 是否记录密码格式信息（硬编码默认值）
     */
    public boolean isLogPasswordFormat() {
        // 在开发和测试环境记录，生产环境可以关闭
        return true;
    }

    /**
     * 是否启用时序攻击防护（硬编码默认值）
     */
    public boolean isEnableTimingAttackProtection() {
        // 始终启用时序攻击防护
        return true;
    }

    /**
     * 获取升级重试次数（硬编码默认值）
     */
    public int getUpgradeRetryAttempts() {
        return 3;
    }



    /**
     * 获取当前部署场景
     */
    public DeploymentScenario getDeploymentScenario() {
        try {
            return DeploymentScenario.fromConfigValue(deploymentScenario);
        } catch (Exception e) {
            log.warn("无效的部署场景配置: {}, 使用默认值: new-system", deploymentScenario);
            return DeploymentScenario.NEW_SYSTEM;
        }
    }

    /**
     * 检查是否应该支持MD5
     */
    public boolean shouldSupportMd5() {
        DeploymentScenario scenario = getDeploymentScenario();
        switch (scenario) {
            case NEW_SYSTEM:
                return false;  // 新系统完全禁用MD5
            case EXISTING_SYSTEM:
                return true;   // 现有系统支持MD5
            case MIGRATION_SYSTEM:
                return !isMigrationExpired();  // 迁移系统检查是否过期
            default:
                return false;
        }
    }

    /**
     * 检查是否启用自动升级
     */
    public boolean isAutoUpgradeEnabled() {
        DeploymentScenario scenario = getDeploymentScenario();
        return scenario == DeploymentScenario.EXISTING_SYSTEM ||
               scenario == DeploymentScenario.MIGRATION_SYSTEM;
    }

    /**
     * 检查升级失败时是否允许登录
     */
    public boolean isAllowLoginOnUpgradeFailure() {
        DeploymentScenario scenario = getDeploymentScenario();
        return scenario == DeploymentScenario.EXISTING_SYSTEM ||
               scenario == DeploymentScenario.MIGRATION_SYSTEM;
    }

    /**
     * 检查是否启用升级统计
     */
    public boolean isUpgradeStatisticsEnabled() {
        DeploymentScenario scenario = getDeploymentScenario();
        return scenario == DeploymentScenario.EXISTING_SYSTEM ||
               scenario == DeploymentScenario.MIGRATION_SYSTEM;
    }

    /**
     * 检查迁移是否已过期（仅在MIGRATION_SYSTEM模式下）
     */
    public boolean isMigrationExpired() {
        if (getDeploymentScenario() != DeploymentScenario.MIGRATION_SYSTEM) {
            return false;
        }

        if (migrationEndTime == null) {
            return false;
        }

        try {
            LocalDateTime endTime = LocalDateTime.parse(migrationEndTime,
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            return LocalDateTime.now().isAfter(endTime);
        } catch (Exception e) {
            log.error("解析迁移截止时间失败: {}", migrationEndTime, e);
            return true; // 解析失败时认为已过期，更安全
        }
    }

    /**
     * 验证配置是否有效
     */
    public boolean isValid() {
        try {
            // 验证部署场景
            getDeploymentScenario(); // 这会验证deploymentScenario的有效性

            // 验证BCrypt强度
            if (bcryptStrength < 4 || bcryptStrength > 31) {
                log.error("BCrypt强度级别无效: {}, 必须在4-31之间", bcryptStrength);
                return false;
            }

            // 密码强度验证已移除，无需验证相关配置

            // 验证迁移时间配置（MIGRATION_SYSTEM模式）
            if (getDeploymentScenario() == DeploymentScenario.MIGRATION_SYSTEM &&
                migrationEndTime != null) {
                try {
                    LocalDateTime.parse(migrationEndTime,
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                } catch (Exception e) {
                    log.error("迁移截止时间格式无效: {}", migrationEndTime);
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            log.error("密码安全策略配置验证失败", e);
            return false;
        }
    }



    /**
     * 获取安全策略描述
     */
    public String getSecurityDescription() {
        DeploymentScenario scenario = getDeploymentScenario();

        StringBuilder desc = new StringBuilder();
        desc.append("部署场景: ").append(scenario.name()).append("\n");
        desc.append("MD5支持: ").append(shouldSupportMd5() ? "启用" : "禁用").append("\n");
        desc.append("BCrypt强度: ").append(bcryptStrength).append("\n");
        desc.append("密码强度验证: 已禁用（仅检查非空）").append("\n");
        desc.append("时序攻击防护: ").append(isEnableTimingAttackProtection() ? "启用" : "禁用").append("\n");
        desc.append("自动升级: ").append(isAutoUpgradeEnabled() ? "启用" : "禁用").append("\n");

        if (scenario == DeploymentScenario.MIGRATION_SYSTEM && migrationEndTime != null) {
            desc.append("迁移截止时间: ").append(migrationEndTime).append("\n");
            desc.append("迁移状态: ").append(isMigrationExpired() ? "已过期" : "有效").append("\n");
        }

        return desc.toString();
    }

    /**
     * 获取推荐的新系统配置
     */
    public static PasswordSecurityPolicy getNewSystemConfig() {
        PasswordSecurityPolicy policy = new PasswordSecurityPolicy();
        policy.setDeploymentScenario("new-system");
        policy.setBcryptStrength(12);
        return policy;
    }

    /**
     * 获取推荐的现有系统配置
     */
    public static PasswordSecurityPolicy getExistingSystemConfig() {
        PasswordSecurityPolicy policy = new PasswordSecurityPolicy();
        policy.setDeploymentScenario("existing-system");
        policy.setBcryptStrength(12);
        return policy;
    }

    /**
     * 获取推荐的迁移系统配置
     */
    public static PasswordSecurityPolicy getMigrationSystemConfig(String endTime) {
        PasswordSecurityPolicy policy = new PasswordSecurityPolicy();
        policy.setDeploymentScenario("migration-system");
        policy.setBcryptStrength(12);
        policy.setMigrationEndTime(endTime);
        return policy;
    }

    /**
     * 兼容性方法：获取兼容模式配置（用于测试）
     */
    public static PasswordSecurityPolicy getCompatibleModeConfig() {
        return getExistingSystemConfig();
    }

    // ========== 兼容性方法 ==========
    // 为了保持与PasswordSecurityMonitorService的兼容性，添加以下方法

    /**
     * 安全模式枚举（兼容性）
     */
    public enum SecurityMode {
        STRICT, COMPATIBLE, MIGRATION
    }

    /**
     * 获取部署场景枚举（兼容性方法）
     */
    public DeploymentScenario getDeploymentScenarioEnum() {
        if (deploymentScenario == null) {
            return DeploymentScenario.EXISTING_SYSTEM; // 默认值
        }

        for (DeploymentScenario scenario : DeploymentScenario.values()) {
            if (scenario.getConfigValue().equals(deploymentScenario)) {
                return scenario;
            }
        }
        return DeploymentScenario.EXISTING_SYSTEM; // 默认值
    }

    /**
     * 获取安全模式（兼容性方法）
     */
    public SecurityMode getMode() {
        DeploymentScenario scenario = getDeploymentScenarioEnum();
        switch (scenario) {
            case NEW_SYSTEM:
                return SecurityMode.STRICT;
            case EXISTING_SYSTEM:
                return SecurityMode.COMPATIBLE;
            case MIGRATION_SYSTEM:
                return SecurityMode.MIGRATION;
            default:
                return SecurityMode.COMPATIBLE;
        }
    }

    /**
     * 获取MD5支持结束时间（兼容性方法）
     */
    public LocalDateTime getMd5SupportEndTime() {
        if (migrationEndTime != null && !migrationEndTime.trim().isEmpty()) {
            try {
                return LocalDateTime.parse(migrationEndTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (Exception e) {
                log.warn("解析MD5支持结束时间失败: {}", migrationEndTime, e);
            }
        }
        return null;
    }

    /**
     * 检查MD5支持是否已过期（兼容性方法）
     */
    public boolean isMd5SupportExpired() {
        LocalDateTime endTime = getMd5SupportEndTime();
        if (endTime == null) {
            // 如果没有设置结束时间，根据部署场景判断
            DeploymentScenario scenario = getDeploymentScenarioEnum();
            return scenario == DeploymentScenario.NEW_SYSTEM;
        }
        return LocalDateTime.now().isAfter(endTime);
    }
}
