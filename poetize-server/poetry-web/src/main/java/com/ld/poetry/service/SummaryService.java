package com.ld.poetry.service;

/**
 * 异步摘要生成服务
 */
public interface SummaryService {
    
    /**
     * 异步生成并保存文章摘要
     * @param articleId 文章ID
     */
    void generateAndSaveSummaryAsync(Integer articleId);
    
    /**
     * 异步更新文章摘要
     * @param articleId 文章ID  
     * @param content 文章内容
     */
    void updateSummaryAsync(Integer articleId, String content);
    
    /**
     * 同步生成摘要（用于紧急情况）
     * @param content 文章内容
     * @return 生成的摘要
     */
    String generateSummarySync(String content);
} 