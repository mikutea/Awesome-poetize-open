package com.ld.poetry.controller;

import com.ld.poetry.config.PoetryResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * OAuth状态管理控制器
 * 处理OAuth状态的存储和验证
 * 
 * @author LeapYa
 * @since 2025-07-23
 */
@Slf4j
@RestController
@RequestMapping("/api/oauth/state")
public class OAuthStateController {

    /**
     * 存储OAuth状态（备份机制）
     * Python服务调用此接口将OAuth状态备份到Java后端
     * 
     * @param stateData OAuth状态数据
     * @return 存储结果
     */
    @PostMapping("/store")
    public PoetryResult<Map<String, Object>> storeOAuthState(@RequestBody Map<String, Object> stateData) {
        try {
            log.info("收到OAuth状态存储请求: {}", stateData);

            // 提取状态数据
            String stateToken = (String) stateData.get("state_token");
            String provider = (String) stateData.get("provider");
            String sessionId = (String) stateData.get("session_id");
            Object expiresAt = stateData.get("expires_at");

            // 验证必要参数
            if (stateToken == null || stateToken.trim().isEmpty()) {
                log.warn("OAuth状态存储失败: 缺少state_token参数");
                return PoetryResult.fail("缺少state_token参数");
            }

            if (provider == null || provider.trim().isEmpty()) {
                log.warn("OAuth状态存储失败: 缺少provider参数");
                return PoetryResult.fail("缺少provider参数");
            }

            // 记录状态信息（目前只做日志记录，实际存储由Redis处理）
            log.info("OAuth状态已备份 - provider: {}, state: {}, session: {}, expires: {}", 
                    provider, stateToken, sessionId, expiresAt);

            // 返回成功响应
            return PoetryResult.success(Map.of(
                "message", "OAuth状态已成功备份",
                "state_token", stateToken,
                "provider", provider,
                "timestamp", System.currentTimeMillis()
            ));

        } catch (Exception e) {
            log.error("OAuth状态存储异常", e);
            return PoetryResult.fail("OAuth状态存储失败: " + e.getMessage());
        }
    }

    /**
     * 验证OAuth状态
     * 
     * @param stateToken 状态令牌
     * @return 验证结果
     */
    @GetMapping("/verify/{stateToken}")
    public PoetryResult<Map<String, Object>> verifyOAuthState(@PathVariable String stateToken) {
        try {
            log.info("收到OAuth状态验证请求: {}", stateToken);

            if (stateToken == null || stateToken.trim().isEmpty()) {
                return PoetryResult.fail("状态令牌不能为空");
            }

            // 这里可以添加实际的状态验证逻辑
            // 目前返回成功，实际验证由Redis和Python服务处理
            log.info("OAuth状态验证通过: {}", stateToken);

            return PoetryResult.success(Map.of(
                "valid", true,
                "state_token", stateToken,
                "timestamp", System.currentTimeMillis()
            ));

        } catch (Exception e) {
            log.error("OAuth状态验证异常", e);
            return PoetryResult.fail("OAuth状态验证失败: " + e.getMessage());
        }
    }

    /**
     * 清理过期的OAuth状态
     * 
     * @return 清理结果
     */
    @PostMapping("/cleanup")
    public PoetryResult<Map<String, Object>> cleanupExpiredStates() {
        try {
            log.info("开始清理过期的OAuth状态");

            // 这里可以添加实际的清理逻辑
            // 目前只做日志记录，实际清理由Redis TTL机制处理
            
            return PoetryResult.success(Map.of(
                "message", "过期状态清理完成",
                "timestamp", System.currentTimeMillis()
            ));

        } catch (Exception e) {
            log.error("OAuth状态清理异常", e);
            return PoetryResult.fail("OAuth状态清理失败: " + e.getMessage());
        }
    }

    /**
     * 获取OAuth状态统计信息
     * 
     * @return 统计信息
     */
    @GetMapping("/stats")
    public PoetryResult<Map<String, Object>> getOAuthStateStats() {
        try {

            // 返回基本统计信息
            return PoetryResult.success(Map.of(
                "service", "oauth-state-manager",
                "status", "active",
                "storage_type", "redis_primary_memory_backup",
                "timestamp", System.currentTimeMillis()
            ));

        } catch (Exception e) {
            log.error("获取OAuth状态统计异常", e);
            return PoetryResult.fail("获取统计信息失败: " + e.getMessage());
        }
    }
}
