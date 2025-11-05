package com.ld.poetry.service;

/**
 * 摘要生成服务
 */
public interface SummaryService {
    
    /**
     * 生成并保存文章多语言AI摘要
     * @param articleId 文章ID
     */
    void generateAndSaveSummary(Integer articleId);
    
    /**
     * 更新文章多语言AI摘要
     * @param articleId 文章ID  
     * @param content 文章内容
     */
    void updateSummary(Integer articleId, String content);
    
    /**
     * 生成单语言摘要（简化版，用于特殊场景）
     * @param content 文章内容
     * @return 生成的摘要
     */
    String generateSummarySync(String content);
} 