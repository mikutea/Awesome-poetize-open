package com.ld.poetry.service;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.ld.poetry.constants.CacheConstants;
import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.dao.WebInfoMapper;
import com.ld.poetry.entity.Article;
import com.ld.poetry.entity.User;
import com.ld.poetry.entity.WebInfo;
import com.ld.poetry.utils.RedisUtil;
import com.ld.poetry.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 缓存服务类
 * 统一管理各种业务数据的缓存操作
 * 
 * @author LeapYa
 * @since 2025-7-20
 */
@Service
@Slf4j
public class CacheService {

    @Autowired
    private RedisUtil redisUtil;

    // ================================ 用户缓存 ================================

    /**
     * 缓存用户信息
     */
    public void cacheUser(User user) {
        if (user != null && user.getId() != null) {
            String key = CacheConstants.buildUserKey(user.getId());
            // 使用与token相同的过期时间，确保用户信息和会话同步
            redisUtil.set(key, user, CommonConst.TOKEN_EXPIRE);
            log.info("缓存用户信息: {}, 过期时间与token一致: {}秒", user.getId(), CommonConst.TOKEN_EXPIRE);
        }
    }

    /**
     * 获取缓存的用户信息
     */
    public User getCachedUser(Integer userId) {
        if (userId == null) return null;
        
        String key = CacheConstants.buildUserKey(userId);
        Object cached = redisUtil.get(key);
        if (cached instanceof User) {
            log.debug("从缓存获取用户信息: {}", userId);
            return (User) cached;
        }
        return null;
    }

    /**
     * 删除用户缓存
     */
    public void evictUser(Integer userId) {
        if (userId != null) {
            String key = CacheConstants.buildUserKey(userId);
            redisUtil.del(key);
            log.debug("删除用户缓存: {}", userId);
        }
    }

    // ================================ 文章缓存 ================================

    /**
     * 缓存文章信息
     */
    public void cacheArticle(Article article) {
        if (article != null && article.getId() != null) {
            String key = CacheConstants.buildArticleKey(article.getId());
            redisUtil.set(key, article, CacheConstants.LONG_EXPIRE_TIME);
            log.debug("缓存文章信息: {}", article.getId());
        }
    }

    /**
     * 获取缓存的文章信息
     */
    public Article getCachedArticle(Integer articleId) {
        if (articleId == null) return null;
        
        String key = CacheConstants.buildArticleKey(articleId);
        Object cached = redisUtil.get(key);
        if (cached instanceof Article) {
            log.debug("从缓存获取文章信息: {}", articleId);
            return (Article) cached;
        }
        return null;
    }

    /**
     * 删除文章缓存
     */
    public void evictArticle(Integer articleId) {
        if (articleId != null) {
            String key = CacheConstants.buildArticleKey(articleId);
            redisUtil.del(key);
            log.debug("删除文章缓存: {}", articleId);
        }
    }

    /**
     * 缓存文章列表
     */
    public void cacheArticleList(Integer sortId, Integer page, Integer size, List<Article> articles) {
        String key = CacheConstants.buildArticleListKey(sortId, page, size);
        redisUtil.set(key, articles, CacheConstants.DEFAULT_EXPIRE_TIME);
        log.debug("缓存文章列表: sortId={}, page={}, size={}", sortId, page, size);
    }

    /**
     * 获取缓存的文章列表
     */
    @SuppressWarnings("unchecked")
    public List<Article> getCachedArticleList(Integer sortId, Integer page, Integer size) {
        String key = CacheConstants.buildArticleListKey(sortId, page, size);
        Object cached = redisUtil.get(key);
        if (cached instanceof List) {
            log.debug("从缓存获取文章列表: sortId={}, page={}, size={}", sortId, page, size);
            return (List<Article>) cached;
        }
        return null;
    }

    /**
     * 增加文章浏览量
     */
    public long incrementArticleView(Integer articleId) {
        if (articleId == null) return 0;
        
        String key = CacheConstants.ARTICLE_VIEW_PREFIX + articleId;
        return redisUtil.incr(key, 1);
    }

    /**
     * 获取文章浏览量
     */
    public long getArticleViewCount(Integer articleId) {
        if (articleId == null) return 0;

        String key = CacheConstants.ARTICLE_VIEW_PREFIX + articleId;
        Object count = redisUtil.get(key);
        if (count instanceof Number) {
            return ((Number) count).longValue();
        }
        return 0;
    }

    /**
     * 缓存分类文章列表
     */
    public void cacheSortArticleList(Map<Integer, List<Article>> sortArticleMap) {
        if (sortArticleMap != null) {
            redisUtil.set(CacheConstants.CACHE_PREFIX + "sort:article:list", sortArticleMap, CacheConstants.LONG_EXPIRE_TIME);
            log.debug("缓存分类文章列表");
        }
    }

    /**
     * 获取缓存的分类文章列表
     * 处理Redis序列化导致的类型转换问题
     */
    @SuppressWarnings("unchecked")
    public Map<Integer, List<Article>> getCachedSortArticleList() {
        Object cached = redisUtil.get(CacheConstants.CACHE_PREFIX + "sort:article:list");
        if (cached instanceof Map) {
            log.debug("从缓存获取分类文章列表");
            Map<?, ?> rawMap = (Map<?, ?>) cached;
            Map<Integer, List<Article>> result = new HashMap<>();

            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                Integer sortId = convertToInteger(entry.getKey());
                if (sortId != null && entry.getValue() instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Article> articles = (List<Article>) entry.getValue();
                    result.put(sortId, articles);
                } else {
                    log.warn("缓存数据类型异常 - sortId: {}, value类型: {}",
                            entry.getKey(),
                            entry.getValue() != null ? entry.getValue().getClass().getSimpleName() : "null");
                }
            }
            return result;
        }
        return null;
    }

    /**
     * 安全地将对象转换为Integer类型
     * 处理Redis序列化导致的类型转换问题
     * @param obj 要转换的对象
     * @return 转换后的Integer，转换失败返回null
     */
    private Integer convertToInteger(Object obj) {
        if (obj == null) {
            return null;
        }

        if (obj instanceof Integer) {
            return (Integer) obj;
        }

        if (obj instanceof Number) {
            return ((Number) obj).intValue();
        }

        if (obj instanceof String) {
            try {
                return Integer.valueOf((String) obj);
            } catch (NumberFormatException e) {
                log.warn("无法将字符串转换为Integer: {}", obj);
                return null;
            }
        }

        log.warn("不支持的类型转换: {} -> Integer", obj.getClass().getSimpleName());
        return null;
    }

    /**
     * 删除分类文章列表缓存
     */
    public void evictSortArticleList() {
        redisUtil.del(CacheConstants.CACHE_PREFIX + "sort:article:list");
        log.debug("删除分类文章列表缓存");
    }

    /**
     * 删除文章相关的所有缓存
     */
    public void evictArticleRelatedCache(Integer articleId) {
        if (articleId != null) {
            // 删除文章详情缓存
            evictArticle(articleId);
            // 删除文章列表缓存（模糊删除）
            // 注意：这里简化处理，实际可以使用Redis的SCAN命令
            evictSortArticleList();
            log.debug("删除文章相关缓存: articleId={}", articleId);
        }
    }

    // ================================ 系统配置缓存 ================================

    /**
     * 缓存网站信息
     */
    public void cacheWebInfo(WebInfo webInfo) {
        if (webInfo != null) {
            // 使用PERMANENT_EXPIRE_TIME常量（值为0）表示永久缓存
            redisUtil.set(CacheConstants.WEB_INFO_KEY, webInfo, CacheConstants.PERMANENT_EXPIRE_TIME);
            log.info("缓存网站信息成功(永久缓存) - Key: {}, webName: {}, webTitle: {}",
                    CacheConstants.WEB_INFO_KEY, webInfo.getWebName(), webInfo.getWebTitle());
        } else {
            log.warn("尝试缓存空的网站信息");
        }
    }

    /**
     * 获取缓存的网站信息
     */
    public WebInfo getCachedWebInfo() {
        try {
            Object cached = redisUtil.get(CacheConstants.WEB_INFO_KEY);
            if (cached instanceof WebInfo) {
                WebInfo webInfo = (WebInfo) cached;
                log.debug("从缓存获取网站信息成功 - Key: {}, webName: {}, webTitle: {}",
                        CacheConstants.WEB_INFO_KEY, webInfo.getWebName(), webInfo.getWebTitle());
                return webInfo;
            } else {
                log.info("缓存中未找到网站信息 - Key: {}, 缓存值类型: {}",
                        CacheConstants.WEB_INFO_KEY, cached != null ? cached.getClass().getSimpleName() : "null");
                
                // 尝试从数据库加载
                try {
                    WebInfo webInfo = loadWebInfoFromDatabase();
                    if (webInfo != null) {
                        // 加载成功，更新缓存
                        cacheWebInfo(webInfo);
                        return webInfo;
                    }
                } catch (Exception e) {
                    log.error("从数据库加载网站信息失败", e);
                }
                
                return null;
            }
        } catch (Exception e) {
            log.error("从缓存获取网站信息失败 - Key: {}", CacheConstants.WEB_INFO_KEY, e);
            
            // 尝试从数据库加载
            try {
                WebInfo webInfo = loadWebInfoFromDatabase();
                if (webInfo != null) {
                    // 加载成功，更新缓存
                    cacheWebInfo(webInfo);
                    return webInfo;
                }
            } catch (Exception dbError) {
                log.error("缓存失败后尝试从数据库加载网站信息也失败", dbError);
            }
            
            return null;
        }
    }
    
    /**
     * 从数据库加载网站信息
     * 私有方法，供getCachedWebInfo使用
     */
    private WebInfo loadWebInfoFromDatabase() {
        try {
            WebInfoMapper webInfoMapper = SpringContextUtil.getBean(WebInfoMapper.class);
            List<WebInfo> list = new LambdaQueryChainWrapper<>(webInfoMapper).list();
            
            if (list != null && !list.isEmpty()) {
                WebInfo webInfo = list.get(0);
                // 确保status字段有默认值
                if (webInfo.getStatus() == null) {
                    webInfo.setStatus(true);
                    log.info("WebInfo status字段为null，设置为默认值true");
                }
                log.info("成功从数据库加载网站信息");
                return webInfo;
            } else {
                log.warn("数据库中未找到网站信息");
                return null;
            }
        } catch (Exception e) {
            log.error("从数据库加载网站信息异常", e);
            return null;
        }
    }

    /**
     * 删除网站信息缓存
     */
    public void evictWebInfo() {
        try {
            redisUtil.del(CacheConstants.WEB_INFO_KEY);
            log.info("删除网站信息缓存成功 - Key: {}", CacheConstants.WEB_INFO_KEY);
        } catch (Exception e) {
            log.error("删除网站信息缓存失败 - Key: {}", CacheConstants.WEB_INFO_KEY, e);
        }
    }

    /**
     * 缓存系统配置
     */
    public void cacheSysConfig(String configKey, String configValue) {
        if (configKey != null) {
            String key = CacheConstants.buildSysConfigKey(configKey);
            redisUtil.set(key, configValue, CacheConstants.PERMANENT_EXPIRE_TIME);
            log.info("缓存系统配置(永久): {}", configKey);
        }
    }

    /**
     * 获取缓存的系统配置
     */
    public String getCachedSysConfig(String configKey) {
        if (configKey == null) return null;

        String key = CacheConstants.buildSysConfigKey(configKey);
        Object cached = redisUtil.get(key);
        if (cached instanceof String) {
            log.debug("从缓存获取系统配置: {}", configKey);
            return (String) cached;
        }
        return null;
    }

    /**
     * 删除系统配置缓存
     */
    public void evictSysConfig(String configKey) {
        if (configKey != null) {
            String key = CacheConstants.buildSysConfigKey(configKey);
            redisUtil.del(key);
            log.debug("删除系统配置缓存: {}", configKey);
        }
    }

    /**
     * 缓存分类信息列表
     */
    public void cacheSortList(List<?> sortList) {
        if (sortList != null) {
            redisUtil.set(CacheConstants.SORT_LIST_KEY, sortList, CacheConstants.VERY_LONG_EXPIRE_TIME);
            log.info("缓存分类信息列表(24小时)");
        }
    }

    /**
     * 获取缓存的分类信息列表
     * 如果缓存不存在或已过期，会自动尝试从数据库重新加载
     */
    @SuppressWarnings("unchecked")
    public List<?> getCachedSortList() {
        Object cached = redisUtil.get(CacheConstants.SORT_LIST_KEY);
        if (cached instanceof List) {
            log.debug("从缓存获取分类信息列表");
            return (List<?>) cached;
        }
        
        // 缓存不存在或已过期，尝试从数据库重新加载
        try {
            org.springframework.context.ApplicationContext context = SpringContextUtil.getApplicationContext();
            if (context != null) {
                com.ld.poetry.dao.SortMapper sortMapper = context.getBean(com.ld.poetry.dao.SortMapper.class);
                List<?> sortList = new com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper<>(sortMapper).list();
                if (sortList != null && !sortList.isEmpty()) {
                    // 重新缓存
                    cacheSortList(sortList);
                    log.info("分类信息缓存已过期，已从数据库重新加载: {} 条记录", sortList.size());
                    return sortList;
                }
            }
        } catch (Exception e) {
            log.error("从数据库重新加载分类信息失败", e);
        }
        
        return null;
    }
    
    /**
     * 获取缓存的标签信息列表
     * 如果缓存不存在或已过期，会自动尝试从数据库重新加载
     */
    @SuppressWarnings("unchecked")
    public List<?> getCachedLabelList(Integer sortId) {
        if (sortId == null) return null;

        String key = CacheConstants.LABEL_LIST_PREFIX + sortId;
        Object cached = redisUtil.get(key);
        if (cached instanceof List) {
            log.debug("从缓存获取标签信息列表: sortId={}", sortId);
            return (List<?>) cached;
        }
        
        // 缓存不存在或已过期，尝试从数据库重新加载
        try {
            org.springframework.context.ApplicationContext context = SpringContextUtil.getApplicationContext();
            if (context != null) {
                com.ld.poetry.dao.LabelMapper labelMapper = context.getBean(com.ld.poetry.dao.LabelMapper.class);
                List<?> labelList = new com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper<>(labelMapper)
                    .eq(com.ld.poetry.entity.Label::getSortId, sortId)
                    .list();
                if (labelList != null) {
                    // 重新缓存
                    cacheLabelList(sortId, labelList);
                    log.info("标签信息缓存已过期，已从数据库重新加载: sortId={}, {} 条记录", sortId, labelList.size());
                    return labelList;
                }
            }
        } catch (Exception e) {
            log.error("从数据库重新加载标签信息失败: sortId={}", sortId, e);
        }
        
        return null;
    }

    /**
     * 删除分类信息列表缓存
     */
    public void evictSortList() {
        redisUtil.del(CacheConstants.SORT_LIST_KEY);
        log.debug("删除分类信息列表缓存");
    }

    /**
     * 缓存标签信息列表
     */
    public void cacheLabelList(Integer sortId, List<?> labelList) {
        if (sortId != null && labelList != null) {
            String key = CacheConstants.LABEL_LIST_PREFIX + sortId;
            redisUtil.set(key, labelList, CacheConstants.VERY_LONG_EXPIRE_TIME);
            log.info("缓存标签信息列表(24小时): sortId={}", sortId);
        }
    }

    /**
     * 删除标签信息列表缓存
     */
    public void evictLabelList(Integer sortId) {
        if (sortId != null) {
            String key = CacheConstants.LABEL_LIST_PREFIX + sortId;
            redisUtil.del(key);
            log.debug("删除标签信息列表缓存: sortId={}", sortId);
        }
    }

    // ================================ IP历史记录和统计缓存 ================================

    /**
     * 缓存IP历史记录集合
     */
    public void cacheIpHistory(Object ipHistorySet) {
        if (ipHistorySet != null) {
            redisUtil.set(CacheConstants.IP_HISTORY_KEY, ipHistorySet, CacheConstants.DEFAULT_EXPIRE_TIME);
            log.debug("缓存IP历史记录集合");
        }
    }

    /**
     * 获取缓存的IP历史记录集合
     */
    public Object getCachedIpHistory() {
        Object cached = redisUtil.get(CacheConstants.IP_HISTORY_KEY);
        if (cached != null) {
            log.debug("从缓存获取IP历史记录集合");
            return cached;
        }
        return null;
    }

    /**
     * 删除IP历史记录缓存
     */
    public void evictIpHistory() {
        redisUtil.del(CacheConstants.IP_HISTORY_KEY);
        log.debug("删除IP历史记录缓存");
    }

    /**
     * 缓存IP历史统计信息
     */
    public void cacheIpHistoryStatistics(Object statistics) {
        if (statistics != null) {
            redisUtil.set(CacheConstants.IP_HISTORY_STATS_KEY, statistics, CacheConstants.LONG_EXPIRE_TIME);
            log.debug("缓存IP历史统计信息");
        }
    }

    /**
     * 获取缓存的IP历史统计信息
     */
    public Object getCachedIpHistoryStatistics() {
        try {
            Object cached = redisUtil.get(CacheConstants.IP_HISTORY_STATS_KEY);
            if (cached != null) {
                log.debug("从缓存获取IP历史统计信息");
                return cached;
            } else {
                log.warn("IP历史统计信息缓存为空");
            }
        } catch (Exception e) {
            log.error("获取IP历史统计信息缓存时出错", e);
        }
        return null;
    }

    /**
     * 安全地获取缓存的IP历史统计信息，如果缓存为空则返回默认值
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getCachedIpHistoryStatisticsSafely() {
        try {
            Object cached = getCachedIpHistoryStatistics();
            if (cached instanceof Map) {
                return (Map<String, Object>) cached;
            }
        } catch (Exception e) {
            log.error("安全获取IP历史统计信息时出错", e);
        }

        // 返回默认值
        Map<String, Object> defaultStats = new HashMap<>();
        defaultStats.put(CommonConst.IP_HISTORY_PROVINCE, new ArrayList<>());
        defaultStats.put(CommonConst.IP_HISTORY_IP, new ArrayList<>());
        defaultStats.put(CommonConst.IP_HISTORY_HOUR, new ArrayList<>());
        defaultStats.put(CommonConst.IP_HISTORY_COUNT, 0L);

        log.info("返回默认IP历史统计信息");
        return defaultStats;
    }

    /**
     * 删除IP历史统计信息缓存
     */
    public void evictIpHistoryStatistics() {
        redisUtil.del(CacheConstants.IP_HISTORY_STATS_KEY);
        log.debug("删除IP历史统计信息缓存");
    }

    /**
     * 缓存管理员家庭信息
     */
    public void cacheAdminFamily(Object family) {
        if (family != null) {
            String key = CacheConstants.CACHE_PREFIX + "admin:family";
            redisUtil.set(key, family, CacheConstants.PERMANENT_EXPIRE_TIME);
            log.info("缓存管理员家庭信息(永久)");
        }
    }

    /**
     * 获取缓存的管理员家庭信息
     */
    public Object getCachedAdminFamily() {
        String key = CacheConstants.CACHE_PREFIX + "admin:family";
        Object cached = redisUtil.get(key);
        if (cached != null) {
            log.debug("从缓存获取管理员家庭信息");
            return cached;
        }
        return null;
    }

    // ================================ Token管理缓存 ================================

    /**
     * 缓存管理员token
     */
    public void cacheAdminToken(Integer userId, String token) {
        if (userId != null && token != null) {
            String key = CacheConstants.buildAdminTokenKey(userId);
            redisUtil.set(key, token, CommonConst.TOKEN_EXPIRE);
            log.info("缓存管理员token: userId={}, 过期时间: {}秒", userId, CommonConst.TOKEN_EXPIRE);
        }
    }

    /**
     * 获取管理员token
     */
    public String getAdminToken(Integer userId) {
        if (userId == null) return null;

        String key = CacheConstants.buildAdminTokenKey(userId);
        Object cached = redisUtil.get(key);
        if (cached instanceof String) {
            log.debug("获取管理员token: userId={}", userId);
            return (String) cached;
        }
        return null;
    }

    /**
     * 删除管理员token
     */
    public void evictAdminToken(Integer userId) {
        if (userId != null) {
            String key = CacheConstants.buildAdminTokenKey(userId);
            redisUtil.del(key);
            log.debug("删除管理员token: userId={}", userId);
        }
    }

    /**
     * 缓存用户token
     */
    public void cacheUserToken(Integer userId, String token) {
        if (userId != null && token != null) {
            String key = CacheConstants.buildUserTokenKey(userId);
            redisUtil.set(key, token, CommonConst.TOKEN_EXPIRE);
            log.info("缓存用户token: userId={}, 过期时间: {}秒", userId, CommonConst.TOKEN_EXPIRE);
        }
    }

    /**
     * 删除用户token
     */
    public void evictUserToken(Integer userId) {
        if (userId != null) {
            String key = CacheConstants.buildUserTokenKey(userId);
            redisUtil.del(key);
            log.debug("删除用户token: userId={}", userId);
        }
    }

    /**
     * 缓存token间隔检查
     */
    public void cacheTokenInterval(Integer userId, boolean isAdmin) {
        if (userId != null) {
            String key = isAdmin ?
                CacheConstants.buildAdminTokenIntervalKey(userId) :
                CacheConstants.buildUserTokenIntervalKey(userId);
            redisUtil.set(key, System.currentTimeMillis(), CommonConst.TOKEN_EXPIRE);
            log.debug("缓存token间隔检查: userId={}, isAdmin={}, 过期时间: {}秒", userId, isAdmin, CommonConst.TOKEN_EXPIRE);
        }
    }

    /**
     * 删除token间隔检查
     */
    public void evictTokenInterval(Integer userId, boolean isAdmin) {
        if (userId != null) {
            String key = isAdmin ?
                CacheConstants.buildAdminTokenIntervalKey(userId) :
                CacheConstants.buildUserTokenIntervalKey(userId);
            redisUtil.del(key);
            log.debug("删除token间隔检查: userId={}, isAdmin={}", userId, isAdmin);
        }
    }

    /**
     * 清理用户的所有token相关缓存
     */
    public void evictAllUserTokens(Integer userId) {
        if (userId != null) {
            // 清理管理员token
            evictAdminToken(userId);
            evictTokenInterval(userId, true);

            // 清理用户token
            evictUserToken(userId);
            evictTokenInterval(userId, false);

            // 清理用户会话
            // 注意：这里需要根据token清理会话，但我们没有反向映射
            // 建议在实际使用中维护userId到token的映射

            log.debug("清理用户所有token相关缓存: userId={}", userId);
        }
    }

    // ================================ 安全相关缓存 ================================

    /**
     * 解除IP拉黑
     */
    public boolean unblacklistIP(String ip) {
        if (ip == null) return false;

        String blacklistKey = CacheConstants.buildIpBlacklistKey(ip);
        String attackKey = CacheConstants.buildIpAttackKey(ip);

        redisUtil.del(blacklistKey, attackKey);
        log.info("管理员手动解除IP拉黑: {}", ip);
        return true;
    }

    /**
     * 获取被拦截的恶意请求总数
     */
    public long getTotalBlockedRequests() {
        Object count = redisUtil.get(CacheConstants.CACHE_PREFIX + "security:blocked:total");
        if (count instanceof Number) {
            return ((Number) count).longValue();
        }
        return 0;
    }

    // ================================ 会话管理 ================================

    /**
     * 缓存用户会话
     * 使用统一的TOKEN_EXPIRE时间，确保与token过期时间一致
     */
    public void cacheUserSession(String token, Integer userId) {
        if (token != null && userId != null) {
            String key = CacheConstants.buildSessionKey(token);
            redisUtil.set(key, userId, CommonConst.TOKEN_EXPIRE);
            log.debug("缓存用户会话: token={}, userId={}, 过期时间: {}秒", token, userId, CommonConst.TOKEN_EXPIRE);
        }
    }

    /**
     * 缓存用户Token映射
     * 使用统一的TOKEN_EXPIRE时间，确保与token过期时间一致
     */
    public void cacheUserTokenMapping(Integer userId, String token) {
        if (userId != null && token != null) {
            String key = CacheConstants.CACHE_PREFIX + "user:token:" + userId;
            redisUtil.set(key, token, CommonConst.TOKEN_EXPIRE);
            log.info("缓存用户Token映射: userId={}, token={}, 过期时间: {}秒", userId, token, CommonConst.TOKEN_EXPIRE);
        }
    }

    /**
     * 获取用户的Token
     */
    public String getUserToken(Integer userId) {
        if (userId == null) return null;

        String key = CacheConstants.buildUserTokenKey(userId);
        Object cached = redisUtil.get(key);
        if (cached instanceof String) {
            log.debug("从缓存获取用户Token: userId={}", userId);
            return (String) cached;
        }
        return null;
    }

    /**
     * 获取会话中的用户ID
     */
    public Integer getUserIdFromSession(String token) {
        if (token == null) return null;

        String key = CacheConstants.buildSessionKey(token);
        Object cached = redisUtil.get(key);
        if (cached instanceof Integer) {
            log.debug("从会话缓存获取用户ID: {}", cached);
            return (Integer) cached;
        }
        return null;
    }

    /**
     * 删除用户会话
     */
    public void evictUserSession(String token) {
        if (token != null) {
            String key = CacheConstants.buildSessionKey(token);
            redisUtil.del(key);
            log.debug("删除用户会话: {}", token);
        }
    }

    /**
     * 删除用户Token映射
     */
    public void evictUserTokenMapping(Integer userId) {
        if (userId != null) {
            String key = CacheConstants.CACHE_PREFIX + "user:token:" + userId;
            redisUtil.del(key);
            log.debug("删除用户Token映射: userId={}", userId);
        }
    }

    /**
     * 缓存管理员用户信息
     */
    public void cacheAdminUser(User admin) {
        if (admin != null) {
            redisUtil.set(CacheConstants.CACHE_PREFIX + "admin", admin, CacheConstants.PERMANENT_EXPIRE_TIME);
            log.info("缓存管理员用户信息(永久): {}", admin.getId());
        }
    }

    /**
     * 获取缓存的管理员用户信息
     */
    public User getCachedAdminUser() {
        Object cached = redisUtil.get(CacheConstants.CACHE_PREFIX + "admin");
        if (cached instanceof User) {
            log.debug("从缓存获取管理员用户信息");
            return (User) cached;
        }
        return null;
    }

    // ================================ 评论缓存 ================================

    /**
     * 缓存评论列表
     */
    public void cacheCommentList(Integer source, String type, List<?> comments) {
        if (source != null && type != null && comments != null) {
            String key = CacheConstants.buildCommentListKey(source, type);
            redisUtil.set(key, comments, CacheConstants.DEFAULT_EXPIRE_TIME);
            log.debug("缓存评论列表: source={}, type={}", source, type);
        }
    }

    /**
     * 获取缓存的评论列表
     */
    @SuppressWarnings("unchecked")
    public List<?> getCachedCommentList(Integer source, String type) {
        if (source == null || type == null) return null;

        String key = CacheConstants.buildCommentListKey(source, type);
        Object cached = redisUtil.get(key);
        if (cached instanceof List) {
            log.debug("从缓存获取评论列表: source={}, type={}", source, type);
            return (List<?>) cached;
        }
        return null;
    }

    /**
     * 删除评论列表缓存
     */
    public void evictCommentList(Integer source, String type) {
        if (source != null && type != null) {
            String key = CacheConstants.buildCommentListKey(source, type);
            redisUtil.del(key);
            log.debug("删除评论列表缓存: source={}, type={}", source, type);
        }
    }

    /**
     * 缓存评论数量
     */
    public void cacheCommentCount(Integer source, String type, Long count) {
        if (source != null && type != null && count != null) {
            String key = CacheConstants.buildCommentListKey(source, type) + ":count";
            redisUtil.set(key, count, CacheConstants.LONG_EXPIRE_TIME);
            log.debug("缓存评论数量: source={}, type={}, count={}", source, type, count);
        }
    }

    /**
     * 获取缓存的评论数量
     */
    public Long getCachedCommentCount(Integer source, String type) {
        if (source == null || type == null) return null;

        String key = CacheConstants.buildCommentListKey(source, type) + ":count";
        Object cached = redisUtil.get(key);
        if (cached instanceof Number) {
            log.debug("从缓存获取评论数量: source={}, type={}", source, type);
            return ((Number) cached).longValue();
        }
        return null;
    }

    /**
     * 递增评论数量
     */
    public long incrementCommentCount(Integer source, String type) {
        if (source == null || type == null) return 0;

        String key = CacheConstants.buildCommentListKey(source, type) + ":count";
        return redisUtil.incr(key, 1);
    }

    /**
     * 递减评论数量
     */
    public long decrementCommentCount(Integer source, String type) {
        if (source == null || type == null) return 0;

        String key = CacheConstants.buildCommentListKey(source, type) + ":count";
        return redisUtil.decr(key, 1);
    }

    /**
     * 删除评论相关的所有缓存
     */
    public void evictCommentRelatedCache(Integer source, String type) {
        if (source != null && type != null) {
            evictCommentList(source, type);
            String countKey = CacheConstants.buildCommentListKey(source, type) + ":count";
            redisUtil.del(countKey);
            log.debug("删除评论相关缓存: source={}, type={}", source, type);
        }
    }

    // ================================ 通用缓存操作 ================================

    /**
     * 通用缓存设置方法（带过期时间）
     */
    public boolean set(String key, Object value, long expireTime) {
        return redisUtil.set(key, value, (int) expireTime);
    }

    /**
     * 通用缓存设置方法（永久缓存）
     */
    public void set(String key, Object value) {
        redisUtil.set(key, value);
        log.debug("设置永久缓存: key={}", key);
    }

    /**
     * 通用缓存获取方法
     */
    public Object get(String key) {
        Object value = redisUtil.get(key);
        if (value != null) {
            log.debug("从缓存获取数据: key={}", key);
        }
        return value;
    }

    /**
     * 检查缓存键是否存在
     */
    public boolean hasKey(String key) {
        return redisUtil.hasKey(key);
    }

    /**
     * 删除缓存键
     */
    public void deleteKey(String key) {
        redisUtil.del(key);
    }

    /**
     * 设置缓存过期时间
     */
    public boolean expire(String key, long time) {
        return redisUtil.expire(key, time);
    }

    /**
     * 删除缓存（别名方法，与deleteKey功能相同）
     */
    public void delete(String key) {
        deleteKey(key);
    }

    /**
     * 根据模式删除多个缓存键
     */
    public void deleteKeysByPattern(String pattern) {
        try {
            // 注意：这里使用简单的实现，生产环境中可能需要更高效的方式
            // 由于Redis的keys命令在大数据量时性能较差，建议使用scan命令
            log.warn("deleteKeysByPattern方法使用了keys命令，在大数据量时可能影响性能: {}", pattern);

            // 这里暂时记录日志，实际删除逻辑可以根据需要实现
            log.info("请求删除匹配模式的缓存键: {}", pattern);
        } catch (Exception e) {
            log.error("根据模式删除缓存键失败: pattern={}", pattern, e);
        }
    }
}
