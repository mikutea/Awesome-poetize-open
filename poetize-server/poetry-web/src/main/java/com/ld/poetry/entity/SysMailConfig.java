package com.ld.poetry.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 邮件配置实体类
 */
@Data
@TableName("sys_mail_config")
public class SysMailConfig implements Serializable {
    
    private static final long serialVersionUID = 1L;
    
    /**
     * 主键ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;
    
    /**
     * 配置名称
     */
    private String configName;
    
    /**
     * 邮箱服务器地址
     */
    private String host;
    
    /**
     * 邮箱服务器端口
     */
    private Integer port;
    
    /**
     * 邮箱账号
     */
    private String username;
    
    /**
     * 邮箱密码或授权码
     */
    private String password;
    
    /**
     * 发件人名称
     */
    private String senderName;
    
    /**
     * 是否启用SSL
     */
    private Boolean useSsl;
    
    /**
     * 是否启用STARTTLS
     */
    private Boolean useStarttls;
    
    /**
     * 是否需要认证
     */
    private Boolean auth;
    
    /**
     * 是否启用
     */
    private Boolean enabled;
    
    /**
     * 是否为默认配置
     */
    private Boolean isDefault;
    
    /**
     * 连接超时(毫秒)
     */
    private Integer connectionTimeout;
    
    /**
     * 读取超时(毫秒)
     */
    private Integer timeout;
    
    /**
     * 是否信任所有证书
     */
    private Boolean trustAllCerts;
    
    /**
     * 协议(smtp, smtps等)
     */
    private String protocol;
    
    /**
     * 认证机制
     */
    private String authMechanism;
    
    /**
     * 是否启用调试模式
     */
    private Boolean debug;
    
    /**
     * 是否使用代理
     */
    private Boolean useProxy;
    
    /**
     * 代理服务器地址
     */
    private String proxyHost;
    
    /**
     * 代理服务器端口
     */
    private Integer proxyPort;
    
    /**
     * 代理服务器用户名
     */
    private String proxyUser;
    
    /**
     * 代理服务器密码
     */
    private String proxyPassword;
    
    /**
     * 自定义属性(JSON格式)
     */
    private String customProperties;
    
    /**
     * 排序顺序
     */
    private Integer sortOrder;
    
    /**
     * 备注说明
     */
    private String remark;
    
    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}

