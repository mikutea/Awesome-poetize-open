package com.ld.poetry.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 验证码配置实体类
 */
@Data
@TableName("sys_captcha_config")
public class SysCaptchaConfig implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    
    private Boolean enable;
    private Boolean login;
    private Boolean register;
    private Boolean comment;
    private Boolean resetPassword;
    private Integer screenSizeThreshold;
    private Boolean forceSlideForMobile;
    private Integer slideAccuracy;
    private BigDecimal slideSuccessThreshold;
    private BigDecimal checkboxTrackSensitivity;
    private Integer checkboxMinTrackPoints;
    private BigDecimal checkboxReplySensitivity;
    private Integer checkboxMaxRetryCount;
    private BigDecimal checkboxRetryDecrement;
    private String remark;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

