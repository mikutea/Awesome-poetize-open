package com.ld.poetry.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ld.poetry.entity.SysCaptchaConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 验证码配置Mapper
 */
@Mapper
public interface SysCaptchaConfigMapper extends BaseMapper<SysCaptchaConfig> {
}

