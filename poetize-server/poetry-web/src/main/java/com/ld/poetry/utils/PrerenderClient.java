package com.ld.poetry.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 调用 prerender-worker 的简易客户端
 */
@Slf4j
@Component
public class PrerenderClient {

    /**
     * prerender-worker 服务基础地址，默认 docker compose 网络内
     * 结尾保持 /render，POST 用于提交渲染，DELETE /render/{id} 用于删除
     */
    @Value("${PRERENDER_URL:http://poetize-prerender:4000/render}")
    private String prerenderUrl;

    /**
     * prerender-worker 页面渲染地址
     */
    @Value("${PRERENDER_PAGES_URL:http://poetize-prerender:4000/render/pages}")
    private String prerenderPagesUrl;

    @Autowired
    private RestTemplate restTemplate;

    /**
     * 创建带有内部服务标识的HTTP头
     */
    private HttpHeaders createInternalServiceHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Service", "poetize-java");
        headers.set("User-Agent", "poetize-java/1.0.0");
        return headers;
    }

    public void renderArticle(Integer id) {
        if (id == null) {
            return;
        }
        renderArticles(Collections.singletonList(id));
    }

    /**
     * 渲染指定文章的指定语言版本
     * @param id 文章ID
     * @param languages 要渲染的语言列表
     */
    public void renderArticleWithLanguages(Integer id, List<String> languages) {
        if (id == null) {
            return;
        }
        renderArticlesWithLanguages(Collections.singletonList(id), languages);
    }

    public void renderArticles(List<Integer> ids) {
        try {
            HttpHeaders headers = createInternalServiceHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> body = new HashMap<>();
            body.put("ids", ids);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            restTemplate.postForEntity(prerenderUrl, request, String.class);
            log.info("已提交文章 {} 到 prerender-worker", ids);
        } catch (Exception e) {
            log.warn("调用 prerender-worker 失败: {}", e.getMessage());
        }
    }

    /**
     * 渲染指定文章列表的指定语言版本
     * @param ids 文章ID列表
     * @param languages 要渲染的语言列表
     */
    public void renderArticlesWithLanguages(List<Integer> ids, List<String> languages) {
        try {
            HttpHeaders headers = createInternalServiceHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            Map<String, Object> body = new HashMap<>();
            body.put("ids", ids);
            body.put("languages", languages);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            restTemplate.postForEntity(prerenderUrl, request, String.class);
            log.info("已提交文章 {} 到 prerender-worker，渲染语言: {}", ids, languages);
        } catch (Exception e) {
            log.warn("调用 prerender-worker 失败: {}", e.getMessage());
        }
    }

    /**
     * 删除指定文章的预渲染静态文件
     */
    public void deleteArticle(Integer id) {
        if (id == null) {
            return;
        }
        try {
            String deleteUrl = prerenderUrl + "/" + id; // DELETE /render/{id}
            HttpEntity<?> entity = new HttpEntity<>(createInternalServiceHeaders());
            restTemplate.exchange(deleteUrl, org.springframework.http.HttpMethod.DELETE, entity, String.class);
            log.info("已请求 prerender-worker 删除文章 {} 的静态文件", id);
        } catch (Exception e) {
            log.warn("删除 prerender 静态文件失败: {}", e.getMessage());
        }
    }

    /**
     * 检查预渲染服务健康状态
     */
    public void checkHealth() {
        String healthUrl = prerenderUrl.replace("/render", "/health");
        try {
            HttpEntity<?> entity = new HttpEntity<>(createInternalServiceHeaders());
            restTemplate.exchange(healthUrl, org.springframework.http.HttpMethod.GET, entity, String.class);
        } catch (Exception e) {
            log.warn("预渲染服务健康检查失败: {}", e.getMessage());
            throw new RuntimeException("预渲染服务不可用", e);
        }
    }

    /**
     * 渲染首页
     */
    public void renderHomePage() {
        try {
            renderPage("home", null);
            log.info("已提交首页到 prerender-worker");
        } catch (Exception e) {
            log.warn("渲染首页失败: {}", e.getMessage());
        }
    }

    /**
     * 渲染百宝箱页面
     */
    public void renderFavoritePage() {
        try {
            renderPage("favorite", null);
            log.info("已提交百宝箱页面到 prerender-worker");
        } catch (Exception e) {
            log.warn("渲染百宝箱页面失败: {}", e.getMessage());
        }
    }

    /**
     * 渲染分类索引页面（显示所有分类列表）
     */
    public void renderSortIndexPage() {
        try {
            renderPage("sort", null); // 不传sortId参数，渲染分类索引页面
            log.info("已提交分类索引页面到 prerender-worker");
        } catch (Exception e) {
            log.warn("渲染分类索引页面失败: {}", e.getMessage());
        }
    }

    /**
     * @deprecated 使用 renderSortIndexPage() 替代
     */
    @Deprecated
    public void renderDefaultSortPage() {
        renderSortIndexPage();
    }

    /**
     * 渲染单个分类详情页面
     */
    public void renderCategoryPage(Integer sortId) {
        renderCategoryPage(sortId, null);
    }

    /**
     * @deprecated 使用 renderCategoryPage() 替代
     */
    @Deprecated
    public void renderSortPage(Integer sortId) {
        renderCategoryPage(sortId);
    }

    /**
     * 批量渲染所有分类详情页面
     */
    public void renderAllCategoryPages(List<Integer> sortIds) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("sortIds", sortIds);
            renderPage("allSorts", params);
            log.info("已提交所有分类详情页面到 prerender-worker (共{}个)", sortIds.size());
        } catch (Exception e) {
            log.warn("渲染所有分类详情页面失败: {}", e.getMessage());
        }
    }

    /**
     * @deprecated 使用 renderAllCategoryPages() 替代
     */
    @Deprecated
    public void renderAllSortPages(List<Integer> sortIds) {
        renderAllCategoryPages(sortIds);
    }

    /**
     * 渲染主要页面（首页和百宝箱）
     */
    public void renderMainPages() {
        try {
            renderHomePage();
            Thread.sleep(500); // 避免并发过高
            renderFavoritePage();
        } catch (Exception e) {
            log.warn("渲染主要页面失败: {}", e.getMessage());
        }
    }

    /**
     * 渲染指定分类详情页面
     * @param sortId 分类ID
     * @param labelId 标签ID (可选)
     */
    public void renderCategoryPage(Integer sortId, Integer labelId) {
        if (sortId == null) {
            return;
        }
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("sortId", sortId);
            if (labelId != null) {
                params.put("labelId", labelId);
            }
            renderPage("sort", params);
            log.info("已提交分类详情页面 sortId={}, labelId={} 到 prerender-worker", sortId, labelId);
        } catch (Exception e) {
            log.warn("渲染分类详情页面 sortId={}, labelId={} 失败: {}", sortId, labelId, e.getMessage());
        }
    }

    /**
     * @deprecated 使用 renderCategoryPage() 替代
     */
    @Deprecated
    public void renderSortPage(Integer sortId, Integer labelId) {
        renderCategoryPage(sortId, labelId);
    }

    /**
     * 通用页面渲染方法
     * @param type 页面类型 (home, favorite, sort)
     * @param params 参数(sort页面需要sortId和labelId)
     */
    private void renderPage(String type, Map<String, Object> params) {
        try {
            HttpHeaders headers = createInternalServiceHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            Map<String, Object> body = new HashMap<>();
            body.put("type", type);
            if (params != null) {
                body.put("params", params);
            }
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            restTemplate.postForEntity(prerenderPagesUrl, request, String.class);
        } catch (Exception e) {
            log.warn("渲染页面 {} 失败: {}", type, e.getMessage());
            throw e;
        }
    }

    /**
     * 删除首页的预渲染文件
     */
    public void deleteHomePage() {
        deletePage("home", null, null);
    }

    /**
     * 删除百宝箱页面的预渲染文件
     */
    public void deleteFavoritePage() {
        deletePage("favorite", null, null);
    }

    /**
     * 删除分类索引页面的预渲染文件
     */
    public void deleteSortIndexPage() {
        deletePage("sort", null, null);
    }

    /**
     * @deprecated 使用 deleteSortIndexPage() 替代
     */
    @Deprecated
    public void deleteDefaultSortPage() {
        deleteSortIndexPage();
    }

    /**
     * 删除指定分类详情页面的预渲染文件
     * @param sortId 分类ID
     */
    public void deleteCategoryPage(Integer sortId) {
        if (sortId == null) {
            return;
        }
        deletePage("sort", sortId, null);
    }

    /**
     * @deprecated 使用 deleteCategoryPage() 替代
     */
    @Deprecated
    public void deleteSortPage(Integer sortId) {
        deleteCategoryPage(sortId);
    }

    /**
     * 删除指定分类详情页面的预渲染文件（含标签）
     * @param sortId 分类ID
     * @param labelId 标签ID
     */
    public void deleteCategoryPage(Integer sortId, Integer labelId) {
        if (sortId == null) {
            return;
        }
        deletePage("sort", sortId, labelId);
    }

    /**
     * @deprecated 使用 deleteCategoryPage() 替代
     */
    @Deprecated
    public void deleteSortPage(Integer sortId, Integer labelId) {
        deleteCategoryPage(sortId, labelId);
    }

    /**
     * 通用页面删除方法
     * @param type 页面类型
     * @param sortId 分类ID (仅sort页面需要)
     * @param labelId 标签ID (可选)
     */
    private void deletePage(String type, Integer sortId, Integer labelId) {
        try {
            StringBuilder deleteUrl = new StringBuilder(prerenderPagesUrl);
            deleteUrl.append("/").append(type);
            
            if ("sort".equals(type) && sortId != null) {
                deleteUrl.append("?sortId=").append(sortId);
                if (labelId != null) {
                    deleteUrl.append("&labelId=").append(labelId);
                }
            }
            
            HttpEntity<?> entity = new HttpEntity<>(createInternalServiceHeaders());
            restTemplate.exchange(deleteUrl.toString(), org.springframework.http.HttpMethod.DELETE, entity, String.class);
            log.info("已请求删除页面 {} 的预渲染文件", type);
        } catch (Exception e) {
            log.warn("删除页面 {} 预渲染文件失败: {}", type, e.getMessage());
        }
    }

    /**
     * 清除所有主要页面的预渲染文件
     */
    public void deleteMainPages() {
        deleteHomePage();
        deleteFavoritePage();
    }

    /**
     * 清理预渲染服务缓存
     * 用于解决删除文章后新建文章无法访问的问题
     */
    public void clearPrerenderCache() {
        try {
            String clearCacheUrl = prerenderUrl.replace("/render", "/clear-cache");
            HttpEntity<?> entity = new HttpEntity<>(createInternalServiceHeaders());
            restTemplate.exchange(clearCacheUrl, org.springframework.http.HttpMethod.POST, entity, String.class);
            log.info("已请求 prerender-worker 清理缓存");
        } catch (Exception e) {
            log.warn("清理预渲染缓存失败: {}", e.getMessage());
        }
    }

    /**
     * 重启预渲染服务路由
     * 用于解决删除文章后路由冲突的问题
     */
    public void restartPrerenderRoutes() {
        try {
            String restartUrl = prerenderUrl.replace("/render", "/restart-routes");
            HttpEntity<?> entity = new HttpEntity<>(createInternalServiceHeaders());
            restTemplate.exchange(restartUrl, org.springframework.http.HttpMethod.POST, entity, String.class);
            log.info("已请求 prerender-worker 重启路由");
        } catch (Exception e) {
            log.warn("重启预渲染路由失败: {}", e.getMessage());
        }
    }
} 