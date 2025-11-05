package com.ld.poetry.service.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 淘宝IP服务地理位置解析提供者
 * 主要支持IPv4地址解析
 * 
 * @author LeapYa
 */
@Slf4j
@Component
public class TaobaoIpProvider implements IpLocationProvider {
    
    private final RestTemplate restTemplate = new RestTemplate();
    private final Semaphore rateLimiter = new Semaphore(1);
    private volatile long lastRequestTime = 0L;
    private static final long MIN_REQUEST_INTERVAL = 1000L; // 1秒间隔
    
    @Override
    public ProviderType getProviderType() {
        return ProviderType.TAOBAO_IP;
    }
    
    @Override
    public String resolveLocation(String ipAddress) {
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
                    Thread.sleep(sleepTime);
                }
                
                // 更新请求时间
                lastRequestTime = System.currentTimeMillis();
                
                // 使用淘宝IP服务API
                String apiUrl = "http://ip.taobao.com/outGetIpInfo?ip=" + ipAddress + "&accessKey=alibaba-inc";
                
                String response = restTemplate.getForObject(apiUrl, String.class);
                
                if (StringUtils.hasText(response)) {
                    return parseResponse(response);
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
    
    @Override
    public boolean isAvailable() {
        // 淘宝IP服务无需特殊配置，默认可用
        return true;
    }
    
    @Override
    public boolean supportsIpType(String ipAddress) {
        // 淘宝IP服务主要支持IPv4，对IPv6支持有限
        return !isIPv6Address(ipAddress);
    }
    
    /**
     * 解析淘宝IP服务响应
     * @param response API响应JSON字符串
     * @return 格式化的地理位置
     */
    private String parseResponse(String response) {
        try {
            // 淘宝IP服务返回格式: {"data":{"country":"中国","region":"北京","city":"北京"}}
            if (response.contains("\"data\":")) {
                String country = extractJsonValue(response, "country");
                String region = extractJsonValue(response, "region");
                String city = extractJsonValue(response, "city");
                
                // 直接使用API返回的地理位置信息
                return formatLocation(country, region, city);
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
    private String formatLocation(String country, String region, String city) {
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
     * 判断是否为IPv6地址
     * @param ip IP地址
     * @return 是否为IPv6
     */
    private boolean isIPv6Address(String ip) {
        if (!StringUtils.hasText(ip)) {
            return false;
        }
        
        try {
            InetAddress inetAddress = InetAddress.getByName(ip);
            return inetAddress instanceof Inet6Address;
        } catch (Exception e) {
            // 如果Java解析失败，使用简单的字符串匹配
            return ip.contains(":") && !ip.contains(".");
        }
    }
}