package com.ld.poetry.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.ld.poetry.entity.SysMailConfig;
import com.ld.poetry.entity.dto.MailConfigDTO;

import java.util.List;

/**
 * 邮件配置服务接口
 */
public interface SysMailConfigService extends IService<SysMailConfig> {
    
    /**
     * 获取所有邮件配置
     * @return 配置列表
     */
    List<MailConfigDTO> getAllConfigs();
    
    /**
     * 获取默认邮件配置
     * @return 默认配置，如果没有则返回null
     */
    MailConfigDTO getDefaultConfig();
    
    /**
     * 获取默认邮件配置的索引
     * @return 默认配置索引，如果没有则返回-1
     */
    Integer getDefaultConfigIndex();
    
    /**
     * 保存邮件配置列表
     * @param configs 配置列表
     * @param defaultIndex 默认配置索引
     * @return 保存是否成功
     */
    boolean saveConfigs(List<MailConfigDTO> configs, Integer defaultIndex);
    
    /**
     * 转换Entity到DTO
     * @param entity 实体对象
     * @return DTO对象
     */
    MailConfigDTO convertToDTO(SysMailConfig entity);
    
    /**
     * 转换DTO到Entity
     * @param dto DTO对象
     * @return 实体对象
     */
    SysMailConfig convertToEntity(MailConfigDTO dto);
}

