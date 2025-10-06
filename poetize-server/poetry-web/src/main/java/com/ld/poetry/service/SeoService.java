package com.ld.poetry.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ld.poetry.entity.Article;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * SEO服务，用于将文章提交到搜索引擎
 */
@SuppressWarnings("unchecked")
@Service
@Slf4j
public class SeoService {

    @Autowired
    private RestTemplate restTemplate;
    
    @Autowired
    @Lazy
    private ArticleService articleService;
    
    
    @Autowired
    private SearchEnginePushService searchEnginePushService;
    
    @Autowired
    private com.ld.poetry.utils.mail.MailUtil mailUtil;
    
    @Value("${PYTHON_SERVICE_URL:http://poetize-python:5000}")
    private String pythonServerUrl;

    /**
     * 将文章提交到搜索引擎
     * 已完全迁移到Java端，使用SearchEnginePushService实现
     *
     * @param articleId 文章ID
     * @return 是否提交成功
     */
    public boolean submitToSearchEngines(Integer articleId) {
        try {
            log.info("开始将文章推送至搜索引擎，文章ID: {}", articleId);
            System.out.println("【SEO服务】开始将文章推送至搜索引擎，文章ID: " + articleId);
            
            // 获取文章信息
            Article article = articleService.getById(articleId);
            if (article == null) {
                log.error("文章不存在，无法推送至搜索引擎，文章ID: {}", articleId);
                System.out.println("【SEO服务】文章不存在，无法推送至搜索引擎，文章ID: " + articleId);
                return false;
            }
            
            // 检查文章是否可见
            if (!Boolean.TRUE.equals(article.getViewStatus())) {
                log.info("文章不可见，跳过搜索引擎推送，文章ID: {}", articleId);
                System.out.println("【SEO服务】文章不可见，跳过搜索引擎推送，文章ID: " + articleId);
                return true; // 跳过推送也视为成功
            }
            
            // 使用Java端的完整搜索引擎推送功能
            if (searchEnginePushService != null && searchEnginePushService.isPushEnabled()) {
                log.info("使用Java端完整搜索引擎推送功能");
                System.out.println("【SEO服务】使用Java端完整搜索引擎推送功能");
                
                // 直接从 MailUtil 获取网站地址并构建文章URL
                String siteAddress = mailUtil.getSiteUrl();
                
                if (org.springframework.util.StringUtils.hasText(siteAddress)) {
                    
                    String articleUrl = siteAddress + "/article/" + articleId;
                    log.info("推送文章URL到搜索引擎: {}", articleUrl);
                    System.out.println("【SEO服务】推送文章URL到搜索引擎: " + articleUrl);
                    
                    // 推送到所有启用的搜索引擎
                    Map<String, Object> pushResult = searchEnginePushService.pushUrlToAllEngines(articleUrl);
                    boolean success = Boolean.TRUE.equals(pushResult.get("success"));
                    
                    Integer successCount = (Integer) pushResult.get("successCount");
                    Integer totalEngines = (Integer) pushResult.get("totalEngines");
                    
                    log.info("Java端搜索引擎推送结果: {}, 详情: 成功{}/{}个引擎", 
                            success ? "成功" : "失败", successCount, totalEngines);
                    System.out.println("【SEO服务】Java端搜索引擎推送结果: " + (success ? "成功" : "失败") + 
                                     ", 成功推送到" + successCount + "/" + totalEngines + "个搜索引擎");
                    
                    return success;
                } else {
                    log.error("网站地址或文章URL格式未配置，无法进行搜索引擎推送");
                    System.out.println("【SEO服务】网站地址或文章URL格式未配置，无法进行搜索引擎推送");
                    return false;
                }
            } else {
                log.info("Java端搜索引擎推送功能未启用");
                System.out.println("【SEO服务】Java端搜索引擎推送功能未启用");
                return true; // 功能未启用也视为成功
            }
            
        } catch (Exception e) {
            log.error("推送文章至搜索引擎出错", e);
            System.out.println("【SEO服务】推送文章至搜索引擎出错: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}