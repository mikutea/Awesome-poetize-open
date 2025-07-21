package com.ld.poetry.service;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ld.poetry.dao.UserMapper;
import com.ld.poetry.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 密码升级服务
 * 负责监控和统计密码升级进度
 * 
 * @author LeapYa
 * @since 2025-07-20
 */
@Service
@Slf4j
public class PasswordUpgradeService {

    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private PasswordService passwordService;
    
    // 升级统计计数器
    private final AtomicInteger totalUpgraded = new AtomicInteger(0);
    private final AtomicInteger todayUpgraded = new AtomicInteger(0);

    /**
     * 记录密码升级
     */
    public void recordPasswordUpgrade() {
        totalUpgraded.incrementAndGet();
        todayUpgraded.incrementAndGet();
        log.info("密码升级记录 - 总计: {}, 今日: {}", totalUpgraded.get(), todayUpgraded.get());
    }

    /**
     * 获取密码升级统计信息
     * 
     * @return 统计信息
     */
    public Map<String, Object> getUpgradeStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        try {
            // 查询所有用户
            LambdaQueryChainWrapper<User> wrapper = new LambdaQueryChainWrapper<>(userMapper);
            List<User> allUsers = wrapper.list();
            
            int totalUsers = allUsers.size();
            int md5Users = 0;
            int bcryptUsers = 0;
            
            for (User user : allUsers) {
                if (user.getPassword() != null) {
                    if (passwordService.isMD5Password(user.getPassword())) {
                        md5Users++;
                    } else if (passwordService.isBCryptPassword(user.getPassword())) {
                        bcryptUsers++;
                    }
                }
            }
            
            stats.put("totalUsers", totalUsers);
            stats.put("md5Users", md5Users);
            stats.put("bcryptUsers", bcryptUsers);
            stats.put("upgradeProgress", totalUsers > 0 ? (double) bcryptUsers / totalUsers * 100 : 0);
            stats.put("totalUpgraded", totalUpgraded.get());
            stats.put("todayUpgraded", todayUpgraded.get());
            
        } catch (Exception e) {
            log.error("获取密码升级统计信息失败: {}", e.getMessage());
            stats.put("error", e.getMessage());
        }
        
        return stats;
    }

    /**
     * 每天重置今日升级计数
     */
    @Scheduled(cron = "0 0 0 * * ?") // 每天凌晨执行
    public void resetDailyCounter() {
        int todayCount = todayUpgraded.getAndSet(0);
        log.info("重置今日密码升级计数，昨日升级数量: {}", todayCount);
    }

    /**
     * 每周输出密码升级进度报告
     */
    @Scheduled(cron = "0 0 9 * * MON") // 每周一上午9点执行
    public void weeklyProgressReport() {
        Map<String, Object> stats = getUpgradeStatistics();
        
        log.info("=== 密码升级周报 ===");
        log.info("总用户数: {}", stats.get("totalUsers"));
        log.info("MD5密码用户: {}", stats.get("md5Users"));
        log.info("BCrypt密码用户: {}", stats.get("bcryptUsers"));
        log.info("升级进度: {:.2f}%", stats.get("upgradeProgress"));
        log.info("累计升级: {}", stats.get("totalUpgraded"));
        log.info("==================");
    }

    /**
     * 检查是否还有需要升级的用户
     * 
     * @return 是否还有MD5密码用户
     */
    public boolean hasUsersNeedingUpgrade() {
        try {
            LambdaQueryChainWrapper<User> wrapper = new LambdaQueryChainWrapper<>(userMapper);
            List<User> users = wrapper.list();
            
            for (User user : users) {
                if (user.getPassword() != null && passwordService.isMD5Password(user.getPassword())) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.error("检查待升级用户失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 获取需要升级的用户数量
     * 
     * @return 需要升级的用户数量
     */
    public int getUsersNeedingUpgradeCount() {
        try {
            LambdaQueryChainWrapper<User> wrapper = new LambdaQueryChainWrapper<>(userMapper);
            List<User> users = wrapper.list();
            
            int count = 0;
            for (User user : users) {
                if (user.getPassword() != null && passwordService.isMD5Password(user.getPassword())) {
                    count++;
                }
            }
            return count;
        } catch (Exception e) {
            log.error("获取待升级用户数量失败: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * 生成密码安全报告
     * 
     * @return 安全报告
     */
    public String generateSecurityReport() {
        Map<String, Object> stats = getUpgradeStatistics();
        
        StringBuilder report = new StringBuilder();
        report.append("=== 密码安全报告 ===\n");
        report.append(String.format("总用户数: %s\n", stats.get("totalUsers")));
        report.append(String.format("使用MD5密码: %s 用户\n", stats.get("md5Users")));
        report.append(String.format("使用BCrypt密码: %s 用户\n", stats.get("bcryptUsers")));
        report.append(String.format("升级进度: %.2f%%\n", stats.get("upgradeProgress")));
        report.append(String.format("累计升级: %s 次\n", stats.get("totalUpgraded")));
        report.append(String.format("今日升级: %s 次\n", stats.get("todayUpgraded")));
        
        // 安全建议
        double progress = (Double) stats.get("upgradeProgress");
        if (progress < 50) {
            report.append("\n⚠️ 安全建议: 超过50%的用户仍在使用MD5密码，建议加强密码升级推广\n");
        } else if (progress < 90) {
            report.append("\n✅ 安全状态: 大部分用户已升级到BCrypt密码，继续推进剩余用户升级\n");
        } else {
            report.append("\n🎉 安全状态: 密码升级进度良好，系统安全性已大幅提升\n");
        }
        
        report.append("==================");
        
        return report.toString();
    }
}
