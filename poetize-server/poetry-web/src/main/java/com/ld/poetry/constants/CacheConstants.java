package com.ld.poetry.constants;

/**
 * 缓存键常量类
 * 统一管理Redis缓存键的命名规范
 * 
 * @author LeapYa
 * @since 2025-07-20
 */
public class CacheConstants {

    /**
     * 缓存键前缀
     */
    public static final String CACHE_PREFIX = "poetize:";

    // ================================ 用户相关缓存 ================================
    
    /**
     * 用户信息缓存键前缀
     * 格式: poetize:user:{userId}
     */
    public static final String USER_CACHE_PREFIX = CACHE_PREFIX + "user:";
    
    /**
     * 用户会话缓存键前缀
     * 格式: poetize:session:{token}
     */
    public static final String USER_SESSION_PREFIX = CACHE_PREFIX + "session:";
    
    /**
     * 用户登录失败次数缓存键前缀
     * 格式: poetize:login:fail:{username}
     */
    public static final String LOGIN_FAIL_PREFIX = CACHE_PREFIX + "login:fail:";

    // ================================ 文章相关缓存 ================================
    
    /**
     * 文章信息缓存键前缀
     * 格式: poetize:article:{articleId}
     */
    public static final String ARTICLE_CACHE_PREFIX = CACHE_PREFIX + "article:";
    
    /**
     * 文章列表缓存键前缀
     * 格式: poetize:article:list:{sortId}:{page}:{size}
     */
    public static final String ARTICLE_LIST_PREFIX = CACHE_PREFIX + "article:list:";
    
    /**
     * 热门文章缓存键
     */
    public static final String HOT_ARTICLES_KEY = CACHE_PREFIX + "article:hot";
    
    /**
     * 文章浏览量缓存键前缀
     * 格式: poetize:article:view:{articleId}
     */
    public static final String ARTICLE_VIEW_PREFIX = CACHE_PREFIX + "article:view:";

    // ================================ 评论相关缓存 ================================
    
    /**
     * 评论列表缓存键前缀
     * 格式: poetize:comment:list:{source}:{type}
     */
    public static final String COMMENT_LIST_PREFIX = CACHE_PREFIX + "comment:list:";
    
    /**
     * 评论数量缓存键前缀
     * 格式: poetize:comment:count:{source}:{type}
     */
    public static final String COMMENT_COUNT_PREFIX = CACHE_PREFIX + "comment:count:";

    // ================================ 分类标签缓存 ================================
    
    /**
     * 分类信息缓存键
     */
    public static final String SORT_LIST_KEY = CACHE_PREFIX + "sort:list";
    
    /**
     * 标签信息缓存键前缀
     * 格式: poetize:label:list:{sortId}
     */
    public static final String LABEL_LIST_PREFIX = CACHE_PREFIX + "label:list:";

    // ================================ 系统配置缓存 ================================
    
    /**
     * 网站信息缓存键
     */
    public static final String WEB_INFO_KEY = CACHE_PREFIX + "webinfo";
    
    /**
     * 系统配置缓存键前缀
     * 格式: poetize:config:{configKey}
     */
    public static final String SYS_CONFIG_PREFIX = CACHE_PREFIX + "config:";

    // ================================ 安全相关缓存 ================================
    
    /**
     * IP攻击次数缓存键前缀
     * 格式: poetize:security:attack:{ip}
     */
    public static final String IP_ATTACK_PREFIX = CACHE_PREFIX + "security:attack:";
    
    /**
     * IP黑名单缓存键前缀
     * 格式: poetize:security:blacklist:{ip}
     */
    public static final String IP_BLACKLIST_PREFIX = CACHE_PREFIX + "security:blacklist:";
    
    /**
     * 验证码缓存键前缀
     * 格式: poetize:captcha:{sessionId}
     */
    public static final String CAPTCHA_PREFIX = CACHE_PREFIX + "captcha:";

    // ================================ 统计相关缓存 ================================
    
    /**
     * 访问统计缓存键前缀
     * 格式: poetize:stats:visit:{date}
     */
    public static final String VISIT_STATS_PREFIX = CACHE_PREFIX + "stats:visit:";
    
    /**
     * 在线用户数缓存键
     */
    public static final String ONLINE_USERS_KEY = CACHE_PREFIX + "stats:online";

    // ================================ 第三方服务缓存 ================================
    
    /**
     * 第三方登录状态缓存键前缀
     * 格式: poetize:oauth:state:{state}
     */
    public static final String OAUTH_STATE_PREFIX = CACHE_PREFIX + "oauth:state:";
    
    /**
     * 翻译缓存键前缀
     * 格式: poetize:translate:{hash}
     */
    public static final String TRANSLATE_PREFIX = CACHE_PREFIX + "translate:";

    // ================================ 缓存过期时间常量 ================================
    
    /**
     * 默认缓存过期时间（秒）- 10分钟
     */
    public static final long DEFAULT_EXPIRE_TIME = 600;
    
    /**
     * 短期缓存过期时间（秒）- 5分钟
     */
    public static final long SHORT_EXPIRE_TIME = 300;
    
    /**
     * 长期缓存过期时间（秒）- 1小时
     */
    public static final long LONG_EXPIRE_TIME = 3600;
    
    /**
     * 超长期缓存过期时间（秒）- 24小时
     */
    public static final long VERY_LONG_EXPIRE_TIME = 86400;
    
    /**
     * 用户会话过期时间（秒）- 7天
     */
    public static final long SESSION_EXPIRE_TIME = 604800;
    
    /**
     * 验证码过期时间（秒）- 5分钟
     */
    public static final long CAPTCHA_EXPIRE_TIME = 300;
    
    /**
     * IP攻击记录过期时间（秒）- 1小时
     */
    public static final long IP_ATTACK_EXPIRE_TIME = 3600;
    
    /**
     * IP黑名单过期时间（秒）- 24小时
     */
    public static final long IP_BLACKLIST_EXPIRE_TIME = 86400;

    // ================================ 工具方法 ================================
    
    /**
     * 构建用户缓存键
     * @param userId 用户ID
     * @return 缓存键
     */
    public static String buildUserKey(Integer userId) {
        return USER_CACHE_PREFIX + userId;
    }
    
    /**
     * 构建用户会话缓存键
     * @param token 会话令牌
     * @return 缓存键
     */
    public static String buildSessionKey(String token) {
        return USER_SESSION_PREFIX + token;
    }
    
    /**
     * 构建文章缓存键
     * @param articleId 文章ID
     * @return 缓存键
     */
    public static String buildArticleKey(Integer articleId) {
        return ARTICLE_CACHE_PREFIX + articleId;
    }
    
    /**
     * 构建文章列表缓存键
     * @param sortId 分类ID
     * @param page 页码
     * @param size 页大小
     * @return 缓存键
     */
    public static String buildArticleListKey(Integer sortId, Integer page, Integer size) {
        return ARTICLE_LIST_PREFIX + sortId + ":" + page + ":" + size;
    }
    
    /**
     * 构建评论列表缓存键
     * @param source 来源ID
     * @param type 类型
     * @return 缓存键
     */
    public static String buildCommentListKey(Integer source, String type) {
        return COMMENT_LIST_PREFIX + source + ":" + type;
    }
    
    /**
     * 构建IP攻击缓存键
     * @param ip IP地址
     * @return 缓存键
     */
    public static String buildIpAttackKey(String ip) {
        return IP_ATTACK_PREFIX + ip;
    }
    
    /**
     * 构建IP黑名单缓存键
     * @param ip IP地址
     * @return 缓存键
     */
    public static String buildIpBlacklistKey(String ip) {
        return IP_BLACKLIST_PREFIX + ip;
    }
    
    /**
     * 构建系统配置缓存键
     * @param configKey 配置键
     * @return 缓存键
     */
    public static String buildSysConfigKey(String configKey) {
        return SYS_CONFIG_PREFIX + configKey;
    }
}
