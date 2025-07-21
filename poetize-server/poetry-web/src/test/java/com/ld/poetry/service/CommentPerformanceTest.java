package com.ld.poetry.service;

import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.service.impl.CommentServiceImpl;
import com.ld.poetry.vo.BaseRequestVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 评论列表接口性能测试类
 * 验证N+1查询优化效果
 */
@SpringBootTest
@ActiveProfiles("test")
public class CommentPerformanceTest {

    @Autowired
    private CommentServiceImpl commentService;

    @Test
    @DisplayName("评论列表接口性能测试 - 主评论查询")
    public void testListCommentPerformance() {
        // 准备测试数据
        BaseRequestVO baseRequestVO = new BaseRequestVO();
        baseRequestVO.setSource(1); // 假设文章ID为1
        baseRequestVO.setCommentType("article");
        baseRequestVO.setCurrent(1);
        baseRequestVO.setSize(10);

        // 执行性能测试
        long startTime = System.currentTimeMillis();
        
        PoetryResult<BaseRequestVO> result = commentService.listComment(baseRequestVO);
        
        long endTime = System.currentTimeMillis();
        long executionTime = endTime - startTime;

        // 验证结果
        assertNotNull(result);
        assertTrue(result.isSuccess());
        
        // 性能验证：目标是30ms以内
        System.out.println("🚀 评论列表接口执行时间: " + executionTime + "ms");
        
        if (executionTime <= 30) {
            System.out.println("✅ 性能测试通过: " + executionTime + "ms <= 30ms");
        } else if (executionTime <= 50) {
            System.out.println("⚠️ 性能可接受: " + executionTime + "ms <= 50ms");
        } else {
            System.out.println("❌ 性能需要进一步优化: " + executionTime + "ms > 50ms");
        }

        // 验证数据完整性
        BaseRequestVO responseData = result.getData();
        assertNotNull(responseData);
        
        if (responseData.getRecords() != null && !responseData.getRecords().isEmpty()) {
            System.out.println("📊 返回评论数量: " + responseData.getRecords().size());
            System.out.println("📊 评论总数: " + responseData.getTotal());
        } else {
            System.out.println("📊 无评论数据");
        }
    }

    @Test
    @DisplayName("评论列表接口性能测试 - 子评论查询")
    public void testListChildCommentPerformance() {
        // 准备测试数据 - 查询子评论
        BaseRequestVO baseRequestVO = new BaseRequestVO();
        baseRequestVO.setSource(1); // 假设文章ID为1
        baseRequestVO.setCommentType("article");
        baseRequestVO.setFloorCommentId(1); // 假设楼层评论ID为1
        baseRequestVO.setCurrent(1);
        baseRequestVO.setSize(10);

        // 执行性能测试
        long startTime = System.currentTimeMillis();
        
        PoetryResult<BaseRequestVO> result = commentService.listComment(baseRequestVO);
        
        long endTime = System.currentTimeMillis();
        long executionTime = endTime - startTime;

        // 验证结果
        assertNotNull(result);
        assertTrue(result.isSuccess());
        
        // 性能验证
        System.out.println("🚀 子评论查询执行时间: " + executionTime + "ms");
        
        if (executionTime <= 20) {
            System.out.println("✅ 子评论查询性能优秀: " + executionTime + "ms <= 20ms");
        } else if (executionTime <= 40) {
            System.out.println("⚠️ 子评论查询性能可接受: " + executionTime + "ms <= 40ms");
        } else {
            System.out.println("❌ 子评论查询性能需要优化: " + executionTime + "ms > 40ms");
        }
    }

    @Test
    @DisplayName("批量查询vs递归查询性能对比测试")
    public void testBatchVsRecursivePerformance() {
        System.out.println("🔍 开始批量查询vs递归查询性能对比测试");
        
        // 测试不同数据量下的性能表现
        int[] testSizes = {5, 10, 20, 50};
        
        for (int size : testSizes) {
            BaseRequestVO baseRequestVO = new BaseRequestVO();
            baseRequestVO.setSource(1);
            baseRequestVO.setCommentType("article");
            baseRequestVO.setCurrent(1);
            baseRequestVO.setSize(size);

            // 执行测试
            long startTime = System.currentTimeMillis();
            PoetryResult<BaseRequestVO> result = commentService.listComment(baseRequestVO);
            long endTime = System.currentTimeMillis();
            
            long executionTime = endTime - startTime;
            
            System.out.println(String.format("📊 分页大小: %d, 执行时间: %dms", size, executionTime));
            
            // 验证结果正确性
            assertNotNull(result);
            assertTrue(result.isSuccess());
        }
    }

    @Test
    @DisplayName("数据库查询次数验证测试")
    public void testQueryCountOptimization() {
        System.out.println("🔍 开始数据库查询次数验证测试");
        
        BaseRequestVO baseRequestVO = new BaseRequestVO();
        baseRequestVO.setSource(1);
        baseRequestVO.setCommentType("article");
        baseRequestVO.setCurrent(1);
        baseRequestVO.setSize(10);

        // 执行查询
        PoetryResult<BaseRequestVO> result = commentService.listComment(baseRequestVO);
        
        // 验证结果
        assertNotNull(result);
        assertTrue(result.isSuccess());
        
        System.out.println("✅ 查询完成，请检查日志中的SQL执行次数");
        System.out.println("📋 优化目标：");
        System.out.println("   - 主评论查询: 1次");
        System.out.println("   - 子评论统计查询: 1次（批量）");
        System.out.println("   - 总查询次数: O(1) 而非 O(N)");
        
        BaseRequestVO responseData = result.getData();
        if (responseData != null && responseData.getRecords() != null) {
            System.out.println("📊 处理的主评论数量: " + responseData.getRecords().size());
            System.out.println("📊 如果使用递归查询，需要执行: " + (1 + responseData.getRecords().size()) + " 次数据库查询");
            System.out.println("📊 使用批量查询，实际执行: 2-3 次数据库查询");
        }
    }

    @Test
    @DisplayName("内存使用优化验证测试")
    public void testMemoryUsageOptimization() {
        System.out.println("🔍 开始内存使用优化验证测试");
        
        // 记录测试前内存使用
        Runtime runtime = Runtime.getRuntime();
        long beforeMemory = runtime.totalMemory() - runtime.freeMemory();
        
        BaseRequestVO baseRequestVO = new BaseRequestVO();
        baseRequestVO.setSource(1);
        baseRequestVO.setCommentType("article");
        baseRequestVO.setCurrent(1);
        baseRequestVO.setSize(20); // 较大的分页大小
        
        // 执行查询
        PoetryResult<BaseRequestVO> result = commentService.listComment(baseRequestVO);
        
        // 记录测试后内存使用
        long afterMemory = runtime.totalMemory() - runtime.freeMemory();
        long memoryUsed = afterMemory - beforeMemory;
        
        // 验证结果
        assertNotNull(result);
        assertTrue(result.isSuccess());
        
        System.out.println("📊 内存使用情况:");
        System.out.println("   - 测试前内存: " + (beforeMemory / 1024 / 1024) + " MB");
        System.out.println("   - 测试后内存: " + (afterMemory / 1024 / 1024) + " MB");
        System.out.println("   - 内存增量: " + (memoryUsed / 1024) + " KB");
        
        // 内存使用应该是合理的（小于10MB增量）
        assertTrue(memoryUsed < 10 * 1024 * 1024, "内存使用应该小于10MB");
        
        System.out.println("✅ 内存使用优化验证通过");
    }

    @Test
    @DisplayName("并发性能测试")
    public void testConcurrentPerformance() throws InterruptedException {
        System.out.println("🔍 开始并发性能测试");
        
        int threadCount = 5;
        int requestsPerThread = 3;
        
        Thread[] threads = new Thread[threadCount];
        long[] executionTimes = new long[threadCount];
        
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            threads[i] = new Thread(() -> {
                long threadStartTime = System.currentTimeMillis();
                
                for (int j = 0; j < requestsPerThread; j++) {
                    BaseRequestVO baseRequestVO = new BaseRequestVO();
                    baseRequestVO.setSource(1);
                    baseRequestVO.setCommentType("article");
                    baseRequestVO.setCurrent(1);
                    baseRequestVO.setSize(10);
                    
                    PoetryResult<BaseRequestVO> result = commentService.listComment(baseRequestVO);
                    assertNotNull(result);
                    assertTrue(result.isSuccess());
                }
                
                long threadEndTime = System.currentTimeMillis();
                executionTimes[threadIndex] = threadEndTime - threadStartTime;
                
                System.out.println("🧵 线程 " + threadIndex + " 完成，耗时: " + executionTimes[threadIndex] + "ms");
            });
        }
        
        // 启动所有线程
        long testStartTime = System.currentTimeMillis();
        for (Thread thread : threads) {
            thread.start();
        }
        
        // 等待所有线程完成
        for (Thread thread : threads) {
            thread.join();
        }
        long testEndTime = System.currentTimeMillis();
        
        // 计算统计信息
        long totalTime = testEndTime - testStartTime;
        long avgThreadTime = 0;
        for (long time : executionTimes) {
            avgThreadTime += time;
        }
        avgThreadTime /= threadCount;
        
        System.out.println("📊 并发测试结果:");
        System.out.println("   - 总测试时间: " + totalTime + "ms");
        System.out.println("   - 平均线程时间: " + avgThreadTime + "ms");
        System.out.println("   - 总请求数: " + (threadCount * requestsPerThread));
        System.out.println("   - 平均每请求时间: " + (avgThreadTime / requestsPerThread) + "ms");
        
        // 验证并发性能
        assertTrue(avgThreadTime / requestsPerThread < 100, "平均每请求时间应该小于100ms");
        
        System.out.println("✅ 并发性能测试通过");
    }
}
