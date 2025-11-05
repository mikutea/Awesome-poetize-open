package com.ld.poetry.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ld.poetry.entity.SysAiConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI配置Mapper
 * 
 * @author LeapYa
 * @since 2025-10-18
 */
@Mapper
public interface SysAiConfigMapper extends BaseMapper<SysAiConfig> {

    /**
     * 根据配置类型和名称查询配置
     *
     * @param configType 配置类型 (ai_chat/ai_api/translation)
     * @param configName 配置名称
     * @return AI配置对象
     */
    SysAiConfig selectByTypeAndName(@Param("configType") String configType, 
                                     @Param("configName") String configName);

    /**
     * 根据配置类型查询所有配置
     *
     * @param configType 配置类型
     * @return 配置列表
     */
    List<SysAiConfig> selectByType(@Param("configType") String configType);

    /**
     * 查询所有已启用的配置
     *
     * @return 配置列表
     */
    List<SysAiConfig> selectAllEnabled();

    /**
     * 更新启用状态
     *
     * @param id      配置ID
     * @param enabled 是否启用
     * @return 影响行数
     */
    int updateEnabled(@Param("id") Integer id, @Param("enabled") Boolean enabled);
}

