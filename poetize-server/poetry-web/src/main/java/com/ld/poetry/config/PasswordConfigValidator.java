package com.ld.poetry.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 密码配置验证器
 * 在应用启动时验证密码安全配置的有效性和一致性
 * 
 * @author LeapYa
 * @since 2025-07-20
 */
@Component
@Slf4j
public class PasswordConfigValidator {

    @Autowired
    private PasswordSecurityPolicy securityPolicy;

    /**
     * 应用启动完成后验证配置
     */
    @EventListener(ApplicationReadyEvent.class)
    public void validatePasswordConfiguration() {
        log.info("=== 开始验证密码安全配置 ===");
        
        try {
            // 基础配置验证
            if (!securityPolicy.isValid()) {
                throw new IllegalStateException("密码安全策略配置无效");
            }
            
            // 部署场景验证
            PasswordSecurityPolicy.DeploymentScenario scenario = securityPolicy.getDeploymentScenario();
            log.info("✅ 部署场景: {}", scenario);
            
            // 场景特定验证
            validateScenarioSpecificConfig(scenario);
            
            // 输出配置摘要
            logConfigurationSummary(scenario);
            
            log.info("=== 密码安全配置验证完成 ===");
            
        } catch (Exception e) {
            log.error("❌ 密码安全配置验证失败: {}", e.getMessage());
            throw new IllegalStateException("密码安全配置验证失败", e);
        }
    }

    /**
     * 验证场景特定配置
     */
    private void validateScenarioSpecificConfig(PasswordSecurityPolicy.DeploymentScenario scenario) {
        switch (scenario) {
            case NEW_SYSTEM:
                validateNewSystemConfig();
                break;
            case EXISTING_SYSTEM:
                validateExistingSystemConfig();
                break;
            case MIGRATION_SYSTEM:
                validateMigrationSystemConfig();
                break;
        }
    }

    /**
     * 验证新系统配置
     */
    private void validateNewSystemConfig() {
        log.info("🔒 新系统部署模式验证:");
        
        // 验证MD5支持状态
        if (securityPolicy.shouldSupportMd5()) {
            log.warn("⚠️ 新系统模式下不应支持MD5，但当前配置支持MD5");
        } else {
            log.info("✅ MD5支持已正确禁用");
        }
        
        // 验证自动升级状态
        if (securityPolicy.isAutoUpgradeEnabled()) {
            log.warn("⚠️ 新系统模式下不需要自动升级功能");
        } else {
            log.info("✅ 自动升级功能已正确禁用");
        }
        
        // 密码强度验证已移除
        log.info("✅ 密码强度验证已禁用，用户可设置任意强度的密码");
    }

    /**
     * 验证现有系统配置
     */
    private void validateExistingSystemConfig() {
        log.info("🔄 现有系统升级模式验证:");
        
        // 验证MD5支持状态
        if (!securityPolicy.shouldSupportMd5()) {
            log.warn("⚠️ 现有系统模式下应该支持MD5以保证兼容性");
        } else {
            log.info("✅ MD5支持已启用，保证向后兼容");
        }
        
        // 验证自动升级状态
        if (!securityPolicy.isAutoUpgradeEnabled()) {
            log.warn("⚠️ 现有系统模式下建议启用自动升级功能");
        } else {
            log.info("✅ 自动升级功能已启用");
        }
        
        // 验证升级失败处理
        if (!securityPolicy.isAllowLoginOnUpgradeFailure()) {
            log.warn("⚠️ 建议允许升级失败时登录，避免影响用户体验");
        } else {
            log.info("✅ 升级失败时允许登录，保证用户体验");
        }
    }

    /**
     * 验证迁移系统配置
     */
    private void validateMigrationSystemConfig() {
        log.info("⏰ 迁移系统模式验证:");
        
        // 验证迁移截止时间
        String migrationEndTime = securityPolicy.getMigrationEndTime();
        if (migrationEndTime == null) {
            log.warn("⚠️ 迁移系统模式下建议设置migration-end-time");
        } else {
            log.info("✅ 迁移截止时间: {}", migrationEndTime);

            // 检查是否已过期
            if (securityPolicy.isMigrationExpired()) {
                log.error("🚨 迁移期已过期，MD5支持已被禁用");
            } else {
                log.info("✅ 迁移期有效，MD5支持正常");
            }
        }
        
        // 验证自动升级状态
        if (!securityPolicy.isAutoUpgradeEnabled()) {
            log.warn("⚠️ 迁移系统模式下应该启用自动升级功能");
        } else {
            log.info("✅ 自动升级功能已启用");
        }
    }

    /**
     * 输出配置摘要
     */
    private void logConfigurationSummary(PasswordSecurityPolicy.DeploymentScenario scenario) {
        log.info("📋 配置摘要:");
        log.info("   部署场景: {}", scenario);
        log.info("   MD5支持: {}", securityPolicy.shouldSupportMd5() ? "启用" : "禁用");
        log.info("   BCrypt强度: {}", securityPolicy.getBcryptStrength());
        log.info("   自动升级: {}", securityPolicy.isAutoUpgradeEnabled() ? "启用" : "禁用");
        log.info("   升级统计: {}", securityPolicy.isUpgradeStatisticsEnabled() ? "启用" : "禁用");
        log.info("   密码强度验证: 已禁用（仅检查非空）");
        log.info("   时序攻击防护: {}", securityPolicy.isEnableTimingAttackProtection() ? "启用" : "禁用");

        if (scenario == PasswordSecurityPolicy.DeploymentScenario.MIGRATION_SYSTEM) {
            String endTime = securityPolicy.getMigrationEndTime();
            if (endTime != null) {
                log.info("   迁移截止时间: {}", endTime);
                log.info("   迁移状态: {}", securityPolicy.isMigrationExpired() ? "已过期" : "有效");
            }
        }
    }

    /**
     * 获取配置建议
     */
    public String getConfigurationRecommendations() {
        StringBuilder recommendations = new StringBuilder();
        PasswordSecurityPolicy.DeploymentScenario scenario = securityPolicy.getDeploymentScenario();
        
        recommendations.append("=== 密码安全配置建议 ===\n");
        recommendations.append("当前部署场景: ").append(scenario).append("\n\n");
        
        switch (scenario) {
            case NEW_SYSTEM:
                recommendations.append("✅ 新系统部署 - 最佳安全实践:\n");
                recommendations.append("- 已禁用MD5支持，仅使用BCrypt\n");
                recommendations.append("- 建议密码长度≥12位\n");
                recommendations.append("- 建议要求特殊字符\n");
                recommendations.append("- 建议强度分数≥4\n");
                break;
                
            case EXISTING_SYSTEM:
                recommendations.append("🔄 现有系统升级 - 平滑迁移:\n");
                recommendations.append("- 保持MD5兼容性\n");
                recommendations.append("- 自动升级MD5到BCrypt\n");
                recommendations.append("- 监控升级进度\n");
                recommendations.append("- 考虑逐步提高密码要求\n");
                break;
                
            case MIGRATION_SYSTEM:
                recommendations.append("⏰ 迁移系统 - 计划过渡:\n");
                recommendations.append("- 设置合理的迁移截止时间\n");
                recommendations.append("- 定期检查迁移进度\n");
                recommendations.append("- 提前通知用户升级\n");
                recommendations.append("- 准备迁移完成后的配置\n");
                break;
        }
        
        return recommendations.toString();
    }
}
