"""
缓存键常量类 - Python版本
与Java端保持一致的缓存键命名规范

主要功能：
- 统一的缓存键前缀和命名规范
- 缓存过期时间常量
- 缓存键构建工具方法

版本: 1.0.0
"""

class CacheConstants:
    """缓存键常量类"""
    
    # ================================ 缓存键前缀 ================================
    
    CACHE_PREFIX = "poetize:"
    
    # ================================ 用户相关缓存 ================================
    
    USER_CACHE_PREFIX = CACHE_PREFIX + "user:"
    USER_SESSION_PREFIX = CACHE_PREFIX + "session:"
    LOGIN_FAIL_PREFIX = CACHE_PREFIX + "login:fail:"
    
    # ================================ 文章相关缓存 ================================
    
    ARTICLE_CACHE_PREFIX = CACHE_PREFIX + "article:"
    ARTICLE_LIST_PREFIX = CACHE_PREFIX + "article:list:"
    HOT_ARTICLES_KEY = CACHE_PREFIX + "article:hot"
    ARTICLE_VIEW_PREFIX = CACHE_PREFIX + "article:view:"
    
    # ================================ 评论相关缓存 ================================
    
    COMMENT_LIST_PREFIX = CACHE_PREFIX + "comment:list:"
    COMMENT_COUNT_PREFIX = CACHE_PREFIX + "comment:count:"
    
    # ================================ 分类标签缓存 ================================
    
    SORT_LIST_KEY = CACHE_PREFIX + "sort:list"
    LABEL_LIST_PREFIX = CACHE_PREFIX + "label:list:"
    
    # ================================ 系统配置缓存 ================================
    
    WEB_INFO_KEY = CACHE_PREFIX + "webinfo"
    SYS_CONFIG_PREFIX = CACHE_PREFIX + "config:"
    
    # ================================ 安全相关缓存 ================================
    
    IP_ATTACK_PREFIX = CACHE_PREFIX + "security:attack:"
    IP_BLACKLIST_PREFIX = CACHE_PREFIX + "security:blacklist:"
    CAPTCHA_PREFIX = CACHE_PREFIX + "captcha:"
    
    # ================================ 统计相关缓存 ================================
    
    VISIT_STATS_PREFIX = CACHE_PREFIX + "stats:visit:"
    ONLINE_USERS_KEY = CACHE_PREFIX + "stats:online"
    
    # ================================ 第三方服务缓存 ================================
    
    OAUTH_STATE_PREFIX = CACHE_PREFIX + "oauth:state:"
    TRANSLATE_PREFIX = CACHE_PREFIX + "translate:"
    
    # ================================ Python特有缓存 ================================

    # 邮件配置缓存
    EMAIL_CONFIG_KEY = CACHE_PREFIX + "email:config"
    EMAIL_TEST_PREFIX = CACHE_PREFIX + "email:test:"

    # 验证码配置缓存
    CAPTCHA_CONFIG_KEY = CACHE_PREFIX + "captcha:config"
    CAPTCHA_PUBLIC_CONFIG_KEY = CACHE_PREFIX + "captcha:public:config"

    # SEO配置缓存
    SEO_CONFIG_KEY = CACHE_PREFIX + "seo:config"
    SEO_SITEMAP_KEY = CACHE_PREFIX + "seo:sitemap"
    SEO_ROBOTS_KEY = CACHE_PREFIX + "seo:robots"
    SEO_AI_ANALYSIS_PREFIX = CACHE_PREFIX + "seo:ai:analysis:"
    SEO_PUSH_STATUS_PREFIX = CACHE_PREFIX + "seo:push:status:"

    # AI翻译缓存
    AI_TRANSLATE_PREFIX = CACHE_PREFIX + "ai:translate:"
    AI_CONFIG_KEY = CACHE_PREFIX + "ai:config"

    # AI聊天缓存
    AI_CHAT_CONFIG_KEY = CACHE_PREFIX + "ai:chat:config"
    AI_CHAT_RESPONSE_PREFIX = CACHE_PREFIX + "ai:chat:response:"
    AI_CHAT_CONVERSATION_PREFIX = CACHE_PREFIX + "ai:chat:conversation:"

    # Web管理缓存
    WEB_INFO_KEY = CACHE_PREFIX + "web:info"
    WEB_INFO_ADMIN_KEY = CACHE_PREFIX + "web:info:admin"
    WEB_INFO_DETAILS_PREFIX = CACHE_PREFIX + "web:info:details:"

    # 访问统计缓存
    VISIT_STATS_DAILY_PREFIX = CACHE_PREFIX + "stats:daily:"
    VISIT_STATS_SUMMARY_PREFIX = CACHE_PREFIX + "stats:summary:"
    
    # 访问统计缓存
    VISIT_COUNT_PREFIX = CACHE_PREFIX + "visit:count:"
    VISIT_IP_PREFIX = CACHE_PREFIX + "visit:ip:"
    
    # ================================ 缓存过期时间常量（秒） ================================
    
    # 基础过期时间
    DEFAULT_EXPIRE_TIME = 600        # 10分钟
    SHORT_EXPIRE_TIME = 300          # 5分钟
    LONG_EXPIRE_TIME = 3600          # 1小时
    VERY_LONG_EXPIRE_TIME = 86400    # 24小时
    
    # 特定功能过期时间
    SESSION_EXPIRE_TIME = 604800     # 7天
    CAPTCHA_EXPIRE_TIME = 300        # 5分钟
    IP_ATTACK_EXPIRE_TIME = 3600     # 1小时
    IP_BLACKLIST_EXPIRE_TIME = 86400 # 24小时
    
    # OAuth相关过期时间
    OAUTH_STATE_EXPIRE_TIME = 600    # 10分钟
    OAUTH_TOKEN_EXPIRE_TIME = 3600   # 1小时
    
    # 翻译缓存过期时间
    TRANSLATE_EXPIRE_TIME = 86400    # 24小时
    
    # 配置缓存过期时间
    CONFIG_EXPIRE_TIME = 3600        # 1小时
    
    # 统计数据过期时间
    STATS_EXPIRE_TIME = 1800         # 30分钟

    # Python API特定过期时间
    # ================================ 持久化缓存（配置类数据） ================================
    # 这些配置变更频率极低，使用持久化缓存，只在配置更新时主动清理
    EMAIL_CONFIG_EXPIRE_TIME = None          # 邮件配置 - 持久化缓存
    CAPTCHA_CONFIG_EXPIRE_TIME = None        # 验证码配置 - 持久化缓存
    SEO_CONFIG_EXPIRE_TIME = None            # SEO配置 - 持久化缓存
    AI_CHAT_CONFIG_EXPIRE_TIME = None        # AI聊天配置 - 持久化缓存
    WEB_INFO_EXPIRE_TIME = None              # 网站信息 - 持久化缓存
    WEB_INFO_ADMIN_EXPIRE_TIME = None        # 管理员网站信息 - 持久化缓存

    # ================================ 临时缓存（动态数据） ================================
    # 这些数据需要定期刷新或可能频繁变化，保留TTL
    SEO_SITEMAP_EXPIRE_TIME = 1800           # sitemap 30分钟（内容可能变化）
    SEO_ROBOTS_EXPIRE_TIME = 1800            # robots.txt 30分钟（内容可能变化）
    SEO_AI_ANALYSIS_EXPIRE_TIME = 7200       # SEO AI分析2小时
    SEO_PUSH_STATUS_EXPIRE_TIME = 300        # SEO推送状态5分钟
    AI_CHAT_RESPONSE_EXPIRE_TIME = 3600      # AI聊天响应1小时
    VISIT_STATS_EXPIRE_TIME = 300            # 访问统计5分钟
    
    # ================================ 工具方法 ================================
    
    @staticmethod
    def build_user_key(user_id: int) -> str:
        """构建用户缓存键"""
        return f"{CacheConstants.USER_CACHE_PREFIX}{user_id}"
    
    @staticmethod
    def build_session_key(token: str) -> str:
        """构建用户会话缓存键"""
        return f"{CacheConstants.USER_SESSION_PREFIX}{token}"
    
    @staticmethod
    def build_article_key(article_id: int) -> str:
        """构建文章缓存键"""
        return f"{CacheConstants.ARTICLE_CACHE_PREFIX}{article_id}"
    
    @staticmethod
    def build_article_list_key(sort_id: int, page: int, size: int) -> str:
        """构建文章列表缓存键"""
        return f"{CacheConstants.ARTICLE_LIST_PREFIX}{sort_id}:{page}:{size}"
    
    @staticmethod
    def build_comment_list_key(source: int, comment_type: str) -> str:
        """构建评论列表缓存键"""
        return f"{CacheConstants.COMMENT_LIST_PREFIX}{source}:{comment_type}"
    
    @staticmethod
    def build_ip_attack_key(ip: str) -> str:
        """构建IP攻击缓存键"""
        return f"{CacheConstants.IP_ATTACK_PREFIX}{ip}"
    
    @staticmethod
    def build_ip_blacklist_key(ip: str) -> str:
        """构建IP黑名单缓存键"""
        return f"{CacheConstants.IP_BLACKLIST_PREFIX}{ip}"
    
    @staticmethod
    def build_sys_config_key(config_key: str) -> str:
        """构建系统配置缓存键"""
        return f"{CacheConstants.SYS_CONFIG_PREFIX}{config_key}"
    
    @staticmethod
    def build_oauth_state_key(state: str) -> str:
        """构建OAuth状态缓存键"""
        return f"{CacheConstants.OAUTH_STATE_PREFIX}{state}"
    
    @staticmethod
    def build_translate_key(text_hash: str) -> str:
        """构建翻译缓存键"""
        return f"{CacheConstants.TRANSLATE_PREFIX}{text_hash}"
    
    @staticmethod
    def build_captcha_key(session_id: str) -> str:
        """构建验证码缓存键"""
        return f"{CacheConstants.CAPTCHA_PREFIX}{session_id}"
    
    @staticmethod
    def build_email_test_key(config_hash: str) -> str:
        """构建邮件测试缓存键"""
        return f"{CacheConstants.EMAIL_TEST_PREFIX}{config_hash}"
    
    @staticmethod
    def build_visit_count_key(date: str) -> str:
        """构建访问统计缓存键"""
        return f"{CacheConstants.VISIT_COUNT_PREFIX}{date}"
    
    @staticmethod
    def build_visit_ip_key(ip: str, date: str) -> str:
        """构建访问IP缓存键"""
        return f"{CacheConstants.VISIT_IP_PREFIX}{ip}:{date}"
    
    @staticmethod
    def build_ai_translate_key(text_hash: str, source_lang: str, target_lang: str) -> str:
        """构建AI翻译缓存键"""
        return f"{CacheConstants.AI_TRANSLATE_PREFIX}{text_hash}:{source_lang}:{target_lang}"

    # ================================ Python API缓存键构建方法 ================================

    @staticmethod
    def build_web_info_details_key(auth_token_hash: str) -> str:
        """构建网站详细信息缓存键"""
        return f"{CacheConstants.WEB_INFO_DETAILS_PREFIX}{auth_token_hash}"

    @staticmethod
    def build_seo_ai_analysis_key(site_info_hash: str) -> str:
        """构建SEO AI分析缓存键"""
        return f"{CacheConstants.SEO_AI_ANALYSIS_PREFIX}{site_info_hash}"

    @staticmethod
    def build_seo_push_status_key(url_hash: str, engine: str) -> str:
        """构建SEO推送状态缓存键"""
        return f"{CacheConstants.SEO_PUSH_STATUS_PREFIX}{engine}:{url_hash}"

    @staticmethod
    def build_ai_chat_response_key(message_hash: str, config_hash: str) -> str:
        """构建AI聊天响应缓存键"""
        return f"{CacheConstants.AI_CHAT_RESPONSE_PREFIX}{config_hash}:{message_hash}"

    @staticmethod
    def build_ai_chat_conversation_key(conversation_id: str, user_id: str) -> str:
        """构建AI聊天会话缓存键"""
        return f"{CacheConstants.AI_CHAT_CONVERSATION_PREFIX}{user_id}:{conversation_id}"

    @staticmethod
    def build_visit_stats_daily_key(days: int) -> str:
        """构建每日访问统计缓存键"""
        return f"{CacheConstants.VISIT_STATS_DAILY_PREFIX}{days}"

    @staticmethod
    def build_visit_stats_summary_key(period: str) -> str:
        """构建访问统计摘要缓存键"""
        return f"{CacheConstants.VISIT_STATS_SUMMARY_PREFIX}{period}"

# 缓存键模式常量
class CachePatterns:
    """缓存键模式常量"""
    
    # 用户相关模式
    USER_PATTERN = CacheConstants.USER_CACHE_PREFIX + "*"
    SESSION_PATTERN = CacheConstants.USER_SESSION_PREFIX + "*"
    
    # 文章相关模式
    ARTICLE_PATTERN = CacheConstants.ARTICLE_CACHE_PREFIX + "*"
    ARTICLE_LIST_PATTERN = CacheConstants.ARTICLE_LIST_PREFIX + "*"
    
    # 安全相关模式
    IP_ATTACK_PATTERN = CacheConstants.IP_ATTACK_PREFIX + "*"
    IP_BLACKLIST_PATTERN = CacheConstants.IP_BLACKLIST_PREFIX + "*"
    
    # OAuth相关模式
    OAUTH_STATE_PATTERN = CacheConstants.OAUTH_STATE_PREFIX + "*"
    
    # 翻译相关模式
    TRANSLATE_PATTERN = CacheConstants.TRANSLATE_PREFIX + "*"
    AI_TRANSLATE_PATTERN = CacheConstants.AI_TRANSLATE_PREFIX + "*"

# 缓存分组常量
class CacheGroups:
    """缓存分组常量"""
    
    USER_GROUP = "user"
    ARTICLE_GROUP = "article"
    COMMENT_GROUP = "comment"
    SECURITY_GROUP = "security"
    CONFIG_GROUP = "config"
    STATS_GROUP = "stats"
    OAUTH_GROUP = "oauth"
    TRANSLATE_GROUP = "translate"
    EMAIL_GROUP = "email"
    CAPTCHA_GROUP = "captcha"
    SEO_GROUP = "seo"
