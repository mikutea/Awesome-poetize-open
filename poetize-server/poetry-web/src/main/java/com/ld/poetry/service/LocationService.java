package com.ld.poetry.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * IP地理位置解析服务
 * 用于获取IP地址对应的地理位置信息
 */
@Slf4j
@Service
public class LocationService {

    private final RestTemplate restTemplate = new RestTemplate();
    
    // IP地理位置缓存，避免重复查询
    private final ConcurrentHashMap<String, String> locationCache = new ConcurrentHashMap<>();
    
    // IPv4地址正则表达式
    private static final Pattern IPV4_PATTERN = Pattern.compile(
        "^(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])$"
    );
    
    // 淘宝IP服务限流器 - 1QPS限制
    private final Semaphore rateLimiter = new Semaphore(1);
    private volatile long lastRequestTime = 0L;
    private static final long MIN_REQUEST_INTERVAL = 1000L; // 1秒间隔

    /**
     * 根据IP地址获取地理位置
     * @param ipAddress IP地址
     * @return 地理位置字符串
     */
    public String getLocationByIp(String ipAddress) {
        if (!StringUtils.hasText(ipAddress) || "unknown".equals(ipAddress)) {
            return "未知";
        }

        // 检查缓存
        String cachedLocation = locationCache.get(ipAddress);
        if (cachedLocation != null) {
            return cachedLocation;
        }

        // 检查是否为内网IP
        if (isInternalIp(ipAddress)) {
            String location = "内网IP";
            locationCache.put(ipAddress, location);
            return location;
        }

        // 解析公网IP地理位置
        String location = parsePublicIpLocation(ipAddress);
        
        // 缓存结果
        locationCache.put(ipAddress, location);
        
        return location;
    }

    /**
     * 解析公网IP的地理位置
     * @param ipAddress IP地址
     * @return 地理位置
     */
    private String parsePublicIpLocation(String ipAddress) {
        // 使用淘宝IP服务获取地理位置信息
        return tryTaobaoIpService(ipAddress);
    }
    
    /**
     * 使用淘宝IP服务解析IP地理位置
     * 实现1QPS限流，确保不超过服务访问频率限制
     * @param ipAddress IP地址
     * @return 地理位置
     */
    private String tryTaobaoIpService(String ipAddress) {
        try {
            // 获取限流许可，最多等待5秒
            if (!rateLimiter.tryAcquire(5, TimeUnit.SECONDS)) {
                log.warn("获取淘宝IP服务限流许可超时，IP: {}", ipAddress);
                return "未知";
            }
            
            try {
                // 确保与上次请求间隔至少1秒
                long currentTime = System.currentTimeMillis();
                long timeSinceLastRequest = currentTime - lastRequestTime;
                
                if (timeSinceLastRequest < MIN_REQUEST_INTERVAL) {
                    long sleepTime = MIN_REQUEST_INTERVAL - timeSinceLastRequest;
                    log.debug("淘宝IP服务限流等待: {}ms, IP: {}", sleepTime, ipAddress);
                    Thread.sleep(sleepTime);
                }
                
                // 更新请求时间
                lastRequestTime = System.currentTimeMillis();
                
                // 使用淘宝IP服务API
                String apiUrl = "http://ip.taobao.com/outGetIpInfo?ip=" + ipAddress + "&accessKey=alibaba-inc";
                
                // 设置超时时间
                restTemplate.getInterceptors().clear();
                
                String response = restTemplate.getForObject(apiUrl, String.class);
                
                if (StringUtils.hasText(response)) {
                    return parseTaobaoIpResponse(response);
                }
                
            } finally {
                // 释放限流许可
                rateLimiter.release();
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("淘宝IP服务请求被中断: {}", ipAddress);
        } catch (Exception e) {
            log.warn("淘宝IP服务解析IP地理位置失败: {}, 错误: {}", ipAddress, e.getMessage());
        }
        
        return "未知";
    }


    /**
     * 解析淘宝IP服务响应
     * @param response API响应JSON字符串
     * @return 格式化的地理位置
     */
    private String parseTaobaoIpResponse(String response) {
        try {
            // 淘宝IP服务返回格式: {"data":{"country":"中国","region":"北京","city":"北京"}}
            if (response.contains("\"data\":")) {
                String country = extractJsonValue(response, "country");
                String region = extractJsonValue(response, "region");
                String city = extractJsonValue(response, "city");
                
                // 直接使用API返回的地理位置信息
                return formatTaobaoLocation(country, region, city);
            }
        } catch (Exception e) {
            log.warn("解析淘宝IP服务响应失败: {}", e.getMessage());
        }
        
        return "未知";
    }

    
    /**
     * 格式化淘宝IP服务返回的地理位置信息
     * @param country 国家
     * @param region 地区/省份
     * @param city 城市
     * @return 格式化后的位置
     */
    private String formatTaobaoLocation(String country, String region, String city) {
        if (!"中国".equals(country)) {
            // 非中国地区，返回国家名
            return StringUtils.hasText(country) ? country : "未知";
        }
        
        // 中国地区处理
        if (StringUtils.hasText(region)) {
            // 特殊地区加上中国前缀
            if ("香港".equals(region)) {
                return "中国香港";
            } else if ("澳门".equals(region)) {
                return "中国澳门";
            } else if ("台湾".equals(region)) {
                return "中国台湾";
            } else {
                // 中国大陆省份，去掉后缀
                String province = region.replaceAll("省|市|自治区|特别行政区", "");
                return province;
            }
        }
        
        return "中国";
    }

    /**
     * 从JSON字符串中提取指定字段的值
     * @param json JSON字符串
     * @param field 字段名
     * @return 字段值
     */
    private String extractJsonValue(String json, String field) {
        try {
            String pattern = "\"" + field + "\":\"";
            int startIndex = json.indexOf(pattern);
            if (startIndex != -1) {
                startIndex += pattern.length();
                int endIndex = json.indexOf("\"", startIndex);
                if (endIndex != -1) {
                    return json.substring(startIndex, endIndex);
                }
            }
        } catch (Exception e) {
            log.warn("提取JSON字段失败: {}", e.getMessage());
        }
        return "";
    }

    /**
     * 判断是否为内网IP
     * @param ip IP地址
     * @return 是否为内网IP
     */
    private boolean isInternalIp(String ip) {
        if (!StringUtils.hasText(ip)) {
            return true;
        }

        // 本地回环地址
        if (ip.equals("127.0.0.1") || ip.equals("localhost") || 
            ip.equals("0:0:0:0:0:0:0:1") || ip.equals("::1")) {
            return true;
        }

        // 私有IP地址段
        if (ip.startsWith("192.168.") || 
            ip.startsWith("10.") || 
            (ip.startsWith("172.") && isInRange172(ip))) {
            return true;
        }

        // 其他无效IP
        return ip.equals("unknown") || ip.equals("0.0.0.0");
    }

    /**
     * 检查是否在172.16.0.0-172.31.255.255范围内
     * @param ip IP地址
     * @return 是否在范围内
     */
    private boolean isInRange172(String ip) {
        try {
            String[] parts = ip.split("\\.");
            if (parts.length == 4) {
                int secondOctet = Integer.parseInt(parts[1]);
                return secondOctet >= 16 && secondOctet <= 31;
            }
        } catch (Exception e) {
            // 忽略解析错误
        }
        return false;
    }

    /**
     * 清理位置缓存（可用于定期清理）
     */
    public void clearLocationCache() {
        locationCache.clear();
        log.info("IP地理位置缓存已清理");
    }

    /**
     * 获取缓存统计信息
     * @return 缓存大小
     */
    public int getCacheSize() {
        return locationCache.size();
    }
    
    /**
     * 获取限流器状态信息
     * @return 限流器可用许可数
     */
    public int getAvailablePermits() {
        return rateLimiter.availablePermits();
    }
    
    /**
     * 获取上次请求时间
     * @return 上次请求时间戳
     */
    public long getLastRequestTime() {
        return lastRequestTime;
    }
    
    /**
     * 获取下次可请求时间
     * @return 下次可请求时间戳
     */
    public long getNextAllowedRequestTime() {
        return lastRequestTime + MIN_REQUEST_INTERVAL;
    }
}
