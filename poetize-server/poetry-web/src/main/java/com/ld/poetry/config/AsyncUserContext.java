package com.ld.poetry.config;

import com.ld.poetry.entity.User;
import lombok.extern.slf4j.Slf4j;

/**
 * 异步任务用户上下文管理类
 * 负责在异步线程中传递和管理用户信息
 */
@Slf4j
public class AsyncUserContext {
    
    private static final ThreadLocal<User> userThreadLocal = new ThreadLocal<>();
    private static final ThreadLocal<String> tokenThreadLocal = new ThreadLocal<>();
    private static final ThreadLocal<Long> startTimeThreadLocal = new ThreadLocal<>();
    
    /**
     * 设置当前线程的用户信息
     * @param user 用户对象
     */
    public static void setUser(User user) {
        userThreadLocal.set(user);
        log.debug("异步线程设置用户上下文: {}", user != null ? user.getUsername() : "null");
    }
    
    /**
     * 设置当前线程的用户Token
     * @param token 用户Token
     */
    public static void setToken(String token) {
        tokenThreadLocal.set(token);
        log.debug("异步线程设置Token上下文: {}", token != null ? token.substring(0, Math.min(token.length(), 10)) + "..." : "null");
    }
    
    /**
     * 设置任务开始时间
     */
    public static void setStartTime() {
        startTimeThreadLocal.set(System.currentTimeMillis());
    }
    
    /**
     * 获取当前线程的用户信息
     * @return 用户对象
     */
    public static User getUser() {
        return userThreadLocal.get();
    }
    
    /**
     * 获取当前线程的用户Token
     * @return 用户Token
     */
    public static String getToken() {
        return tokenThreadLocal.get();
    }
    
    /**
     * 获取任务执行时间
     * @return 执行时间（毫秒）
     */
    public static Long getExecutionTime() {
        Long startTime = startTimeThreadLocal.get();
        return startTime != null ? System.currentTimeMillis() - startTime : null;
    }
    
    /**
     * 清理当前线程的所有上下文信息
     */
    public static void clear() {
        User user = userThreadLocal.get();
        Long executionTime = getExecutionTime();
        
        if (user != null && executionTime != null) {
            log.debug("异步任务完成 - 用户: {}, 执行时间: {}ms", user.getUsername(), executionTime);
        }
        
        userThreadLocal.remove();
        tokenThreadLocal.remove();
        startTimeThreadLocal.remove();
        
        log.debug("异步线程上下文已清理");
    }
    
    /**
     * 检查当前线程是否有用户上下文
     * @return 是否有用户上下文
     */
    public static boolean hasUserContext() {
        return userThreadLocal.get() != null;
    }
    
    /**
     * 获取当前用户ID（如果存在）
     * @return 用户ID
     */
    public static Integer getCurrentUserId() {
        User user = getUser();
        return user != null ? user.getId() : null;
    }
    
    /**
     * 获取当前用户名（如果存在）
     * @return 用户名
     */
    public static String getCurrentUsername() {
        User user = getUser();
        return user != null ? user.getUsername() : null;
    }
}