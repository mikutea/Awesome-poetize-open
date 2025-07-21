package com.ld.poetry.service;

import java.util.List;
import java.util.Map;

/**
 * 翻译服务接口
 */
public interface TranslationService {

    /**
     * 翻译并保存文章
     * @param articleId 文章ID
     */
    void translateAndSaveArticle(Integer articleId);

    /**
     * 翻译并保存文章（支持跳过AI翻译和暂存翻译数据）
     * @param articleId 文章ID
     * @param skipAiTranslation 是否跳过AI翻译
     * @param pendingTranslation 暂存的翻译数据
     */
    void translateAndSaveArticle(Integer articleId, boolean skipAiTranslation, Map<String, String> pendingTranslation);

    /**
     * 删除文章的所有翻译
     * @param articleId 文章ID
     */
    void refreshArticleTranslation(Integer articleId);

    /**
     * 翻译文本
     * @param text 待翻译文本
     * @param sourceLang 源语言
     * @param targetLang 目标语言
     * @return 翻译结果
     */
    String translateText(String text, String sourceLang, String targetLang);

    /**
     * 获取文章翻译
     * @param articleId 文章ID
     * @param language 目标语言
     * @return 翻译结果
     */
    Map<String, String> getArticleTranslation(Integer articleId, String language);

    /**
     * 获取文章所有可用的翻译语言
     * @param articleId 文章ID
     * @return 可用翻译语言列表
     */
    List<String> getArticleAvailableLanguages(Integer articleId);

    /**
     * 手动保存文章翻译
     * @param articleId 文章ID
     * @param targetLanguage 目标语言
     * @param translatedTitle 翻译后的标题
     * @param translatedContent 翻译后的内容
     * @return 保存结果
     */
    Map<String, Object> saveManualTranslation(Integer articleId, String targetLanguage,
                                            String translatedTitle, String translatedContent);

    /**
     * 检查文章是否应该跳过自动翻译
     * @param articleId 文章ID
     * @param targetLanguage 目标语言
     * @return 是否跳过自动翻译
     */
    boolean shouldSkipAutoTranslation(Integer articleId, String targetLanguage);

    /**
     * 删除文章所有翻译，不做重新翻译
     */
    void deleteArticleTranslation(Integer articleId);

    /**
     * 获取翻译语言配置
     * @return 包含源语言和目标语言的配置Map
     */
    Map<String, String> getTranslationLanguageConfig();
}