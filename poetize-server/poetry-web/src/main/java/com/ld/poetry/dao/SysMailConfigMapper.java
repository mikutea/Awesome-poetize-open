package com.ld.poetry.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ld.poetry.entity.SysMailConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 邮件配置Mapper
 */
@Mapper
public interface SysMailConfigMapper extends BaseMapper<SysMailConfig> {
}

