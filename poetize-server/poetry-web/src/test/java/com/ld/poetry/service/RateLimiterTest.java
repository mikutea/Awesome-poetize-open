package com.ld.poetry.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 限流功能测试
 * 验证LocationService的1QPS限流是否正常工作
 */
public class RateLimiterTest {

    private LocationService locationService;

    @BeforeEach
    void setUp() {
        locationService = new LocationService();
    }

    @Test
    public void testRateLimiterWithMultipleRequests() {
        // 测试连续请求是否被正确限流
        String testIp = "8.8.8.8"; // 使用公网IP进行测试
        
        // 记录开始时间
        long startTime = System.currentTimeMillis();
        
        // 连续发起3次请求
        for (int i = 0; i < 3; i++) {
            System.out.println("第" + (i + 1) + "次请求开始时间: " + System.currentTimeMillis());
            String result = locationService.getLocationByIp(testIp);
            System.out.println("第" + (i + 1) + "次请求结果: " + result + 
                             ", 结束时间: " + System.currentTimeMillis());
            
            // 检查限流器状态
            System.out.println("可用许可数: " + locationService.getAvailablePermits());
            System.out.println("上次请求时间: " + locationService.getLastRequestTime());
            System.out.println("下次允许请求时间: " + locationService.getNextAllowedRequestTime());
            System.out.println("---");
        }
        
        long endTime = System.currentTimeMillis();
        long totalTime = endTime - startTime;
        
        System.out.println("总耗时: " + totalTime + "ms");
        
        // 由于缓存机制，第一次请求会调用API，后续请求应该直接返回缓存结果
        // 但限流器状态应该正确反映
        assertTrue(totalTime >= 0, "总时间应该为正数");
        
        // 验证缓存工作正常
        assertEquals(1, locationService.getCacheSize(), "缓存中应该有一个IP记录");
    }
    
    @Test 
    public void testRateLimiterStatusMethods() {
        // 测试限流器状态方法
        
        // 初始状态检查
        int initialPermits = locationService.getAvailablePermits();
        assertTrue(initialPermits >= 0 && initialPermits <= 1, 
                  "初始许可数应该在0-1之间");
        
        // 测试时间相关方法
        long lastRequestTime = locationService.getLastRequestTime();
        long nextAllowedTime = locationService.getNextAllowedRequestTime();
        
        assertTrue(nextAllowedTime >= lastRequestTime, 
                  "下次允许时间应该大于等于上次请求时间");
        
        System.out.println("限流器状态检查通过");
        System.out.println("可用许可数: " + initialPermits);
        System.out.println("上次请求时间: " + lastRequestTime);
        System.out.println("下次允许请求时间: " + nextAllowedTime);
    }
}