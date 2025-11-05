package com.ld.poetry.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 锁管理工具类
 * 用于替代 String.intern() 的不安全用法，避免内存泄漏
 * 支持读写锁，优化读多写少场景的并发性能
 */
@Slf4j
@Component
public class LockManager {
    
    /**
     * 存储普通锁对象的Map
     * key: 锁的标识符
     * value: 对应的锁对象
     */
    private final ConcurrentHashMap<String, Lock> lockMap = new ConcurrentHashMap<>();
    
    /**
     * 存储读写锁对象的Map
     * key: 锁的标识符
     * value: 对应的读写锁对象
     */
    private final ConcurrentHashMap<String, ReadWriteLock> rwLockMap = new ConcurrentHashMap<>();
    
    /**
     * 最大锁数量限制，防止无限增长
     */
    private static final int MAX_LOCKS = 10000;
    
    /**
     * 获取指定key对应的锁对象
     * 如果不存在则创建，线程安全
     * 
     * @param key 锁的唯一标识符
     * @return 锁对象
     */
    public Lock getLock(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Lock key cannot be null or empty");
        }
        
        // 检查是否超过最大锁数量
        if (lockMap.size() >= MAX_LOCKS) {
            log.warn("[LockManager] 锁数量达到上限 {}, 当前key: {}", MAX_LOCKS, key);
            // 可以考虑清理一些旧的锁，或者使用LRU策略
            // 这里暂时使用已有锁或创建新锁
        }
        
        // 使用 computeIfAbsent 保证线程安全且避免重复创建
        return lockMap.computeIfAbsent(key, k -> {
            return new ReentrantLock();
        });
    }
    
    /**
     * 执行带锁的操作
     * 
     * @param key 锁的唯一标识符
     * @param action 需要在锁保护下执行的操作
     */
    public void executeWithLock(String key, Runnable action) {
        Lock lock = getLock(key);
        lock.lock();
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 执行带锁的操作并返回结果
     * 
     * @param key 锁的唯一标识符
     * @param supplier 需要在锁保护下执行的操作
     * @param <T> 返回类型
     * @return 执行结果
     */
    public <T> T executeWithLock(String key, java.util.function.Supplier<T> supplier) {
        Lock lock = getLock(key);
        lock.lock();
        try {
            return supplier.get();
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 清理指定key的锁
     * 
     * @param key 锁的唯一标识符
     */
    public void removeLock(String key) {
        Lock removed = lockMap.remove(key);
        if (removed != null) {
        }
    }
    
    /**
     * 获取指定key对应的读写锁对象
     * 如果不存在则创建，线程安全
     * 
     * @param key 锁的唯一标识符
     * @return 读写锁对象
     */
    public ReadWriteLock getReadWriteLock(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("Lock key cannot be null or empty");
        }
        
        // 检查是否超过最大锁数量
        if (rwLockMap.size() >= MAX_LOCKS) {
            log.warn("[LockManager] 读写锁数量达到上限 {}, 当前key: {}", MAX_LOCKS, key);
        }
        
        // 使用 computeIfAbsent 保证线程安全且避免重复创建
        return rwLockMap.computeIfAbsent(key, k -> {
            return new ReentrantReadWriteLock();
        });
    }
    
    /**
     * 执行带读锁的操作并返回结果
     * 适用于读多写少的场景，多个读操作可以并发执行
     * 
     * @param key 锁的唯一标识符
     * @param supplier 需要在读锁保护下执行的操作
     * @param <T> 返回类型
     * @return 执行结果
     */
    public <T> T executeWithReadLock(String key, java.util.function.Supplier<T> supplier) {
        ReadWriteLock rwLock = getReadWriteLock(key);
        Lock readLock = rwLock.readLock();
        readLock.lock();
        try {
            return supplier.get();
        } finally {
            readLock.unlock();
        }
    }
    
    /**
     * 执行带写锁的操作并返回结果
     * 写锁是独占的，执行期间其他读写操作都会被阻塞
     * 
     * @param key 锁的唯一标识符
     * @param supplier 需要在写锁保护下执行的操作
     * @param <T> 返回类型
     * @return 执行结果
     */
    public <T> T executeWithWriteLock(String key, java.util.function.Supplier<T> supplier) {
        ReadWriteLock rwLock = getReadWriteLock(key);
        Lock writeLock = rwLock.writeLock();
        writeLock.lock();
        try {
            return supplier.get();
        } finally {
            writeLock.unlock();
        }
    }
    
    /**
     * 执行带写锁的操作（无返回值）
     * 
     * @param key 锁的唯一标识符
     * @param action 需要在写锁保护下执行的操作
     */
    public void executeWithWriteLock(String key, Runnable action) {
        ReadWriteLock rwLock = getReadWriteLock(key);
        Lock writeLock = rwLock.writeLock();
        writeLock.lock();
        try {
            action.run();
        } finally {
            writeLock.unlock();
        }
    }
    
    /**
     * 清理指定key的读写锁
     * 
     * @param key 锁的唯一标识符
     */
    public void removeReadWriteLock(String key) {
        ReadWriteLock removed = rwLockMap.remove(key);
        if (removed != null) {
        }
    }
    
    /**
     * 清理所有锁
     */
    public void clearAllLocks() {
        int size = lockMap.size() + rwLockMap.size();
        lockMap.clear();
        rwLockMap.clear();
        log.info("[LockManager] 清理所有锁，数量: {}", size);
    }
    
    /**
     * 获取当前锁的数量
     * 
     * @return 锁的数量（包括普通锁和读写锁）
     */
    public int getLockCount() {
        return lockMap.size() + rwLockMap.size();
    }
}

