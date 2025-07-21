package com.ld.poetry.handle;

import com.ld.poetry.dao.HistoryInfoMapper;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.service.CacheService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArraySet;

@SuppressWarnings("unchecked")
@Component
@EnableScheduling
@Slf4j
public class ScheduleTask {

    @Autowired
    private HistoryInfoMapper historyInfoMapper;

    @Autowired
    private CacheService cacheService;

    /**
     * 每天凌晨执行的完整清理和统计任务
     */
    @Scheduled(cron = "0 0 0 * * ?")
    public void cleanIpHistory() {
        try {
            log.info("开始执行IP历史记录清理和统计任务");
            
            // 清理今日IP历史缓存
            CopyOnWriteArraySet<String> ipHistory = (CopyOnWriteArraySet<String>) cacheService.getCachedIpHistory();
            if (ipHistory == null) {
                ipHistory = new CopyOnWriteArraySet<>();
                cacheService.cacheIpHistory(ipHistory);
            }
            int clearedCount = ipHistory.size();
            ipHistory.clear();
            log.info("清理了 {} 条今日IP缓存记录", clearedCount);

            // 重新生成统计数据
            cacheService.evictIpHistoryStatistics();
            Map<String, Object> history = new HashMap<>();
            
            try {
                history.put(CommonConst.IP_HISTORY_PROVINCE, historyInfoMapper.getHistoryByProvince());
                log.info("成功更新省份访问统计");
            } catch (Exception e) {
                log.error("省份访问统计更新失败", e);
                history.put(CommonConst.IP_HISTORY_PROVINCE, new ArrayList<>());
            }
            
            try {
                history.put(CommonConst.IP_HISTORY_IP, historyInfoMapper.getHistoryByIp());
                log.info("成功更新IP访问统计");
            } catch (Exception e) {
                log.error("IP访问统计更新失败", e);
                history.put(CommonConst.IP_HISTORY_IP, new ArrayList<>());
            }
            
            try {
                history.put(CommonConst.IP_HISTORY_HOUR, historyInfoMapper.getHistoryBy24Hour());
                log.info("成功更新24小时访问统计");
            } catch (Exception e) {
                log.error("24小时访问统计更新失败", e);
                history.put(CommonConst.IP_HISTORY_HOUR, new ArrayList<>());
            }
            
            try {
                history.put(CommonConst.IP_HISTORY_COUNT, historyInfoMapper.getHistoryCount());
                log.info("成功更新总访问量统计");
            } catch (Exception e) {
                log.error("总访问量统计更新失败", e);
                history.put(CommonConst.IP_HISTORY_COUNT, 0L);
            }
            
            cacheService.cacheIpHistoryStatistics(history);
            log.info("IP历史记录清理和统计任务执行完成");
            
        } catch (Exception e) {
            log.error("IP历史记录清理和统计任务执行失败", e);
            // 确保缓存不为空，避免前端显示异常
            ensureStatisticsCache();
        }
    }
    

    
    /**
     * 确保统计缓存存在，避免前端报错
     */
    private void ensureStatisticsCache() {
        Map<String, Object> cachedStats = (Map<String, Object>) cacheService.getCachedIpHistoryStatistics();
        if (cachedStats == null) {
            log.warn("统计缓存为空，初始化默认数据");
            Map<String, Object> defaultStats = new HashMap<>();
            defaultStats.put(CommonConst.IP_HISTORY_PROVINCE, new ArrayList<>());
            defaultStats.put(CommonConst.IP_HISTORY_IP, new ArrayList<>());
            defaultStats.put(CommonConst.IP_HISTORY_HOUR, new ArrayList<>());
            defaultStats.put(CommonConst.IP_HISTORY_COUNT, 0L);
            cacheService.cacheIpHistoryStatistics(defaultStats);
        }
    }
}
