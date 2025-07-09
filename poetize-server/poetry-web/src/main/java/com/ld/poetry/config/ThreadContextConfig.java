package com.ld.poetry.config;

import com.ld.poetry.constants.CommonConst;
import com.ld.poetry.entity.User;
import com.ld.poetry.utils.PoetryUtil;
import com.ld.poetry.utils.cache.PoetryCache;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.servlet.http.HttpServletRequest;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * 线程上下文配置类
 * 解决线程间用户信息传递问题
 */
@Configuration
@Slf4j
public class ThreadContextConfig implements AsyncConfigurer {

    /**
     * 上下文传递装饰器
     * 负责在线程间传递用户Token和请求上下文
     */
    @Bean
    public TaskDecorator contextPropagatingTaskDecorator() {
        return task -> {
            // 获取主线程的用户Token（如果需要的话）
            String token = null;
            User currentUser = null;
            
            try {
                // 安全地获取Token，避免RequestAttributes异常
                RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
                if (requestAttributes != null) {
                    HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
                    if (request != null) {
                        token = request.getHeader(CommonConst.TOKEN_HEADER);
                        if ("null".equals(token)) {
                            token = null;
                        }
                        if (StringUtils.hasText(token)) {
                            currentUser = (User) PoetryCache.get(token);
                        }
                    }
                }
            } catch (Exception e) {
                // 如果获取Token失败，记录日志但不影响异步任务执行
                log.debug("异步任务中获取用户Token失败: {}", e.getMessage());
            }
            
            // 保存最终用户对象，供内部类使用
            final User finalUser = currentUser;
            final String finalToken = token;
            
            return () -> {
                try {
                    // 设置异步任务生命周期管理
                    AsyncUserContext.setStartTime();
                    
                    // 设置用户上下文到异步线程
                    if (finalUser != null) {
                        AsyncUserContext.setUser(finalUser);
                    }
                    if (finalToken != null) {
                        AsyncUserContext.setToken(finalToken);
                    }
                    
                    log.debug("异步任务开始 - 用户: {}, Token: {}", 
                            finalUser != null ? finalUser.getUsername() : "匿名",
                            finalToken != null ? finalToken.substring(0, Math.min(finalToken.length(), 10)) + "..." : "无");
                    
                    // 执行异步任务
                    task.run();
                    
                    log.debug("异步任务执行成功");
                } catch (Exception e) {
                    log.error("异步任务执行失败 - 用户: {}, 异常: {}", 
                            AsyncUserContext.getCurrentUsername(), e.getMessage(), e);
                    throw e;
                } finally {
                    // 清理异步任务上下文
                    AsyncUserContext.clear();
                }
            };
        };
    }

    /**
     * 配置默认的异步执行器
     * 所有@Async注解的方法将使用此执行器
     */
    @Override
    @Bean(name = "taskExecutor")
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // 核心线程数
        executor.setCorePoolSize(5);
        // 最大线程数
        executor.setMaxPoolSize(10);
        // 队列容量
        executor.setQueueCapacity(25);
        // 线程名前缀
        executor.setThreadNamePrefix("Poetize-Async-");
        // 拒绝策略
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 等待所有任务结束后再关闭线程池
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // 应用上下文传递装饰器
        executor.setTaskDecorator(contextPropagatingTaskDecorator());
        
        executor.initialize();
        return executor;
    }

    /**
     * 异步方法异常处理器
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (ex, method, params) -> {
            log.error("异步方法执行异常 - 方法: {}, 参数: {}, 异常: {}", 
                    method.getName(), 
                    params, 
                    ex.getMessage(), 
                    ex);
        };
    }
}