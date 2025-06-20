package com.ld.poetry.dao;

import com.ld.poetry.entity.HistoryInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 历史信息 Mapper 接口
 * </p>
 *
 * @author sara
 * @since 2023-07-24
 */
@Mapper
public interface HistoryInfoMapper extends BaseMapper<HistoryInfo> {

    /**
     * 访问IP最多的10个省
     */
    @Select("select nation, province, count(distinct ip) as num" +
            " from history_info" +
            " where nation is not null and province is not null" +
            " group by nation, province" +
            " order by num desc" +
            " limit 10")
    List<Map<String, Object>> getHistoryByProvince();

    /**
     * 访问次数最多的10个IP
     */
    @Select("select ip, count(*) as num" +
            " from history_info" +
            " group by ip" +
            " order by num desc" +
            " limit 10")
    List<Map<String, Object>> getHistoryByIp();

    /**
     * 访问24小时内的数据
     */
    @Select("select ip, user_id, nation, province" +
            " from history_info" +
            " where create_time >= (now() - interval 24 hour)")
    List<Map<String, Object>> getHistoryBy24Hour();

    /**
     * 总访问量
     */
    @Select("select count(*) from history_info")
    Long getHistoryCount();

    /**
     * 指定天数内的每日访问统计
     * @param days 需要统计的天数，最大 365
     * @return 每日唯一 IP 和总访问量
     */
    @Select("SELECT DATE_FORMAT(create_time, '%Y-%m-%d') AS visit_date, " +
            "COUNT(DISTINCT ip) AS unique_visits, " +
            "COUNT(*) AS total_visits " +
            "FROM history_info " +
            "WHERE create_time >= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) " +
            "GROUP BY DATE_FORMAT(create_time, '%Y-%m-%d') " +
            "ORDER BY visit_date")
    List<Map<String, Object>> getDailyVisitStats(@Param("days") Integer days);
}
