package com.ld.poetry.vo;

import lombok.Data;

/**
 * 图片压缩测试结果DTO
 * 用于返回压缩测试的详细信息
 */
@Data
public class CompressTestResult {

    /**
     * 原文件名
     */
    private String originalFilename;

    /**
     * 压缩后的Content-Type
     */
    private String contentType;

    /**
     * 原始文件大小（字节）
     */
    private long originalSize;

    /**
     * 压缩后文件大小（字节）
     */
    private long compressedSize;

    /**
     * 压缩率（百分比）
     */
    private double compressionRatio;

    /**
     * 原始文件大小（格式化显示）
     */
    private String originalSizeDisplay;

    /**
     * 压缩后文件大小（格式化显示）
     */
    private String compressedSizeDisplay;

    /**
     * 节省的文件大小（格式化显示）
     */
    private String savedSizeDisplay;

    /**
     * 目标宽度（自定义压缩参数）
     */
    private int targetWidth;

    /**
     * 目标高度（自定义压缩参数）
     */
    private int targetHeight;

    /**
     * 目标质量（自定义压缩参数）
     */
    private float targetQuality;

    /**
     * 目标大小（自定义压缩参数，格式化显示）
     */
    private String targetSizeDisplay;

    public CompressTestResult() {
    }

    public CompressTestResult(String originalFilename, String contentType, long originalSize,
                            long compressedSize, double compressionRatio, String originalSizeDisplay,
                            String compressedSizeDisplay, String savedSizeDisplay) {
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.originalSize = originalSize;
        this.compressedSize = compressedSize;
        this.compressionRatio = compressionRatio;
        this.originalSizeDisplay = originalSizeDisplay;
        this.compressedSizeDisplay = compressedSizeDisplay;
        this.savedSizeDisplay = savedSizeDisplay;
    }

    public CompressTestResult(String originalFilename, String contentType, long originalSize,
                            long compressedSize, double compressionRatio, String originalSizeDisplay,
                            String compressedSizeDisplay, String savedSizeDisplay,
                            int targetWidth, int targetHeight, float targetQuality, String targetSizeDisplay) {
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.originalSize = originalSize;
        this.compressedSize = compressedSize;
        this.compressionRatio = compressionRatio;
        this.originalSizeDisplay = originalSizeDisplay;
        this.compressedSizeDisplay = compressedSizeDisplay;
        this.savedSizeDisplay = savedSizeDisplay;
        this.targetWidth = targetWidth;
        this.targetHeight = targetHeight;
        this.targetQuality = targetQuality;
        this.targetSizeDisplay = targetSizeDisplay;
    }
}
