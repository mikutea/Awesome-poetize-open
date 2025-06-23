package com.ld.poetry.service.impl;

import com.ld.poetry.entity.Article;
import com.ld.poetry.service.ArticleService;
import com.ld.poetry.service.SummaryService;
import com.ld.poetry.utils.SmartSummaryGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 异步摘要生成服务实现
 */
@Service
@Slf4j
public class SummaryServiceImpl implements SummaryService {

    @Autowired
    private ArticleService articleService;

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
        
        try {
            // 使用智能摘要生成器
            return SmartSummaryGenerator.generateSummary(content);
        } catch (Exception e) {
            log.error("【摘要生成】智能摘要生成失败，使用简单截取，错误: {}", e.getMessage());
            // 简单fallback：直接截取前150个字符
            String fallback = content.replaceAll("[#>`*\\[\\]()]", "")
                                   .replaceAll("\\s+", " ")
                                   .trim();
            return fallback.length() > 150 ? fallback.substring(0, 150) + "..." : fallback;
        }
    }
} 