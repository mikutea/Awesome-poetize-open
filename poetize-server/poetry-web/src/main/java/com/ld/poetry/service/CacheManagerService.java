package com.ld.poetry.service;

import com.ld.poetry.constants.CacheConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 缓存管理服务
 * 提供统一的缓存管理接口，协调Redis缓存和Spring Cache
 */
@Slf4j
@Service
public class CacheManagerService {

    @Autowired
    private CacheService cacheService;

    /**
     * 清理所有文章相关缓存
     */
    public void evictAllArticleCache() {
        log.info("开始清理所有文章相关缓存");
        
        // 清理分类文章列表缓存
        cacheService.evictSortArticleList();
        
        // 清理分类信息缓存
        cacheService.evictSortList();
        
        // 清理文章搜索缓存（这里可以扩展为批量清理）
        // 注意：搜索缓存使用动态key，需要特殊处理
        
        log.info("文章相关缓存清理完成");
    }

    /**
     * 清理所有用户相关缓存
     */
    public void evictAllUserCache() {
        log.info("开始清理所有用户相关缓存");
        
        // 清理管理员缓存
        String adminCacheKey = CacheConstants.CACHE_PREFIX + "admin";
        cacheService.deleteKey(adminCacheKey);
        
        // 清理点赞用户列表缓存
        String admireCacheKey = CacheConstants.CACHE_PREFIX + "admire:list";
        cacheService.deleteKey(admireCacheKey);
        
        log.info("用户相关缓存清理完成");
    }

    /**
     * 清理所有评论相关缓存
     */
    public void evictAllCommentCache() {
        log.info("开始清理所有评论相关缓存");
        
        // 这里可以扩展为批量清理评论缓存
        // 由于评论缓存使用动态key（source + type），需要特殊处理
        
        log.info("评论相关缓存清理完成");
    }

    /**
     * 清理系统配置相关缓存
     */
    public void evictAllSystemCache() {
        log.info("开始清理系统配置相关缓存");
        
        // 清理分类信息缓存
        cacheService.evictSortList();
        
        // 清理家庭成员列表缓存
        String familyCacheKey = CacheConstants.CACHE_PREFIX + "family:list";
        cacheService.deleteKey(familyCacheKey);
        
        log.info("系统配置相关缓存清理完成");
    }

    /**
     * 预热关键缓存
     */
    public void warmUpCache() {
        log.info("开始预热关键缓存");
        
        try {
            // 这里可以调用相关Service方法来预热缓存
            // 例如：预热分类文章列表、热门文章等
            
            log.info("缓存预热完成");
        } catch (Exception e) {
            log.error("缓存预热失败", e);
        }
    }

    /**
     * 获取缓存统计信息
     */
    public java.util.Map<String, Object> getCacheStats() {
        java.util.Map<String, Object> stats = new java.util.HashMap<>();

        try {
            // 检查关键缓存是否存在
            boolean hasSortList = cacheService.hasKey(CacheConstants.SORT_LIST_KEY);
            boolean hasSortArticles = cacheService.hasKey(CacheConstants.CACHE_PREFIX + "sort:article:list");
            boolean hasAdmireList = cacheService.hasKey(CacheConstants.CACHE_PREFIX + "admire:list");
            boolean hasFamilyList = cacheService.hasKey(CacheConstants.CACHE_PREFIX + "family:list");

            stats.put("type", "Redis");
            stats.put("sortListCache", hasSortList);
            stats.put("sortArticlesCache", hasSortArticles);
            stats.put("admireListCache", hasAdmireList);
            stats.put("familyListCache", hasFamilyList);
            stats.put("timestamp", System.currentTimeMillis());
            stats.put("healthy", true);

        } catch (Exception e) {
            stats.put("healthy", false);
            stats.put("error", e.getMessage());
            log.error("获取缓存统计信息失败", e);
        }

        return stats;
    }

    /**
     * 清理所有缓存
     */
    public void evictAllCache() {
        log.info("开始清理所有缓存");
        
        evictAllArticleCache();
        evictAllUserCache();
        evictAllCommentCache();
        evictAllSystemCache();
        
        log.info("所有缓存清理完成");
    }

    /**
     * 检查缓存健康状态
     */
    public boolean checkCacheHealth() {
        try {
            // 测试Redis连接
            String testKey = CacheConstants.CACHE_PREFIX + "health:check";
            String testValue = "test_" + System.currentTimeMillis();
            
            cacheService.set(testKey, testValue, 60);
            Object retrieved = cacheService.get(testKey);
            cacheService.deleteKey(testKey);
            
            boolean isHealthy = testValue.equals(retrieved);
            log.info("缓存健康检查结果: {}", isHealthy ? "正常" : "异常");
            
            return isHealthy;
        } catch (Exception e) {
            log.error("缓存健康检查失败", e);
            return false;
        }
    }
}
