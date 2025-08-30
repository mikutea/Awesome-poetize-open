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
                case "UPDATE":
                    if (Boolean.TRUE.equals(event.getViewStatus())) {
                        // 文章可见，使用延迟去重机制进行预渲染
                        scheduleRenderWithDeduplication(event.getArticleId(), event.getSortId(), "RENDER");
                        log.info("已安排文章预渲染任务: ID={}", event.getArticleId());
                    } else {
                        // 文章不可见，删除预渲染文件
                        scheduleRenderWithDeduplication(event.getArticleId(), event.getSortId(), "DELETE");
                        log.info("已安排文章预渲染删除任务: ID={}", event.getArticleId());
                    }
                    break;
                    
                case "DELETE":
                    // 删除操作立即执行，不需要去重
                    scheduleRenderWithDeduplication(event.getArticleId(), event.getSortId(), "DELETE");
                    log.info("已安排文章删除预渲染任务: ID={}", event.getArticleId());
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
                
                if ("RENDER".equals(action)) {
                    // 执行预渲染
                    prerenderClient.renderArticle(articleId);
                    prerenderClient.renderHomePage();
                    if (sortId != null) {
                        prerenderClient.renderCategoryPage(sortId);
                    }
                    log.info("延迟预渲染执行完成: ID={}", articleId);
                } else if ("DELETE".equals(action)) {
                    // 执行删除
                    prerenderClient.deleteArticle(articleId);
                    prerenderClient.renderHomePage();
                    if (sortId != null) {
                        prerenderClient.renderCategoryPage(sortId);
                    }
                    log.info("延迟预渲染删除执行完成: ID={}", articleId);
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