package com.ld.poetry.service.provider;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.lionsoul.ip2region.xdb.Searcher;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;
import java.net.Inet6Address;
import java.net.InetAddress;

/**
 * IP2Region离线库地理位置解析提供者
 * 作为备用方案，无网络依赖，响应快速
 * 
 * @author LeapYa
 */
@Slf4j
@Component
public class Ip2RegionProvider implements IpLocationProvider {
    
    private Searcher ip2regionSearcher;
    
    @PostConstruct
    public void initIp2Region() {
        try {
            ip2regionSearcher = Searcher.newWithBuffer(
                IOUtils.toByteArray(new ClassPathResource("ip2region.xdb").getInputStream())
            );
            log.info("IP2Region离线库初始化成功，作为备用IP解析方案");
        } catch (Exception e) {
            log.warn("IP2Region离线库初始化失败: {}", e.getMessage());
            ip2regionSearcher = null;
        }
    }
    
    @Override
    public ProviderType getProviderType() {
        return ProviderType.IP2_REGION;
    }
    
    @Override
    public String resolveLocation(String ipAddress) {
        if (!isAvailable()) {
            return "未知";
        }
        
        try {
            String searchResult = ip2regionSearcher.search(ipAddress);
            if (StringUtils.hasText(searchResult)) {
                return parseResponse(searchResult);
            }
        } catch (Exception e) {
            log.warn("IP2Region离线库解析IP失败: {}, 错误: {}", ipAddress, e.getMessage());
        }
        
        return "未知";
    }
    
    @Override
    public boolean isAvailable() {
        return ip2regionSearcher != null;
    }
    
    @Override
    public boolean supportsIpType(String ipAddress) {
        // IP2Region主要支持IPv4，对IPv6支持有限
        return !isIPv6Address(ipAddress);
    }
    
    /**
     * 解析IP2Region响应结果
     * IP2Region格式: 国家|区域|省份|城市|ISP
     * @param searchResult IP2Region搜索结果
     * @return 格式化的地理位置
     */
    private String parseResponse(String searchResult) {
        try {
            String[] regions = searchResult.split("\\|");
            if (regions.length >= 4) {
                String country = regions[0];
                String province = regions[2];
                
                // 如果不是中国，直接返回国家名
                if (!"中国".equals(country) && !"0".equals(country)) {
                    return country;
                }
                
                // 中国地区处理
                if (StringUtils.hasText(province) && !"0".equals(province)) {
                    // 特殊地区处理
                    if ("香港".equals(province)) {
                        return "中国香港";
                    } else if ("澳门".equals(province)) {
                        return "中国澳门";
                    } else if ("台湾".equals(province)) {
                        return "中国台湾";
                    } else {
                        // 中国大陆省份，去掉后缀
                        return province.replaceAll("省|市|自治区|特别行政区", "");
                    }
                }
                
                return "中国";
            }
        } catch (Exception e) {
            log.warn("解析IP2Region响应失败: {}", e.getMessage());
        }
        
        return "未知";
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
    
    /**
     * 获取IP2Region搜索器状态
     * @return 搜索器是否已初始化
     */
    public boolean isSearcherInitialized() {
        return ip2regionSearcher != null;
    }
}