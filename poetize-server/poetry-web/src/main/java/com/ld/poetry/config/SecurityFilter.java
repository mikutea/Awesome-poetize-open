package com.ld.poetry.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 安全过滤器 - 拦截常见的恶意扫描请求并实现IP拉黑
 * 记录恶意IP，达到阈值后自动拉黑一段时间
 */
@Component
@Slf4j
public class SecurityFilter extends OncePerRequestFilter {

    // IP攻击次数记录 <IP, 攻击次数>
    private static final ConcurrentHashMap<String, AtomicInteger> ipAttackCount = new ConcurrentHashMap<>();
    
    // 拉黑IP记录 <IP, 拉黑时间>
    private static final ConcurrentHashMap<String, LocalDateTime> blacklistedIPs = new ConcurrentHashMap<>();
    
    // 攻击次数阈值 - 超过此次数将被拉黑
    private static final int ATTACK_THRESHOLD = 3;
    
    // 拉黑时长（小时）
    private static final int BLACKLIST_DURATION_HOURS = 24;
    
    // 攻击计数重置时间（小时）- 超过此时间未攻击则重置计数
    private static final int ATTACK_COUNT_RESET_HOURS = 1;
    
    // 内部服务标识
    private static final Set<String> INTERNAL_SERVICES = Set.of(
        "poetize-python",
        "poetize-java", 
        "poetize-prerender",
        "poetize-nginx"
    );

    // 常见的恶意扫描路径 - 只包含明确的恶意路径，避免误拦截正常请求
    private static final Set<String> MALICIOUS_PATHS = Set.of(
        "/.env",                    // 环境变量文件
        "/.env.local",
        "/.env.production", 
        "/.git",                    // Git版本控制目录
        "/.git/config",
        "/phpmyadmin",              // 数据库管理工具
        "/pma",
        "/wp-admin",                // WordPress管理后台
        "/wp-login.php",
        "/wp-config.php",
        "/config.php",              // PHP配置文件
        "/database.php",
        "/xmlrpc.php",              // WordPress XML-RPC
        "/.aws",                    // AWS配置
        "/.docker",                 // Docker配置
        "/docker-compose.yml",
        "/Dockerfile",
        "/.DS_Store",               // macOS系统文件
        "/admin.php",               // PHP管理页面（而不是/admin）
        "/admin/login.php",
        "/administrator.php",
        "/manager.php",
        "/console.php",
        "/debug.php",
        "/test.php",
        "/info.php",
        "/phpinfo.php",
        "/sql.php",
        "/backup.sql",
        "/database.sql",
        "/.htaccess",               // Apache配置文件
        "/.htpasswd",
        "/web.config",              // IIS配置文件
        "/server.xml",              // Tomcat配置
        "/application.properties",  // Spring配置文件（如果在根目录就是恶意扫描）
        "/application.yml"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, 
                                   FilterChain filterChain) throws ServletException, IOException {
        
        String requestURI = request.getRequestURI();
        String clientIP = getClientIpAddress(request);
        
        // 检查是否为内部服务请求，如果是则直接放行
        if (isInternalServiceRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // 清理过期的拉黑记录和攻击计数
        cleanupExpiredRecords();
        
        // 检查IP是否被拉黑
        if (isIPBlacklisted(clientIP)) {
            log.warn("拒绝已拉黑IP的访问: {} from IP: {}", requestURI, clientIP);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("403 Forbidden - IP Blacklisted");
            return;
        }
        
        boolean isMaliciousRequest = false;
        String attackType = "";
        
        // 检查是否为恶意扫描路径
        if (MALICIOUS_PATHS.contains(requestURI)) {
            isMaliciousRequest = true;
            attackType = "恶意路径扫描";
        }
        // 检查是否包含恶意扫描特征
        else if (containsMaliciousPattern(requestURI)) {
            isMaliciousRequest = true;
            attackType = "可疑请求特征";
        }
        
        if (isMaliciousRequest) {
            // 记录攻击并检查是否需要拉黑
            recordAttackAndCheckBlacklist(clientIP, requestURI, attackType);
            
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            response.getWriter().write("404 Not Found");
            return;
        }
        
        filterChain.doFilter(request, response);
    }
    
    /**
     * 检查是否包含恶意模式
     */
    private boolean containsMaliciousPattern(String requestURI) {
        try {
            // URL解码，防止编码绕过
            String decodedURI = URLDecoder.decode(requestURI, StandardCharsets.UTF_8);
            String lowerURI = decodedURI.toLowerCase();
            
            // 路径遍历攻击
            if (decodedURI.contains("..") || decodedURI.contains("./")) {
                return true;
            }
            
            // 敏感文件扩展名
            if (decodedURI.endsWith(".sql") || decodedURI.endsWith(".bak") || 
                decodedURI.endsWith(".backup") || decodedURI.endsWith(".old") || 
                decodedURI.endsWith(".tmp") || decodedURI.endsWith(".log")) {
                return true;
            }
            
            // PHP相关恶意路径（WordPress、PHPMyAdmin等）
            if (lowerURI.contains("/wp-") || lowerURI.contains(".php.") ||
                lowerURI.contains("admin.php") || lowerURI.contains("login.php") ||
                lowerURI.contains("config.php") || lowerURI.contains("phpinfo")) {
                return true;
            }
            
            // XSS和脚本注入检测
            if (lowerURI.contains("<script") || lowerURI.contains("javascript:") ||
                lowerURI.contains("eval(") || lowerURI.contains("base64_decode")) {
                return true;
            }
            
            // SQL注入检测（大小写不敏感）
            if (lowerURI.contains("union") && lowerURI.contains("select") ||
                lowerURI.contains("drop") && lowerURI.contains("table") ||
                lowerURI.contains("insert") && lowerURI.contains("into") ||
                lowerURI.contains("delete") && lowerURI.contains("from") ||
                lowerURI.contains("update") && lowerURI.contains("set") ||
                lowerURI.contains("select") && lowerURI.contains("from")) {
                return true;
            }
            
            return false;
            
        } catch (Exception e) {
            // URL解码失败，可能是恶意构造的URL
            log.warn("URL解码失败，可能为恶意请求: {}", requestURI);
            return true;
        }
    }
    
    /**
     * 检查是否为内部服务请求
     */
    private boolean isInternalServiceRequest(HttpServletRequest request) {
        String internalService = request.getHeader("X-Internal-Service");
        
        if (internalService != null) {
            if (INTERNAL_SERVICES.contains(internalService)) {
                log.debug("识别到内部服务请求: {} from {}, IP: {}", 
                         request.getRequestURI(), internalService, getClientIpAddress(request));
                return true;
            } else {
                // 记录未知的内部服务标识，可能是恶意伪造
                log.warn("检测到未知的内部服务标识: {}, URI: {}, IP: {}", 
                        internalService, request.getRequestURI(), getClientIpAddress(request));
            }
        }
        
        return false;
    }
    
    /**
     * 获取客户端真实IP地址
     */
    private String getClientIpAddress(HttpServletRequest request) {
        try {
            // 尝试从X-Forwarded-For获取（反向代理场景）
            String xForwardedFor = request.getHeader("X-Forwarded-For");
            if (isValidIP(xForwardedFor)) {
                // 取第一个IP（客户端真实IP）
                String ip = xForwardedFor.split(",")[0].trim();
                if (isValidIP(ip)) {
                    return ip;
                }
            }
            
            // 尝试从X-Real-IP获取（Nginx代理场景）
            String xRealIp = request.getHeader("X-Real-IP");
            if (isValidIP(xRealIp)) {
                return xRealIp;
            }
            
            // 尝试从Proxy-Client-IP获取
            String proxyClientIp = request.getHeader("Proxy-Client-IP");
            if (isValidIP(proxyClientIp)) {
                return proxyClientIp;
            }
            
            // 尝试从WL-Proxy-Client-IP获取（WebLogic）
            String wlProxyClientIp = request.getHeader("WL-Proxy-Client-IP");
            if (isValidIP(wlProxyClientIp)) {
                return wlProxyClientIp;
            }
            
            // 最后尝试从RemoteAddr获取
            String remoteAddr = request.getRemoteAddr();
            if (isValidIP(remoteAddr)) {
                return remoteAddr;
            }
            
            // 如果所有方法都获取不到有效IP，返回默认值
            log.warn("无法获取客户端真实IP地址，请求URI: {}, User-Agent: {}", 
                    request.getRequestURI(), request.getHeader("User-Agent"));
            return "unknown_ip";
            
        } catch (Exception e) {
            log.error("获取客户端IP地址时发生异常: {}", e.getMessage(), e);
            return "unknown_ip";
        }
    }
    
    /**
     * 验证IP地址是否有效
     */
    private boolean isValidIP(String ip) {
        if (ip == null || ip.trim().isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            return false;
        }
        
        ip = ip.trim();
        
        // 过滤掉明显无效的IP值
        if ("::1".equals(ip) || "0:0:0:0:0:0:0:1".equals(ip) || "localhost".equals(ip)) {
            return false;
        }
        
        // 127.0.0.1本地回环地址在生产环境通常不是真实客户端IP
        // 但在开发环境可能是有效的，所以保留但会在日志中标记
        if ("127.0.0.1".equals(ip)) {
            log.debug("检测到本地回环IP: {}，可能在开发环境或本地测试", ip);
            return true;
        }
        
        // 检查是否为内网IP（保留，因为可能是企业内网环境）
        if (isPrivateIP(ip)) {
            log.debug("检测到内网IP: {}，可能在企业内网或代理环境", ip);
            return true;
        }
        
        return true;
    }
    
    /**
     * 判断是否为内网IP
     */
    private boolean isPrivateIP(String ip) {
        if (ip == null) return false;
        
        // 常见内网IP段
        return ip.startsWith("192.168.") || 
               ip.startsWith("10.") || 
               (ip.startsWith("172.") && isInRange172(ip)) ||
               ip.startsWith("169.254."); // 链路本地地址
    }
    
    /**
     * 检查是否在172.16.0.0-172.31.255.255范围内
     */
    private boolean isInRange172(String ip) {
        try {
            String[] parts = ip.split("\\.");
            if (parts.length >= 2) {
                int second = Integer.parseInt(parts[1]);
                return second >= 16 && second <= 31;
            }
        } catch (NumberFormatException e) {
            // 忽略解析错误
        }
        return false;
    }
    
    /**
     * 检查IP是否被拉黑
     */
    private boolean isIPBlacklisted(String ip) {
        LocalDateTime blacklistTime = blacklistedIPs.get(ip);
        if (blacklistTime == null) {
            return false;
        }
        
        // 检查是否已过期
        if (LocalDateTime.now().isAfter(blacklistTime.plusHours(BLACKLIST_DURATION_HOURS))) {
            blacklistedIPs.remove(ip);
            return false;
        }
        
        return true;
    }
    
    /**
     * 记录攻击并检查是否需要拉黑
     */
    private void recordAttackAndCheckBlacklist(String ip, String requestURI, String attackType) {
        // 对于无法获取IP的情况，采用更严格的策略
        if ("unknown_ip".equals(ip)) {
            log.error("拦截{}攻击: {} from 未知IP (无法获取真实IP地址，可能存在代理配置问题或恶意伪造)", 
                     attackType, requestURI);
            
            // 对unknown_ip立即拉黑，防止绕过检测
            LocalDateTime blacklistTime = LocalDateTime.now();
            blacklistedIPs.put(ip, blacklistTime);
            
            log.error("未知IP因恶意攻击被立即拉黑{}小时，拉黑时间: {}", 
                     BLACKLIST_DURATION_HOURS, 
                     blacklistTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            return;
        }
        
        // 正常IP的处理逻辑
        AtomicInteger count = ipAttackCount.computeIfAbsent(ip, k -> new AtomicInteger(0));
        int currentCount = count.incrementAndGet();
        
        log.warn("拦截{}攻击: {} from IP: {} (攻击次数: {})", attackType, requestURI, ip, currentCount);
        
        // 检查是否达到拉黑阈值
        if (currentCount >= ATTACK_THRESHOLD) {
            LocalDateTime blacklistTime = LocalDateTime.now();
            blacklistedIPs.put(ip, blacklistTime);
            
            log.error("IP {} 因连续{}次恶意攻击被拉黑{}小时，拉黑时间: {}", 
                     ip, currentCount, BLACKLIST_DURATION_HOURS, 
                     blacklistTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
            
            // 重置攻击计数
            ipAttackCount.remove(ip);
        }
    }
    
    /**
     * 清理过期的拉黑记录和攻击计数
     */
    private void cleanupExpiredRecords() {
        LocalDateTime now = LocalDateTime.now();
        
        // 清理过期的拉黑记录
        blacklistedIPs.entrySet().removeIf(entry -> 
            now.isAfter(entry.getValue().plusHours(BLACKLIST_DURATION_HOURS))
        );
        
        // 清理过期的攻击计数（超过1小时未攻击则重置）
        ipAttackCount.entrySet().removeIf(entry -> {
            // 这里简化处理，实际可以记录最后攻击时间
            // 目前每次清理都会重置所有计数，防止内存泄漏
            return ipAttackCount.size() > 1000; // 防止内存占用过大
        });
    }
    
    /**
     * 获取当前拉黑的IP数量（用于监控）
     */
    public static int getBlacklistedIPCount() {
        return blacklistedIPs.size();
    }
    
    /**
     * 获取当前监控的IP数量（用于监控）
     */
    public static int getMonitoredIPCount() {
        return ipAttackCount.size();
    }
    
    /**
     * 手动解除IP拉黑（管理员功能）
     */
    public static boolean unblacklistIP(String ip) {
        boolean removed = blacklistedIPs.remove(ip) != null;
        ipAttackCount.remove(ip);
        if (removed) {
            log.info("管理员手动解除IP拉黑: {}", ip);
        }
        return removed;
    }
} 