package com.ld.poetry.controller;

import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.service.CacheManagerService;
import com.ld.poetry.utils.PoetryUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

/**
 * 缓存管理Controller
 * 提供缓存监控和管理接口（仅管理员可用）
 */
@Slf4j
@RestController
@RequestMapping("/admin/cache")
public class CacheController {

    @Autowired
    private CacheManagerService cacheManagerService;

    /**
     * 获取缓存统计信息
     */
    @GetMapping("/stats")
    public PoetryResult<Map<String, Object>> getCacheStats() {
        if (!PoetryUtil.isBoss()) {
            return PoetryResult.fail("权限不足");
        }
        
        try {
            Map<String, Object> stats = cacheManagerService.getCacheStats();
            return PoetryResult.success(stats);
        } catch (Exception e) {
            log.error("获取缓存统计信息失败", e);
            return PoetryResult.fail("获取缓存统计信息失败: " + e.getMessage());
        }
    }

    /**
     * 检查缓存健康状态
     */
    @GetMapping("/health")
    public PoetryResult<Boolean> checkCacheHealth() {
        if (!PoetryUtil.isBoss()) {
            return PoetryResult.fail("权限不足");
        }
        
        try {
            boolean isHealthy = cacheManagerService.checkCacheHealth();
            return PoetryResult.success(isHealthy);
        } catch (Exception e) {
            log.error("检查缓存健康状态失败", e);
            return PoetryResult.fail("检查缓存健康状态失败: " + e.getMessage());
        }
    }

    /**
     * 预热缓存
     */
    @PostMapping("/warmup")
    public PoetryResult<String> warmUpCache() {
        if (!PoetryUtil.isBoss()) {
            return PoetryResult.fail("权限不足");
        }
        
        try {
            cacheManagerService.warmUpCache();
            return PoetryResult.success("缓存预热完成");
        } catch (Exception e) {
            log.error("缓存预热失败", e);
            return PoetryResult.fail("缓存预热失败: " + e.getMessage());
        }
    }

    /**
     * 清理所有缓存
     */
    @DeleteMapping("/all")
    public PoetryResult<String> evictAllCache() {
        if (!PoetryUtil.isBoss()) {
            return PoetryResult.fail("权限不足");
        }
        
        try {
            cacheManagerService.evictAllCache();
            return PoetryResult.success("所有缓存清理完成");
        } catch (Exception e) {
            log.error("清理所有缓存失败", e);
            return PoetryResult.fail("清理所有缓存失败: " + e.getMessage());
        }
    }

    /**
     * 清理文章相关缓存
     */
    @DeleteMapping("/articles")
    public PoetryResult<String> evictArticleCache() {
        if (!PoetryUtil.isBoss()) {
            return PoetryResult.fail("权限不足");
        }
        
        try {
            cacheManagerService.evictAllArticleCache();
            return PoetryResult.success("文章相关缓存清理完成");
        } catch (Exception e) {
            log.error("清理文章缓存失败", e);
            return PoetryResult.fail("清理文章缓存失败: " + e.getMessage());
        }
    }

    /**
     * 清理用户相关缓存
     */
    @DeleteMapping("/users")
    public PoetryResult<String> evictUserCache() {
        if (!PoetryUtil.isBoss()) {
            return PoetryResult.fail("权限不足");
        }
        
        try {
            cacheManagerService.evictAllUserCache();
            return PoetryResult.success("用户相关缓存清理完成");
        } catch (Exception e) {
            log.error("清理用户缓存失败", e);
            return PoetryResult.fail("清理用户缓存失败: " + e.getMessage());
        }
    }

    /**
     * 清理评论相关缓存
     */
    @DeleteMapping("/comments")
    public PoetryResult<String> evictCommentCache() {
        if (!PoetryUtil.isBoss()) {
            return PoetryResult.fail("权限不足");
        }
        
        try {
            cacheManagerService.evictAllCommentCache();
            return PoetryResult.success("评论相关缓存清理完成");
        } catch (Exception e) {
            log.error("清理评论缓存失败", e);
            return PoetryResult.fail("清理评论缓存失败: " + e.getMessage());
        }
    }

    /**
     * 清理系统配置相关缓存
     */
    @DeleteMapping("/system")
    public PoetryResult<String> evictSystemCache() {
        if (!PoetryUtil.isBoss()) {
            return PoetryResult.fail("权限不足");
        }
        
        try {
            cacheManagerService.evictAllSystemCache();
            return PoetryResult.success("系统配置相关缓存清理完成");
        } catch (Exception e) {
            log.error("清理系统配置缓存失败", e);
            return PoetryResult.fail("清理系统配置缓存失败: " + e.getMessage());
        }
    }
}
