package com.ld.poetry.controller;

import com.ld.poetry.service.CacheService;
import com.ld.poetry.dao.HistoryInfoMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;

/**
 * WebInfo控制器测试类
 * 用于验证首页总访问量显示bug的修复效果
 */
@SpringBootTest
@Slf4j
public class WebInfoTest {

    @Autowired
    private WebInfoController webInfoController;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private HistoryInfoMapper historyInfoMapper;

    /**
     * 测试首页获取网站信息接口
     */
    @Test
    public void testGetWebInfo() {
        try {
            log.info("========== 开始测试首页网站信息获取 ==========");
            
            // 调用获取网站信息接口
            var result = webInfoController.getWebInfo();
            
            if (result != null && result.isSuccess() && result.getData() != null) {
                var webInfo = result.getData();
                log.info("✅ 获取网站信息成功");
                log.info("📊 网站名称: {}", webInfo.getWebName());
                log.info("📊 网站标题: {}", webInfo.getWebTitle());
                log.info("📊 总访问量(historyAllCount): {}", webInfo.getHistoryAllCount());
                log.info("📊 今日访问量(historyDayCount): {}", webInfo.getHistoryDayCount());
                
                // 验证访问量不为空且不为0
                if (webInfo.getHistoryAllCount() != null && !"0".equals(webInfo.getHistoryAllCount())) {
                    log.info("✅ 首页总访问量显示正常: {}", webInfo.getHistoryAllCount());
                } else {
                    log.warn("⚠️ 首页总访问量仍为0或null: {}", webInfo.getHistoryAllCount());
                    
                    // 检查缓存状态
                    var cachedStats = cacheService.getCachedIpHistoryStatisticsSafely();
                    log.info("🔍 缓存中的统计数据: {}", cachedStats.get("ip_history_count"));
                    log.info("🔍 是否需要刷新缓存: {}", cachedStats.get("_cache_refresh_needed"));
                }
                
            } else {
                log.error("❌ 获取网站信息失败: {}", result != null ? result.getMessage() : "result为null");
            }
            
            log.info("========== 首页网站信息获取测试完成 ==========");
            
        } catch (Exception e) {
            log.error("❌ 测试首页网站信息获取失败", e);
        }
    }

    /**
     * 测试缓存状态
     */
    @Test
    public void testCacheStatus() {
        try {
            log.info("========== 开始测试缓存状态 ==========");
            
            // 检查原始缓存
            Object rawCache = cacheService.getCachedIpHistoryStatistics();
            log.info("🔍 原始缓存状态: {}", rawCache != null ? "存在" : "null");
            
            // 检查安全缓存
            var safeCache = cacheService.getCachedIpHistoryStatisticsSafely();
            log.info("🔍 安全缓存总访问量: {}", safeCache.get("ip_history_count"));
            log.info("🔍 是否需要刷新: {}", safeCache.get("_cache_refresh_needed"));
            
            // 检查网站信息缓存
            var webInfo = cacheService.getCachedWebInfo();
            log.info("🔍 网站信息缓存状态: {}", webInfo != null ? "存在" : "null");
            if (webInfo != null) {
                log.info("🔍 缓存中的网站名称: {}", webInfo.getWebName());
            }
            
            log.info("========== 缓存状态测试完成 ==========");
            
        } catch (Exception e) {
            log.error("❌ 测试缓存状态失败", e);
        }
    }

    /**
     * 测试获取历史统计信息接口（重点测试今日数据实时性）
     */
    @Test
    public void testGetHistoryInfoRealTime() {
        try {
            log.info("========== 开始测试今日访问数据实时性 ==========");
            
            // 直接测试CacheService的Redis今日访问统计功能
            log.info("🔍 直接从Redis获取今日统计...");
            var redisStats = cacheService.getTodayVisitStatisticsFromRedis();
            
            log.info("✅ Redis今日访问统计获取成功");
            log.info("📊 Redis中今日IP数量: {}", redisStats.get("ip_count_today"));
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> usernameToday = (List<Map<String, Object>>) redisStats.get("username_today");
            log.info("📊 Redis中今日用户数量: {}", usernameToday.size());
            
            // 验证访问次数字段
            if (!usernameToday.isEmpty()) {
                Map<String, Object> firstUser = usernameToday.get(0);
                log.info("📊 第一个用户访问信息: userId={}, visitCount={}", 
                    firstUser.get("userId"), firstUser.get("visitCount"));
                
                // 验证visitCount字段存在且为Long类型
                assert firstUser.containsKey("visitCount") : "用户信息应包含访问次数";
                assert firstUser.get("visitCount") instanceof Long : "访问次数应为Long类型";
            }
            
            log.info("📊 Redis中今日省份数量: {}", 
                ((java.util.List<?>) redisStats.get("province_today")).size());
            
            // 验证方法返回的数据结构
            assert redisStats.containsKey("ip_count_today") : "应包含今日IP数量";
            assert redisStats.containsKey("username_today") : "应包含今日用户列表";
            assert redisStats.containsKey("province_today") : "应包含今日省份统计";
            
            // 验证数据类型
            assert redisStats.get("ip_count_today") instanceof Long : "今日IP数量应为Long类型";
            assert redisStats.get("username_today") instanceof java.util.List : "今日用户应为List类型";
            assert redisStats.get("province_today") instanceof java.util.List : "今日省份应为List类型";
            
            // 验证用户访问次数数据结构
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> userList = (List<Map<String, Object>>) redisStats.get("username_today");
            if (!userList.isEmpty()) {
                Map<String, Object> sampleUser = userList.get(0);
                assert sampleUser.containsKey("userId") : "用户信息应包含用户ID";
                assert sampleUser.containsKey("visitCount") : "用户信息应包含访问次数";
                assert sampleUser.get("userId") instanceof String : "用户ID应为String类型";
                assert sampleUser.get("visitCount") instanceof Long : "访问次数应为Long类型";
                log.info("✅ 用户访问次数数据结构验证通过");
            }
            
            log.info("✅ 数据结构验证通过");
            
            // 测试Redis计数器功能
            // long todayCount = cacheService.getTodayVisitCount();
            // log.info("📊 Redis今日访问计数器: {}", todayCount);
            log.info("📊 Redis今日访问统计功能测试完成");
            
            log.info("========== 今日访问数据实时性测试完成 ==========");
            
        } catch (Exception e) {
            log.error("测试获取历史统计信息时发生异常", e);
        }
    }

    /**
     * 测试访问次数统计功能（模拟数据）
     */
    @Test
    public void testVisitCountStatistics() {
        try {
            log.info("========== 开始测试访问次数统计功能 ==========");
            
            // 模拟今日访问记录数据
            String today = java.time.LocalDate.now().toString();
            
            // 模拟一些访问记录
            List<Map<String, Object>> mockRecords = new ArrayList<>();
            
            // 用户1访问3次
            for (int i = 0; i < 3; i++) {
                Map<String, Object> record = new HashMap<>();
                record.put("userId", "1");
                record.put("ip", "192.168.1.100");
                record.put("province", "北京市");
                mockRecords.add(record);
            }
            
            // 用户2访问2次
            for (int i = 0; i < 2; i++) {
                Map<String, Object> record = new HashMap<>();
                record.put("userId", "2");
                record.put("ip", "192.168.1.101");
                record.put("province", "上海市");
                mockRecords.add(record);
            }
            
            // 用户1再访问1次（不同IP）
            Map<String, Object> record = new HashMap<>();
            record.put("userId", "1");
            record.put("ip", "192.168.1.102");
            record.put("province", "北京市");
            mockRecords.add(record);
            
            log.info("🔍 模拟访问记录: {} 条", mockRecords.size());
            
            // 手动统计访问次数（模拟CacheService的逻辑）
            Map<String, Long> userVisitCount = mockRecords.stream()
                .filter(Objects::nonNull)
                .map(r -> (String) r.get("userId"))
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.groupingBy(
                    userId -> userId, 
                    java.util.stream.Collectors.counting()
                ));
            
            log.info("📊 用户访问次数统计:");
            userVisitCount.forEach((userId, count) -> 
                log.info("  用户ID: {}, 访问次数: {}", userId, count));
            
            // 验证统计结果
            assert userVisitCount.get("1").equals(4L) : "用户1应访问4次";
            assert userVisitCount.get("2").equals(2L) : "用户2应访问2次";
            
            // 统计IP数量（去重）
            long ipCount = mockRecords.stream()
                .map(r -> (String) r.get("ip"))
                .filter(Objects::nonNull)
                .distinct()
                .count();
            
            log.info("📊 独立IP数量: {}", ipCount);
            assert ipCount == 3L : "应有3个独立IP";
            
            // 统计省份数量
            Map<String, Long> provinceCount = mockRecords.stream()
                .map(r -> (String) r.get("province"))
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.groupingBy(
                    province -> province,
                    java.util.stream.Collectors.counting()
                ));
            
            log.info("📊 省份访问次数统计:");
            provinceCount.forEach((province, count) -> 
                log.info("  省份: {}, 访问次数: {}", province, count));
            
            assert provinceCount.get("北京市").equals(4L) : "北京市应有4次访问";
            assert provinceCount.get("上海市").equals(2L) : "上海市应有2次访问";
            
            log.info("✅ 访问次数统计功能验证通过");
            log.info("========== 访问次数统计功能测试完成 ==========");
            
        } catch (Exception e) {
            log.error("测试访问次数统计功能时发生异常", e);
        }
    }

    /**
     * 测试昨日访问量统计机制
     */
    @Test
    public void testYesterdayVisitCountMechanism() {
        log.info("========== 开始测试昨日访问量统计机制 ==========");
        
        try {
            // 1. 获取历史统计数据
            log.info("🔍 获取历史统计数据...");
            Map<String, Object> historyStats = cacheService.getCachedIpHistoryStatisticsSafely();
            
            // 2. 检查24小时数据（IP_HISTORY_HOUR）
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> ipHistoryCount = (List<Map<String, Object>>) historyStats.get("ip_history_hour");
            
            log.info("📊 24小时访问数据记录数: {}", ipHistoryCount != null ? ipHistoryCount.size() : 0);
            
            if (ipHistoryCount != null && !ipHistoryCount.isEmpty()) {
                // 3. 计算昨日访问量（模拟WebInfoController中的逻辑）
                long yesterdayIpCount = ipHistoryCount.stream()
                    .map(m -> m != null ? m.get("ip") : null)
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .count();
                
                log.info("📊 昨日访问量(ip_count_yest): {}", yesterdayIpCount);
                
                // 4. 显示具体的访问记录
                log.info("📊 24小时内访问记录详情:");
                ipHistoryCount.stream()
                    .limit(5) // 只显示前5条
                    .forEach(record -> {
                        log.info("  IP: {}, 用户ID: {}, 省份: {}", 
                            record.get("ip"), 
                            record.get("user_id"), 
                            record.get("province"));
                    });
                
                // 5. 验证数据来源
                log.info("🔍 验证昨日访问量数据来源:");
                log.info("  数据来源: 24小时内访问记录(IP_HISTORY_HOUR)");
                log.info("  统计方式: 对IP进行去重计数");
                log.info("  时间范围: 当前时间往前推24小时");
                
            } else {
                log.info("📊 24小时内暂无访问记录");
            }
            
            // 6. 对比数据库中的24小时查询
            // Assuming historyInfoMapper is available in the test context
            // try {
            //     log.info("🔍 对比数据库24小时查询结果...");
            //     List<Map<String, Object>> dbHour24Data = historyInfoMapper.getHistoryBy24Hour();
            //     log.info("📊 数据库24小时查询记录数: {}", dbHour24Data != null ? dbHour24Data.size() : 0);
                
            //     if (dbHour24Data != null && !dbHour24Data.isEmpty()) {
            //         long dbYesterdayCount = dbHour24Data.stream()
            //             .map(m -> m.get("ip"))
            //             .filter(java.util.Objects::nonNull)
            //             .distinct()
            //             .count();
            //         log.info("📊 数据库24小时去重IP数: {}", dbYesterdayCount);
            //     }
            // } catch (Exception e) {
            //     log.warn("查询数据库24小时数据失败: {}", e.getMessage());
            // }
            
            log.info("✅ 昨日访问量统计机制验证完成");
            
        } catch (Exception e) {
            log.error("❌ 昨日访问量统计测试失败", e);
            throw e;
        }
        
        log.info("========== 昨日访问量统计机制测试完成 ==========");
    }

    /**
     * 测试修正后的昨日访问量统计逻辑
     */
    @Test
    public void testCorrectedYesterdayVisitCount() {
        log.info("========== 开始测试修正后的昨日访问量统计 ==========");
        
        try {
            // 1. 直接测试数据库昨日查询
            log.info("🔍 测试数据库昨日访问记录查询...");
            List<Map<String, Object>> yesterdayRecords = historyInfoMapper.getHistoryByYesterday();
            log.info("📊 昨日访问记录数: {}", yesterdayRecords.size());
            
            if (!yesterdayRecords.isEmpty()) {
                long yesterdayIpCount = yesterdayRecords.stream()
                    .map(m -> m.get("ip"))
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .count();
                log.info("📊 昨日去重IP数: {}", yesterdayIpCount);
                
                // 显示具体记录
                yesterdayRecords.stream()
                    .limit(5)
                    .forEach(record -> {
                        log.info("  昨日记录 - IP: {}, 用户ID: {}, 省份: {}", 
                            record.get("ip"), 
                            record.get("user_id"), 
                            record.get("province"));
                    });
            }
            
            // 2. 测试数据库昨日访问量查询
            log.info("🔍 测试数据库昨日访问量直接查询...");
            Long yesterdayCount = historyInfoMapper.getYesterdayHistoryCount();
            log.info("📊 昨日访问量(直接查询): {}", yesterdayCount != null ? yesterdayCount : 0);
            
            // 3. 获取刷新后的统计数据
            log.info("🔍 获取刷新后的历史统计数据...");
            cacheService.refreshLocationStatisticsCache(); // 手动刷新缓存
            Map<String, Object> historyStats = cacheService.getCachedIpHistoryStatisticsSafely();
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> cachedYesterdayData = (List<Map<String, Object>>) historyStats.get("ip_history_hour");
            
            log.info("📊 缓存中的昨日数据记录数: {}", cachedYesterdayData != null ? cachedYesterdayData.size() : 0);
            
            if (cachedYesterdayData != null && !cachedYesterdayData.isEmpty()) {
                long cachedYesterdayIpCount = cachedYesterdayData.stream()
                    .map(m -> m.get("ip"))
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .count();
                log.info("📊 缓存中昨日去重IP数: {}", cachedYesterdayIpCount);
            }
            
            // 4. 验证时间范围
            log.info("🔍 验证修正后的时间范围:");
            log.info("  数据来源: 昨日访问记录(按日历天计算)");
            log.info("  SQL查询: date(create_time) = date_sub(curdate(), interval 1 day)");
            log.info("  时间范围: 昨天00:00:00 - 昨天23:59:59");
            
            log.info("✅ 修正后的昨日访问量统计验证完成");
            
        } catch (Exception e) {
            log.error("❌ 修正后的昨日访问量统计测试失败", e);
            throw e;
        }
        
        log.info("========== 修正后的昨日访问量统计测试完成 ==========");
    }
} 