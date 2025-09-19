package com.ld.poetry.event;

import com.ld.poetry.utils.PrerenderClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 文章事件监听器
 * 在数据库事务提交后执行预渲染操作
 */
@Component
@Slf4j
public class ArticleEventListener {
    
    @Autowired
    private PrerenderClient prerenderClient;
    
    // 用于去重的延迟调度器
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    
    // 记录待处理的预渲染任务，用于去重
    private final ConcurrentHashMap<Integer, Runnable> pendingRenderTasks = new ConcurrentHashMap<>();
    
    /**
     * 监听文章保存事件，在事务提交后执行预渲染
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Async
    public void handleArticleSavedEvent(ArticleSavedEvent event) {
        log.info("收到文章事件: ID={}, 操作={}, 可见={}", 
                 event.getArticleId(), event.getOperationType(), event.getViewStatus());
        
        try {
            switch (event.getOperationType()) {
                case "CREATE":
                    if (Boolean.TRUE.equals(event.getViewStatus())) {
                        // 新建可见文章，使用延迟去重机制进行预渲染
                        scheduleRenderWithDeduplication(event.getArticleId(), event.getSortId(), "CREATE");
                        log.info("已安排新文章预渲染任务: ID={}", event.getArticleId());
                    } else {
                        // 新建不可见文章，不需要预渲染
                        log.info("新建文章不可见，跳过预渲染: ID={}", event.getArticleId());
                    }
                    break;
                    
                case "UPDATE":
                    if (Boolean.TRUE.equals(event.getViewStatus())) {
                        // 文章可见，使用延迟去重机制进行预渲染
                        scheduleRenderWithDeduplication(event.getArticleId(), event.getSortId(), "RENDER");
                        log.info("已安排文章更新预渲染任务: ID={}", event.getArticleId());
                    } else {
                        // 文章不可见，删除预渲染文件
                        scheduleRenderWithDeduplication(event.getArticleId(), event.getSortId(), "DELETE");
                        log.info("已安排文章预渲染删除任务: ID={}", event.getArticleId());
                    }
                    break;
                    
                case "DELETE":
                    // 删除操作立即执行，不需要去重
                    scheduleRenderWithDeduplication(event.getArticleId(), event.getSortId(), "DELETE");
                    log.info("已安排文章删除预渲染任务: ID={}, 分类ID={}", event.getArticleId(), event.getSortId());
                    break;
                    
                default:
                    log.warn("未知的文章操作类型: {}", event.getOperationType());
            }
        } catch (Exception e) {
            log.warn("文章预渲染任务安排失败: ID={}, 操作={}, 错误={}", 
                     event.getArticleId(), event.getOperationType(), e.getMessage());
        }
    }
    
    /**
     * 延迟去重调度预渲染任务
     * 如果短时间内同一文章有多次渲染请求，只执行最后一次
     */
    private void scheduleRenderWithDeduplication(Integer articleId, Integer sortId, String action) {
        Runnable renderTask = () -> {
            try {
                // 从待处理任务中移除
                pendingRenderTasks.remove(articleId);
                
                if ("CREATE".equals(action)) {
                    // 新建文章的预渲染处理
                    
                    // 先清理预渲染缓存，确保新文章能正常访问
                    prerenderClient.clearPrerenderCache();
                    log.info("新建文章前已清理预渲染缓存: ID={}", articleId);
                    
                    // 执行文章预渲染
                    prerenderClient.renderArticle(articleId);
                    log.info("新建文章预渲染完成: ID={}", articleId);
                    
                    // 重新渲染首页（显示最新文章列表）
                    prerenderClient.renderHomePage();
                    log.info("新建文章后已重新渲染首页: ID={}", articleId);
                    
                    // 重新渲染分类索引页面（显示所有分类的文章统计）
                    prerenderClient.renderCategoryIndexPage();
                    log.info("新建文章后已重新渲染分类索引页面: ID={}", articleId);
                    
                    // 如果有分类ID，重新渲染对应的分类页面
                    if (sortId != null) {
                        prerenderClient.renderCategoryPage(sortId);
                        log.info("新建文章后已重新渲染分类页面: ID={}, 分类ID={}", articleId, sortId);
                    }
                    
                    log.info("延迟新建文章预渲染执行完成: ID={}, 分类ID={}", articleId, sortId);
                    
                } else if ("RENDER".equals(action)) {
                    // 更新文章的预渲染处理
                    
                    // 执行预渲染
                    prerenderClient.renderArticle(articleId);
                    
                    // 重新渲染首页（显示最新文章列表）
                    prerenderClient.renderHomePage();
                    log.info("文章更新后已重新渲染首页: ID={}", articleId);
                    
                    // 重新渲染分类索引页面（显示所有分类的文章统计）
                    prerenderClient.renderCategoryIndexPage();
                    log.info("文章更新后已重新渲染分类索引页面: ID={}", articleId);
                    
                    // 如果有分类ID，重新渲染对应的分类页面
                    if (sortId != null) {
                        prerenderClient.renderCategoryPage(sortId);
                        log.info("文章更新后已重新渲染分类页面: ID={}, 分类ID={}", articleId, sortId);
                    }
                    
                    log.info("延迟文章更新预渲染执行完成: ID={}, 分类ID={}", articleId, sortId);
                } else if ("DELETE".equals(action)) {
                    // 执行删除
                    prerenderClient.deleteArticle(articleId);
                    
                    // 清理预渲染服务缓存，解决删除后新建文章无法访问的问题
                    prerenderClient.clearPrerenderCache();
                    log.info("删除文章后已清理预渲染缓存: ID={}", articleId);
                    
                    // 删除文章后，必须重新渲染首页（显示最新文章列表）
                    prerenderClient.renderHomePage();
                    log.info("删除文章后已重新渲染首页: ID={}", articleId);
                    
                    // 重新渲染分类索引页面（显示所有分类的文章统计）
                    prerenderClient.renderCategoryIndexPage();
                    log.info("删除文章后已重新渲染分类索引页面: ID={}", articleId);
                    
                    // 如果有分类ID，重新渲染对应的分类页面
                    if (sortId != null) {
                        prerenderClient.renderCategoryPage(sortId);
                        log.info("删除文章后已重新渲染分类页面: ID={}, 分类ID={}", articleId, sortId);
                    } else {
                        log.warn("删除文章时分类ID为空，无法重新渲染分类页面: ID={}", articleId);
                    }
                    
                    log.info("延迟预渲染删除执行完成: ID={}, 分类ID={}", articleId, sortId);
                }
            } catch (Exception e) {
                log.warn("延迟预渲染执行失败: ID={}, 操作={}, 错误={}", 
                         articleId, action, e.getMessage());
            }
        };
        
        // 如果已经有待处理的任务，先取消之前的任务
        Runnable previousTask = pendingRenderTasks.put(articleId, renderTask);
        if (previousTask != null) {
            log.info("发现重复预渲染任务，已取消前一个任务: ID={}", articleId);
        }
        
        // 延迟2秒执行，给翻译服务一点时间
        scheduler.schedule(renderTask, 2, TimeUnit.SECONDS);
    }
} 