package com.ld.poetry.utils;

import com.alibaba.fastjson.JSON;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.entity.User;
import com.ld.poetry.entity.WebInfo;
import com.ld.poetry.handle.PoetryRuntimeException;
import com.ld.poetry.utils.cache.PoetryCache;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.util.List;

public class PoetryUtil {

    public static HttpServletRequest getRequest() {
        return ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();
    }

    public static void checkEmail() {
        User user = (User) PoetryCache.get(PoetryUtil.getToken());
        if (!StringUtils.hasText(user.getEmail())) {
            throw new PoetryRuntimeException("请先绑定邮箱！");
        }
    }

    public static String getToken() {
        String token = PoetryUtil.getRequest().getHeader(CommonConst.TOKEN_HEADER);
        return "null".equals(token) ? null : token;
    }

    public static String getTokenWithoutBearer() {
        String token = getToken();
        if (token != null && token.startsWith("Bearer ")) {
            return token.substring(7);
        }
        return token;
    }

    public static User getCurrentUser() {
        User user = (User) PoetryCache.get(PoetryUtil.getTokenWithoutBearer());
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
        try {
            // 尝试获取token（不带Bearer前缀）
            String tokenWithoutBearer = getTokenWithoutBearer();
            if (StringUtils.hasText(tokenWithoutBearer)) {
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
        } catch (Exception e) {
            // 捕获所有异常，避免认证失败导致整个请求失败
            System.err.println("获取用户ID时发生异常: " + e.getMessage());
            e.printStackTrace();
        }
        
        return null;
    }

    public static String getUsername() {
        User user = (User) PoetryCache.get(PoetryUtil.getToken());
        return user == null ? null : user.getUsername();
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

    public static String getIpAddr(HttpServletRequest request) {
        String ipAddress = null;
        try {
            // 优先获取X-Real-IP头部
            ipAddress = request.getHeader("X-Real-IP");
            if (ipAddress == null || ipAddress.length() == 0 || "unknown".equalsIgnoreCase(ipAddress)) {
                ipAddress = request.getHeader("x-forwarded-for");
            }
            if (ipAddress == null || ipAddress.length() == 0 || "unknown".equalsIgnoreCase(ipAddress)) {
                ipAddress = request.getHeader("Proxy-Client-IP");
            }
            if (ipAddress == null || ipAddress.length() == 0 || "unknown".equalsIgnoreCase(ipAddress)) {
                ipAddress = request.getHeader("WL-Proxy-Client-IP");
            }
            if (ipAddress == null || ipAddress.length() == 0 || "unknown".equalsIgnoreCase(ipAddress)) {
                ipAddress = request.getRemoteAddr();
            }
            
            // 对于通过多个代理的情况，第一个IP为客户端真实IP,多个IP按照','分割
            if (ipAddress != null && ipAddress.indexOf(",") > 0) {
                ipAddress = ipAddress.substring(0, ipAddress.indexOf(",")).trim();
            }
            
            // 过滤Docker内部IP和本地IP
            if (ipAddress != null) {
                // 如果是本地回环地址，尝试使用X-Forwarded-For中的真实IP
                if (ipAddress.equals("127.0.0.1") || ipAddress.equals("0:0:0:0:0:0:0:1") || ipAddress.equals("::1")) {
                    String forwardedFor = request.getHeader("x-forwarded-for");
                    if (forwardedFor != null && !forwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(forwardedFor)) {
                        String[] ips = forwardedFor.split(",");
                        for (String ip : ips) {
                            ip = ip.trim();
                            if (!isInternalIP(ip)) {
                                ipAddress = ip;
                                break;
                            }
                        }
                    }
                }
                
                // 如果仍然是内部IP，标记为未知
                if (isInternalIP(ipAddress)) {
                    ipAddress = "unknown";
                }
            }
        } catch (Exception e) {
            // 异常时设置为unknown而不是null
            ipAddress = "unknown";
        }
        
        return ipAddress != null ? ipAddress : "unknown";
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
