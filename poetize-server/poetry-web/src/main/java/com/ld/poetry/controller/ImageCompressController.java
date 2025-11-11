package com.ld.poetry.controller;

import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.service.AsyncImageCompressService;
import com.ld.poetry.utils.image.ImageCompressUtil;
import com.ld.poetry.utils.security.FileSecurityValidator;
import com.ld.poetry.vo.CompressTestResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.CompletableFuture;

/**
 * 图片压缩管理控制器
 * 提供图片压缩统计、批量操作等功能
 */
@RestController
@RequestMapping("/imageCompress")
@Slf4j
public class ImageCompressController {

    @Autowired
    private AsyncImageCompressService asyncImageCompressService;

    @Autowired
    private FileSecurityValidator fileSecurityValidator;

    /**
     * 获取图片压缩统计信息
     */
    @GetMapping("/stats")
    @LoginCheck(0)
    public PoetryResult<AsyncImageCompressService.CompressStats> getCompressStats() {
        AsyncImageCompressService.CompressStats stats = asyncImageCompressService.getCompressStats();
        return PoetryResult.success(stats);
    }

    /**
     * 重置压缩统计信息
     */
    @PostMapping("/resetStats")
    @LoginCheck(0)
    public PoetryResult resetStats() {
        asyncImageCompressService.resetStats();
        return PoetryResult.success("统计信息已重置");
    }

    /**
     * 测试图片压缩效果
     */
    @PostMapping("/testCompress")
    @LoginCheck
    public PoetryResult<Object> testCompress(@RequestParam("file") MultipartFile file) {
        try {
            // 检查文件大小是否超过Integer.MAX_VALUE，防止溢出
            long fileSize = file.getSize();
            if (fileSize > Integer.MAX_VALUE) {
                log.error("文件大小超过系统限制: {} bytes, 最大允许: {} bytes", fileSize, Integer.MAX_VALUE);
                return PoetryResult.fail("文件大小超过系统限制(" + (Integer.MAX_VALUE / 1024 / 1024) + "MB)，请上传较小的文件");
            }

            // 验证文件安全性
            FileSecurityValidator.ValidationResult validationResult =
                    fileSecurityValidator.validateFile(file, file.getOriginalFilename(), file.getContentType());

            if (!validationResult.isSuccess()) {
                log.warn("文件安全验证失败: {}", validationResult.getMessage());
                return PoetryResult.fail("文件验证失败: " + validationResult.getMessage());
            }

            ImageCompressUtil.CompressResult result = ImageCompressUtil.smartCompress(file);

            CompressTestResult resultData = new CompressTestResult(
                file.getOriginalFilename(),
                result.getContentType(),
                result.getOriginalSize(),
                result.getCompressedSize(),
                result.getCompressionRatio(),
                formatFileSize(result.getOriginalSize()),
                formatFileSize(result.getCompressedSize()),
                formatFileSize(result.getOriginalSize() - result.getCompressedSize())
            );

            return PoetryResult.success(resultData);

        } catch (Exception e) {
            log.error("测试压缩失败: {}", e.getMessage(), e);
            return PoetryResult.fail("测试压缩失败: " + e.getMessage());
        }
    }

    /**
     * 异步压缩单个图片
     */
    @PostMapping("/compressAsync")
    @LoginCheck
    public PoetryResult<String> compressAsync(@RequestParam("file") MultipartFile file) {
        try {
            // 检查文件大小是否超过Integer.MAX_VALUE，防止溢出
            long fileSize = file.getSize();
            if (fileSize > Integer.MAX_VALUE) {
                log.error("文件大小超过系统限制: {} bytes, 最大允许: {} bytes", fileSize, Integer.MAX_VALUE);
                return PoetryResult.fail("文件大小超过系统限制(" + (Integer.MAX_VALUE / 1024 / 1024) + "MB)，请上传较小的文件");
            }

            // 验证文件安全性
            FileSecurityValidator.ValidationResult validationResult =
                    fileSecurityValidator.validateFile(file, file.getOriginalFilename(), file.getContentType());

            if (!validationResult.isSuccess()) {
                log.warn("文件安全验证失败: {}", validationResult.getMessage());
                return PoetryResult.fail("文件验证失败: " + validationResult.getMessage());
            }

            CompletableFuture<ImageCompressUtil.CompressResult> future = 
                    asyncImageCompressService.compressImageAsync(file);

            return PoetryResult.success("图片压缩任务已提交，正在后台处理...");

        } catch (Exception e) {
            log.error("异步压缩提交失败: {}", e.getMessage(), e);
            return PoetryResult.fail("异步压缩提交失败: " + e.getMessage());
        }
    }

    /**
     * 批量压缩图片
     */
    @PostMapping("/batchCompress")
    @LoginCheck(0)
    public PoetryResult<String> batchCompress(@RequestParam("files") MultipartFile[] files) {
        try {
            if (files == null || files.length == 0) {
                return PoetryResult.fail("请选择要压缩的图片文件！");
            }

            // 验证所有文件的大小和安全性
            for (MultipartFile file : files) {
                // 检查文件大小是否超过Integer.MAX_VALUE，防止溢出
                long fileSize = file.getSize();
                if (fileSize > Integer.MAX_VALUE) {
                    log.error("批量压缩中有文件大小超过系统限制: {} bytes, 最大允许: {} bytes", fileSize, Integer.MAX_VALUE);
                    return PoetryResult.fail("文件大小超过系统限制(" + (Integer.MAX_VALUE / 1024 / 1024) + "MB)，请上传较小的文件");
                }

                FileSecurityValidator.ValidationResult validationResult =
                        fileSecurityValidator.validateFile(file, file.getOriginalFilename(), file.getContentType());

                if (!validationResult.isSuccess()) {
                    log.warn("批量压缩中有文件验证失败: {}", validationResult.getMessage());
                    return PoetryResult.fail("文件验证失败: " + validationResult.getMessage());
                }
            }

            CompletableFuture<AsyncImageCompressService.BatchCompressResult> future = 
                    asyncImageCompressService.batchCompressAsync(files);

            return PoetryResult.success("批量压缩任务已提交，正在后台处理 " + files.length + " 个文件...");

        } catch (Exception e) {
            log.error("批量压缩提交失败: {}", e.getMessage(), e);
            return PoetryResult.fail("批量压缩提交失败: " + e.getMessage());
        }
    }

    /**
     * 自定义参数压缩测试
     */
    @PostMapping("/testCustomCompress")
    @LoginCheck
    public PoetryResult<Object> testCustomCompress(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "maxWidth", defaultValue = "1920") int maxWidth,
            @RequestParam(value = "maxHeight", defaultValue = "1080") int maxHeight,
            @RequestParam(value = "quality", defaultValue = "0.85") float quality,
            @RequestParam(value = "targetSize", defaultValue = "512000") long targetSize) {

        try {
            // 检查文件大小是否超过Integer.MAX_VALUE，防止溢出
            long fileSize = file.getSize();
            if (fileSize > Integer.MAX_VALUE) {
                log.error("文件大小超过系统限制: {} bytes, 最大允许: {} bytes", fileSize, Integer.MAX_VALUE);
                return PoetryResult.fail("文件大小超过系统限制(" + (Integer.MAX_VALUE / 1024 / 1024) + "MB)，请上传较小的文件");
            }

            // 验证文件安全性
            FileSecurityValidator.ValidationResult validationResult =
                    fileSecurityValidator.validateFile(file, file.getOriginalFilename(), file.getContentType());

            if (!validationResult.isSuccess()) {
                log.warn("文件安全验证失败: {}", validationResult.getMessage());
                return PoetryResult.fail("文件验证失败: " + validationResult.getMessage());
            }

            ImageCompressUtil.CompressResult result =
                    ImageCompressUtil.smartCompress(file, maxWidth, maxHeight, quality, targetSize);

            CompressTestResult resultData = new CompressTestResult(
                file.getOriginalFilename(),
                result.getContentType(),
                result.getOriginalSize(),
                result.getCompressedSize(),
                result.getCompressionRatio(),
                formatFileSize(result.getOriginalSize()),
                formatFileSize(result.getCompressedSize()),
                formatFileSize(result.getOriginalSize() - result.getCompressedSize()),
                maxWidth,
                maxHeight,
                quality,
                formatFileSize(targetSize)
            );

            return PoetryResult.success(resultData);

        } catch (Exception e) {
            log.error("自定义参数压缩测试失败: {}", e.getMessage(), e);
            return PoetryResult.fail("自定义参数压缩测试失败: " + e.getMessage());
        }
    }

    /**
     * 格式化文件大小显示
     */
    private String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
}