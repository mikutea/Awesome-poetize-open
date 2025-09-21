package com.ld.poetry.controller;

import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.entity.*;
import com.ld.poetry.service.ArticleService;
import com.ld.poetry.service.SummaryService;
import com.ld.poetry.service.TranslationService;
import com.ld.poetry.utils.PoetryUtil;
import com.ld.poetry.vo.ArticleVO;
import com.ld.poetry.vo.BaseRequestVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * <p>
 * 后台文章 前端控制器
 * </p>
 *
 * @author sara
 * @since 2021-08-13
 */
@RestController
@RequestMapping("/admin")
@Slf4j
public class AdminArticleController {

    @Autowired
    private ArticleService articleService;
    
    @Autowired
    private SummaryService summaryService;
    
    @Autowired
    private TranslationService translationService;
    
    @Autowired
    private RestTemplate restTemplate;

    /**
     * 用户查询文章
     */
    @PostMapping("/article/user/list")
    @LoginCheck(1)
    public PoetryResult<Page> listUserArticle(@RequestBody BaseRequestVO baseRequestVO) {
        return articleService.listAdminArticle(baseRequestVO, false);
    }

    /**
     * Boss查询文章
     */
    @PostMapping("/article/boss/list")
    @LoginCheck(1)
    public PoetryResult<Page> listBossArticle(@RequestBody BaseRequestVO baseRequestVO) {
        return articleService.listAdminArticle(baseRequestVO, true);
    }

    /**
     * 修改文章状态
     */
    @GetMapping("/article/changeArticleStatus")
    @LoginCheck(1)
    public PoetryResult changeArticleStatus(@RequestParam("articleId") Integer articleId,
                                            @RequestParam(value = "viewStatus", required = false) Boolean viewStatus,
                                            @RequestParam(value = "commentStatus", required = false) Boolean commentStatus,
                                            @RequestParam(value = "recommendStatus", required = false) Boolean recommendStatus) {
        LambdaUpdateChainWrapper<Article> updateChainWrapper = articleService.lambdaUpdate()
                .eq(Article::getId, articleId)
                .eq(Article::getUserId, PoetryUtil.getUserId());
        if (viewStatus != null) {
            updateChainWrapper.set(Article::getViewStatus, viewStatus);
        }
        if (commentStatus != null) {
            updateChainWrapper.set(Article::getCommentStatus, commentStatus);
        }
        if (recommendStatus != null) {
            updateChainWrapper.set(Article::getRecommendStatus, recommendStatus);
        }
        updateChainWrapper.update();
        
        // 如果修改了文章可见性，需要更新sitemap
        if (viewStatus != null) {
            final Integer finalArticleId = articleId;
            final Boolean finalViewStatus = viewStatus;
            
            // 异步更新sitemap
            new Thread(() -> {
                try {
                    // 调用Python服务更新sitemap
                    Map<String, Object> sitemapData = new HashMap<>();
                    sitemapData.put("articleId", finalArticleId);
                    sitemapData.put("action", finalViewStatus ? "add_or_update" : "remove");
                    
                    HttpHeaders headers = new HttpHeaders();
                    headers.setContentType(MediaType.APPLICATION_JSON);
                    headers.set("X-Internal-Service", "poetize-java");
                    headers.set("User-Agent", "poetize-java/1.0.0");
                    HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(sitemapData, headers);
                    
                    // 调用专门的sitemap更新接口
                    String pythonServerUrl = System.getenv().getOrDefault("PYTHON_SERVICE_URL", "http://localhost:5000");
                    String sitemapApiUrl = pythonServerUrl + "/seo/updateArticleSitemap";
                    
                    try {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> response = restTemplate.postForObject(
                            sitemapApiUrl, 
                            requestEntity, 
                            Map.class
                        );
                        if (response != null && "200".equals(String.valueOf(response.get("code")))) {
                            log.info("文章ID {} sitemap{}成功", finalArticleId, finalViewStatus ? "更新" : "删除");
                        } else {
                            log.warn("文章ID {} sitemap{}响应异常: {}", finalArticleId, finalViewStatus ? "更新" : "删除", response);
                        }
                    } catch (Exception apiException) {
                        log.error("调用sitemap{}API失败，文章ID: {}, 错误: {}", finalViewStatus ? "更新" : "删除", finalArticleId, apiException.getMessage(), apiException);
                    }
                } catch (Exception e) {
                    log.error("{}sitemap失败，但不影响状态修改，文章ID: {}", finalViewStatus ? "更新" : "删除", finalArticleId, e);
                }
            }).start();
        }
        
        return PoetryResult.success();
    }

    /**
     * 查询文章
     */
    @GetMapping("/article/getArticleById")
    @LoginCheck(1)
    public PoetryResult<ArticleVO> getArticleByIdForUser(@RequestParam("id") Integer id) {
        return articleService.getArticleByIdForUser(id);
    }
    
    /**
     * 手动生成文章摘要
     */
    @PostMapping("/article/generateSummary")
    @LoginCheck(1)
    public PoetryResult generateSummary(@RequestParam Integer articleId) {
        try {
            summaryService.generateAndSaveSummaryAsync(articleId);
            return PoetryResult.success("摘要生成任务已启动");
        } catch (Exception e) {
            log.error("启动摘要生成任务失败", e);
            return PoetryResult.fail("启动摘要生成任务失败：" + e.getMessage());
        }
    }
    
    /**
     * 批量生成摘要（为所有没有摘要的文章生成摘要）
     */
    @PostMapping("/article/generateAllSummaries") 
    @LoginCheck(0)  // Boss权限
    public PoetryResult generateAllSummaries() {
        try {
            // 查找所有没有摘要的文章
            java.util.List<Article> articlesWithoutSummary = articleService.lambdaQuery()
                .select(Article::getId)
                .and(wrapper -> wrapper.isNull(Article::getSummary).or().eq(Article::getSummary, ""))
                .list();
                
            if (articlesWithoutSummary.isEmpty()) {
                return PoetryResult.success("所有文章都已有摘要");
            }
            
            // 异步生成所有摘要
            for (Article article : articlesWithoutSummary) {
                summaryService.generateAndSaveSummaryAsync(article.getId());
            }
            
            return PoetryResult.success("已启动" + articlesWithoutSummary.size() + "篇文章的摘要生成任务");
        } catch (Exception e) {
            log.error("批量生成摘要失败", e);
            return PoetryResult.fail("批量生成摘要失败：" + e.getMessage());
        }
    }
    
    /**
     * 获取文章的所有翻译语言
     */
    @GetMapping("/article/getAvailableLanguages")
    @LoginCheck(1)
    public PoetryResult getArticleAvailableLanguages(@RequestParam("articleId") Integer articleId) {
        try {
            java.util.List<String> languages = translationService.getArticleAvailableLanguages(articleId);
            return PoetryResult.success(languages);
        } catch (Exception e) {
            log.error("获取文章可用翻译语言失败", e);
            return PoetryResult.fail("获取文章可用翻译语言失败：" + e.getMessage());
        }
    }
    
    /**
     * 删除文章的特定语言翻译
     */
    @PostMapping("/article/deleteTranslation")
    @LoginCheck(1)
    public PoetryResult deleteArticleTranslation(@RequestBody Map<String, Object> requestData) {
        Integer articleId = (Integer) requestData.get("articleId");
        String language = (String) requestData.get("language");
        
        if (articleId == null || language == null) {
            return PoetryResult.fail("参数不完整");
        }
        try {
            // 验证文章所有权
            Article article = articleService.getById(articleId);
            if (article == null) {
                return PoetryResult.fail("文章不存在");
            }
            
            // 检查用户权限（只能删除自己的文章翻译，或者Boss可以删除所有）
            Integer currentUserId = PoetryUtil.getUserId();
            User currentUser = PoetryUtil.getCurrentUser();
            boolean isBoss = currentUser != null && currentUser.getUserType() != null && currentUser.getUserType() == 0;
            
            if (!isBoss && !article.getUserId().equals(currentUserId)) {
                return PoetryResult.fail("无权限删除此文章的翻译");
            }
            
            // 删除特定语言的翻译
            boolean deleted = translationService.deleteSpecificTranslation(articleId, language);
            
            if (deleted) {
                log.info("用户 {} 删除了文章 {} 的 {} 翻译", currentUserId, articleId, language);
                return PoetryResult.success("翻译删除成功");
            } else {
                return PoetryResult.fail("翻译不存在或删除失败");
            }
        } catch (Exception e) {
            log.error("删除文章翻译失败", e);
            return PoetryResult.fail("删除翻译失败：" + e.getMessage());
        }
    }
    
    /**
     * 删除文章的所有翻译
     */
    @PostMapping("/article/deleteAllTranslations")
    @LoginCheck(1)
    public PoetryResult deleteAllArticleTranslations(@RequestBody Map<String, Object> requestData) {
        Integer articleId = (Integer) requestData.get("articleId");
        
        if (articleId == null) {
            return PoetryResult.fail("参数不完整");
        }
        try {
            // 验证文章所有权
            Article article = articleService.getById(articleId);
            if (article == null) {
                return PoetryResult.fail("文章不存在");
            }
            
            // 检查用户权限
            Integer currentUserId = PoetryUtil.getUserId();
            User currentUser = PoetryUtil.getCurrentUser();
            boolean isBoss = currentUser != null && currentUser.getUserType() != null && currentUser.getUserType() == 0;
            
            if (!isBoss && !article.getUserId().equals(currentUserId)) {
                return PoetryResult.fail("无权限删除此文章的翻译");
            }
            
            // 删除所有翻译
            translationService.deleteArticleTranslation(articleId);
            
            log.info("用户 {} 删除了文章 {} 的所有翻译", currentUserId, articleId);
            return PoetryResult.success("所有翻译删除成功");
        } catch (Exception e) {
            log.error("删除文章所有翻译失败", e);
            return PoetryResult.fail("删除所有翻译失败：" + e.getMessage());
        }
    }
    
    /**
     * 重新生成文章翻译
     */
    @PostMapping("/article/regenerateTranslations")
    @LoginCheck(1)
    public PoetryResult regenerateArticleTranslations(@RequestParam("articleId") Integer articleId) {
        try {
            // 验证文章所有权
            Article article = articleService.getById(articleId);
            if (article == null) {
                return PoetryResult.fail("文章不存在");
            }
            
            // 检查用户权限
            Integer currentUserId = PoetryUtil.getUserId();
            User currentUser = PoetryUtil.getCurrentUser();
            boolean isBoss = currentUser != null && currentUser.getUserType() != null && currentUser.getUserType() == 0;
            
            if (!isBoss && !article.getUserId().equals(currentUserId)) {
                return PoetryResult.fail("无权限重新生成此文章的翻译");
            }
            
            // 删除现有翻译并重新生成
            translationService.refreshArticleTranslation(articleId);
            
            log.info("用户 {} 重新生成了文章 {} 的翻译", currentUserId, articleId);
            return PoetryResult.success("翻译重新生成任务已启动");
        } catch (Exception e) {
            log.error("重新生成文章翻译失败", e);
            return PoetryResult.fail("重新生成翻译失败：" + e.getMessage());
        }
    }
    
    /**
     * 更新文章sitemap（代理接口）
     */
    @PostMapping("/article/updateSitemap")
    @LoginCheck(1)
    public PoetryResult updateArticleSitemap(@RequestBody Map<String, Object> requestData) {
        try {
            Integer articleId = (Integer) requestData.get("articleId");
            String action = (String) requestData.get("action");
            String language = (String) requestData.get("language");
            
            if (articleId == null || action == null) {
                return PoetryResult.fail("缺少必要参数");
            }
            
            // 验证文章所有权
            Article article = articleService.getById(articleId);
            if (article == null) {
                return PoetryResult.fail("文章不存在");
            }
            
            // 检查用户权限
            Integer currentUserId = PoetryUtil.getUserId();
            User currentUser = PoetryUtil.getCurrentUser();
            boolean isBoss = currentUser != null && currentUser.getUserType() != null && currentUser.getUserType() == 0;
            
            if (!isBoss && !article.getUserId().equals(currentUserId)) {
                return PoetryResult.fail("无权限操作此文章的sitemap");
            }
            
            // 调用Python服务更新sitemap
            try {
                RestTemplate restTemplate = new RestTemplate();
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                headers.set("X-Internal-Service", "poetize-java");
                headers.set("X-Admin-Request", "true");
                headers.set("User-Agent", "poetize-java/1.0.0");
                
                Map<String, Object> sitemapData = new HashMap<>();
                sitemapData.put("articleId", articleId);
                sitemapData.put("action", action);
                if (language != null) {
                    sitemapData.put("language", language);
                }
                
                HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(sitemapData, headers);
                
                String pythonServerUrl = System.getenv().getOrDefault("PYTHON_SERVICE_URL", "http://localhost:5000");
                String sitemapApiUrl = pythonServerUrl + "/seo/updateArticleSitemap";
                
                Map<String, Object> response = restTemplate.postForObject(
                    sitemapApiUrl,
                    requestEntity,
                    Map.class
                );
                
                if (response != null && "200".equals(String.valueOf(response.get("code")))) {
                    log.info("用户 {} 成功更新文章 {} 的sitemap，操作: {}, 语言: {}", currentUserId, articleId, action, language);
                    return PoetryResult.success(response.get("data"));
                } else {
                    log.warn("sitemap更新响应异常: {}", response);
                    return PoetryResult.fail("sitemap更新失败：" + (response != null ? response.get("message") : "未知错误"));
                }
                
            } catch (Exception apiException) {
                log.error("调用sitemap更新API失败，文章ID: {}, 错误: {}", articleId, apiException.getMessage(), apiException);
                return PoetryResult.fail("sitemap更新失败：" + apiException.getMessage());
            }
            
        } catch (Exception e) {
            log.error("更新文章sitemap失败", e);
            return PoetryResult.fail("更新sitemap失败：" + e.getMessage());
        }
    }
}
