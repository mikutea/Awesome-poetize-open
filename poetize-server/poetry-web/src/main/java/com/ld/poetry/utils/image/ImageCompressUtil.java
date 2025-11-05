package com.ld.poetry.utils.image;

import com.ld.poetry.service.SysConfigService;
import com.ld.poetry.utils.SpringContextUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;

/**
 * 图片压缩工具类
 * 提供高质量的图片压缩功能，支持多种压缩策略
 */
@Slf4j
public class ImageCompressUtil {

    // 压缩配置常量
    private static final int MAX_WIDTH = 1920;        // 最大宽度
    private static final int MAX_HEIGHT = 1080;       // 最大高度
    private static final float DEFAULT_QUALITY = 0.85f; // 默认压缩质量
    private static final long MAX_FILE_SIZE = 500 * 1024; // 目标文件大小：500KB
    
    // 压缩模式
    private static final String COMPRESSION_MODE_LOSSY = "lossy";      // 有损压缩
    private static final String COMPRESSION_MODE_LOSSLESS = "lossless"; // 无损压缩
    
    /**
     * 智能压缩图片
     * 根据图片尺寸和文件大小自动选择最佳压缩策略
     */
    public static CompressResult smartCompress(MultipartFile file) throws IOException {
        return smartCompress(file, MAX_WIDTH, MAX_HEIGHT, DEFAULT_QUALITY, MAX_FILE_SIZE);
    }

    /**
     * 智能压缩图片（自定义参数）
     */
    public static CompressResult smartCompress(MultipartFile file, int maxWidth, int maxHeight, 
                                             float quality, long targetSize) throws IOException {
        // 检查文件类型
        String contentType = file.getContentType();
        if (!isImageFile(contentType)) {
            throw new IllegalArgumentException("不支持的图片格式: " + contentType);
        }

        // 从系统配置中读取压缩配置
        SysConfigService sysConfigService = SpringContextUtil.getBean(SysConfigService.class);
        
        // 检查是否启用压缩
        String compressEnabledStr = sysConfigService.getConfigValueByKey("image.compress.enabled");
        boolean compressEnabled = compressEnabledStr == null || "true".equalsIgnoreCase(compressEnabledStr);
        
        if (!compressEnabled) {
            log.info("图片压缩已在系统配置中禁用，将直接使用原图");
            byte[] originalBytes = file.getBytes();
            return new CompressResult(originalBytes, contentType, 0, file.getSize(), file.getSize());
        }
        
        // 获取压缩模式
        String compressMode = sysConfigService.getConfigValueByKey("image.compress.mode");
        if (compressMode == null) {
            compressMode = COMPRESSION_MODE_LOSSY; // 默认为有损压缩
        }
        
        boolean isLosslessMode = COMPRESSION_MODE_LOSSLESS.equalsIgnoreCase(compressMode);
        log.info("当前压缩模式: {}", isLosslessMode ? "无损压缩" : "有损压缩");

        try (InputStream inputStream = file.getInputStream()) {
            BufferedImage originalImage = ImageIO.read(inputStream);
            if (originalImage == null) {
                throw new IOException("无法读取图片文件");
            }

            long originalSize = file.getSize();
            int originalWidth = originalImage.getWidth();
            int originalHeight = originalImage.getHeight();

            log.info("开始压缩图片 - 原始尺寸: {}x{}, 原始大小: {}KB", 
                    originalWidth, originalHeight, originalSize / 1024);

            byte[] compressedBytes;
            
            // 第一步：尺寸压缩（无论有损还是无损模式都进行尺寸压缩）
            BufferedImage resizedImage = resizeImage(originalImage, maxWidth, maxHeight);
            
            if (isLosslessMode) {
                // 无损模式：只进行尺寸调整，不进行质量压缩
                compressedBytes = losslessCompress(resizedImage, contentType);
                log.info("使用无损压缩模式，仅调整尺寸");
            } else {
                // 有损模式：进行质量压缩
                // 第二步：质量压缩
                compressedBytes = compressImageQuality(resizedImage, quality, contentType);
                
                // 第三步：如果还是太大，进一步压缩
                if (compressedBytes.length > targetSize) {
                    compressedBytes = progressiveCompress(resizedImage, contentType, targetSize);
                }
            }

            // 第四步：WebP格式转换
            // 从系统配置中读取WebP转换的配置
            String webpEnabledStr = sysConfigService.getConfigValueByKey("image.webp.enabled");
            boolean webpEnabled = webpEnabledStr == null || "true".equalsIgnoreCase(webpEnabledStr);
            
            // 获取最小转换大小和最小节省比例
            String minSizeStr = sysConfigService.getConfigValueByKey("image.webp.min-size");
            int minSize = 50 * 1024; // 默认50KB
            try {
                if (minSizeStr != null) {
                    minSize = Integer.parseInt(minSizeStr) * 1024;
                }
            } catch (NumberFormatException e) {
                log.warn("解析WebP最小转换大小配置失败，使用默认值50KB: {}", e.getMessage());
            }
            
            String minSavingRatioStr = sysConfigService.getConfigValueByKey("image.webp.min-saving-ratio");
            double minSavingRatio = 0.1; // 默认10%
            try {
                if (minSavingRatioStr != null) {
                    minSavingRatio = Double.parseDouble(minSavingRatioStr) / 100.0;
                }
            } catch (NumberFormatException e) {
                log.warn("解析WebP最小节省比例配置失败，使用默认值10%: {}", e.getMessage());
            }
            
            // 如果WebP转换已启用，且不是已经是WebP，并且文件大于最小转换大小，尝试WebP转换
            if (webpEnabled && !contentType.contains("webp") && compressedBytes.length > minSize) {
                try {
                    byte[] webpBytes;
                    if (isLosslessMode) {
                        // 无损模式下使用无损WebP
                        webpBytes = convertToLosslessWebP(resizedImage);
                    } else {
                        // 有损模式下使用有损WebP
                        webpBytes = convertToWebP(resizedImage);
                    }
                    
                    // 只有当WebP转换后的大小比原格式小指定比例以上才使用WebP
                    if (webpBytes.length < compressedBytes.length * (1 - minSavingRatio)) {
                        log.info("使用WebP格式({}模式)，减小了{}%的体积", 
                                isLosslessMode ? "无损" : "有损",
                                String.format("%.1f", (1.0 - (double) webpBytes.length / compressedBytes.length) * 100));
                        compressedBytes = webpBytes;
                        contentType = "image/webp";
                    } else {
                        log.info("WebP转换后体积未显著减小，保持原格式");
                    }
                } catch (IOException e) {
                    log.warn("WebP转换失败，使用原格式: {}", e.getMessage());
                }
            } else if (!webpEnabled) {
                log.info("WebP转换已在系统配置中禁用");
            }

            long finalSize = compressedBytes.length;
            double compressionRatio = (1.0 - (double) finalSize / originalSize) * 100;

            log.info("图片处理完成 - 处理后大小: {}KB, 压缩率: {:.1f}%, 最终格式: {}", 
                    finalSize / 1024, compressionRatio, contentType);

            return new CompressResult(compressedBytes, contentType, compressionRatio, originalSize, finalSize);
        }
    }

    /**
     * 尺寸压缩
     */
    private static BufferedImage resizeImage(BufferedImage originalImage, int maxWidth, int maxHeight) {
        int originalWidth = originalImage.getWidth();
        int originalHeight = originalImage.getHeight();

        // 如果尺寸已经合适，直接返回
        if (originalWidth <= maxWidth && originalHeight <= maxHeight) {
            return originalImage;
        }

        // 计算缩放比例，保持宽高比
        double scaleWidth = (double) maxWidth / originalWidth;
        double scaleHeight = (double) maxHeight / originalHeight;
        double scale = Math.min(scaleWidth, scaleHeight);

        int newWidth = (int) (originalWidth * scale);
        int newHeight = (int) (originalHeight * scale);

        // 创建高质量的缩放图片
        BufferedImage resizedImage = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = resizedImage.createGraphics();
        
        // 设置高质量渲染
        g2d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g2d.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        
        // 绘制缩放后的图片
        g2d.drawImage(originalImage, 0, 0, newWidth, newHeight, null);
        g2d.dispose();

        return resizedImage;
    }

    /**
     * 质量压缩
     */
    private static byte[] compressImageQuality(BufferedImage image, float quality, String contentType) 
            throws IOException {
        String formatName = getFormatName(contentType);
        
        // JPEG格式支持质量压缩
        if ("jpeg".equalsIgnoreCase(formatName) || "jpg".equalsIgnoreCase(formatName)) {
            return compressJPEG(image, quality);
        }
        
        // PNG格式直接输出
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, formatName, baos);
            return baos.toByteArray();
        }
    }

    /**
     * JPEG质量压缩
     */
    private static byte[] compressJPEG(BufferedImage image, float quality) throws IOException {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IOException("没有找到JPEG编码器");
        }

        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            writer.setOutput(ImageIO.createImageOutputStream(baos));
            writer.write(null, new IIOImage(image, null, null), param);
            writer.dispose();
            return baos.toByteArray();
        }
    }

    /**
     * 渐进式压缩
     * 当文件仍然过大时，逐步降低质量直到达到目标大小
     */
    private static byte[] progressiveCompress(BufferedImage image, String contentType, long targetSize) 
            throws IOException {
        float quality = 0.9f;
        byte[] result = null;
        
        while (quality > 0.1f) {
            result = compressImageQuality(image, quality, contentType);
            if (result.length <= targetSize) {
                break;
            }
            quality -= 0.1f;
        }
        
        // 如果质量压缩还是不够，进行尺寸进一步压缩
        if (result != null && result.length > targetSize) {
            int newWidth = (int) (image.getWidth() * 0.8);
            int newHeight = (int) (image.getHeight() * 0.8);
            BufferedImage smallerImage = resizeImage(image, newWidth, newHeight);
            result = compressImageQuality(smallerImage, quality, contentType);
        }
        
        return result;
    }

    /**
     * 无损压缩
     * 只进行格式转换，不降低质量
     */
    private static byte[] losslessCompress(BufferedImage image, String contentType) throws IOException {
        String formatName = getFormatName(contentType);
        
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            // 使用最高质量写入图片
            ImageIO.write(image, formatName, baos);
            return baos.toByteArray();
        }
    }
    
    /**
     * 转换为WebP格式（有损）
     * 优先使用cwebp命令行工具
     */
    private static byte[] convertToWebP(BufferedImage image) throws IOException {
        // 优先尝试使用cwebp命令行工具（支持质量控制）
        try {
            byte[] webpBytes = convertToWebPUsingCwebp(image, false);
            if (webpBytes != null && webpBytes.length > 0) {
                log.info("成功使用cwebp转换为有损WebP格式，转换后大小: {}KB", webpBytes.length / 1024);
                return webpBytes;
            }
        } catch (Exception e) {
            log.warn("cwebp有损转换失败: {}, 尝试备用方案", e.getMessage());
        }
        
        // 备用方案1：使用ImageIO的WebP编码器
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            boolean success = ImageIO.write(image, "webp", baos);
            
            if (success && baos.size() > 0) {
                byte[] webpData = baos.toByteArray();
                log.info("使用ImageIO转换为WebP格式，转换后大小: {}KB", webpData.length / 1024);
                return webpData;
            }
        } catch (Exception e) {
            log.warn("ImageIO WebP转换失败: {}", e.getMessage());
        }
        
        // 备用方案2：使用高质量JPEG压缩
        log.info("WebP转换失败，使用JPEG格式");
        return compressJPEG(image, 0.9f);
    }

    /**
     * 转换为无损WebP格式
     * 使用cwebp命令行工具实现真正的无损转换
     */
    private static byte[] convertToLosslessWebP(BufferedImage image) throws IOException {
        // 优先尝试使用cwebp命令行工具（支持真正的无损压缩）
        try {
            byte[] webpBytes = convertToWebPUsingCwebp(image, true);
            if (webpBytes != null && webpBytes.length > 0) {
                log.info("成功使用cwebp转换为无损WebP格式，转换后大小: {}KB", webpBytes.length / 1024);
                return webpBytes;
            }
        } catch (Exception e) {
            log.warn("cwebp无损转换失败: {}, 尝试备用方案", e.getMessage());
        }
        
        // 备用方案1：尝试使用ImageIO的WebP编码器（但无法保证无损）
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            boolean success = ImageIO.write(image, "webp", baos);
            
            if (success && baos.size() > 0) {
                byte[] webpData = baos.toByteArray();
                log.warn("使用ImageIO转换为WebP格式(可能为有损)，转换后大小: {}KB", webpData.length / 1024);
                return webpData;
            }
        } catch (Exception e) {
            log.warn("ImageIO WebP转换失败: {}", e.getMessage());
        }
        
        // 备用方案2：使用PNG格式（真正的无损）
        log.info("WebP转换失败，使用PNG格式(真正无损)");
        ByteArrayOutputStream pngBaos = new ByteArrayOutputStream();
        ImageIO.write(image, "png", pngBaos);
        return pngBaos.toByteArray();
    }

    /**
     * 使用cwebp命令行工具转换图片
     * @param image 图片对象
     * @param lossless 是否无损压缩
     * @return WebP格式的字节数组，失败返回null
     */
    private static byte[] convertToWebPUsingCwebp(BufferedImage image, boolean lossless) throws IOException {
        java.io.File tempInputFile = null;
        java.io.File tempOutputFile = null;
        
        try {
            // 创建临时文件
            tempInputFile = java.io.File.createTempFile("img_input_", ".png");
            tempOutputFile = java.io.File.createTempFile("img_output_", ".webp");
            
            // 将BufferedImage保存为临时PNG文件
            ImageIO.write(image, "png", tempInputFile);
            
            // 构建cwebp命令
            ProcessBuilder processBuilder;
            if (lossless) {
                // 无损模式：-lossless -z 9 (最高压缩比)
                processBuilder = new ProcessBuilder(
                    "cwebp",
                    "-lossless",
                    "-z", "9",          // 压缩级别0-9，9为最高
                    "-m", "6",          // 压缩方法0-6，6为最慢但最优
                    "-mt",              // 多线程
                    tempInputFile.getAbsolutePath(),
                    "-o", tempOutputFile.getAbsolutePath()
                );
            } else {
                // 有损模式：质量85
                processBuilder = new ProcessBuilder(
                    "cwebp",
                    "-q", "85",         // 质量0-100
                    "-m", "6",          // 压缩方法
                    "-mt",              // 多线程
                    tempInputFile.getAbsolutePath(),
                    "-o", tempOutputFile.getAbsolutePath()
                );
            }
            
            // 执行命令
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            
            // 读取命令输出（用于日志）
            StringBuilder output = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            
            // 等待命令完成
            int exitCode = process.waitFor();
            
            if (exitCode != 0) {
                log.warn("cwebp命令执行失败，退出代码: {}, 输出: {}", exitCode, output);
                return null;
            }
            
            // 读取输出文件
            if (!tempOutputFile.exists() || tempOutputFile.length() == 0) {
                log.warn("cwebp未生成输出文件或文件为空");
                return null;
            }
            
            byte[] webpData = java.nio.file.Files.readAllBytes(tempOutputFile.toPath());
            
            return webpData;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("cwebp命令执行被中断", e);
            return null;
        } catch (Exception e) {
            return null;
        } finally {
            // 清理临时文件
            if (tempInputFile != null && tempInputFile.exists()) {
                tempInputFile.delete();
            }
            if (tempOutputFile != null && tempOutputFile.exists()) {
                tempOutputFile.delete();
            }
        }
    }

    /**
     * 检查是否为图片文件
     */
    private static boolean isImageFile(String contentType) {
        return contentType != null && (
                contentType.startsWith("image/jpeg") ||
                contentType.startsWith("image/jpg") ||
                contentType.startsWith("image/png") ||
                contentType.startsWith("image/gif") ||
                contentType.startsWith("image/bmp") ||
                contentType.startsWith("image/webp")
        );
    }

    /**
     * 获取图片格式名称
     */
    private static String getFormatName(String contentType) {
        if (contentType.contains("jpeg") || contentType.contains("jpg")) {
            return "jpeg";
        } else if (contentType.contains("png")) {
            return "png";
        } else if (contentType.contains("gif")) {
            return "gif";
        } else if (contentType.contains("bmp")) {
            return "bmp";
        }
        return "jpeg"; // 默认格式
    }

    /**
     * 压缩结果类
     */
    public static class CompressResult {
        private final byte[] data;
        private final String contentType;
        private final double compressionRatio;
        private final long originalSize;
        private final long compressedSize;

        public CompressResult(byte[] data, String contentType, double compressionRatio, 
                            long originalSize, long compressedSize) {
            this.data = data;
            this.contentType = contentType;
            this.compressionRatio = compressionRatio;
            this.originalSize = originalSize;
            this.compressedSize = compressedSize;
        }

        public byte[] getData() { return data; }
        public String getContentType() { return contentType; }
        public double getCompressionRatio() { return compressionRatio; }
        public long getOriginalSize() { return originalSize; }
        public long getCompressedSize() { return compressedSize; }
    }
} 