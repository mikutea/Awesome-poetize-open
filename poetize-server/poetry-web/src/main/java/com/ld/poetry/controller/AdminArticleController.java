package com.ld.poetry.controller;

import com.baomidou.mybatisplus.extension.conditions.update.LambdaUpdateChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.entity.*;
import com.ld.poetry.service.ArticleService;
import com.ld.poetry.service.SummaryService;
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
    @LoginCheck(0)
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
                    String pythonServerUrl = System.getenv().getOrDefault("PYTHON_SERVICE_URL", "http://poetize-python:5000");
                    String sitemapApiUrl = pythonServerUrl + "/python/seo/updateArticleSitemap";
                    
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
}
