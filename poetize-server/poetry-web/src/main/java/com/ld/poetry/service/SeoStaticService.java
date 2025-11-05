package com.ld.poetry.service;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * <p>
 * SEO静态文件服务接口
 * </p>
 *
 * @author LeapYa
 * @since 2025-09-25
 */
public interface SeoStaticService {
    
    /**
     * 生成PWA manifest.json
     * @param request HTTP请求
     * @return manifest.json内容
     */
    Map<String, Object> generateManifestJson(HttpServletRequest request);
    
    /**
     * 生成robots.txt内容
     * @param request HTTP请求
     * @return robots.txt内容
     */
    String generateRobotsTxt(HttpServletRequest request);
    
    /**
     * 检查静态文件缓存是否需要更新
     * @param fileType 文件类型 (manifest, robots)
     * @return 是否需要更新
     */
    boolean needsUpdate(String fileType);
    
    /**
     * 清理静态文件缓存
     * @param fileType 文件类型，null表示清理所有
     */
    void clearStaticCache(String fileType);
}
