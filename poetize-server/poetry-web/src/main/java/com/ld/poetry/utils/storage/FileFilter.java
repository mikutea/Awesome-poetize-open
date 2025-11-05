package com.ld.poetry.utils.storage;

import com.ld.poetry.constants.CacheConstants;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.entity.User;
import com.ld.poetry.service.CacheService;
import com.ld.poetry.utils.PoetryUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 文件上传过滤器
 * 用于控制文件上传的频率限制和权限检查
 */
@Slf4j
@Component
public class FileFilter {

    private final AntPathMatcher matcher = new AntPathMatcher();
    
    @Autowired
    private CacheService cacheService;

    /**
     * 文件上传过滤检查
     * 
     * @param httpServletRequest HTTP请求
     * @param httpServletResponse HTTP响应
     * @return true表示拦截，false表示放行
     */
    public boolean doFilterFile(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse) {
        // 仅对上传路径进行检查
        if (!matcher.match("/resource/upload", httpServletRequest.getRequestURI())) {
            return false; // 非上传路径不拦截
        }
        
        try {
            User user = PoetryUtil.getCurrentUser();
            
            // 未登录用户拦截上传
            if (user == null) {
                log.info("未登录用户尝试上传文件，IP: {}", PoetryUtil.getIpAddr(httpServletRequest));
                return true;
            }
            
            // 管理员不受上传次数限制
            User adminUser = PoetryUtil.getAdminUser();
            if (adminUser != null && user.getId().intValue() == adminUser.getId().intValue()) {
                return false;
            }

            // 用户上传频率限制检查
            boolean userLimitReached = checkUserUploadLimit(user.getId());
            if (userLimitReached) {
                log.info("用户上传超限：userId={}", user.getId());
                return true;
            }
            
            // IP上传频率限制检查
            String ip = PoetryUtil.getIpAddr(httpServletRequest);
            boolean ipLimitReached = checkIpUploadLimit(ip);
            if (ipLimitReached) {
                log.info("IP上传超限：ip={}", ip);
                return true;
            }
            
            // 通过所有检查，允许上传
            return false;
            
        } catch (Exception e) {
            // 发生异常时允许上传，但记录错误
            log.error("文件上传过滤器检查时发生异常: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * 检查用户上传频率限制
     * 
     * @param userId 用户ID
     * @return true表示超限，false表示未超限
     */
    private boolean checkUserUploadLimit(Integer userId) {
        try {
            String uploadCountKey = CacheConstants.buildFileUploadCountUserKey(userId);
            Object countObj = cacheService.get(uploadCountKey);
            int currentCount = countObj != null ? (Integer) countObj : 0;
            
            if (currentCount >= CommonConst.SAVE_MAX_COUNT) {
                return true;
            }
            
            // 增加上传计数
            cacheService.set(uploadCountKey, currentCount + 1, CommonConst.SAVE_EXPIRE);
            return false;
        } catch (Exception e) {
            log.error("检查用户上传频率限制时发生错误: userId={}", userId, e);
            // 发生错误时不阻止操作
            return false;
        }
    }
    
    /**
     * 检查IP上传频率限制
     * 
     * @param ip IP地址
     * @return true表示超限，false表示未超限
     */
    private boolean checkIpUploadLimit(String ip) {
        try {
            String ipCountKey = CacheConstants.buildFileUploadCountIpKey(ip);
            Object ipCountObj = cacheService.get(ipCountKey);
            int currentIpCount = ipCountObj != null ? (Integer) ipCountObj : 0;
            
            if (currentIpCount > CommonConst.SAVE_MAX_COUNT) {
                return true;
            }
            
            // 增加IP上传计数
            cacheService.set(ipCountKey, currentIpCount + 1, CommonConst.SAVE_EXPIRE);
            return false;
        } catch (Exception e) {
            log.error("检查IP上传频率限制时发生错误: ip={}", ip, e);
            // 发生错误时不阻止操作
            return false;
        }
    }
}
