package com.ld.poetry.utils;

import com.alibaba.fastjson.JSON;
import com.ld.poetry.config.AsyncUserContext;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.entity.User;
import com.ld.poetry.entity.WebInfo;
import com.ld.poetry.handle.PoetryRuntimeException;
import com.ld.poetry.utils.cache.PoetryCache;
import com.ld.poetry.utils.cache.UserCacheManager;
import com.ld.poetry.utils.IpUtil;
import com.ld.poetry.utils.RetryUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.annotation.PostConstruct;
import java.net.InetAddress;
import java.util.List;

@Component
@Slf4j
public class PoetryUtil {

    @Autowired
    private UserCacheManager userCacheManager;

    private static UserCacheManager staticUserCacheManager;

    @PostConstruct
    public void init() {
        staticUserCacheManager = userCacheManager;
    }

    public static HttpServletRequest getRequest() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attributes != null ? attributes.getRequest() : null;
        } catch (IllegalStateException e) {
            // 在异步线程中RequestContextHolder可能不可用
            return null;
        }
    }

    public static void checkEmail() {
        User user = (User) PoetryCache.get(PoetryUtil.getToken());
        if (!StringUtils.hasText(user.getEmail())) {
            throw new PoetryRuntimeException("请先绑定邮箱！");
        }
    }

    public static String getToken() {
        // 首先尝试从异步上下文获取Token
        String asyncToken = AsyncUserContext.getToken();
        if (StringUtils.hasText(asyncToken)) {
            return asyncToken;
        }
        
        // 如果异步上下文中没有，尝试从请求中获取
        HttpServletRequest request = getRequest();
        if (request != null) {
            String token = request.getHeader(CommonConst.TOKEN_HEADER);
            return "null".equals(token) ? null : token;
        }
        
        return null;
    }

    public static String getTokenWithoutBearer() {
        String token = getToken();
        if (token != null && token.startsWith("Bearer ")) {
            return token.substring(7);
        }
        return token;
    }
    
    /**
     * 获取当前用户Token，如果获取不到则抛出异常
     * 适用于必须要求用户登录的场景
     * @return 当前用户Token
     * @throws PoetryRuntimeException 如果用户未登录或获取Token失败
     */
    public static String getTokenRequired() {
        String token = getToken();
        if (!StringUtils.hasText(token)) {
            throw new PoetryRuntimeException("用户未登录或Token获取失败，请重新登录");
        }
        return token;
    }

    public static User getCurrentUser() {
        return RetryUtil.executeWithRetry(() -> {
            // 首先尝试从异步上下文获取用户
            User asyncUser = AsyncUserContext.getUser();
            if (asyncUser != null) {
                return asyncUser;
            }
            
            // 如果异步上下文中没有，尝试通过Token从缓存获取
            String token = getTokenWithoutBearer();
            if (StringUtils.hasText(token)) {
                // 使用用户缓存管理器获取用户信息
                if (staticUserCacheManager != null) {
                    User user = staticUserCacheManager.getUserByToken(token);
                    if (user != null) {
                        return user;
                    }
                }
                
                // 降级到直接从PoetryCache获取
                User user = (User) PoetryCache.get(token);
                if (user != null) {
                    return user;
                }
            }
            
            return null;
        }, 2, 50, "获取当前用户");
    }
    
    /**
     * 获取当前用户，如果获取不到则抛出异常
     * 适用于必须要求用户登录的场景
     * @return 当前用户
     * @throws PoetryRuntimeException 如果用户未登录或获取用户信息失败
     */
    public static User getCurrentUserRequired() {
        User user = getCurrentUser();
        if (user == null) {
            throw new PoetryRuntimeException("用户未登录或用户信息获取失败，请重新登录");
        }
        return user;
    }

    public static User getAdminUser() {
        User admin = (User) PoetryCache.get(CommonConst.ADMIN);
        return admin;
    }

    /**
     * 判断当前用户是否为管理员(Boss)
     * @return 是否为管理员
     */
    public static boolean isBoss() {
        User currentUser = getCurrentUser();
        if (currentUser == null) {
            return false;
        }
        return currentUser.getUserType() != null && currentUser.getUserType() == 0; // 0 = 管理员
    }

    public static Integer getUserId() {
        return RetryUtil.executeWithRetry(() -> {
            try {
                // 首先尝试从异步上下文获取用户ID
                Integer asyncUserId = AsyncUserContext.getCurrentUserId();
                if (asyncUserId != null) {
                    return asyncUserId;
                }
                
                // 尝试获取token（不带Bearer前缀）
                String tokenWithoutBearer = getTokenWithoutBearer();
                if (StringUtils.hasText(tokenWithoutBearer)) {
                    // 使用用户缓存管理器获取用户信息
                    if (staticUserCacheManager != null) {
                        User user = staticUserCacheManager.getUserByToken(tokenWithoutBearer);
                        if (user != null) {
                            return user.getId();
                        }
                    }
                    
                    // 降级到直接从PoetryCache获取
                    User user = (User) PoetryCache.get(tokenWithoutBearer);
                    if (user != null) {
                        return user.getId();
                    }
                    
                    // 如果直接获取失败，检查是否为管理员token
                    if (tokenWithoutBearer.contains(CommonConst.ADMIN_ACCESS_TOKEN)) {
                        User adminUser = getAdminUser();
                        if (adminUser != null) {
                            return adminUser.getId();
                        }
                    }
                }
                
                // 如果获取不到，尝试从请求属性获取
                HttpServletRequest request = getRequest();
                if (request != null) {
                    // 从请求属性中获取用户ID
                    Object userIdAttr = request.getAttribute("userId");
                    if (userIdAttr != null) {
                        try {
                            return Integer.parseInt(userIdAttr.toString());
                        } catch (NumberFormatException e) {
                            // 忽略转换异常
                        }
                    }
                }
                
                return null;
                
            } catch (PoetryRuntimeException e) {
                // 重新抛出业务异常
                throw e;
            } catch (Exception e) {
                // 捕获所有其他异常，避免认证失败导致整个请求失败
                System.err.println("获取用户ID时发生异常: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        }, 2, 50, "获取用户ID");
    }
    
    /**
     * 获取当前用户ID，如果获取不到则抛出异常
     * 适用于必须要求用户登录的场景
     * @return 当前用户ID
     * @throws PoetryRuntimeException 如果用户未登录或获取用户ID失败
     */
    public static Integer getUserIdRequired() {
        Integer userId = getUserId();
        if (userId == null) {
            throw new PoetryRuntimeException("用户未登录或用户ID获取失败，请重新登录");
        }
        return userId;
    }

    public static String getUsername() {
        return RetryUtil.executeWithRetry(() -> {
            // 首先尝试从异步上下文获取用户名
            String asyncUsername = AsyncUserContext.getCurrentUsername();
            if (StringUtils.hasText(asyncUsername)) {
                return asyncUsername;
            }
            
            // 如果异步上下文中没有，尝试通过Token从缓存获取
            String token = getTokenWithoutBearer();
            if (StringUtils.hasText(token)) {
                // 使用用户缓存管理器获取用户信息
                if (staticUserCacheManager != null) {
                    User user = staticUserCacheManager.getUserByToken(token);
                    if (user != null && StringUtils.hasText(user.getUsername())) {
                        return user.getUsername();
                    }
                }
                
                // 降级到直接从PoetryCache获取
                User user = (User) PoetryCache.get(token);
                if (user != null && StringUtils.hasText(user.getUsername())) {
                    return user.getUsername();
                }
            }
            
            return null;
        }, 2, 50, "获取用户名");
    }

    public static String getRandomAvatar(String key) {
        WebInfo webInfo = (WebInfo) PoetryCache.get(CommonConst.WEB_INFO);
        if (webInfo != null) {
            String randomAvatar = webInfo.getRandomAvatar();
            List<String> randomAvatars = JSON.parseArray(randomAvatar, String.class);
            if (!CollectionUtils.isEmpty(randomAvatars)) {
                if (StringUtils.hasText(key)) {
                    return randomAvatars.get(PoetryUtil.hashLocation(key, randomAvatars.size()));
                } else {
                    String ipAddr = PoetryUtil.getIpAddr(PoetryUtil.getRequest());
                    if (StringUtils.hasText(ipAddr)) {
                        return randomAvatars.get(PoetryUtil.hashLocation(ipAddr, randomAvatars.size()));
                    } else {
                        return randomAvatars.get(0);
                    }
                }
            }
        }
        return null;
    }

    public static String getRandomName(String key) {
        WebInfo webInfo = (WebInfo) PoetryCache.get(CommonConst.WEB_INFO);
        if (webInfo != null) {
            String randomName = webInfo.getRandomName();
            List<String> randomNames = JSON.parseArray(randomName, String.class);
            if (!CollectionUtils.isEmpty(randomNames)) {
                if (StringUtils.hasText(key)) {
                    return randomNames.get(PoetryUtil.hashLocation(key, randomNames.size()));
                } else {
                    String ipAddr = PoetryUtil.getIpAddr(PoetryUtil.getRequest());
                    if (StringUtils.hasText(ipAddr)) {
                        return randomNames.get(PoetryUtil.hashLocation(ipAddr, randomNames.size()));
                    } else {
                        return randomNames.get(0);
                    }
                }
            }
        }
        return null;
    }

    public static String getRandomCover(String key) {
        WebInfo webInfo = (WebInfo) PoetryCache.get(CommonConst.WEB_INFO);
        if (webInfo != null) {
            String randomCover = webInfo.getRandomCover();
            List<String> randomCovers = JSON.parseArray(randomCover, String.class);
            if (!CollectionUtils.isEmpty(randomCovers)) {
                if (StringUtils.hasText(key)) {
                    return randomCovers.get(PoetryUtil.hashLocation(key, randomCovers.size()));
                } else {
                    String ipAddr = PoetryUtil.getIpAddr(PoetryUtil.getRequest());
                    if (StringUtils.hasText(ipAddr)) {
                        return randomCovers.get(PoetryUtil.hashLocation(ipAddr, randomCovers.size()));
                    } else {
                        return randomCovers.get(0);
                    }
                }
            }
        }
        return null;
    }

    /**
     * 获取客户端IP地址
     * 使用增强的IP获取工具，提供更好的容错和监控能力
     */
    public static String getIpAddr(HttpServletRequest request) {
        return IpUtil.getClientRealIp(request);
    }
    
    /**
     * 获取客户端IP地址（兼容旧版本方法名）
     * @deprecated 建议使用 getIpAddr(HttpServletRequest request)
     */
    @Deprecated
    public static String getClientIp(HttpServletRequest request) {
        return getIpAddr(request);
    }
    
    /**
     * 获取当前请求的客户端IP地址
     */
    public static String getCurrentClientIp() {
        HttpServletRequest request = getRequest();
        return getIpAddr(request);
    }
    
    /**
     * 验证IP地址格式是否正确
     */
    public static boolean isValidIp(String ip) {
        return IpUtil.isValidIpFormat(ip);
    }
    
    /**
     * 判断是否为内网IP
     */
    public static boolean isInternalIp(String ip) {
        return IpUtil.isInternalIp(ip);
    }
    
    /**
     * 获取IP获取统计信息
     */
    public static String getIpStatistics() {
        return IpUtil.getIpStatistics();
    }
    
    /**
     * 判断是否为内部IP地址
     */
    private static boolean isInternalIP(String ip) {
        if (ip == null || ip.isEmpty()) {
            return true;
        }
        
        // 本地回环地址
        if (ip.equals("127.0.0.1") || ip.equals("localhost") || ip.equals("0:0:0:0:0:0:0:1") || ip.equals("::1")) {
            return true;
        }
        
        // Docker内部网络地址
        if (ip.startsWith("172.") || ip.startsWith("10.") || ip.startsWith("192.168.")) {
            return true;
        }
        
        // 其他无效IP
        if (ip.equals("unknown") || ip.equals("0.0.0.0")) {
            return true;
        }
        
        return false;
    }


    public static int hashLocation(String key, int length) {
        int h = key.hashCode();
        return (h ^ (h >>> 16)) & (length - 1);
    }
}
