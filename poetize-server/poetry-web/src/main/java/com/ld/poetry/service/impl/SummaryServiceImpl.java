package com.ld.poetry.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ld.poetry.dao.ArticleTranslationMapper;
import com.ld.poetry.entity.Article;
import com.ld.poetry.entity.ArticleTranslation;
import com.ld.poetry.service.ArticleService;
import com.ld.poetry.service.SummaryService;
import com.ld.poetry.service.TranslationService;
import com.ld.poetry.utils.SmartSummaryGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 异步摘要生成服务实现
 */
@Service
@Slf4j
public class SummaryServiceImpl implements SummaryService {

    @Autowired
    @Lazy
    private ArticleService articleService;
    
    @Autowired
    private ArticleTranslationMapper articleTranslationMapper;
    
    @Autowired
    @Lazy
    private TranslationService translationService;
    
    @Autowired
    private com.ld.poetry.service.SysAiConfigService sysAiConfigService;
    
    @Autowired
    private RestTemplate restTemplate;

    @Override
    public void generateAndSaveSummary(Integer articleId) {
        
        try {
            // 获取文章内容
            Article article = articleService.getById(articleId);
            if (article == null) {
                log.warn("文章{}不存在，跳过摘要生成", articleId);
                return;
            }
            
            // 检查文章内容是否为空
            if (!StringUtils.hasText(article.getArticleContent())) {
                log.warn("文章{}内容为空，跳过摘要生成", articleId);
                return;
            }
            
            // 获取源语言配置
            Map<String, Object> defaultLangs = sysAiConfigService.getDefaultLanguages();
            String sourceLanguage = defaultLangs != null ? 
                (String) defaultLangs.getOrDefault("default_source_lang", "zh") : "zh";
            
            // 收集所有语言的内容
            Map<String, Map<String, String>> languageContents = collectArticleLanguageContents(
                articleId, article, sourceLanguage);
            
            if (languageContents.isEmpty()) {
                log.warn("文章{}没有任何语言内容，跳过摘要生成", articleId);
                return;
            }
            
            // 生成多语言摘要
            Map<String, String> summaries = generateMultiLangSummarySync(
                articleId, languageContents);
            
            if (summaries == null || summaries.isEmpty()) {
                log.error("文章{}摘要生成失败，返回空结果", articleId);
                return;
            }
            
            // 保存摘要到数据库
            saveMultiLangSummaries(articleId, summaries, sourceLanguage);
            
        } catch (Exception e) {
            log.error("文章{}摘要生成失败，错误: {}", articleId, e.getMessage(), e);
        }
    }

    @Override
    public void updateSummary(Integer articleId, String content) {
        
        try {
            if (!StringUtils.hasText(content)) {
                log.warn("文章{}内容为空，跳过摘要更新", articleId);
                return;
            }
            
            // 获取文章
            Article article = articleService.getById(articleId);
            if (article == null) {
                log.warn("文章{}不存在，跳过摘要更新", articleId);
                return;
            }
            
            // 获取源语言配置
            Map<String, Object> defaultLangs = sysAiConfigService.getDefaultLanguages();
            String sourceLanguage = defaultLangs != null ? 
                (String) defaultLangs.getOrDefault("default_source_lang", "zh") : "zh";
            
            // 收集所有语言的内容
            Map<String, Map<String, String>> languageContents = collectArticleLanguageContents(
                articleId, article, sourceLanguage);
            
            if (languageContents.isEmpty()) {
                log.warn("文章{}没有任何语言内容，跳过摘要更新", articleId);
                return;
            }
            
            // 生成多语言摘要
            Map<String, String> summaries = generateMultiLangSummarySync(
                articleId, languageContents);
            
            if (summaries == null || summaries.isEmpty()) {
                log.error("文章{}摘要更新失败，返回空结果", articleId);
                return;
            }
            
            // 保存摘要到数据库
            saveMultiLangSummaries(articleId, summaries, sourceLanguage);
                
        } catch (Exception e) {
            log.error("文章{}摘要更新失败，错误: {}", articleId, e.getMessage(), e);
        }
    }

    @Override
    public String generateSummarySync(String content) {
        if (!StringUtils.hasText(content)) {
            return "";
        }
        
        log.warn("generateSummarySync方法已废弃，建议使用异步多语言摘要生成");
        
        // 简单回退：使用本地算法生成单语言摘要
        try {
            String smartSummary = SmartSummaryGenerator.generateAdvancedSummary(content, 150);
            if (StringUtils.hasText(smartSummary)) {
                return smartSummary;
            }
        } catch (Exception e) {
            log.error("本地算法摘要生成失败: {}", e.getMessage());
        }
        
        // 最后的简单回退
        String fallback = content.replaceAll("[#>`*\\[\\]()]", "")
                               .replaceAll("\\s+", " ")
                               .trim();
        return fallback.length() > 150 ? fallback.substring(0, 150) + "..." : fallback;
    }
    
    /**
     * 收集文章的所有语言内容
     * @param articleId 文章ID
     * @param article 文章对象
     * @param sourceLanguage 源语言代码
     * @return 语言代码 -> {title, content} 映射（使用LinkedHashMap保证源语言在第一位）
     */
    private Map<String, Map<String, String>> collectArticleLanguageContents(
            Integer articleId, Article article, String sourceLanguage) {
        Map<String, Map<String, String>> languageContents = new LinkedHashMap<>();
        
        // 添加源语言内容
        if (StringUtils.hasText(article.getArticleContent())) {
            Map<String, String> sourceContent = new HashMap<>();
            sourceContent.put("title", article.getArticleTitle());
            sourceContent.put("content", article.getArticleContent());
            languageContents.put(sourceLanguage, sourceContent);
        }
        
        // 查询所有翻译内容
        LambdaQueryWrapper<ArticleTranslation> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ArticleTranslation::getArticleId, articleId);
        List<ArticleTranslation> translations = articleTranslationMapper.selectList(queryWrapper);
        
        if (translations != null && !translations.isEmpty()) {
            for (ArticleTranslation translation : translations) {
                if (StringUtils.hasText(translation.getContent())) {
                    Map<String, String> translationContent = new HashMap<>();
                    translationContent.put("title", translation.getTitle());
                    translationContent.put("content", translation.getContent());
                    languageContents.put(translation.getLanguage(), translationContent);
                }
            }
        }
        
        return languageContents;
    }
    
    /**
     * 生成多语言摘要
     * @param articleId 文章ID
     * @param languageContents 各语言内容
     * @return 语言代码 -> 摘要 映射
     */
    private Map<String, String> generateMultiLangSummarySync(
            Integer articleId, Map<String, Map<String, String>> languageContents) {
        
        // 1. 首先尝试调用Python AI服务生成多语言摘要
        try {
            Map<String, String> aiSummaries = callPythonMultiLangSummary(articleId, languageContents);
            if (aiSummaries != null && !aiSummaries.isEmpty()) {
                return aiSummaries;
            }
        } catch (Exception e) {
            log.warn("AI多语言摘要生成失败，回退到本地算法: {}", e.getMessage());
        }
        
        // 2. 回退到本地算法：对每个语言分别生成摘要
        Map<String, String> localSummaries = new HashMap<>();
        for (Map.Entry<String, Map<String, String>> entry : languageContents.entrySet()) {
            String langCode = entry.getKey();
            String content = entry.getValue().get("content");
            
            try {
                String summary = SmartSummaryGenerator.generateAdvancedSummary(content, 150);
                if (StringUtils.hasText(summary)) {
                    localSummaries.put(langCode, summary);
                }
            } catch (Exception e) {
                log.error("本地算法生成{}语言摘要失败: {}", langCode, e.getMessage());
            }
        }
        
        return localSummaries;
    }
    
    /**
     * 调用Python端的多语言AI摘要生成服务
     * @param articleId 文章ID
     * @param languageContents 各语言内容
     * @return 多语言摘要JSON映射，失败时返回null
     */
    private Map<String, String> callPythonMultiLangSummary(
            Integer articleId, Map<String, Map<String, String>> languageContents) {
        try {
            String pythonServerUrl = System.getenv().getOrDefault("PYTHON_SERVICE_URL", "http://localhost:5000");
            String pythonApiUrl = pythonServerUrl + "/api/translation/generate-summary";
            
            // 构建各语言的纯内容映射
            Map<String, String> languages = new HashMap<>();
            for (Map.Entry<String, Map<String, String>> entry : languageContents.entrySet()) {
                languages.put(entry.getKey(), entry.getValue().get("content"));
            }
            
            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("article_id", articleId);
            requestBody.put("languages", languages);
            requestBody.put("max_length", 150);
            requestBody.put("style", "concise");
            
            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Service", "poetize-java");
            headers.set("X-Admin-Request", "true");
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            // 发送请求
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(pythonApiUrl, request, Map.class);
            
            if (response != null && "200".equals(String.valueOf(response.get("code")))) {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) response.get("data");
                if (data != null && data.get("summaries") != null) {
                    // 解析多语言摘要JSON
                    @SuppressWarnings("unchecked")
                    Map<String, String> summaries = (Map<String, String>) data.get("summaries");
                    String method = data.get("method") != null ? data.get("method").toString() : "unknown";
                    return summaries;
                }
            } else {
                String message = response != null ? String.valueOf(response.get("message")) : "Unknown error";
                log.warn("Python AI摘要服务返回错误: {}", message);
            }
        } catch (Exception e) {
            log.warn("调用Python AI摘要服务失败: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * 保存多语言摘要到数据库
     * @param articleId 文章ID
     * @param summaries 多语言摘要映射
     * @param sourceLanguage 源语言代码
     */
    private void saveMultiLangSummaries(Integer articleId, Map<String, String> summaries, String sourceLanguage) {
        // 保存源语言摘要到article表
        if (summaries.containsKey(sourceLanguage)) {
            String sourceSummary = summaries.get(sourceLanguage);
            articleService.lambdaUpdate()
                .eq(Article::getId, articleId)
                .set(Article::getSummary, sourceSummary)
                .update();
        }
        
        // 保存其他语言摘要到article_translation表
        for (Map.Entry<String, String> entry : summaries.entrySet()) {
            String langCode = entry.getKey();
            String summary = entry.getValue();
            
            // 跳过源语言（已在article表中保存）
            if (langCode.equals(sourceLanguage)) {
                continue;
            }
            
            // 查找对应的翻译记录
            LambdaQueryWrapper<ArticleTranslation> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(ArticleTranslation::getArticleId, articleId)
                       .eq(ArticleTranslation::getLanguage, langCode);
            
            ArticleTranslation translation = articleTranslationMapper.selectOne(queryWrapper);
            if (translation != null) {
                translation.setSummary(summary);
                translation.setUpdateTime(LocalDateTime.now());
                articleTranslationMapper.updateById(translation);
            } else {
                log.warn("未找到文章{}的{}语言翻译记录，跳过摘要保存", articleId, langCode);
            }
        }
    }
}