package com.ld.poetry.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.constants.CacheConstants;
import com.ld.poetry.service.ArticleService;
import com.ld.poetry.service.CacheService;
import com.ld.poetry.utils.PoetryUtil;
import com.ld.poetry.vo.ArticleVO;
import com.ld.poetry.vo.BaseRequestVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import com.ld.poetry.service.MailService;
import com.ld.poetry.service.UserService;
import com.ld.poetry.service.SeoService;
import com.ld.poetry.entity.User;
import lombok.extern.slf4j.Slf4j;
import com.ld.poetry.service.TranslationService;
import java.util.Map;
import java.util.HashMap;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import com.ld.poetry.service.impl.ArticleServiceImpl;
import com.ld.poetry.utils.PrerenderClient;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;
import com.ld.poetry.event.ArticleSavedEvent;
import org.springframework.context.ApplicationEventPublisher;

/**
 * <p>
 * 文章表 前端控制器
 * </p>
 *
 * @author sara
 * @since 2021-08-13
 */
@RestController
@RequestMapping("/article")
@Slf4j
public class ArticleController {

    @Autowired
    private ArticleService articleService;
    
    @Autowired
    private MailService mailService;
    
    @Autowired
    private UserService userService;

    @Autowired
    private TranslationService translationService;

    @Autowired
    private PrerenderClient prerenderClient;

    @Autowired
    private SeoService seoService;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    /**
     * 保存文章（同步版本）
     */
    @LoginCheck(1)
    @PostMapping("/saveArticle")
    public PoetryResult saveArticle(@Validated @RequestBody ArticleVO articleVO,
                                   @RequestParam(value = "skipAiTranslation", defaultValue = "false") boolean skipAiTranslation,
                                   @RequestParam(value = "pendingTranslationTitle", required = false) String pendingTranslationTitle,
                                   @RequestParam(value = "pendingTranslationContent", required = false) String pendingTranslationContent,
                                   @RequestParam(value = "pendingTranslationLanguage", required = false) String pendingTranslationLanguage) {
        // 防止空指针异常，验证输入
        if (articleVO == null) {
            return PoetryResult.fail("文章内容不能为空");
        }
        
        try {
            long step1Time = System.currentTimeMillis();
            
            // 确保用户ID不为空
            if (articleVO.getUserId() == null) {
                // 尝试获取当前用户ID
                Integer currentUserId = PoetryUtil.getUserId();
                if (currentUserId == null) {
                    // 使用PoetryUtil获取当前用户（已集成Redis缓存）
                    User user = PoetryUtil.getCurrentUser();
                    if (user != null) {
                        currentUserId = user.getId();
                    }
                }
                
                if (currentUserId == null) {
                    return PoetryResult.fail("无法获取当前用户信息，请重新登录后再试");
                }
                articleVO.setUserId(currentUserId);
            }
            
            // 使用Redis缓存清理替换PoetryCache
            if (articleVO.getUserId() != null) {
                // 清理用户文章列表缓存，使用统一的缓存键常量
                String userArticleKey = CacheConstants.buildUserArticleListKey(articleVO.getUserId());
                cacheService.deleteKey(userArticleKey);
                log.debug("清理用户文章列表缓存，用户ID: {}", articleVO.getUserId());
            }
            // 清理文章相关缓存
            cacheService.evictSortArticleList();
            
            // 保存文章
            PoetryResult result = articleService.saveArticle(articleVO);
            
            // 如果保存成功并且文章有ID，执行后续任务
            if (result.getCode() == 200 && articleVO.getId() != null) {
                final Integer articleId = articleVO.getId();
                final Integer sortId = articleVO.getSortId();
                
                // 准备暂存翻译数据
                Map<String, String> pendingTranslation = null;
                if (pendingTranslationTitle != null && pendingTranslationContent != null && pendingTranslationLanguage != null) {
                    pendingTranslation = new HashMap<>();
                    pendingTranslation.put("title", pendingTranslationTitle);
                    pendingTranslation.put("content", pendingTranslationContent);
                    pendingTranslation.put("language", pendingTranslationLanguage);
                }

                // 异步执行翻译，避免阻塞用户操作（翻译完成后内部会触发预渲染）
                final Map<String, String> finalPendingTranslation = pendingTranslation;
                new Thread(() -> {
                    try {
                        translationService.translateAndSaveArticle(articleId, skipAiTranslation, finalPendingTranslation);
                    } catch (Exception e) {
                        // 翻译失败不影响保存结果
                        log.error("翻译文章失败: " + e.getMessage(), e);
                    }
                }).start();
                
                // 如果文章可见，异步更新sitemap（不管是否推送搜索引擎）
                if (Boolean.TRUE.equals(articleVO.getViewStatus())) {
                    log.info("文章ID {} 可见，开始异步更新sitemap", articleId);
                    
                    // 异步更新sitemap
                    new Thread(() -> {
                        try {
                            // 调用Python服务更新sitemap
                            Map<String, Object> sitemapData = new HashMap<>();
                            sitemapData.put("articleId", articleId);
                            sitemapData.put("action", "add_or_update");
                            
                            HttpHeaders headers = new HttpHeaders();
                            headers.setContentType(MediaType.APPLICATION_JSON);
                            headers.set("X-Internal-Service", "poetize-java");
                            headers.set("User-Agent", "poetize-java/1.0.0");
                            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(sitemapData, headers);
                            
                            // 调用专门的sitemap更新接口
                            String pythonServerUrl = System.getenv().getOrDefault("PYTHON_SERVICE_URL", "http://localhost:5000");
                            String sitemapApiUrl = pythonServerUrl + "/python/seo/updateArticleSitemap";
                            
                            try {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> response = restTemplate.postForObject(
                                    sitemapApiUrl, 
                                    requestEntity, 
                                    Map.class
                                );
                                if (response != null && "200".equals(String.valueOf(response.get("code")))) {
                                    log.info("文章ID {} sitemap更新成功", articleId);
                                } else {
                                    log.warn("文章ID {} sitemap更新响应异常: {}", articleId, response);
                                }
                            } catch (Exception apiException) {
                                log.error("调用sitemap更新API失败，文章ID: " + articleId + ", 错误: " + apiException.getMessage(), apiException);
                            }
                        } catch (Exception e) {
                            log.error("更新sitemap失败，但不影响文章保存，文章ID: " + articleId, e);
                        }
                    }).start();
                }
                
                // 如果需要推送至搜索引擎且文章可见，异步处理
                if (Boolean.TRUE.equals(articleVO.getSubmitToSearchEngine()) && Boolean.TRUE.equals(articleVO.getViewStatus())) {
                    log.info("文章ID {} 标记为需要推送至搜索引擎，开始异步处理", articleId);
                    
                    // 异步执行SEO推送，避免阻塞用户操作
                    new Thread(() -> {
                        try {
                            boolean seoResult = seoService.submitToSearchEngines(articleId);
                            log.info("文章ID {} 搜索引擎推送完成，结果: {}", articleId, seoResult ? "成功" : "失败");
                        } catch (Exception e) {
                            log.error("搜索引擎推送失败，但不影响文章保存，文章ID: " + articleId, e);
                        }
                    }).start();
                } else {
                    log.info("文章ID {} 未标记为需要推送至搜索引擎或文章不可见", articleId);
                }
            }
            
            return result;
        } catch (Exception e) {
            return PoetryResult.fail("保存文章失败: " + e.getMessage());
        }
    }

    /**
     * 异步保存文章（快速响应版本）
     */
    @LoginCheck(1)
    @PostMapping("/saveArticleAsync")
    public PoetryResult<String> saveArticleAsync(@Validated @RequestBody ArticleVO articleVO,
                                                @RequestParam(value = "skipAiTranslation", defaultValue = "false") boolean skipAiTranslation,
                                                @RequestParam(value = "pendingTranslationTitle", required = false) String pendingTranslationTitle,
                                                @RequestParam(value = "pendingTranslationContent", required = false) String pendingTranslationContent,
                                                @RequestParam(value = "pendingTranslationLanguage", required = false) String pendingTranslationLanguage) {
        // 防止空指针异常，验证输入
        if (articleVO == null) {
            return PoetryResult.fail("文章内容不能为空");
        }
        
        try {
            // 确保用户ID不为空
            if (articleVO.getUserId() == null) {
                Integer currentUserId = PoetryUtil.getUserId();
                if (currentUserId == null) {
                    // 使用PoetryUtil获取当前用户（已集成Redis缓存）
                    User user = PoetryUtil.getCurrentUser();
                    if (user != null) {
                        currentUserId = user.getId();
                    }
                }
                
                if (currentUserId == null) {
                    return PoetryResult.fail("无法获取当前用户信息，请重新登录后再试");
                }
                articleVO.setUserId(currentUserId);
            }
            
            // 准备暂存翻译数据
            Map<String, String> pendingTranslation = null;
            if (pendingTranslationTitle != null && pendingTranslationContent != null && pendingTranslationLanguage != null) {
                pendingTranslation = new HashMap<>();
                pendingTranslation.put("title", pendingTranslationTitle);
                pendingTranslation.put("content", pendingTranslationContent);
                pendingTranslation.put("language", pendingTranslationLanguage);
            }

            // 调用异步保存服务
            PoetryResult<String> result = articleService.saveArticleAsync(articleVO, skipAiTranslation, pendingTranslation);
            
            // 使用Redis缓存清理替换PoetryCache
            if (articleVO.getUserId() != null) {
                // 清理用户文章列表缓存
                String userArticleKey = CacheConstants.buildUserArticleListKey(articleVO.getUserId());
                cacheService.deleteKey(userArticleKey);
            }
            // 清理文章相关缓存
            cacheService.evictSortArticleList();
            
            return result;
        } catch (Exception e) {
            return PoetryResult.fail("启动异步保存失败: " + e.getMessage());
        }
    }

    /**
     * 查询文章保存状态
     */
    @LoginCheck(1)
    @GetMapping("/getArticleSaveStatus")
    public PoetryResult<ArticleServiceImpl.ArticleSaveStatus> getArticleSaveStatus(@RequestParam("taskId") String taskId) {
        if (!StringUtils.hasText(taskId)) {
            return PoetryResult.fail("任务ID不能为空");
        }
        
        try {
            // 轮询期间的日志降级为DEBUG，减少噪音
            log.debug("【Controller】收到状态查询请求，任务ID: {}", taskId);
            PoetryResult<ArticleServiceImpl.ArticleSaveStatus> result = articleService.getArticleSaveStatus(taskId);
            log.debug("【Controller】状态查询结果: {}", result.getCode() == 200 ? "成功" : "失败");
            return result;
        } catch (Exception e) {
            log.error("【Controller】查询保存状态异常: {}", e.getMessage(), e);
            return PoetryResult.fail("查询保存状态失败: " + e.getMessage());
        }
    }
    


    /**
     * 删除文章
     */
    @GetMapping("/deleteArticle")
    @LoginCheck(1)
    public PoetryResult deleteArticle(@RequestParam("id") Integer id) {
        // 在删除前先获取文章信息，以便获取分类ID用于预渲染
        Integer sortId = null;
        try {
            PoetryResult<ArticleVO> articleResult = articleService.getArticleById(id, null);
            if (articleResult.getCode() == 200 && articleResult.getData() != null) {
                sortId = articleResult.getData().getSortId();
                log.info("删除文章前获取分类ID: 文章ID={}, 分类ID={}", id, sortId);
            }
        } catch (Exception e) {
            log.warn("删除文章前获取分类ID失败，将影响分类页面预渲染: 文章ID={}, 错误={}", id, e.getMessage());
        }
        
        // 使用Redis缓存清理替换PoetryCache
        Integer userId = PoetryUtil.getUserId();
        if (userId != null) {
            String userArticleKey = CacheConstants.buildUserArticleListKey(userId);
            cacheService.deleteKey(userArticleKey);
        }
        // 清理文章相关缓存
        cacheService.evictSortArticleList();
        
        // 删除文章翻译（仅删除，不重新翻译）
        try {
            translationService.deleteArticleTranslation(id);
        } catch (Exception e) {
            log.error("删除文章翻译失败", e);
        }

        PoetryResult result = articleService.deleteArticle(id);

        if (result.getCode() == 200) {
            // 发布文章删除事件，触发预渲染清理（在事务提交后执行）
            // 传递正确的分类ID，确保分类页面也会被重新渲染
            eventPublisher.publishEvent(new ArticleSavedEvent(id, sortId, false, "DELETE"));
            
            // 异步删除sitemap条目
            final Integer articleId = id;
            new Thread(() -> {
                try {
                    // 调用Python服务删除sitemap条目
                    Map<String, Object> sitemapData = new HashMap<>();
                    sitemapData.put("articleId", articleId);
                    sitemapData.put("action", "remove");
                    
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.set("X-Internal-Service", "poetize-java");
                    headers.set("User-Agent", "poetize-java/1.0.0");
                    HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(sitemapData, headers);
                    
                    // 调用专门的sitemap更新接口
                    String pythonServerUrl = System.getenv().getOrDefault("PYTHON_SERVICE_URL", "http://localhost:5000");
                    String sitemapApiUrl = pythonServerUrl + "/python/seo/updateArticleSitemap";
                    
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> response = restTemplate.postForObject(
                            sitemapApiUrl, 
                            requestEntity, 
                            Map.class
                        );
                        if (response != null && "200".equals(String.valueOf(response.get("code")))) {
                            log.info("文章ID {} sitemap删除成功", articleId);
                        } else {
                            log.warn("文章ID {} sitemap删除响应异常: {}", articleId, response);
                        }
                    } catch (Exception apiException) {
                        log.error("调用sitemap删除API失败，文章ID: " + articleId + ", 错误: " + apiException.getMessage(), apiException);
                    }
                } catch (Exception e) {
                    log.error("删除sitemap条目失败，但不影响文章删除，文章ID: " + articleId, e);
                }
            }).start();
        }

        return result;
    }


    /**
     * 更新文章
     */
    @LoginCheck(1)
    @PostMapping("/updateArticle")
    public PoetryResult updateArticle(@Validated @RequestBody ArticleVO articleVO,
                                     @RequestParam(value = "skipAiTranslation", defaultValue = "false") boolean skipAiTranslation,
                                     @RequestParam(value = "pendingTranslationTitle", required = false) String pendingTranslationTitle,
                                     @RequestParam(value = "pendingTranslationContent", required = false) String pendingTranslationContent,
                                     @RequestParam(value = "pendingTranslationLanguage", required = false) String pendingTranslationLanguage) {
        // 使用Redis缓存清理替换PoetryCache
        Integer userId = PoetryUtil.getUserId();
        if (userId != null) {
            String userArticleKey = CacheConstants.buildUserArticleListKey(userId);
            cacheService.deleteKey(userArticleKey);
        }
        // 清理文章相关缓存
        cacheService.evictSortArticleList();
        
        PoetryResult result = articleService.updateArticle(articleVO);
        
        // 更新文章成功后执行后续任务
        if (result.getCode() == 200 && articleVO.getId() != null) {
            final Integer articleId = articleVO.getId();
            final Integer sortId = articleVO.getSortId();
            
            // 准备暂存翻译数据
            Map<String, String> pendingTranslation = null;
            if (pendingTranslationTitle != null && pendingTranslationContent != null && pendingTranslationLanguage != null) {
                pendingTranslation = new HashMap<>();
                pendingTranslation.put("title", pendingTranslationTitle);
                pendingTranslation.put("content", pendingTranslationContent);
                pendingTranslation.put("language", pendingTranslationLanguage);
            }

            // 异步执行翻译，避免阻塞用户操作（翻译完成后内部会触发预渲染）
            final Map<String, String> finalPendingTranslation = pendingTranslation;
            new Thread(() -> {
                try {
                    translationService.translateAndSaveArticle(articleId, skipAiTranslation, finalPendingTranslation);
                } catch (Exception e) {
                    log.error("文章更新后自动翻译失败", e);
                }
            }).start();
            
            // 更新文章时不更新sitemap，因为创建时已经更新过了
            // 只有当文章变为不可见时才从sitemap中删除
            if (!Boolean.TRUE.equals(articleVO.getViewStatus())) {
                log.info("文章ID {} 变为不可见，开始异步删除sitemap条目", articleId);
                
                // 异步删除sitemap条目
                new Thread(() -> {
                    try {
                        // 调用Python服务删除sitemap条目
                        Map<String, Object> sitemapData = new HashMap<>();
                        sitemapData.put("articleId", articleId);
                        sitemapData.put("action", "remove");
                        
                        HttpHeaders headers = new HttpHeaders();
                        headers.setContentType(MediaType.APPLICATION_JSON);
                        headers.set("X-Internal-Service", "poetize-java");
                        headers.set("User-Agent", "poetize-java/1.0.0");
                        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(sitemapData, headers);
                        
                        // 调用专门的sitemap更新接口
                        String pythonServerUrl = System.getenv().getOrDefault("PYTHON_SERVICE_URL", "http://localhost:5000");
                        String sitemapApiUrl = pythonServerUrl + "/python/seo/updateArticleSitemap";
                        
                        try {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> response = restTemplate.postForObject(
                                sitemapApiUrl, 
                                requestEntity, 
                                Map.class
                            );
                            if (response != null && "200".equals(String.valueOf(response.get("code")))) {
                                log.info("文章ID {} sitemap删除成功", articleId);
                            } else {
                                log.warn("文章ID {} sitemap删除响应异常: {}", articleId, response);
                            }
                        } catch (Exception apiException) {
                            log.error("调用sitemap删除API失败，文章ID: " + articleId + ", 错误: " + apiException.getMessage(), apiException);
                        }
                    } catch (Exception e) {
                        log.error("删除sitemap条目失败，但不影响文章更新，文章ID: " + articleId, e);
                    }
                }).start();
            } else {
                log.info("文章ID {} 保持可见状态，无需更新sitemap（创建时已更新）", articleId);
            }
            
            // 如果需要推送至搜索引擎且文章可见，异步处理
            if (Boolean.TRUE.equals(articleVO.getSubmitToSearchEngine()) && Boolean.TRUE.equals(articleVO.getViewStatus())) {
                log.info("更新文章ID {} 标记为需要推送至搜索引擎，开始异步处理", articleId);
                
                // 异步执行SEO推送，避免阻塞用户操作
                new Thread(() -> {
                    try {
                        boolean seoResult = seoService.submitToSearchEngines(articleId);
                        log.info("更新文章ID {} 搜索引擎推送完成，结果: {}", articleId, seoResult ? "成功" : "失败");
                    } catch (Exception e) {
                        log.error("搜索引擎推送失败，但不影响文章更新，文章ID: " + articleId, e);
                    }
                }).start();
            } else {
                log.info("更新文章ID {} 未标记为需要推送至搜索引擎或文章不可见", articleId);
            }
        }
        
        return result;
    }


    /**
     * 查询文章List
     */
    @PostMapping("/listArticle")
    public PoetryResult<Page> listArticle(@RequestBody BaseRequestVO baseRequestVO) {
        return articleService.listArticle(baseRequestVO);
    }

    /**
     * 查询分类文章List
     */
    @GetMapping("/listSortArticle")
    public PoetryResult<Map<Integer, List<ArticleVO>>> listSortArticle() {
        return articleService.listSortArticle();
    }

    /**
     * 查询文章
     */
    @GetMapping("/getArticleById")
    public PoetryResult<ArticleVO> getArticleById(@RequestParam("id") Integer id, @RequestParam(value = "password", required = false) String password) {
        return articleService.getArticleById(id, password);
    }

    /**
     * 查询文章(不增加浏览量)
     * 用于元数据获取、SEO等不需要增加访问量的场景
     */
    @GetMapping("/getArticleByIdNoCount")
    public PoetryResult<ArticleVO> getArticleByIdNoCount(@RequestParam("id") Integer id, @RequestParam(value = "password", required = false) String password) {
        return ((ArticleServiceImpl)articleService).getArticleById(id, password, false);
    }

    /**
     * 获取热门文章列表（智能热度算法排序）
     * 综合考虑浏览量、点赞数、评论数、发布时间、互动率等多个因素
     */
    @GetMapping("/getArticlesByLikesTop")
    public PoetryResult<List<ArticleVO>> getArticlesByLikesTop() {
        return articleService.getArticlesByLikesTop();
    }

    /**
     * 获取热门文章列表（智能热度算法排序）
     * 综合考虑浏览量、点赞数、评论数、发布时间、互动率等多个因素
     * 推荐使用此端点，命名更准确
     */
    @GetMapping("/getHotArticles")
    public PoetryResult<List<ArticleVO>> getHotArticles() {
        return articleService.getArticlesByLikesTop();
    }

    /**
     * 接收SEO推送结果并发送邮件通知
     * 此接口由Python SEO模块调用
     */
    @PostMapping("/notifySeoResult")
    public PoetryResult notifySeoResult(@RequestBody Map<String, Object> notificationData) {
        try {
            log.info("收到SEO推送结果通知: {}", notificationData);
            
            // 1. 提取所需数据
            Integer articleId = null;
            if (notificationData.containsKey("articleId") && notificationData.get("articleId") != null) {
                articleId = Integer.parseInt(notificationData.get("articleId").toString());
            }
            
            String title = notificationData.containsKey("title") ? notificationData.get("title").toString() : "未知文章";
            String url = notificationData.containsKey("url") ? notificationData.get("url").toString() : "";
            boolean success = notificationData.containsKey("success") && Boolean.parseBoolean(notificationData.get("success").toString());
            String timestamp = notificationData.containsKey("timestamp") ? notificationData.get("timestamp").toString() : 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            
            // 检查是否提供了通知邮箱
            String notificationEmail = notificationData.containsKey("notificationEmail") ? 
                notificationData.get("notificationEmail").toString() : null;
            
            @SuppressWarnings("unchecked")
            Map<String, Object> results = notificationData.containsKey("results") ? 
                (Map<String, Object>) notificationData.get("results") : new HashMap<>();
            
            // 确定收件人列表
            List<String> recipients = new ArrayList<>();
            
            // 如果提供了通知邮箱，优先使用
            if (notificationEmail != null && !notificationEmail.isEmpty()) {
                recipients.add(notificationEmail);
                log.info("使用SEO配置中指定的通知邮箱: {}", notificationEmail);
            } 
            // 如果没有提供通知邮箱，尝试使用文章作者的邮箱
            else if (articleId != null) {
                // 查询文章信息以获取作者ID
                ArticleVO article = articleService.getArticleById(articleId, null).getData();
                if (article == null) {
                    log.warn("未找到文章信息，文章ID: {}", articleId);
                    return PoetryResult.success("已接收SEO推送结果，但未找到文章信息");
                }
                
                Integer authorId = article.getUserId();
                if (authorId == null) {
                    log.warn("无法确定文章作者，文章ID: {}", articleId);
                    return PoetryResult.success("已接收SEO推送结果，但无法确定文章作者");
                }
                
                // 查询作者信息
                User author = userService.getById(authorId);
                if (author == null) {
                    log.warn("未找到文章作者信息，作者ID: {}", authorId);
                    return PoetryResult.success("已接收SEO推送结果，但未找到作者信息");
                }
                
                // 如果作者有邮箱，添加到收件人列表
                if (author.getEmail() != null && !author.getEmail().isEmpty()) {
                    recipients.add(author.getEmail());
                    log.info("使用文章作者邮箱: {}", author.getEmail());
                }
            }
            
            // 如果没有收件人，不发送邮件
            if (recipients.isEmpty()) {
                log.info("没有有效的收件人，不发送SEO推送结果通知");
                return PoetryResult.success("已接收SEO推送结果，但无法发送通知");
            }
            
            // 4. 构建HTML邮件内容
            StringBuilder emailContent = new StringBuilder();
            emailContent.append("<html><head><style>");
            emailContent.append("body{font-family:Arial,sans-serif;line-height:1.6;color:#333;}");
            emailContent.append("h2{color:#006699;}");
            emailContent.append("table{border-collapse:collapse;width:100%;margin:20px 0;}");
            emailContent.append("th,td{border:1px solid #ddd;padding:8px;text-align:left;}");
            emailContent.append("th{background-color:#f2f2f2;}");
            emailContent.append(".success{color:green;font-weight:bold;}");
            emailContent.append(".failure{color:red;}");
            emailContent.append("</style></head><body>");
            
            emailContent.append("<h2>搜索引擎推送结果通知</h2>");
            emailContent.append("<p>您的文章 <strong>\"").append(title).append("\"</strong> 已提交到搜索引擎。</p>");
            emailContent.append("<p>文章链接: <a href=\"").append(url).append("\">").append(url).append("</a></p>");
            emailContent.append("<p>推送时间: ").append(timestamp).append("</p>");
            
            emailContent.append("<h3>推送结果详情:</h3>");
            emailContent.append("<table><tr><th>搜索引擎</th><th>状态</th><th>详情</th></tr>");
            
            // 添加各搜索引擎结果
            if (results.isEmpty()) {
                emailContent.append("<tr><td colspan=\"3\">无推送结果数据</td></tr>");
            } else {
                for (Map.Entry<String, Object> entry : results.entrySet()) {
                    String engine = entry.getKey();
                    String engineName = getSearchEngineName(engine);
                    
                    if (entry.getValue() instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> resultDetails = (Map<String, Object>) entry.getValue();
                        boolean engineSuccess = resultDetails.containsKey("success") && 
                            Boolean.parseBoolean(resultDetails.get("success").toString());
                        
                        String statusClass = engineSuccess ? "success" : "failure";
                        String status = engineSuccess ? "成功" : "失败";
                        
                        String detail = "";
                        if (resultDetails.containsKey("result")) {
                            detail = resultDetails.get("result").toString();
                        } else if (resultDetails.containsKey("message")) {
                            detail = resultDetails.get("message").toString();
                        }
                        
                        emailContent.append("<tr>");
                        emailContent.append("<td>").append(engineName).append("</td>");
                        emailContent.append("<td class=\"").append(statusClass).append("\">").append(status).append("</td>");
                        emailContent.append("<td>").append(detail).append("</td>");
                        emailContent.append("</tr>");
                    }
                }
            }
            
            emailContent.append("</table>");
            
            // 添加推送总结
            if (success) {
                emailContent.append("<p class=\"success\">推送总结: 至少有一个搜索引擎推送成功。</p>");
            } else {
                emailContent.append("<p class=\"failure\">推送总结: 所有搜索引擎推送均失败。</p>");
            }
            
            emailContent.append("<p>此邮件由系统自动发送，请勿回复。</p>");
            emailContent.append("</body></html>");
            
            // 5. 发送邮件通知
            String subject = (success ? "SEO推送成功: " : "SEO推送失败: ") + title;
            boolean mailSent = mailService.sendMail(recipients, subject, emailContent.toString(), true, null);
            
            if (mailSent) {
                log.info("SEO推送结果通知邮件发送成功，收件人: {}", recipients);
                return PoetryResult.success("SEO推送结果通知已发送");
            } else {
                log.warn("SEO推送结果通知邮件发送失败，收件人: {}", recipients);
                return PoetryResult.fail("SEO推送结果通知邮件发送失败");
            }
        } catch (Exception e) {
            log.error("处理SEO推送结果通知出错", e);
            return PoetryResult.fail("处理SEO推送结果通知出错: " + e.getMessage());
        }
    }
    
    /**
     * 根据搜索引擎代码获取显示名称
     */
    private String getSearchEngineName(String engine) {
        String engineLower = engine.toLowerCase();
        if ("baidu".equals(engineLower)) {
            return "百度搜索";
        } else if ("google".equals(engineLower)) {
            return "谷歌搜索";
        } else if ("bing".equals(engineLower)) {
            return "必应搜索";
        } else if ("yandex".equals(engineLower)) {
            return "Yandex搜索";
        } else if ("sogou".equals(engineLower)) {
            return "搜狗搜索";
        } else if ("so".equals(engineLower)) {
            return "360搜索";
        } else if ("shenma".equals(engineLower)) {
            return "神马搜索";
        } else if ("yahoo".equals(engineLower)) {
            return "雅虎搜索";
        } else {
            return engine;
        }
    }

    /**
     * 获取文章翻译
     */
    @GetMapping("/getTranslation")
    public PoetryResult<Map<String, String>> getTranslation(@RequestParam("id") Integer id,
                                     @RequestParam(value = "language", defaultValue = "en") String language) {
        // 检查参数
        if (id == null) {
            return PoetryResult.fail("文章ID不能为空");
        }

        if (!StringUtils.hasText(language)) {
            return PoetryResult.fail("翻译语言不能为空");
        }

        try {
            // 获取文章翻译
            Map<String, String> translationResult = translationService.getArticleTranslation(id, language);
            return PoetryResult.success(translationResult);
        } catch (Exception e) {
            log.error("获取文章翻译失败", e);
            return PoetryResult.fail("获取翻译失败：" + e.getMessage());
        }
    }

    /**
     * 获取文章所有可用的翻译语言
     */
    @GetMapping("/getAvailableLanguages")
    public PoetryResult<List<String>> getAvailableLanguages(@RequestParam("id") Integer id) {
        // 检查参数
        if (id == null) {
            return PoetryResult.fail("文章ID不能为空");
        }

        try {
            // 获取文章所有可用的翻译语言
            List<String> availableLanguages = translationService.getArticleAvailableLanguages(id);
            return PoetryResult.success(availableLanguages);
        } catch (Exception e) {
            log.error("获取文章可用翻译语言失败", e);
            return PoetryResult.fail("获取可用翻译语言失败：" + e.getMessage());
        }
    }

    /**
     * 获取翻译语言配置
     */
    @GetMapping("/getTranslationConfig")
    public PoetryResult<Map<String, String>> getTranslationConfig() {
        try {
            // 获取翻译语言配置
            Map<String, String> config = translationService.getTranslationLanguageConfig();
            return PoetryResult.success(config);
        } catch (Exception e) {
            log.error("获取翻译语言配置失败", e);
            return PoetryResult.fail("获取翻译语言配置失败：" + e.getMessage());
        }
    }

    /**
     * 手动保存文章翻译
     */
    @PostMapping("/saveManualTranslation")
    public PoetryResult<String> saveManualTranslation(@RequestParam("id") Integer id,
                                                     @RequestParam("targetLanguage") String targetLanguage,
                                                     @RequestParam("translatedTitle") String translatedTitle,
                                                     @RequestParam("translatedContent") String translatedContent) {
        // 检查参数
        if (id == null) {
            return PoetryResult.fail("文章ID不能为空");
        }

        if (!StringUtils.hasText(targetLanguage)) {
            return PoetryResult.fail("目标语言不能为空");
        }

        if (!StringUtils.hasText(translatedTitle)) {
            return PoetryResult.fail("翻译标题不能为空");
        }

        if (!StringUtils.hasText(translatedContent)) {
            return PoetryResult.fail("翻译内容不能为空");
        }

        try {
            // 保存手动翻译
            Map<String, Object> result = translationService.saveManualTranslation(id, targetLanguage,
                                                                                 translatedTitle, translatedContent);

            if ((Boolean) result.get("success")) {
                return PoetryResult.success((String) result.get("message"));
            } else {
                return PoetryResult.fail((String) result.get("message"));
            }
        } catch (Exception e) {
            log.error("保存手动翻译失败", e);
            return PoetryResult.fail("保存翻译失败：" + e.getMessage());
        }
    }

    /**
     * 生成文章摘要 - 供Python端调用
     */
    @PostMapping("/generateSummary")
    public PoetryResult<String> generateSummary(@RequestBody Map<String, Object> request) {
        try {
            String content = (String) request.get("content");
            Integer maxLength = request.get("maxLength") != null ? 
                Integer.parseInt(request.get("maxLength").toString()) : 150;
            
            return articleService.generateSummary(content, maxLength);
        } catch (Exception e) {
            log.error("摘要生成API调用失败", e);
            return PoetryResult.fail("摘要生成失败: " + e.getMessage());
        }
    }

    /**
     * 异步更新文章（快速响应版本）
     */
    @LoginCheck(1)
    @PostMapping("/updateArticleAsync")
    public PoetryResult<String> updateArticleAsync(@Validated @RequestBody ArticleVO articleVO,
                                                  @RequestParam(value = "skipAiTranslation", defaultValue = "false") boolean skipAiTranslation,
                                                  @RequestParam(value = "pendingTranslationTitle", required = false) String pendingTranslationTitle,
                                                  @RequestParam(value = "pendingTranslationContent", required = false) String pendingTranslationContent,
                                                  @RequestParam(value = "pendingTranslationLanguage", required = false) String pendingTranslationLanguage) {
        // 防止空指针异常，验证输入
        if (articleVO == null) {
            return PoetryResult.fail("文章内容不能为空");
        }
        
        if (articleVO.getId() == null) {
            return PoetryResult.fail("文章ID不能为空");
        }
        
        try {
            // 准备暂存翻译数据
            Map<String, String> pendingTranslation = null;
            if (pendingTranslationTitle != null && pendingTranslationContent != null && pendingTranslationLanguage != null) {
                pendingTranslation = new HashMap<>();
                pendingTranslation.put("title", pendingTranslationTitle);
                pendingTranslation.put("content", pendingTranslationContent);
                pendingTranslation.put("language", pendingTranslationLanguage);
            }

            // 调用异步更新服务
            PoetryResult<String> result = articleService.updateArticleAsync(articleVO, skipAiTranslation, pendingTranslation);
            
            // 使用Redis缓存清理替换PoetryCache
            if (articleVO.getUserId() != null) {
                String userArticleKey = CacheConstants.buildUserArticleListKey(articleVO.getUserId());
                cacheService.deleteKey(userArticleKey);
            }
            // 清理文章相关缓存
            cacheService.evictSortArticleList();
            
            return result;
        } catch (Exception e) {
            return PoetryResult.fail("启动异步更新失败: " + e.getMessage());
        }
    }
}

