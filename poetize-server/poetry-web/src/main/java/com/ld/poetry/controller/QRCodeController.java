package com.ld.poetry.controller;

import com.ld.poetry.service.QRCodeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 二维码控制器
 * 安全说明：仅支持通过文章ID生成二维码，防止API被滥用
 * 
 * @author LeapYa
 * @since 2025-10-06
 */
@RestController
@RequestMapping("/qrcode")
@Slf4j
public class QRCodeController {

    @Autowired
    private QRCodeService qrCodeService;

    /**
     * 生成文章分享二维码（带网站Logo）
     * 安全说明：仅接受文章ID，不接受任意URL，防止API被盗用
     * 
     * @param articleId 文章ID
     * @return 二维码图片（PNG格式）
     */
    @GetMapping("/article/{articleId}")
    public ResponseEntity<byte[]> getArticleQRCode(@PathVariable Integer articleId) {
        try {
            if (articleId == null || articleId <= 0) {
                log.warn("请求文章二维码失败：文章ID无效 [{}]", articleId);
                return ResponseEntity.badRequest().build();
            }

            log.debug("请求文章二维码：文章ID [{}]", articleId);
            
            // 从缓存获取或生成二维码
            byte[] qrCode = qrCodeService.getOrGenerateArticleQRCode(articleId);
            
            if (qrCode == null || qrCode.length == 0) {
                log.error("生成文章二维码失败：返回数据为空，文章ID [{}]", articleId);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            // 设置永久缓存（与Redis缓存时间一致）
            headers.setCacheControl("public, max-age=31536000, immutable");
            // 设置ETag用于协商缓存
            headers.setETag("\"article-qr-" + articleId + "\"");
            
            log.debug("成功返回文章二维码：文章ID [{}]，大小 [{}] bytes", articleId, qrCode.length);
            return new ResponseEntity<>(qrCode, headers, HttpStatus.OK);
            
        } catch (IllegalArgumentException e) {
            log.warn("请求文章二维码失败：{}", e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("生成文章二维码失败：文章ID [{}]，错误: {}", articleId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

