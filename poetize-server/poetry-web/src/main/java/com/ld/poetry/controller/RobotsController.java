package com.ld.poetry.controller;

import com.ld.poetry.service.RobotsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Robots.txt控制器
 * 处理robots.txt请求
 */
@Slf4j
@RestController
public class RobotsController {

    @Autowired
    private RobotsService robotsService;

    /**
     * 获取robots.txt
     * @return robots.txt内容
     */
    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getRobots() {
        try {
            
            String robots = robotsService.getRobots();
            if (robots == null) {
                log.warn("robots.txt生成失败，返回404");
                return ResponseEntity.notFound().build();
            }

            // 设置缓存头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.TEXT_PLAIN);
            headers.setCacheControl("public, max-age=3600"); // 缓存1小时
            headers.add("X-Robots-Tag", "noindex"); // 防止搜索引擎索引robots.txt本身

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(robots);
                    
        } catch (Exception e) {
            log.error("处理robots.txt请求时出错", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Internal Server Error");
        }
    }
}
