package com.ld.poetry.service;

import org.springframework.web.multipart.MultipartFile;
import java.util.Map;
import java.util.List;

/**
 * <p>
 * SEO图像处理服务接口
 * </p>
 *
 * @author LeapYa
 * @since 2025-09-25
 */
public interface SeoImageService {
    
    /**
     * 处理单个图片
     * @param imageFile 图片文件
     * @param targetType 目标类型 (logo, icon, banner等)
     * @param preferredFormat 首选格式 (png, jpg, webp)
     * @return 处理结果
     */
    Map<String, Object> processImage(MultipartFile imageFile, String targetType, String preferredFormat);
    
    /**
     * 批量处理图标
     * @param imageFile 原始图片文件
     * @param iconTypes 需要生成的图标类型列表
     * @return 批量处理结果
     */
    Map<String, Object> batchProcessIcons(MultipartFile imageFile, List<String> iconTypes);
    
    /**
     * 获取图片信息
     * @param imageFile 图片文件
     * @return 图片信息和建议
     */
    Map<String, Object> getImageInfo(MultipartFile imageFile);
    
    /**
     * 压缩图片
     * @param imageData 原始图片数据
     * @param quality 压缩质量 (0.0-1.0)
     * @param maxWidth 最大宽度
     * @param maxHeight 最大高度
     * @return 压缩后的图片数据
     */
    byte[] compressImage(byte[] imageData, float quality, int maxWidth, int maxHeight);
    
    /**
     * 调整图片尺寸
     * @param imageData 原始图片数据
     * @param width 目标宽度
     * @param height 目标高度
     * @param keepAspectRatio 是否保持宽高比
     * @return 调整后的图片数据
     */
    byte[] resizeImage(byte[] imageData, int width, int height, boolean keepAspectRatio);
    
    /**
     * 转换图片格式
     * @param imageData 原始图片数据
     * @param targetFormat 目标格式 (png, jpg, webp)
     * @return 转换后的图片数据
     */
    byte[] convertImageFormat(byte[] imageData, String targetFormat);
}
