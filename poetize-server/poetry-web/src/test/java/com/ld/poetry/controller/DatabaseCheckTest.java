package com.ld.poetry.controller;

import com.ld.poetry.dao.HistoryInfoMapper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

/**
 * 数据库检查测试类
 * 用于检查history_info表的实际数据
 */
@SpringBootTest
@Slf4j
public class DatabaseCheckTest {

    @Autowired
    private HistoryInfoMapper historyInfoMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 检查数据库中的历史访问记录
     */
    @Test
    public void checkHistoryInfoTable() {
        try {
            log.info("========== 开始检查数据库中的历史访问记录 ==========");
            
            // 1. 检查表的总记录数
            String countSql = "SELECT COUNT(*) as total_count FROM history_info";
            Integer totalCount = jdbcTemplate.queryForObject(countSql, Integer.class);
            log.info("📊 history_info表总记录数: {}", totalCount);
            
            // 2. 检查唯一IP数量
            String uniqueIpSql = "SELECT COUNT(DISTINCT ip) as unique_ip_count FROM history_info";
            Integer uniqueIpCount = jdbcTemplate.queryForObject(uniqueIpSql, Integer.class);
            log.info("📊 唯一IP数量: {}", uniqueIpCount);
            
            // 3. 通过Mapper方法检查
            Long mapperCount = historyInfoMapper.getHistoryCount();
            log.info("📊 Mapper方法返回的唯一访客数: {}", mapperCount);
            
            // 4. 查看最近几条记录
            String recentSql = "SELECT ip, nation, province, city, create_time FROM history_info ORDER BY create_time DESC LIMIT 5";
            List<Map<String, Object>> recentRecords = jdbcTemplate.queryForList(recentSql);
            log.info("📊 最近5条访问记录:");
            for (int i = 0; i < recentRecords.size(); i++) {
                Map<String, Object> record = recentRecords.get(i);
                log.info("  {}. IP: {}, 地址: {}-{}-{}, 时间: {}", 
                    i + 1, 
                    record.get("ip"), 
                    record.get("nation"), 
                    record.get("province"), 
                    record.get("city"),
                    record.get("create_time"));
            }
            
            // 5. 按IP分组统计
            String ipGroupSql = "SELECT ip, COUNT(*) as visit_count FROM history_info GROUP BY ip ORDER BY visit_count DESC LIMIT 5";
            List<Map<String, Object>> ipStats = jdbcTemplate.queryForList(ipGroupSql);
            log.info("📊 访问次数最多的5个IP:");
            for (int i = 0; i < ipStats.size(); i++) {
                Map<String, Object> stat = ipStats.get(i);
                log.info("  {}. IP: {}, 访问次数: {}", 
                    i + 1, 
                    stat.get("ip"), 
                    stat.get("visit_count"));
            }
            
            // 6. 检查表结构
            String structureSql = "DESCRIBE history_info";
            List<Map<String, Object>> tableStructure = jdbcTemplate.queryForList(structureSql);
            log.info("📊 history_info表结构:");
            for (Map<String, Object> column : tableStructure) {
                log.info("  列: {}, 类型: {}, 是否为空: {}", 
                    column.get("Field"), 
                    column.get("Type"), 
                    column.get("Null"));
            }
            
            log.info("========== 数据库检查完成 ==========");
            
        } catch (Exception e) {
            log.error("❌ 检查数据库失败", e);
        }
    }

    /**
     * 模拟添加一些测试访问记录
     */
    @Test
    public void addTestHistoryRecords() {
        try {
            log.info("========== 开始添加测试访问记录 ==========");
            
            // 添加几条测试记录
            String insertSql = "INSERT INTO history_info (ip, nation, province, city, create_time) VALUES (?, ?, ?, ?, NOW())";
            
            jdbcTemplate.update(insertSql, "192.168.1.100", "中国", "北京", "北京市");
            jdbcTemplate.update(insertSql, "192.168.1.101", "中国", "上海", "上海市");
            jdbcTemplate.update(insertSql, "192.168.1.102", "中国", "广东", "深圳市");
            jdbcTemplate.update(insertSql, "192.168.1.100", "中国", "北京", "北京市"); // 同一IP多次访问
            jdbcTemplate.update(insertSql, "192.168.1.103", "中国", "浙江", "杭州市");
            
            log.info("✅ 已添加5条测试访问记录");
            
            // 重新检查统计
            Long totalCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM history_info", Long.class);
            Long uniqueIpCount = jdbcTemplate.queryForObject("SELECT COUNT(DISTINCT ip) FROM history_info", Long.class);
            
            log.info("📊 添加后的总记录数: {}", totalCount);
            log.info("📊 添加后的唯一IP数: {}", uniqueIpCount);
            
            log.info("========== 测试记录添加完成 ==========");
            
        } catch (Exception e) {
            log.error("❌ 添加测试记录失败", e);
        }
    }
} 