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
 * 腾讯位置服务（LBS）IP地理位置解析提供者
 * 支持IPv4和IPv6地址解析
 * 
 * @author LeapYa
 */
@Slf4j
@Component
public class TencentLbsProvider implements IpLocationProvider {
    
    private final RestTemplate restTemplate = new RestTemplate();
    private final Semaphore rateLimiter = new Semaphore(1);
    private volatile long lastRequestTime = 0L;
    private static final long MIN_REQUEST_INTERVAL = 1000L; // 1秒间隔
    
    private String apiKey;
    
    /**
     * 设置API密钥
     * @param apiKey 腾讯位置服务API密钥
     */
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
    
    @Override
    public ProviderType getProviderType() {
        return ProviderType.TENCENT_LBS;
    }
    
    @Override
    public String resolveLocation(String ipAddress) {
        if (!isAvailable()) {
            return "未知";
        }
        
        try {
            // 获取限流许可，最多等待5秒
            if (!rateLimiter.tryAcquire(5, TimeUnit.SECONDS)) {
                log.warn("获取腾讯位置服务限流许可超时，IP: {}", ipAddress);
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
                
                // 腾讯位置服务API地址
                String apiUrl = "https://apis.map.qq.com/ws/location/v1/ip?ip=" + ipAddress + "&key=" + apiKey;
                
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
            log.warn("腾讯位置服务请求被中断: {}", ipAddress);
        } catch (Exception e) {
            log.warn("腾讯位置服务解析IP地理位置失败: {}, 错误: {}", ipAddress, e.getMessage());
        }
        
        return "未知";
    }
    
    @Override
    public boolean isAvailable() {
        return StringUtils.hasText(apiKey);
    }
    
    @Override
    public boolean supportsIpType(String ipAddress) {
        // 腾讯位置服务支持IPv4和IPv6
        return true;
    }
    
    /**
     * 解析腾讯位置服务响应
     * @param response API响应JSON字符串
     * @return 格式化的地理位置
     */
    private String parseResponse(String response) {
        try {
            // 腾讯位置服务返回格式：{"status":0,"message":"query ok","result":{"ip":"8.8.8.8","location":{"lat":37.751,"lng":-97.822},"ad_info":{"nation":"美国","province":"","city":"","district":"","adcode":0}}}
            if (response.contains("\"status\":0") && response.contains("\"result\":")) {
                
                // 提取地理位置信息
                String nation = extractJsonValue(response, "nation");
                String province = extractJsonValue(response, "province");
                String city = extractJsonValue(response, "city");
                
                return formatLocation(nation, province, city);
                
            } else if (response.contains("\"status\":")) {
                // 提取错误信息
                String message = extractJsonValue(response, "message");
                log.warn("腾讯位置服务返回错误: {}", message);
            }
        } catch (Exception e) {
            log.warn("解析腾讯位置服务响应失败: {}", e.getMessage());
        }
        
        return "未知";
    }
    
    /**
     * 格式化腾讯位置服务返回的地理位置信息
     * @param nation 国家
     * @param province 省份
     * @param city 城市
     * @return 格式化后的位置
     */
    private String formatLocation(String nation, String province, String city) {
        if (!"中国".equals(nation)) {
            // 非中国地区，返回国家名
            return StringUtils.hasText(nation) ? nation : "未知";
        }
        
        // 中国地区处理
        if (StringUtils.hasText(province)) {
            // 特殊地区加上中国前缀
            if ("香港".equals(province)) {
                return "中国香港";
            } else if ("澳门".equals(province)) {
                return "中国澳门";
            } else if ("台湾".equals(province)) {
                return "中国台湾";
            } else {
                // 中国大陆省份，去掉后缀
                String cleanProvince = province.replaceAll("省|市|自治区|特别行政区", "");
                return cleanProvince;
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