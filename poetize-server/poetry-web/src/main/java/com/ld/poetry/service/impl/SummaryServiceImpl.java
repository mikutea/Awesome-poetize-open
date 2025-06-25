package com.ld.poetry.service.impl;

import com.ld.poetry.entity.Article;
import com.ld.poetry.service.ArticleService;
import com.ld.poetry.service.SummaryService;
import com.ld.poetry.utils.SmartSummaryGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 异步摘要生成服务实现
 */
@Service
@Slf4j
public class SummaryServiceImpl implements SummaryService {

    @Autowired
    private ArticleService articleService;
    
    @Autowired
    private RestTemplate restTemplate;

    @Override
    @Async
    public void generateAndSaveSummaryAsync(Integer articleId) {
        long startTime = System.currentTimeMillis();
        log.info("【异步摘要】开始为文章{}生成摘要", articleId);
        
        try {
            // 获取文章内容
            Article article = articleService.getById(articleId);
            if (article == null) {
                log.warn("【异步摘要】文章{}不存在，跳过摘要生成", articleId);
                return;
            }
            
            // 如果已经有摘要，跳过生成
            if (StringUtils.hasText(article.getSummary())) {
                log.info("【异步摘要】文章{}已有摘要，跳过生成", articleId);
                return;
            }
            
            // 生成摘要
            if (StringUtils.hasText(article.getArticleContent())) {
                String summary = generateSummarySync(article.getArticleContent());
                
                // 更新数据库
                articleService.lambdaUpdate()
                    .eq(Article::getId, articleId)
                    .set(Article::getSummary, summary)
                    .update();
                
                long endTime = System.currentTimeMillis();
                log.info("【异步摘要】文章{}摘要生成完成，耗时: {}ms，摘要: {}", 
                    articleId, endTime - startTime, summary.length() > 50 ? summary.substring(0, 50) + "..." : summary);
            } else {
                log.warn("【异步摘要】文章{}内容为空，跳过摘要生成", articleId);
            }
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            log.error("【异步摘要】文章{}摘要生成失败，耗时: {}ms，错误: {}", 
                articleId, endTime - startTime, e.getMessage(), e);
        }
    }

    @Override
    @Async
    public void updateSummaryAsync(Integer articleId, String content) {
        long startTime = System.currentTimeMillis();
        log.info("【异步摘要】开始更新文章{}的摘要", articleId);
        
        try {
            if (!StringUtils.hasText(content)) {
                log.warn("【异步摘要】文章{}内容为空，跳过摘要更新", articleId);
                return;
            }
            
            // 生成新摘要
            String summary = generateSummarySync(content);
            
            // 更新数据库
            articleService.lambdaUpdate()
                .eq(Article::getId, articleId)
                .set(Article::getSummary, summary)
                .update();
                
            long endTime = System.currentTimeMillis();
            log.info("【异步摘要】文章{}摘要更新完成，耗时: {}ms，摘要: {}", 
                articleId, endTime - startTime, summary.length() > 50 ? summary.substring(0, 50) + "..." : summary);
        } catch (Exception e) {
            long endTime = System.currentTimeMillis();
            log.error("【异步摘要】文章{}摘要更新失败，耗时: {}ms，错误: {}", 
                articleId, endTime - startTime, e.getMessage(), e);
        }
    }

    @Override
    public String generateSummarySync(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        
        // 1. 首先尝试AI摘要生成
        try {
            String aiSummary = callPythonAiSummary(content);
            if (StringUtils.hasText(aiSummary)) {
                log.info("【摘要生成】AI摘要生成成功，长度: {}", aiSummary.length());
                return aiSummary;
            }
        } catch (Exception e) {
            log.warn("【摘要生成】AI摘要生成失败，回退到本地算法: {}", e.getMessage());
        }
        
        // 2. 回退到本地智能摘要生成器
        try {
            String smartSummary = SmartSummaryGenerator.generateAdvancedSummary(content, 150);
            if (StringUtils.hasText(smartSummary)) {
                log.info("【摘要生成】本地算法摘要生成成功，长度: {}", smartSummary.length());
                return smartSummary;
            }
        } catch (Exception e) {
            log.error("【摘要生成】本地算法摘要生成失败: {}", e.getMessage());
        }
        
        // 3. 最后的简单回退
        log.warn("【摘要生成】所有摘要生成方法都失败，使用简单截取");
        String fallback = content.replaceAll("[#>`*\\[\\]()]", "")
                               .replaceAll("\\s+", " ")
                               .trim();
        return fallback.length() > 150 ? fallback.substring(0, 150) + "..." : fallback;
    }
    
    /**
     * 调用Python端的AI摘要生成服务
     * @param content 文章内容
     * @return AI生成的摘要，失败时返回null
     */
    private String callPythonAiSummary(String content) {
        try {
            String pythonServerUrl = System.getenv().getOrDefault("PYTHON_SERVICE_URL", "http://localhost:5000");
            String pythonApiUrl = pythonServerUrl + "/api/translation/generate-summary";
            
            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("content", content);
            requestBody.put("max_length", 150);
            requestBody.put("style", "concise");
            
            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Service", "poetize-java");
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            // 发送请求
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(pythonApiUrl, request, Map.class);
            
            if (response != null && "200".equals(String.valueOf(response.get("code")))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) response.get("data");
                if (data != null && data.get("summary") != null) {
                    String summary = data.get("summary").toString();
                    String method = data.get("method") != null ? data.get("method").toString() : "unknown";
                    log.info("【摘要生成】AI摘要生成成功，使用方法: {}, 摘要长度: {}", method, summary.length());
                    return summary;
                }
            } else {
                String message = response != null ? String.valueOf(response.get("message")) : "Unknown error";
                log.warn("【摘要生成】Python AI摘要服务返回错误: {}", message);
            }
        } catch (Exception e) {
            log.warn("【摘要生成】调用Python AI摘要服务失败: {}", e.getMessage());
        }
        
        return null;
    }
} 