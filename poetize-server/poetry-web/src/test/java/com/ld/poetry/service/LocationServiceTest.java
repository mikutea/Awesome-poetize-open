package com.ld.poetry.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LocationService 测试类
 * 简单的单元测试，不依赖Spring上下文
 */
public class LocationServiceTest {

    private LocationService locationService;

    @BeforeEach
    void setUp() {
        locationService = new LocationService();
    }

    @Test
    public void testInternalIp() {
        // 测试内网IP
        assertEquals("内网IP", locationService.getLocationByIp("192.168.1.1"));
        assertEquals("内网IP", locationService.getLocationByIp("10.0.0.1"));
        assertEquals("内网IP", locationService.getLocationByIp("172.16.0.1"));
        assertEquals("内网IP", locationService.getLocationByIp("127.0.0.1"));
    }

    @Test
    public void testInvalidIp() {
        // 测试无效IP
        assertEquals("未知", locationService.getLocationByIp("unknown"));
        assertEquals("未知", locationService.getLocationByIp(""));
        assertEquals("未知", locationService.getLocationByIp(null));
    }

    @Test
    public void testPublicIp() {
        // 测试公网IP（这个测试需要网络连接）
        // 使用Google的公共DNS IP进行测试
        String location = locationService.getLocationByIp("8.8.8.8");
        assertNotNull(location);
        assertNotEquals("未知", location);
        
        // 测试缓存功能
        String cachedLocation = locationService.getLocationByIp("8.8.8.8");
        assertEquals(location, cachedLocation);
    }
    
    @Test
    public void testChinaSpecialRegions() {
        // 测试中国特殊地区前缀处理
        // 注意：这些是模拟测试，实际需要mock LocationService的parsePublicIpLocation方法
        
        // 可以通过反射测试formatLocation方法
        try {
            java.lang.reflect.Method formatLocationMethod = LocationService.class.getDeclaredMethod("formatLocation", String.class, String.class);
            formatLocationMethod.setAccessible(true);
            
            // 测试香港
            String result1 = (String) formatLocationMethod.invoke(locationService, "中国", "香港特别行政区");
            assertEquals("中国香港", result1);
            
            // 测试澳门
            String result2 = (String) formatLocationMethod.invoke(locationService, "中国", "澳门特别行政区");
            assertEquals("中国澳门", result2);
            
            // 测试台湾
            String result3 = (String) formatLocationMethod.invoke(locationService, "中国", "台湾省");
            assertEquals("中国台湾", result3);
            
            // 测试英文名称
            String result4 = (String) formatLocationMethod.invoke(locationService, "China", "Hong Kong");
            assertEquals("中国香港", result4);
            
            String result5 = (String) formatLocationMethod.invoke(locationService, "China", "Macao");
            assertEquals("中国澳门", result5);
            
            String result6 = (String) formatLocationMethod.invoke(locationService, "China", "Taiwan");
            assertEquals("中国台湾", result6);
            
            // 测试普通省份（不加前缀）
            String result7 = (String) formatLocationMethod.invoke(locationService, "中国", "广东省");
            assertEquals("广东", result7);
            
            String result8 = (String) formatLocationMethod.invoke(locationService, "中国", "北京市");
            assertEquals("北京", result8);
            
        } catch (Exception e) {
            fail("反射调用formatLocation方法失败: " + e.getMessage());
        }
    }

    @Test
    public void testCacheSize() {
        // 测试缓存功能
        int initialSize = locationService.getCacheSize();
        
        locationService.getLocationByIp("192.168.1.100");
        assertEquals(initialSize + 1, locationService.getCacheSize());
        
        // 清理缓存
        locationService.clearLocationCache();
        assertEquals(0, locationService.getCacheSize());
    }

    @Test
    public void testChinaIpFormatting() {
        // 这个测试需要模拟API响应，实际项目中可以使用Mock
        // 这里只是展示测试的思路
        
        // 可以通过反射或者创建测试专用的方法来测试内部逻辑
        // 例如测试formatLocation方法的逻辑
    }
}
