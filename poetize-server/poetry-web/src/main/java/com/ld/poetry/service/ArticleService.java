package com.ld.poetry.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.entity.Article;
import com.baomidou.mybatisplus.extension.service.IService;
import com.ld.poetry.vo.ArticleVO;
import com.ld.poetry.vo.BaseRequestVO;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 文章表 服务类
 * </p>
 *
 * @author sara
 * @since 2021-08-13
 */
public interface ArticleService extends IService<Article> {

    PoetryResult saveArticle(ArticleVO articleVO);

    PoetryResult deleteArticle(Integer id);

    PoetryResult updateArticle(ArticleVO articleVO);

    PoetryResult<Page> listArticle(BaseRequestVO baseRequestVO);

    PoetryResult<ArticleVO> getArticleById(Integer id, String password);

    PoetryResult<Page> listAdminArticle(BaseRequestVO baseRequestVO, Boolean isBoss);

    PoetryResult<ArticleVO> getArticleByIdForUser(Integer id);

    PoetryResult<Map<Integer, List<ArticleVO>>> listSortArticle();

    /**
     * 生成文章摘要
     * @param content 文章内容
     * @param maxLength 最大长度
     * @return 摘要结果
     */
    PoetryResult<String> generateSummary(String content, Integer maxLength);

    /**
     * 获取热门文章列表（智能热度算法排序）
     * 综合考虑浏览量、点赞数、评论数、发布时间、互动率等多个因素
     * @return 热门文章列表
     */
    PoetryResult<List<ArticleVO>> getArticlesByLikesTop();
}
