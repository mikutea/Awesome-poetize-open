package com.ld.poetry.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ld.poetry.dao.ArticleMapper;
import com.ld.poetry.dao.ArticleTranslationMapper;
import com.ld.poetry.entity.Article;
import com.ld.poetry.entity.ArticleTranslation;
import com.ld.poetry.service.TranslationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 翻译服务实现类
 * 通过HTTP调用Python端的翻译服务
 */
@Service
@Slf4j
public class TranslationServiceImpl implements TranslationService {
    
    @Value("${PYTHON_SERVICE_URL:http://localhost:5000}")
    private String pythonServiceUrl;
    
    @Autowired
    private ArticleMapper articleMapper;
    
    @Autowired
    private ArticleTranslationMapper articleTranslationMapper;
    
    private final RestTemplate restTemplate;
    
    @Autowired
    private com.ld.poetry.utils.PrerenderClient prerenderClient;
    
    public TranslationServiceImpl() {
        this.restTemplate = new RestTemplate();
    }
    
    @Override
    public void translateAndSaveArticle(Integer articleId) {
        // 调用新的重载方法，使用默认参数
        translateAndSaveArticle(articleId, false, null);
    }

    @Override
    public void translateAndSaveArticle(Integer articleId, boolean skipAiTranslation, Map<String, String> pendingTranslation) {
        log.info("开始翻译并保存文章，ID: {}, 跳过AI翻译: {}, 有暂存翻译: {}",
                articleId, skipAiTranslation, pendingTranslation != null && !pendingTranslation.isEmpty());
        
        try {
            // 1. 获取文章内容
            Article article = articleMapper.selectById(articleId);
            if (article == null) {
                log.warn("文章不存在，ID: {}", articleId);
                return;
            }

            // 检查文章是否有内容
            if (article.getArticleTitle() == null || article.getArticleTitle().trim().isEmpty() ||
                article.getArticleContent() == null || article.getArticleContent().trim().isEmpty()) {
                log.warn("文章标题或内容为空，跳过翻译，ID: {}", articleId);
                return;
            }

            // 2. 获取翻译配置
            Map<String, String> languageConfig = getLanguageConfig();
            String sourceLanguage = languageConfig.get("source");
            String targetLanguage = languageConfig.get("target");

            log.info("翻译配置 - 源语言: {}, 目标语言: {}", sourceLanguage, targetLanguage);

            // 3. 处理跳过AI翻译的情况
            if (skipAiTranslation) {
                log.info("跳过AI自动翻译，文章ID: {}", articleId);

                // 如果有暂存的翻译数据，保存它
                if (pendingTranslation != null && !pendingTranslation.isEmpty()) {
                    String translatedTitle = pendingTranslation.get("title");
                    String translatedContent = pendingTranslation.get("content");
                    String translationLanguage = pendingTranslation.get("language");

                    if (translatedTitle != null && translatedContent != null && translationLanguage != null) {
                        boolean success = saveOrUpdateTranslation(articleId, translationLanguage,
                                                                translatedTitle, translatedContent);
                        if (success) {
                            log.info("暂存翻译保存成功，文章ID: {}, 目标语言: {}", articleId, translationLanguage);

                            // 获取需要渲染的语言（使用系统配置的源语言 + 暂存翻译的目标语言）
                            List<String> languagesToRender = getLanguagesToRender(sourceLanguage, translationLanguage);

                            // 触发静态预渲染（传递语言参数）
                            prerenderClient.renderArticleWithLanguages(articleId, languagesToRender);
                            prerenderClient.renderHomePage();
                            if (article.getSortId() != null) {
                                prerenderClient.renderCategoryPage(article.getSortId());
                            }

                            log.info("暂存翻译预渲染完成，文章ID: {}, 源语言: {}, 目标语言: {}, 渲染语言: {}",
                                    articleId, sourceLanguage, translationLanguage, languagesToRender);
                        } else {
                            log.error("暂存翻译保存失败，文章ID: {}, 目标语言: {}", articleId, translationLanguage);
                        }
                    }
                } else {
                    // 没有暂存翻译，但仍需要触发原文的预渲染
                    log.info("跳过AI翻译且无暂存翻译，触发原文预渲染，文章ID: {}", articleId);

                    List<String> languagesToRender = getLanguagesToRender(sourceLanguage, null);

                    // 触发静态预渲染（只渲染源语言版本）
                    prerenderClient.renderArticleWithLanguages(articleId, languagesToRender);
                    prerenderClient.renderHomePage();
                    if (article.getSortId() != null) {
                        prerenderClient.renderCategoryPage(article.getSortId());
                    }

                    log.info("跳过翻译预渲染完成，文章ID: {}, 渲染语言: {}", articleId, languagesToRender);
                }
                return;
            }

            // 4. 翻译标题
            String translatedTitle = translateText(article.getArticleTitle(), sourceLanguage, targetLanguage);
            if (translatedTitle == null || translatedTitle.trim().isEmpty() ||
                translatedTitle.equals(article.getArticleTitle())) {
                log.error("标题翻译失败，文章ID: {}，原标题: {}", articleId, article.getArticleTitle());
                return; // 翻译失败，不保存任何记录
            }

            // 5. 翻译内容（分段处理长文本）
            String translatedContent = translateLongText(article.getArticleContent(), sourceLanguage, targetLanguage);
            if (translatedContent == null || translatedContent.trim().isEmpty() ||
                translatedContent.equals(article.getArticleContent())) {
                log.error("内容翻译失败，文章ID: {}，内容长度: {}", articleId,
                         article.getArticleContent() != null ? article.getArticleContent().length() : 0);
                return; // 翻译失败，不保存任何记录
            }

            // 6. 保存或更新翻译结果（使用事务和重试机制处理并发）
            boolean success = saveOrUpdateTranslation(articleId, targetLanguage, translatedTitle, translatedContent);
            
            if (success) {
                // 获取系统默认语言配置
                List<String> languagesToRender = getLanguagesToRender(sourceLanguage, targetLanguage);

                // 触发静态预渲染（传递语言参数）
                prerenderClient.renderArticleWithLanguages(articleId, languagesToRender);
                // 重新渲染首页（显示最新文章）
                prerenderClient.renderHomePage();
                // 重新渲染相关分类页面
                if (article.getSortId() != null) {
                    prerenderClient.renderCategoryPage(article.getSortId());
                }
                log.info("文章翻译及预渲染完成，文章ID: {}, 渲染语言: {}", articleId, languagesToRender);
            }
            
        } catch (Exception e) {
            log.error("翻译文章失败，文章ID: {}, 错误: {}", articleId, e.getMessage(), e);
        }
    }

    /**
     * 获取需要渲染的语言列表
     * @param sourceLanguage 源语言
     * @param targetLanguage 目标语言
     * @return 需要渲染的语言列表
     */
    private List<String> getLanguagesToRender(String sourceLanguage, String targetLanguage) {
        List<String> languages = new ArrayList<>();

        // 始终包含源语言（通常是中文）
        if (sourceLanguage != null && !languages.contains(sourceLanguage)) {
            languages.add(sourceLanguage);
        }

        // 包含目标语言（翻译后的语言）
        if (targetLanguage != null && !languages.contains(targetLanguage)) {
            languages.add(targetLanguage);
        }

        // 如果没有任何语言，默认添加中文
        if (languages.isEmpty()) {
            languages.add("zh");
        }

        log.debug("确定需要渲染的语言: 源语言={}, 目标语言={}, 渲染语言={}",
                 sourceLanguage, targetLanguage, languages);

        return languages;
    }

    /**
     * 保存或更新翻译结果，处理并发重复插入问题
     */
    private boolean saveOrUpdateTranslation(Integer articleId, String targetLanguage, String translatedTitle, String translatedContent) {
        // 使用重试机制处理并发问题
        int maxRetries = 3;
        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                // 再次检查是否已存在翻译记录（防止并发情况下的重复插入）
                LambdaQueryWrapper<ArticleTranslation> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.eq(ArticleTranslation::getArticleId, articleId)
                           .eq(ArticleTranslation::getLanguage, targetLanguage);

                ArticleTranslation existingTranslation = articleTranslationMapper.selectOne(queryWrapper);

                if (existingTranslation != null) {
                    // 更新现有翻译
                    existingTranslation.setTitle(translatedTitle);
                    existingTranslation.setContent(translatedContent);
                    existingTranslation.setUpdateTime(LocalDateTime.now());
                    articleTranslationMapper.updateById(existingTranslation);
                    log.info("更新文章翻译成功，文章ID: {}, 目标语言: {} (尝试第{}次)", articleId, targetLanguage, attempt);
                    return true;
                } else {
                    // 创建新翻译
                    ArticleTranslation newTranslation = new ArticleTranslation();
                    newTranslation.setArticleId(articleId);
                    newTranslation.setLanguage(targetLanguage);
                    newTranslation.setTitle(translatedTitle);
                    newTranslation.setContent(translatedContent);
                    newTranslation.setCreateTime(LocalDateTime.now());
                    newTranslation.setUpdateTime(LocalDateTime.now());
                    
                    try {
                        articleTranslationMapper.insert(newTranslation);
                        log.info("创建文章翻译成功，文章ID: {}, 目标语言: {} (尝试第{}次)", articleId, targetLanguage, attempt);
                        return true;
                    } catch (org.springframework.dao.DuplicateKeyException e) {
                        // 如果遇到重复键异常，说明在我们检查后有其他线程插入了记录
                        log.warn("检测到并发插入，尝试更新现有记录，文章ID: {}, 目标语言: {} (尝试第{}次)", articleId, targetLanguage, attempt);
                        if (attempt < maxRetries) {
                            Thread.sleep(100 * attempt); // 短暂等待后重试
                            continue;
                        } else {
                            // 最后一次尝试：直接尝试更新
                            existingTranslation = articleTranslationMapper.selectOne(queryWrapper);
                            if (existingTranslation != null) {
                                existingTranslation.setTitle(translatedTitle);
                                existingTranslation.setContent(translatedContent);
                                existingTranslation.setUpdateTime(LocalDateTime.now());
                                articleTranslationMapper.updateById(existingTranslation);
                                log.info("最终更新文章翻译成功，文章ID: {}, 目标语言: {}", articleId, targetLanguage);
                                return true;
                            }
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("翻译保存被中断，文章ID: {}, 目标语言: {}", articleId, targetLanguage);
                return false;
            } catch (Exception e) {
                log.error("保存翻译失败，文章ID: {}, 目标语言: {}, 尝试第{}次, 错误: {}", articleId, targetLanguage, attempt, e.getMessage());
                if (attempt == maxRetries) {
                    return false;
                }
            }
        }

        log.error("保存翻译最终失败，文章ID: {}, 目标语言: {}", articleId, targetLanguage);
        return false;
    }

    /**
     * 删除文章的所有翻译
     */
    public void refreshArticleTranslation(Integer articleId) {
        try {
            LambdaQueryWrapper<ArticleTranslation> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(ArticleTranslation::getArticleId, articleId);
            int rows = articleTranslationMapper.delete(queryWrapper);
            if (rows > 0) {
                translateAndSaveArticle(articleId); // 重新翻译并将在内部触发 prerender
            }
            log.info("删除文章翻译成功，文章ID: {}", articleId);
        } catch (Exception e) {
            log.error("删除文章翻译失败，文章ID: {}, 错误: {}", articleId, e.getMessage(), e);
        }
    }
    
    /**
     * 翻译长文本
     */
    private String translateLongText(String content, String sourceLang, String targetLang) {
        if (content == null || content.trim().isEmpty()) {
            return content;
        }
        
        try {
            // 直接翻译整个文本，保持上下文连贯性
            String translatedContent = translateText(content, sourceLang, targetLang);
            if (translatedContent != null && !translatedContent.trim().isEmpty() &&
                !translatedContent.equals(content)) {
                return translatedContent;
            } else {
                // 翻译失败时返回null，而不是原文
                log.warn("长文本翻译失败，内容长度: {}", content.length());
                return null;
            }
            
        } catch (Exception e) {
            log.error("长文本翻译失败: {}", e.getMessage(), e);
            return content; // 翻译失败时返回原文
        }
    }
    
    @Override
    public Map<String, String> getArticleTranslation(Integer articleId, String language) {
        Map<String, String> result = new HashMap<>();

        if (articleId == null || language == null || language.trim().isEmpty()) {
            result.put("error", "文章ID或语言参数无效");
            return result;
        }

        try {
            // 查询文章翻译
            LambdaQueryWrapper<ArticleTranslation> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(ArticleTranslation::getArticleId, articleId)
                       .eq(ArticleTranslation::getLanguage, language);

            ArticleTranslation translation = articleTranslationMapper.selectOne(queryWrapper);

            if (translation != null) {
                result.put("title", translation.getTitle() != null ? translation.getTitle() : "");
                result.put("content", translation.getContent() != null ? translation.getContent() : "");
                result.put("language", translation.getLanguage());
                result.put("status", "success");
                log.debug("成功获取文章翻译，文章ID: {}, 语言: {}", articleId, language);
            } else {
                result.put("error", "未找到对应语言的翻译");
                result.put("status", "not_found");
                log.warn("未找到文章翻译，文章ID: {}, 语言: {}", articleId, language);
            }

        } catch (Exception e) {
            log.error("获取文章翻译失败，文章ID: {}, 语言: {}, 错误: {}", articleId, language, e.getMessage(), e);
            result.put("error", "获取翻译失败: " + e.getMessage());
            result.put("status", "error");
        }

        return result;
    }

    @Override
    public List<String> getArticleAvailableLanguages(Integer articleId) {
        List<String> availableLanguages = new ArrayList<>();

        if (articleId == null) {
            log.warn("文章ID为空，无法获取可用翻译语言");
            return availableLanguages;
        }

        try {
            // 查询文章的所有翻译语言
            LambdaQueryWrapper<ArticleTranslation> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(ArticleTranslation::getArticleId, articleId)
                       .select(ArticleTranslation::getLanguage);

            List<ArticleTranslation> translations = articleTranslationMapper.selectList(queryWrapper);

            if (translations != null && !translations.isEmpty()) {
                availableLanguages = translations.stream()
                    .map(ArticleTranslation::getLanguage)
                    .filter(lang -> lang != null && !lang.trim().isEmpty())
                    .distinct()
                    .collect(Collectors.toList());

                log.debug("成功获取文章可用翻译语言，文章ID: {}, 语言列表: {}", articleId, availableLanguages);
            } else {
                log.debug("文章暂无翻译，文章ID: {}", articleId);
            }

        } catch (Exception e) {
            log.error("获取文章可用翻译语言失败，文章ID: {}, 错误: {}", articleId, e.getMessage(), e);
        }

        return availableLanguages;
    }

    @Override
    public Map<String, Object> saveManualTranslation(Integer articleId, String targetLanguage,
                                                   String translatedTitle, String translatedContent) {
        Map<String, Object> result = new HashMap<>();

        if (articleId == null || targetLanguage == null || targetLanguage.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "参数无效：文章ID或目标语言不能为空");
            return result;
        }

        if (translatedTitle == null || translatedTitle.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "翻译标题不能为空");
            return result;
        }

        if (translatedContent == null || translatedContent.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "翻译内容不能为空");
            return result;
        }

        try {
            // 检查文章是否存在
            Article article = articleMapper.selectById(articleId);
            if (article == null) {
                result.put("success", false);
                result.put("message", "文章不存在");
                return result;
            }

            // 保存手动翻译
            boolean success = saveOrUpdateTranslation(articleId, targetLanguage,
                                                    translatedTitle.trim(), translatedContent.trim());

            if (success) {
                result.put("success", true);
                result.put("message", "翻译保存成功");
                log.info("手动翻译保存成功，文章ID: {}, 目标语言: {}", articleId, targetLanguage);
            } else {
                result.put("success", false);
                result.put("message", "翻译保存失败");
                log.error("手动翻译保存失败，文章ID: {}, 目标语言: {}", articleId, targetLanguage);
            }

        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "保存翻译时发生错误: " + e.getMessage());
            log.error("手动翻译保存异常，文章ID: {}, 目标语言: {}", articleId, targetLanguage, e);
        }

        return result;
    }

    @Override
    public boolean shouldSkipAutoTranslation(Integer articleId, String targetLanguage) {
        if (articleId == null || targetLanguage == null || targetLanguage.trim().isEmpty()) {
            return false;
        }

        try {
            // 检查是否已存在翻译记录（无论是手动还是自动生成的）
            LambdaQueryWrapper<ArticleTranslation> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(ArticleTranslation::getArticleId, articleId)
                       .eq(ArticleTranslation::getLanguage, targetLanguage);

            ArticleTranslation existingTranslation = articleTranslationMapper.selectOne(queryWrapper);

            // 如果存在翻译记录，则跳过自动翻译
            boolean shouldSkip = existingTranslation != null;

            if (shouldSkip) {
                log.debug("跳过自动翻译，文章ID: {}, 目标语言: {} (已存在翻译记录)", articleId, targetLanguage);
            }

            return shouldSkip;

        } catch (Exception e) {
            log.error("检查是否跳过自动翻译时发生异常，文章ID: {}, 目标语言: {}", articleId, targetLanguage, e);
            return false;
        }
    }

    @Override
    public String translateText(String text, String sourceLang, String targetLang) {
        if (text == null || text.trim().isEmpty()) {
            return text;
        }
        
        try {
            // 构建请求参数，设置默认中文转英文
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("text", text);
            requestBody.put("source_lang", sourceLang != null ? sourceLang : "zh");  // 默认中文
            requestBody.put("target_lang", targetLang != null ? targetLang : "en");  // 默认英文
            
            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Service", "poetize-java");
            headers.set("User-Agent", "poetize-java/1.0.0");
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            // 调用Python端翻译服务
            String url = pythonServiceUrl + "/api/translation/translate";
            ResponseEntity<Map> response = restTemplate.postForEntity(url, request, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                Integer code = (Integer) responseBody.get("code");
                
                if (code != null && code == 200) {
                    Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
                    if (data != null) {
                        String translatedText = (String) data.get("translated_text");
                        if (translatedText != null && !translatedText.trim().isEmpty() &&
                            !translatedText.equals(text)) {
                            log.debug("翻译成功: {} -> {}",
                                    text.length() > 50 ? text.substring(0, 50) + "..." : text,
                                    translatedText.length() > 50 ? translatedText.substring(0, 50) + "..." : translatedText);
                            return translatedText;
                        } else {
                            log.warn("翻译服务返回无效结果，原文: {}, 翻译结果: {}",
                                   text.length() > 50 ? text.substring(0, 50) + "..." : text,
                                   translatedText != null ? (translatedText.length() > 50 ? translatedText.substring(0, 50) + "..." : translatedText) : "null");
                        }
                    }
                } else {
                    String message = (String) responseBody.get("message");
                    log.warn("翻译失败: {}", message);
                }
            }
            
            log.error("翻译服务返回异常响应，原文: {}", text.length() > 50 ? text.substring(0, 50) + "..." : text);
            return null; // 翻译失败时返回null，而不是原文

        } catch (Exception e) {
            log.error("调用翻译服务失败: {}", e.getMessage(), e);
            return null; // 翻译失败时返回null，而不是原文
        }
    }

    @Override
    public void deleteArticleTranslation(Integer articleId) {
        try {
            LambdaQueryWrapper<ArticleTranslation> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(ArticleTranslation::getArticleId, articleId);
            int rows = articleTranslationMapper.delete(queryWrapper);
            log.info("仅删除文章翻译，无重译，文章ID: {}, 行数: {}", articleId, rows);
        } catch (Exception e) {
            log.error("删除文章翻译失败，文章ID: {}", articleId, e);
        }
    }

    /**
     * 获取翻译语言配置
     * @return Map包含source和target语言配置
     */
    private Map<String, String> getLanguageConfig() {
        Map<String, String> config = new HashMap<>();

        try {
            // 调用Python服务获取语言配置
            String url = pythonServiceUrl + "/api/translation/default-lang";
            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> responseBody = response.getBody();
                Integer code = (Integer) responseBody.get("code");

                if (code != null && code == 200) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
                    if (data != null) {
                        String sourceLang = (String) data.get("default_source_lang");
                        String targetLang = (String) data.get("default_target_lang");

                        config.put("source", sourceLang != null && !sourceLang.trim().isEmpty() ? sourceLang : "zh");
                        config.put("target", targetLang != null && !targetLang.trim().isEmpty() ? targetLang : "en");

                        log.debug("成功获取翻译语言配置: {} -> {}", config.get("source"), config.get("target"));
                        return config;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("获取翻译语言配置失败，使用默认值: {}", e.getMessage());
        }

        // 返回默认配置：中文 -> 英文
        config.put("source", "zh");
        config.put("target", "en");
        return config;
    }

    @Override
    public Map<String, String> getTranslationLanguageConfig() {
        return getLanguageConfig();
    }

}