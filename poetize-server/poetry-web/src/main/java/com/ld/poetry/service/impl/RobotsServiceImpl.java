package com.ld.poetry.service.impl;

import com.ld.poetry.service.RobotsService;
import com.ld.poetry.service.CacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * Robots.txt服务实现
 * 复用searchEnginePushService获取SEO配置，简化实现
 */
@Slf4j
@Service
public class RobotsServiceImpl implements RobotsService {

    @Autowired
    private CacheService cacheService;

    @Autowired
    private SearchEnginePushServiceImpl searchEnginePushService;
    
    @Autowired
    private com.ld.poetry.utils.mail.MailUtil mailUtil;

    private static final String ROBOTS_CACHE_KEY = "seo:robots";
    private static final int ROBOTS_CACHE_EXPIRE_TIME = 24 * 60 * 60; // 24小时缓存

    @Override
    public String generateRobots() {
        try {
            
            // 复用searchEnginePushService获取SEO配置
            Map<String, Object> seoConfig = searchEnginePushService.getSeoConfig();
            if (seoConfig == null) {
                log.warn("无法获取SEO配置，返回null");
                return null;
            }

            // 检查SEO是否启用
            Boolean seoEnabled = (Boolean) seoConfig.get("enable");
            if (seoEnabled == null || !seoEnabled) {
                log.info("SEO功能已关闭，跳过生成robots.txt");
                return null;
            }

            // 获取robots.txt模板
            String robotsTemplate = (String) seoConfig.get("robots_txt");
            if (!StringUtils.hasText(robotsTemplate)) {
                log.warn("SEO配置中没有robots_txt字段");
                return null;
            }

            // 直接从 MailUtil 获取网站地址并替换占位符
            String siteAddress = mailUtil.getSiteUrl();
            if (StringUtils.hasText(siteAddress)) {
                robotsTemplate = robotsTemplate.replace("{site_address}", siteAddress);
            } else {
                log.warn("无法获取网站地址，保留占位符");
            }

            return robotsTemplate;
            
        } catch (Exception e) {
            log.error("生成robots.txt出错", e);
            return null;
        }
    }

    @Override
    public String getRobots() {
        try {
            // 先尝试从缓存获取
            String cachedRobots = (String) cacheService.get(ROBOTS_CACHE_KEY);
            if (StringUtils.hasText(cachedRobots)) {
                return cachedRobots;
            }

            // 缓存中没有，重新生成
            String robots = generateRobots();
            if (StringUtils.hasText(robots)) {
                // 缓存生成结果
                cacheService.set(ROBOTS_CACHE_KEY, robots, ROBOTS_CACHE_EXPIRE_TIME);
                return robots;
            }

            log.warn("生成robots.txt失败");
            return null;
            
        } catch (Exception e) {
            log.error("获取robots.txt出错", e);
            return null;
        }
    }

    @Override
    public void clearRobotsCache() {
        try {
            cacheService.delete(ROBOTS_CACHE_KEY);
            log.info("已清除robots.txt缓存");
        } catch (Exception e) {
            log.warn("清除robots.txt缓存失败", e);
        }
    }

    @Override
    public boolean needsUpdate() {
        // 检查缓存是否过期
        String cachedRobots = (String) cacheService.get(ROBOTS_CACHE_KEY);
        return !StringUtils.hasText(cachedRobots);
    }
}
