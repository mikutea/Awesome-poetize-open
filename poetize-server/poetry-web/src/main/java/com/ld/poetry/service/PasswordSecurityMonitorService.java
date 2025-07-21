package com.ld.poetry.service;

import com.ld.poetry.config.PasswordSecurityPolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 密码安全监控服务
 * 负责监控密码安全相关的威胁和异常行为
 * 
 * @author LeapYa
 * @since 2025-01-20
 */
@Service
@Slf4j
public class PasswordSecurityMonitorService {

    @Autowired
    private PasswordSecurityPolicy securityPolicy;

    // 安全事件计数器
    private final AtomicLong md5VerificationAttempts = new AtomicLong(0);
    private final AtomicLong bcryptVerificationAttempts = new AtomicLong(0);
    private final AtomicLong unknownFormatAttempts = new AtomicLong(0);
    private final AtomicLong md5BlockedAttempts = new AtomicLong(0);
    
    // IP级别的异常行为监控
    private final ConcurrentHashMap<String, AtomicInteger> suspiciousIpActivity = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, LocalDateTime> lastAlertTime = new ConcurrentHashMap<>();
    
    // 时序攻击检测
    private final ConcurrentHashMap<String, Long> verificationTimes = new ConcurrentHashMap<>();
    
    // 告警阈值
    private static final int SUSPICIOUS_ACTIVITY_THRESHOLD = 10;
    private static final int ALERT_COOLDOWN_MINUTES = 30;

    /**
     * 记录密码验证事件
     * 
     * @param passwordFormat 密码格式（MD5、BCrypt、Unknown）
     * @param clientIp 客户端IP
     * @param verificationTime 验证耗时（毫秒）
     * @param success 验证是否成功
     */
    public void recordPasswordVerification(String passwordFormat, String clientIp, 
                                         long verificationTime, boolean success) {
        
        // 记录格式统计
        switch (passwordFormat.toUpperCase()) {
            case "MD5":
                md5VerificationAttempts.incrementAndGet();
                break;
            case "BCRYPT":
                bcryptVerificationAttempts.incrementAndGet();
                break;
            default:
                unknownFormatAttempts.incrementAndGet();
                break;
        }
        
        // 记录验证时间（用于时序攻击检测）
        if (securityPolicy.isEnableTimingAttackProtection()) {
            verificationTimes.put(clientIp + "_" + passwordFormat, verificationTime);
        }
        
        // 检测异常行为
        if (!success) {
            detectSuspiciousActivity(clientIp, passwordFormat);
        }
        
        // 在严格模式下，如果尝试使用MD5验证，记录为可疑活动
        if ("MD5".equalsIgnoreCase(passwordFormat) && 
            securityPolicy.getMode() == PasswordSecurityPolicy.SecurityMode.STRICT) {
            recordMd5BlockedAttempt(clientIp);
        }
    }

    /**
     * 记录MD5验证被阻止的尝试
     */
    public void recordMd5BlockedAttempt(String clientIp) {
        md5BlockedAttempts.incrementAndGet();
        log.warn("MD5密码验证被安全策略阻止 - IP: {}, 安全模式: {}", clientIp, securityPolicy.getMode());
        
        // 记录为可疑活动
        detectSuspiciousActivity(clientIp, "MD5_BLOCKED");
    }

    /**
     * 检测可疑活动
     */
    private void detectSuspiciousActivity(String clientIp, String activityType) {
        AtomicInteger count = suspiciousIpActivity.computeIfAbsent(clientIp, k -> new AtomicInteger(0));
        int currentCount = count.incrementAndGet();
        
        if (currentCount >= SUSPICIOUS_ACTIVITY_THRESHOLD) {
            triggerSecurityAlert(clientIp, activityType, currentCount);
        }
    }

    /**
     * 触发安全告警
     */
    private void triggerSecurityAlert(String clientIp, String activityType, int count) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastAlert = lastAlertTime.get(clientIp);
        
        // 检查告警冷却时间
        if (lastAlert != null && lastAlert.plusMinutes(ALERT_COOLDOWN_MINUTES).isAfter(now)) {
            return; // 在冷却期内，不重复告警
        }
        
        lastAlertTime.put(clientIp, now);
        
        log.error("🚨 密码安全告警 - IP: {}, 活动类型: {}, 次数: {}, 时间: {}", 
            clientIp, activityType, count, now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        
        // 这里可以集成更多的告警机制，如邮件、短信、钉钉等
        // sendAlertNotification(clientIp, activityType, count);
    }

    /**
     * 检查MD5支持是否即将过期（仅在迁移模式下）
     */
    @Scheduled(cron = "0 0 9 * * ?") // 每天上午9点检查
    public void checkMd5SupportExpiration() {
        if (securityPolicy.getMode() != PasswordSecurityPolicy.SecurityMode.MIGRATION) {
            return;
        }
        
        if (securityPolicy.getMd5SupportEndTime() == null) {
            return;
        }
        
        try {
            LocalDateTime endTime = securityPolicy.getMd5SupportEndTime();
            LocalDateTime now = LocalDateTime.now();
            
            long daysUntilExpiration = java.time.Duration.between(now, endTime).toDays();
            
            if (daysUntilExpiration <= 7 && daysUntilExpiration > 0) {
                log.warn("⚠️ MD5支持即将过期 - 剩余天数: {}, 过期时间: {}", 
                    daysUntilExpiration, securityPolicy.getMd5SupportEndTime());
            } else if (daysUntilExpiration <= 0) {
                log.error("🚨 MD5支持已过期 - 过期时间: {}", securityPolicy.getMd5SupportEndTime());
            }
        } catch (Exception e) {
            log.error("检查MD5支持过期时间失败", e);
        }
    }

    /**
     * 生成安全监控报告
     */
    public String generateSecurityReport() {
        StringBuilder report = new StringBuilder();
        report.append("=== 密码安全监控报告 ===\n");
        report.append(String.format("安全模式: %s\n", securityPolicy.getMode()));
        report.append(String.format("MD5支持: %s\n", securityPolicy.shouldSupportMd5() ? "启用" : "禁用"));
        report.append("\n--- 验证统计 ---\n");
        report.append(String.format("BCrypt验证次数: %d\n", bcryptVerificationAttempts.get()));
        report.append(String.format("MD5验证次数: %d\n", md5VerificationAttempts.get()));
        report.append(String.format("未知格式验证次数: %d\n", unknownFormatAttempts.get()));
        report.append(String.format("MD5被阻止次数: %d\n", md5BlockedAttempts.get()));
        
        report.append("\n--- 安全事件 ---\n");
        report.append(String.format("可疑IP数量: %d\n", suspiciousIpActivity.size()));
        
        if (!suspiciousIpActivity.isEmpty()) {
            report.append("可疑IP详情:\n");
            suspiciousIpActivity.forEach((ip, count) -> {
                report.append(String.format("  IP: %s, 异常次数: %d\n", ip, count.get()));
            });
        }
        
        // 迁移模式特殊信息
        if (securityPolicy.getMode() == PasswordSecurityPolicy.SecurityMode.MIGRATION) {
            report.append("\n--- 迁移信息 ---\n");
            report.append(String.format("MD5支持截止时间: %s\n", securityPolicy.getMd5SupportEndTime()));
            report.append(String.format("MD5支持状态: %s\n", 
                securityPolicy.isMd5SupportExpired() ? "已过期" : "有效"));
        }
        
        report.append("========================");
        return report.toString();
    }

    /**
     * 重置监控统计（用于测试或定期清理）
     */
    public void resetStatistics() {
        md5VerificationAttempts.set(0);
        bcryptVerificationAttempts.set(0);
        unknownFormatAttempts.set(0);
        md5BlockedAttempts.set(0);
        suspiciousIpActivity.clear();
        lastAlertTime.clear();
        verificationTimes.clear();
        
        log.info("密码安全监控统计已重置");
    }

    /**
     * 获取监控统计数据
     */
    public SecurityStatistics getStatistics() {
        return new SecurityStatistics(
            md5VerificationAttempts.get(),
            bcryptVerificationAttempts.get(),
            unknownFormatAttempts.get(),
            md5BlockedAttempts.get(),
            suspiciousIpActivity.size()
        );
    }

    /**
     * 安全统计数据类
     */
    public static class SecurityStatistics {
        public final long md5Verifications;
        public final long bcryptVerifications;
        public final long unknownFormatVerifications;
        public final long md5BlockedAttempts;
        public final int suspiciousIpCount;

        public SecurityStatistics(long md5Verifications, long bcryptVerifications, 
                                long unknownFormatVerifications, long md5BlockedAttempts, 
                                int suspiciousIpCount) {
            this.md5Verifications = md5Verifications;
            this.bcryptVerifications = bcryptVerifications;
            this.unknownFormatVerifications = unknownFormatVerifications;
            this.md5BlockedAttempts = md5BlockedAttempts;
            this.suspiciousIpCount = suspiciousIpCount;
        }
    }
}
