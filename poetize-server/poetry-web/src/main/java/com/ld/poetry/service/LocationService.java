package com.ld.poetry.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.ConcurrentHashMap;
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
        try {
            // 使用免费的IP地理位置API
            // 这里使用ip-api.com作为示例，您可以根据需要更换其他服务
            String apiUrl = "http://ip-api.com/json/" + ipAddress + "?lang=zh-CN&fields=status,country,regionName,city";
            
            // 设置超时时间
            restTemplate.getInterceptors().clear();
            
            String response = restTemplate.getForObject(apiUrl, String.class);
            
            if (StringUtils.hasText(response)) {
                return parseLocationFromResponse(response);
            }
            
        } catch (Exception e) {
            log.warn("解析IP地理位置失败: {}, 错误: {}", ipAddress, e.getMessage());
        }
        
        return "未知";
    }

    /**
     * 解析API响应获取地理位置
     * @param response API响应JSON字符串
     * @return 格式化的地理位置
     */
    private String parseLocationFromResponse(String response) {
        try {
            // 简单的JSON解析，您可以使用Jackson或其他JSON库
            if (response.contains("\"status\":\"success\"")) {
                String country = extractJsonValue(response, "country");
                String region = extractJsonValue(response, "regionName");
                
                // 根据国家和地区格式化位置信息
                return formatLocation(country, region);
            }
        } catch (Exception e) {
            log.warn("解析地理位置响应失败: {}", e.getMessage());
        }
        
        return "未知";
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
     * 格式化地理位置信息
     * @param country 国家
     * @param region 地区/省份
     * @return 格式化后的位置
     */
    private String formatLocation(String country, String region) {
        if (!StringUtils.hasText(country)) {
            return "未知";
        }

        // 中国大陆地区显示省份
        if ("中国".equals(country) || "China".equals(country)) {
            if (StringUtils.hasText(region)) {
                // 处理特殊地区
                if (region.contains("香港") || region.contains("Hong Kong")) {
                    return "香港";
                } else if (region.contains("澳门") || region.contains("Macao")) {
                    return "澳门";
                } else if (region.contains("台湾") || region.contains("Taiwan")) {
                    return "台湾";
                } else {
                    // 中国大陆省份，去掉"省"、"市"、"自治区"等后缀
                    String province = region.replaceAll("省|市|自治区|特别行政区", "");
                    return province;
                }
            }
            return "中国";
        }

        // 海外地区显示国家名
        return country;
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
}
