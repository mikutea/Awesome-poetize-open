package com.ld.poetry.service.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.ld.poetry.service.SearchEnginePushService;
import com.ld.poetry.service.SeoConfigService;
import com.ld.poetry.service.ArticleService;
import com.ld.poetry.service.UserService;
import com.ld.poetry.service.MailService;
import com.ld.poetry.vo.ArticleVO;
import com.ld.poetry.entity.User;
import com.ld.poetry.utils.mail.MailUtil;
import com.ld.poetry.service.CacheService;
import com.ld.poetry.service.TranslationService;
import com.ld.poetry.entity.WebInfo;
import com.ld.poetry.utils.RedisUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Subtask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 搜索引擎推送服务实现类
 * 整合所有搜索引擎的推送功能，读取数据库中的SEO配置
 * 
 * @author LeapYa
 * @since 2025-09-22
 */
@Service
@Slf4j
public class SearchEnginePushServiceImpl implements SearchEnginePushService {

    @Autowired
    private RestTemplate restTemplate;
    
    @Autowired
    private SeoConfigService seoConfigService;
    
    @Autowired
    @Lazy
    private ArticleService articleService;
    
    @Autowired
    private UserService userService;
    
    @Autowired
    private MailService mailService;
    
    @Autowired
    private MailUtil mailUtil;
    
    @Autowired
    private CacheService cacheService;
    
    @Autowired
    private TranslationService translationService;
    
    @Autowired
    private RedisUtil redisUtil;

    private static final String[] SUPPORTED_ENGINES = {
        "baidu", "google", "bing", "yandex", "yahoo", "sogou", "so", "shenma"
    };
    
    // Redis缓存键名和过期时间（使用Redis分布式缓存替代实例变量）
    private static final String SEO_CONFIG_CACHE_KEY = "seo:config:cache";
    private static final long CONFIG_CACHE_DURATION_SECONDS = 300; // 5分钟缓存

    @Override
    public Map<String, Object> pushUrlToAllEngines(String url) {
        
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> engineResults = new ConcurrentHashMap<>();
        
        if (!StringUtils.hasText(url)) {
            result.put("success", false);
            result.put("message", "URL不能为空");
            return result;
        }
        
        // 如果是文章URL，获取所有需要推送的URL（包括翻译版本）
        List<String> urlsToPush = getUrlsIncludingTranslations(url);
        
        // 获取SEO配置
        Map<String, Object> seoConfig = getSeoConfig();
        if (seoConfig == null || seoConfig.isEmpty()) {
            result.put("success", false);
            result.put("message", "无法获取SEO配置");
            return result;
        }
        
        AtomicInteger totalEngines = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger totalUrlsPushed = new AtomicInteger(0);
        AtomicInteger successfulUrlsPushed = new AtomicInteger(0);
        
        // 并行推送到所有启用的搜索引擎
        try (var scope = StructuredTaskScope.open()) {
            Map<String, Subtask<Map<String, Object>>> engineTasks = new HashMap<>();
            
            // 为每个启用的搜索引擎创建并行推送任务
            for (String engine : SUPPORTED_ENGINES) {
                boolean enabled = isEngineEnabled(seoConfig, engine);
                if (enabled) {
                    totalEngines.incrementAndGet();
                    
                    engineTasks.put(engine, scope.fork(() -> {
                        Map<String, Object> engineResult = new HashMap<>();
                        List<Map<String, Object>> urlResults = new ArrayList<>();
                        int engineSuccessCount = 0;
                        
                        // 为当前引擎推送所有URL
                        for (String urlToPush : urlsToPush) {
                            try {
                                Map<String, Object> singleUrlResult = pushUrlToEngine(urlToPush, engine);
                                urlResults.add(Map.of(
                                    "url", urlToPush,
                                    "success", singleUrlResult.get("success"),
                                    "message", singleUrlResult.get("message")
                                ));
                                
                                if (Boolean.TRUE.equals(singleUrlResult.get("success"))) {
                                    engineSuccessCount++;
                                }
                            } catch (Exception e) {
                                log.warn("推送{}到{}失败: {}", urlToPush, engine, e.getMessage());
                                urlResults.add(Map.of(
                                    "url", urlToPush,
                                    "success", false,
                                    "message", e.getMessage()
                                ));
                            }
                        }
                        
                        engineResult.put("success", engineSuccessCount > 0);
                        engineResult.put("successCount", engineSuccessCount);
                        engineResult.put("totalUrls", urlsToPush.size());
                        engineResult.put("urlResults", urlResults);
                        engineResult.put("message", String.format("成功推送 %d/%d 个URL", engineSuccessCount, urlsToPush.size()));
                        
                        return engineResult;
                    }));
                } else {
                }
            }
            
            // 等待所有引擎推送完成
            scope.join();
            
            // 收集结果
            for (Map.Entry<String, Subtask<Map<String, Object>>> entry : engineTasks.entrySet()) {
                String engine = entry.getKey();
                Subtask<Map<String, Object>> task = entry.getValue();
                
                if (task.state() == Subtask.State.SUCCESS) {
                    Map<String, Object> engineResult = task.get();
                    engineResults.put(engine, engineResult);
                    
                    int engineSuccessCount = (Integer) engineResult.get("successCount");
                    int engineTotalUrls = (Integer) engineResult.get("totalUrls");
                    
                    totalUrlsPushed.addAndGet(engineTotalUrls);
                    successfulUrlsPushed.addAndGet(engineSuccessCount);
                    
                    if (engineSuccessCount > 0) {
                        successCount.incrementAndGet();
                    }
                } else {
                    log.error("搜索引擎{}推送失败", engine);
                    engineResults.put(engine, Map.of(
                        "success", false,
                        "message", "推送任务执行失败"
                    ));
                }
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("搜索引擎推送被中断", e);
            result.put("success", false);
            result.put("message", "推送被中断");
            return result;
        }
        
        result.put("success", successCount.get() > 0);
        result.put("totalEngines", totalEngines.get());
        result.put("successCount", successCount.get());
        result.put("totalUrlsPushed", totalUrlsPushed.get());
        result.put("successfulUrlsPushed", successfulUrlsPushed.get());
        result.put("url", url);  // 原始URL
        result.put("allUrls", urlsToPush);  // 所有推送的URL
        result.put("results", engineResults);
        result.put("timestamp", new Date());
        
        if (successCount.get() > 0) {
            result.put("message", String.format("成功推送到 %d/%d 个搜索引擎，共推送 %d/%d 个URL", 
                    successCount.get(), totalEngines.get(), successfulUrlsPushed.get(), totalUrlsPushed.get()));
        } else {
            result.put("message", totalEngines.get() > 0 ? "所有启用的搜索引擎推送都失败了" : "没有启用任何搜索引擎");
            log.warn("URL推送完成，成功引擎: {}/{}, 成功URL: {}/{}", 
                    successCount.get(), totalEngines.get(), successfulUrlsPushed.get(), totalUrlsPushed.get());
        }
        
        // 触发邮件通知回调
        triggerEmailNotification(url, result);
        
        return result;
    }

    @Override
    public Map<String, Object> pushUrlToEngine(String url, String engine) {
        Map<String, Object> result = new HashMap<>();
        Map<String, Object> seoConfig = getSeoConfig();
        
        if (seoConfig == null) {
            result.put("success", false);
            result.put("message", "无法获取SEO配置");
            return result;
        }
        
        try {
            switch (engine.toLowerCase()) {
                case "baidu":
                    return pushToBaidu(url, seoConfig);
                case "google":
                    return pushToGoogle(url, seoConfig);
                case "bing":
                    return pushToBing(url, seoConfig);
                case "yandex":
                    return pushToYandex(url, seoConfig);
                case "yahoo":
                    return pushToYahoo(url, seoConfig);
                case "sogou":
                    return pushToSogou(url, seoConfig);
                case "so":
                    return pushToSo(url, seoConfig);
                case "shenma":
                    return pushToShenma(url, seoConfig);
                default:
                    result.put("success", false);
                    result.put("message", "不支持的搜索引擎: " + engine);
                    return result;
            }
        } catch (Exception e) {
            log.error("推送到{}失败", engine, e);
            result.put("success", false);
            result.put("message", "推送失败: " + e.getMessage());
            return result;
        }
    }

    @Override
    public Map<String, Object> pushSitemapToAllEngines() {
        Map<String, Object> seoConfig = getSeoConfig();
        if (seoConfig == null) {
            return Map.of("success", false, "message", "无法获取SEO配置");
        }
        
        // 直接从 MailUtil 获取网站地址
        String siteAddress = mailUtil.getSiteUrl();
        if (!StringUtils.hasText(siteAddress)) {
            return Map.of("success", false, "message", "网站地址未配置");
        }
        
        String sitemapUrl = siteAddress + "/sitemap.xml";
        return pushUrlToAllEngines(sitemapUrl);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> getSeoConfig() {
        try {
            // 尝试从Redis缓存获取SEO配置
            Object cachedConfig = redisUtil.get(SEO_CONFIG_CACHE_KEY);
            if (cachedConfig != null) {
                return (Map<String, Object>) cachedConfig;
            }
            
            
            // 从数据库查询SEO配置
            Map<String, Object> seoConfig = seoConfigService.getSeoConfigAsJson();
            
            if (seoConfig != null && !seoConfig.isEmpty()) {
                // site_address 已迁移至 web_info 表，使用的地方应直接调用 mailUtil.getSiteUrl()
                
                // 将配置存入Redis缓存（5分钟过期）
                boolean cached = redisUtil.set(SEO_CONFIG_CACHE_KEY, seoConfig, CONFIG_CACHE_DURATION_SECONDS);
                if (cached) {
                } else {
                    log.warn("SEO配置存入Redis缓存失败，但仍返回配置");
                }
                
                return seoConfig;
            } else {
                log.warn("数据库中SEO配置为空，使用备用默认配置");
                return getFallbackSeoConfig();
            }
            
        } catch (Exception e) {
            log.error("获取SEO配置失败: {}", e.getMessage(), e);
            return getFallbackSeoConfig();
        }
    }
    
    @Override
    public void clearSeoConfigCache() {
        try {
            redisUtil.del(SEO_CONFIG_CACHE_KEY);
            log.info("SearchEnginePushService SEO配置Redis缓存已清理");
        } catch (Exception e) {
            log.error("清理SEO配置Redis缓存失败", e);
        }
    }
    
    /**
     * 获取备用SEO配置
     * 当无法从数据库获取配置时使用（所有搜索引擎推送均禁用）
     */
    private Map<String, Object> getFallbackSeoConfig() {
        log.warn("使用备用默认SEO配置（所有搜索引擎推送禁用）");
        
        // 返回一个安全的默认配置，禁用所有搜索引擎推送
        Map<String, Object> fallbackConfig = new HashMap<>();
        fallbackConfig.put("enable", true);
        // site_address 已迁移至 web_info 表，使用的地方应直接调用 mailUtil.getSiteUrl()
        
        // 所有搜索引擎推送都禁用，避免无有效API Token时的推送
        fallbackConfig.put("baidu_push_enabled", false);
        fallbackConfig.put("google_index_enabled", false);
        fallbackConfig.put("bing_push_enabled", false);
        fallbackConfig.put("yandex_push_enabled", false);
        fallbackConfig.put("yahoo_push_enabled", false);
        fallbackConfig.put("sogou_push_enabled", false);
        fallbackConfig.put("so_push_enabled", false);
        fallbackConfig.put("shenma_push_enabled", false);
        
        return fallbackConfig;
    }

    @Override
    public boolean isPushEnabled() {
        Map<String, Object> seoConfig = getSeoConfig();
        if (seoConfig == null || seoConfig.isEmpty()) {
            return false;
        }
        
        // 检查总开关
        Boolean globalEnable = (Boolean) seoConfig.get("enable");
        if (!Boolean.TRUE.equals(globalEnable)) {
            return false;
        }
        
        // 检查是否有任何搜索引擎启用
        for (String engine : SUPPORTED_ENGINES) {
            if (isEngineEnabled(seoConfig, engine)) {
                return true;
            }
        }
        
        return false;
    }

    @Override
    public String[] getSupportedEngines() {
        return SUPPORTED_ENGINES.clone();
    }


    /**
     * 检查指定搜索引擎是否启用
     */
    private boolean isEngineEnabled(Map<String, Object> seoConfig, String engine) {
        String enabledKey = getEngineEnabledKey(engine);
        Boolean enabled = (Boolean) seoConfig.get(enabledKey);
        return Boolean.TRUE.equals(enabled);
    }

    /**
     * 获取搜索引擎启用状态的配置键名
     */
    private String getEngineEnabledKey(String engine) {
        switch (engine.toLowerCase()) {
            case "baidu": return "baidu_push_enabled";
            case "google": return "google_index_enabled";
            case "bing": return "bing_push_enabled";
            case "yandex": return "yandex_push_enabled";
            case "yahoo": return "yahoo_push_enabled";
            case "sogou": return "sogou_push_enabled";
            case "so": return "so_push_enabled";
            case "shenma": return "shenma_push_enabled";
            default: return null;
        }
    }

    /**
     * 推送到百度搜索引擎
     */
    private Map<String, Object> pushToBaidu(String url, Map<String, Object> seoConfig) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String token = (String) seoConfig.get("baidu_token");
            String pushToken = (String) seoConfig.get("baidu_push_token");
            
            // 优先使用baidu_push_token，如果没有则使用baidu_token
            String finalToken = StringUtils.hasText(pushToken) ? pushToken : token;
            
            if (!StringUtils.hasText(finalToken)) {
                result.put("success", false);
                result.put("message", "百度推送Token未配置");
                return result;
            }
            
            // 直接从 MailUtil 获取网站地址
            String siteAddress = mailUtil.getSiteUrl();
            if (!StringUtils.hasText(siteAddress)) {
                result.put("success", false);
                result.put("message", "网站地址未配置");
                return result;
            }
            
            String pushUrl = String.format("http://data.zz.baidu.com/urls?site=%s&token=%s", siteAddress, finalToken);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            headers.set("User-Agent", "poetize-java/1.0.0");
            
            HttpEntity<String> entity = new HttpEntity<>(url, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(pushUrl, entity, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                JSONObject responseJson = JSON.parseObject(response.getBody());
                result.put("success", true);
                result.put("message", "百度推送成功");
                result.put("response", responseJson);
            } else {
                result.put("success", false);
                result.put("message", "百度推送失败: HTTP " + response.getStatusCode());
                result.put("response", response.getBody());
            }
            
        } catch (Exception e) {
            log.error("百度推送失败", e);
            result.put("success", false);
            result.put("message", "百度推送异常: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 推送到Google搜索引擎
     */
    private Map<String, Object> pushToGoogle(String url, Map<String, Object> seoConfig) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String apiKey = (String) seoConfig.get("google_api_key");
            if (!StringUtils.hasText(apiKey)) {
                // 回退到简单ping方式
                return pushToGooglePing(url, seoConfig);
            }
            
            // 使用Google Indexing API
            String indexingUrl = "https://indexing.googleapis.com/v3/urlNotifications:publish";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            headers.set("User-Agent", "poetize-java/1.0.0");
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("url", url);
            requestBody.put("type", "URL_UPDATED");
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(indexingUrl, entity, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                JSONObject responseJson = JSON.parseObject(response.getBody());
                result.put("success", true);
                result.put("message", "Google索引提交成功");
                result.put("response", responseJson);
            } else {
                result.put("success", false);
                result.put("message", "Google索引提交失败: HTTP " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            log.error("Google索引提交失败", e);
            result.put("success", false);
            result.put("message", "Google索引提交异常: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * Google简单ping方式（当没有API key时使用）
     */
    private Map<String, Object> pushToGooglePing(String url, Map<String, Object> seoConfig) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String encodedUrl = java.net.URLEncoder.encode(url, "UTF-8");
            String pingUrl = "http://www.google.com/ping?sitemap=" + encodedUrl;
            
            String response = restTemplate.getForObject(pingUrl, String.class);
            
            result.put("success", true);
            result.put("message", "Google ping推送成功");
            result.put("response", response != null ? response : "");
            
        } catch (Exception e) {
            log.error("Google ping推送失败", e);
            result.put("success", false);
            result.put("message", "Google ping推送异常: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 推送到Bing搜索引擎
     */
    private Map<String, Object> pushToBing(String url, Map<String, Object> seoConfig) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String apiKey = (String) seoConfig.get("bing_api_key");
            if (!StringUtils.hasText(apiKey)) {
                // 回退到简单ping方式
                return pushToBingPing(url, seoConfig);
            }
            
            // 使用Bing IndexNow API
            String indexNowUrl = "https://api.indexnow.org/indexnow";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("User-Agent", "poetize-java/1.0.0");
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("host", extractHostFromUrl(url));
            requestBody.put("key", apiKey);
            requestBody.put("urlList", Arrays.asList(url));
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(indexNowUrl, entity, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK || response.getStatusCode() == HttpStatus.ACCEPTED) {
                result.put("success", true);
                result.put("message", "Bing IndexNow提交成功");
                result.put("response", response.getBody());
            } else {
                result.put("success", false);
                result.put("message", "Bing IndexNow提交失败: HTTP " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            log.error("Bing IndexNow提交失败", e);
            result.put("success", false);
            result.put("message", "Bing IndexNow提交异常: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * Bing简单ping方式
     */
    private Map<String, Object> pushToBingPing(String url, Map<String, Object> seoConfig) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String encodedUrl = java.net.URLEncoder.encode(url, "UTF-8");
            String pingUrl = "http://www.bing.com/ping?sitemap=" + encodedUrl;
            
            String response = restTemplate.getForObject(pingUrl, String.class);
            
            result.put("success", true);
            result.put("message", "Bing ping推送成功");
            result.put("response", response != null ? response : "");
            
        } catch (Exception e) {
            log.error("Bing ping推送失败", e);
            result.put("success", false);
            result.put("message", "Bing ping推送异常: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 推送到Yandex搜索引擎
     */
    private Map<String, Object> pushToYandex(String url, Map<String, Object> seoConfig) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String apiKey = (String) seoConfig.get("yandex_api_key");
            if (!StringUtils.hasText(apiKey)) {
                result.put("success", false);
                result.put("message", "Yandex API密钥未配置");
                return result;
            }
            
            // 使用Yandex Webmaster API
            String yandexUrl = "https://api.webmaster.yandex.net/v4/user/{user-id}/hosts/{host-id}/url-notification/submit";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);
            headers.set("User-Agent", "poetize-java/1.0.0");
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("url", url);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(yandexUrl, entity, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                result.put("success", true);
                result.put("message", "Yandex推送成功");
                result.put("response", response.getBody());
            } else {
                result.put("success", false);
                result.put("message", "Yandex推送失败: HTTP " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            log.error("Yandex推送失败", e);
            result.put("success", false);
            result.put("message", "Yandex推送异常: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 推送到Yahoo搜索引擎
     */
    private Map<String, Object> pushToYahoo(String url, Map<String, Object> seoConfig) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String encodedUrl = java.net.URLEncoder.encode(url, "UTF-8");
            String pingUrl = "http://search.yahooapis.com/SiteExplorerService/V1/ping?sitemap=" + encodedUrl;
            
            String response = restTemplate.getForObject(pingUrl, String.class);
            
            result.put("success", true);
            result.put("message", "Yahoo推送成功");
            result.put("response", response != null ? response : "");
            
        } catch (Exception e) {
            log.error("Yahoo推送失败", e);
            result.put("success", false);
            result.put("message", "Yahoo推送异常: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 推送到搜狗搜索引擎
     */
    private Map<String, Object> pushToSogou(String url, Map<String, Object> seoConfig) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String token = (String) seoConfig.get("sogou_token");
            String pushToken = (String) seoConfig.get("sogou_push_token");
            
            // 优先使用sogou_push_token，如果没有则使用sogou_token
            String finalToken = StringUtils.hasText(pushToken) ? pushToken : token;
            
            if (!StringUtils.hasText(finalToken)) {
                result.put("success", false);
                result.put("message", "搜狗推送Token未配置");
                return result;
            }
            
            // 直接从 MailUtil 获取网站地址
            String siteAddress = mailUtil.getSiteUrl();
            if (!StringUtils.hasText(siteAddress)) {
                result.put("success", false);
                result.put("message", "网站地址未配置");
                return result;
            }
            
            // 搜狗站长平台API
            String sogouUrl = String.format("http://zhanzhang.sogou.com/linksubmit/url?site=%s&token=%s", siteAddress, finalToken);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            headers.set("User-Agent", "poetize-java/1.0.0");
            
            HttpEntity<String> entity = new HttpEntity<>(url, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(sogouUrl, entity, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                result.put("success", true);
                result.put("message", "搜狗推送成功");
                result.put("response", response.getBody());
            } else {
                result.put("success", false);
                result.put("message", "搜狗推送失败: HTTP " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            log.error("搜狗推送失败", e);
            result.put("success", false);
            result.put("message", "搜狗推送异常: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 推送到360搜索引擎
     */
    private Map<String, Object> pushToSo(String url, Map<String, Object> seoConfig) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String token = (String) seoConfig.get("so_token");
            String pushToken = (String) seoConfig.get("so_push_token");
            
            // 优先使用so_push_token，如果没有则使用so_token
            String finalToken = StringUtils.hasText(pushToken) ? pushToken : token;
            
            if (!StringUtils.hasText(finalToken)) {
                result.put("success", false);
                result.put("message", "360推送Token未配置");
                return result;
            }
            
            // 直接从 MailUtil 获取网站地址
            String siteAddress = mailUtil.getSiteUrl();
            if (!StringUtils.hasText(siteAddress)) {
                result.put("success", false);
                result.put("message", "网站地址未配置");
                return result;
            }
            
            // 360站长工具API
            String soUrl = String.format("http://data.so.com/urls?site=%s&token=%s", siteAddress, finalToken);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            headers.set("User-Agent", "poetize-java/1.0.0");
            
            HttpEntity<String> entity = new HttpEntity<>(url, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(soUrl, entity, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                JSONObject responseJson = JSON.parseObject(response.getBody());
                result.put("success", true);
                result.put("message", "360推送成功");
                result.put("response", responseJson);
            } else {
                result.put("success", false);
                result.put("message", "360推送失败: HTTP " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            log.error("360推送失败", e);
            result.put("success", false);
            result.put("message", "360推送异常: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 推送到神马搜索引擎（UC浏览器）
     */
    private Map<String, Object> pushToShenma(String url, Map<String, Object> seoConfig) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String token = (String) seoConfig.get("shenma_token");
            if (!StringUtils.hasText(token)) {
                result.put("success", false);
                result.put("message", "神马推送Token未配置");
                return result;
            }
            
            // 直接从 MailUtil 获取网站地址
            String siteAddress = mailUtil.getSiteUrl();
            if (!StringUtils.hasText(siteAddress)) {
                result.put("success", false);
                result.put("message", "网站地址未配置");
                return result;
            }
            
            // 神马搜索站长平台API
            String shenmaUrl = String.format("http://data.sm.cn/webmaster/url/submit?site=%s&token=%s", siteAddress, token);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            headers.set("User-Agent", "poetize-java/1.0.0");
            
            HttpEntity<String> entity = new HttpEntity<>(url, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(shenmaUrl, entity, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                JSONObject responseJson = JSON.parseObject(response.getBody());
                result.put("success", true);
                result.put("message", "神马推送成功");
                result.put("response", responseJson);
            } else {
                result.put("success", false);
                result.put("message", "神马推送失败: HTTP " + response.getStatusCode());
            }
            
        } catch (Exception e) {
            log.error("神马推送失败", e);
            result.put("success", false);
            result.put("message", "神马推送异常: " + e.getMessage());
        }
        
        return result;
    }

    /**
     * 从URL中提取主机名
     */
    private String extractHostFromUrl(String url) {
        try {
            if (url.startsWith("http://") || url.startsWith("https://")) {
                return java.net.URI.create(url).getHost();
            } else {
                return java.net.URI.create("http://" + url).getHost();
            }
        } catch (Exception e) {
            log.warn("提取主机名失败: {}", url, e);
            return "localhost";
        }
    }

    /**
     * 触发邮件通知回调
     * 直接发送给文章作者的邮箱
     */
    private void triggerEmailNotification(String url, Map<String, Object> pushResult) {
        try {
            // 从URL中提取文章ID
            Integer articleId = extractArticleIdFromUrl(url);
            if (articleId == null) {
                log.warn("无法从URL中提取文章ID: {}", url);
                return;
            }

            // 获取SEO配置检查是否启用推送通知
            Map<String, Object> seoConfig = getSeoConfig();
            if (seoConfig == null) {
                log.warn("无法获取SEO配置，跳过邮件通知");
                return;
            }

            // 检查是否启用推送通知
            Boolean enablePushNotification = (Boolean) seoConfig.get("enable_push_notification");
            if (!Boolean.TRUE.equals(enablePushNotification)) {
                return;
            }

            // 检查是否只在失败时通知
            Boolean notifyOnlyOnFailure = (Boolean) seoConfig.get("notify_only_on_failure");
            Boolean pushSuccess = (Boolean) pushResult.get("success");
            
            if (Boolean.TRUE.equals(notifyOnlyOnFailure) && Boolean.TRUE.equals(pushSuccess)) {
                return;
            }

            // 使用虚拟线程异步发送邮件通知，避免影响推送性能
            Thread.ofVirtual().name("seo-email-notify").start(() -> {
                try {
                    sendNotificationEmail(articleId, url, pushResult);
                } catch (Exception e) {
                    log.error("发送SEO推送结果通知邮件时出错", e);
                }
            });

        } catch (Exception e) {
            log.error("触发邮件通知时出错", e);
        }
    }

    /**
     * 从URL中提取文章ID
     * 预期URL格式: https://domain.com/article/123
     */
    private Integer extractArticleIdFromUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        
        try {
            // 使用正则表达式提取文章ID
            Pattern pattern = Pattern.compile("/article/(\\d+)");
            Matcher matcher = pattern.matcher(url);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        } catch (Exception e) {
            log.warn("解析文章URL失败: {}", url, e);
        }
        
        return null;
    }

    /**
     * 发送通知邮件给文章作者
     */
    private void sendNotificationEmail(Integer articleId, String url, Map<String, Object> pushResult) {
        try {
            // 查询文章信息以获取作者ID和文章标题
            ArticleVO article = articleService.getArticleById(articleId, null).getData();
            if (article == null) {
                log.warn("未找到文章信息，文章ID: {}", articleId);
                return;
            }

            Integer authorId = article.getUserId();
            if (authorId == null) {
                log.warn("无法确定文章作者，文章ID: {}", articleId);
                return;
            }

            // 查询作者信息
            User author = userService.getById(authorId);
            if (author == null) {
                log.warn("未找到文章作者信息，作者ID: {}", authorId);
                return;
            }

            // 检查作者是否有邮箱
            if (!StringUtils.hasText(author.getEmail())) {
                log.info("文章作者未设置邮箱，跳过邮件通知，作者ID: {}, 文章ID: {}", authorId, articleId);
                return;
            }

            // 构建邮件内容
            String subject = buildEmailSubject(article.getArticleTitle(), pushResult);
            String content = buildEmailContent(article, url, pushResult);

            // 发送邮件
            List<String> recipients = Arrays.asList(author.getEmail());
            boolean mailSent = mailService.sendMail(recipients, subject, content, true, null);

            if (mailSent) {
            } else {
                log.warn("SEO推送结果通知邮件发送失败，收件人: {}, 文章ID: {}", author.getEmail(), articleId);
            }

        } catch (Exception e) {
            log.error("发送通知邮件给文章作者时出错，文章ID: {}", articleId, e);
        }
    }

    /**
     * 构建邮件标题
     */
    private String buildEmailSubject(String articleTitle, Map<String, Object> pushResult) {
        Boolean success = (Boolean) pushResult.get("success");
        return (Boolean.TRUE.equals(success) ? "SEO推送成功: " : "SEO推送失败: ") + articleTitle;
    }

    /**
     * 构建邮件内容（使用统一的邮件模板）
     */
    private String buildEmailContent(ArticleVO article, String url, Map<String, Object> pushResult) {
        Boolean success = (Boolean) pushResult.get("success");
        Integer successCount = (Integer) pushResult.get("successCount");
        Integer totalEngines = (Integer) pushResult.get("totalEngines");
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        
        @SuppressWarnings("unchecked")
        Map<String, Object> results = (Map<String, Object>) pushResult.get("results");

        // 构建邮件主体内容
        StringBuilder mainContent = new StringBuilder();
        
        // 文章信息
        mainContent.append("您的文章「<strong>").append(article.getArticleTitle()).append("</strong>」已完成搜索引擎推送。<br><br>");
        
        // 获取URL推送统计信息
        Integer totalUrlsPushed = (Integer) pushResult.get("totalUrlsPushed");
        Integer successfulUrlsPushed = (Integer) pushResult.get("successfulUrlsPushed");
        @SuppressWarnings("unchecked")
        List<String> allUrls = (List<String>) pushResult.get("allUrls");
        
        // 推送结果摘要
        if (Boolean.TRUE.equals(success)) {
            mainContent.append("✅ <span style='color: #52c41a; font-weight: bold;'>推送成功</span><br>");
            mainContent.append("成功推送到 <strong>").append(successCount).append("/").append(totalEngines).append("</strong> 个搜索引擎<br>");
            if (totalUrlsPushed != null && totalUrlsPushed > 1) {
                mainContent.append("共推送 <strong>").append(successfulUrlsPushed).append("/").append(totalUrlsPushed).append("</strong> 个URL（包含翻译版本）<br><br>");
            } else {
                mainContent.append("<br>");
            }
        } else {
            mainContent.append("❌ <span style='color: #ff4d4f; font-weight: bold;'>推送失败</span><br>");
            mainContent.append("成功推送到 <strong>").append(successCount).append("/").append(totalEngines).append("</strong> 个搜索引擎<br>");
            if (totalUrlsPushed != null && totalUrlsPushed > 1) {
                mainContent.append("共推送 <strong>").append(successfulUrlsPushed != null ? successfulUrlsPushed : 0).append("/").append(totalUrlsPushed).append("</strong> 个URL（包含翻译版本）<br><br>");
            } else {
                mainContent.append("<br>");
            }
        }
        
        // 文章链接
        mainContent.append("📝 文章链接：<a href='").append(url).append("' style='color: #1890ff;'>").append(url).append("</a><br>");
        
        // 如果有翻译版本，显示翻译链接
        if (allUrls != null && allUrls.size() > 1) {
            mainContent.append("🌐 翻译文章: ").append(allUrls.size() - 1).append(" 个语言版本");
        }

        // 构建详细结果内容（作为引用内容）
        StringBuilder detailContent = new StringBuilder();
        if (results != null && !results.isEmpty()) {
            detailContent.append("<hr style=\"border: 1px dashed #ef859d2e;margin: 20px 0\">\n");
            detailContent.append("<div>\n");
            detailContent.append("    <div style=\"font-size: 18px;font-weight: bold;color: #C5343E\">\n");
            detailContent.append("        推送详细结果\n");
            detailContent.append("    </div>\n");
            detailContent.append("    <div style=\"margin-top: 10px;\">\n");
            
            for (Map.Entry<String, Object> entry : results.entrySet()) {
                String engineName = entry.getKey();
                @SuppressWarnings("unchecked")
                Map<String, Object> engineResult = (Map<String, Object>) entry.getValue();
                
                Boolean engineSuccess = (Boolean) engineResult.get("success");
                String message = (String) engineResult.get("message");
                Integer engineSuccessCount = (Integer) engineResult.get("successCount");
                Integer totalUrls = (Integer) engineResult.get("totalUrls");
                
                detailContent.append("        <div style=\"margin: 8px 0; padding: 8px; background: #f9f9f9; border-radius: 4px;\">\n");
                detailContent.append("            <strong>").append(getEngineDisplayName(engineName)).append("</strong>: ");
                
                if (Boolean.TRUE.equals(engineSuccess)) {
                    detailContent.append("<span style='color: #52c41a;'>✅ 成功</span>");
                    if (engineSuccessCount != null && totalUrls != null && totalUrls > 1) {
                        detailContent.append(" (").append(engineSuccessCount).append("/").append(totalUrls).append(" URLs)");
                    }
                } else {
                    detailContent.append("<span style='color: #ff4d4f;'>❌ 失败</span>");
                    if (engineSuccessCount != null && totalUrls != null && totalUrls > 1) {
                        detailContent.append(" (").append(engineSuccessCount).append("/").append(totalUrls).append(" URLs)");
                    }
                }
                
                if (message != null && !message.isEmpty()) {
                    detailContent.append("<br><small style='color: #666;'>").append(message).append("</small>");
                }
                detailContent.append("        </div>\n");
            }
            
            detailContent.append("    </div>\n");
            detailContent.append("</div>");
        }
        
        // 动态获取网站名称
        String siteName = getSiteName();
        
        // 使用统一的邮件模板
        // 模板参数：网站名称, 时间戳, 发件人名称, 邮件内容, 引用内容, 网站名称
        String emailTemplate = mailUtil.getMailText();
        return String.format(emailTemplate,
                siteName, // 网站名称
                timestamp, // 时间戳  
                "SEO推送系统", // 发件人名称
                mainContent.toString(), // 邮件主体内容
                detailContent.toString(), // 详细结果（引用内容）
                siteName // 底部网站名称
        );
    }

    /**
     * 获取搜索引擎显示名称
     */
    private String getEngineDisplayName(String engineName) {
        switch (engineName.toLowerCase()) {
            case "baidu": return "百度";
            case "google": return "Google";
            case "bing": return "必应";
            case "yandex": return "Yandex";
            case "yahoo": return "Yahoo";
            case "sogou": return "搜狗";
            case "so": return "360搜索";
            case "shenma": return "神马搜索";
            default: return engineName;
        }
    }
    
    /**
     * 动态获取网站名称
     */
    private String getSiteName() {
        try {
            WebInfo webInfo = cacheService.getCachedWebInfo();
            if (webInfo != null && StringUtils.hasText(webInfo.getWebName())) {
                return webInfo.getWebName();
            }
        } catch (Exception e) {
            log.warn("获取网站名称失败，使用默认值", e);
        }
        return "POETIZE"; // 默认网站名称
    }
    
    /**
     * 获取包括翻译版本在内的所有URL
     * 如果是文章URL，会返回源文章和所有翻译版本的URL
     * 如果不是文章URL，则只返回原URL
     */
    private List<String> getUrlsIncludingTranslations(String url) {
        List<String> urls = new ArrayList<>();
        
        if (!StringUtils.hasText(url)) {
            return urls;
        }
        
        // 首先添加原始URL
        urls.add(url);
        
        try {
            // 检查是否是文章URL格式: {site_address}/article/{id}
            Integer articleId = extractArticleIdFromUrl(url);
            if (articleId == null) {
                return urls;
            }
            
            // 获取文章的所有翻译语言
            List<String> availableLanguages = translationService.getArticleAvailableLanguages(articleId);
            if (availableLanguages == null || availableLanguages.isEmpty()) {
                return urls;
            }
            
            // 构建翻译文章URL
            for (String language : availableLanguages) {
                String translatedUrl = buildTranslatedArticleUrl(url, articleId, language);
                if (translatedUrl != null) {
                    urls.add(translatedUrl);
                }
            }
            
            
        } catch (Exception e) {
            log.warn("获取翻译URL时出错，仅推送原始URL: {}", url, e);
        }
        
        return urls;
    }
    
    /**
     * 安全地构建翻译文章URL
     * 使用 URI 类处理，支持带查询参数和 fragment 的 URL
     * 
     * @param originalUrl 原始文章URL (如: https://example.com/article/123?ref=twitter#comments)
     * @param articleId 文章ID
     * @param language 目标语言
     * @return 翻译文章URL (如: https://example.com/article/en/123?ref=twitter#comments)
     */
    private String buildTranslatedArticleUrl(String originalUrl, Integer articleId, String language) {
        try {
            java.net.URI originalUri = java.net.URI.create(originalUrl);
            
            // 获取原始路径
            String originalPath = originalUri.getPath();
            if (originalPath == null) {
                log.warn("URL路径为空，无法构建翻译URL: {}", originalUrl);
                return null;
            }
            
            // 查找 /article/ 的位置
            int articleIndex = originalPath.lastIndexOf("/article/");
            if (articleIndex == -1) {
                log.warn("URL路径中未找到 /article/ 标识，无法构建翻译URL: {}", originalUrl);
                return null;
            }
            
            // 构建新路径：保留 /article/ 之前的部分 + /article/ + 语言 + / + 文章ID
            String basePath = originalPath.substring(0, articleIndex);
            String newPath = basePath + "/article/" + language + "/" + articleId;
            
            // 构建新的 URI，保留 scheme, host, port, query, fragment
            StringBuilder translatedUrl = new StringBuilder();
            
            // Scheme (http/https)
            if (originalUri.getScheme() != null) {
                translatedUrl.append(originalUri.getScheme()).append("://");
            }
            
            // Host
            if (originalUri.getHost() != null) {
                translatedUrl.append(originalUri.getHost());
            }
            
            // Port (如果不是默认端口)
            if (originalUri.getPort() != -1) {
                translatedUrl.append(":").append(originalUri.getPort());
            }
            
            // Path
            translatedUrl.append(newPath);
            
            // Query parameters (保留原始查询参数，如 ?ref=twitter)
            if (originalUri.getQuery() != null) {
                translatedUrl.append("?").append(originalUri.getQuery());
            }
            
            // Fragment (保留原始fragment，如 #comments)
            if (originalUri.getFragment() != null) {
                translatedUrl.append("#").append(originalUri.getFragment());
            }
            
            return translatedUrl.toString();
            
        } catch (Exception e) {
            log.error("构建翻译文章URL失败，原始URL: {}, 语言: {}, 错误: {}", 
                    originalUrl, language, e.getMessage(), e);
            return null;
        }
    }
}
