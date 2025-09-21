package com.ld.poetry.controller;

import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.vo.BaseRequestVO;
import com.ld.poetry.config.PoetryResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * SEO管理控制器 - 代理Python SEO服务
 */
@RestController
@RequestMapping("/admin/seo")
@Slf4j
public class AdminSeoController {

    /**
     * 获取SEO配置
     */
    @GetMapping("/getConfig")
    @LoginCheck(1)
    public PoetryResult getSeoConfig() {
        try {
            return callPythonSeoApi("GET", "/seo/getSeoConfig", null);
        } catch (Exception e) {
            log.error("获取SEO配置失败", e);
            return PoetryResult.fail("获取SEO配置失败：" + e.getMessage());
        }
    }

    /**
     * 更新SEO数据
     */
    @PostMapping("/updateSeoData")
    @LoginCheck(1)
    public PoetryResult updateSeoData() {
        try {
            return callPythonSeoApi("POST", "/seo/updateSeoData", new HashMap<>());
        } catch (Exception e) {
            log.error("更新SEO数据失败", e);
            return PoetryResult.fail("更新SEO数据失败：" + e.getMessage());
        }
    }

    /**
     * 分析站点SEO
     */
    @GetMapping("/analyzeSite")
    @LoginCheck(1)
    public PoetryResult analyzeSite() {
        try {
            return callPythonSeoApi("GET", "/seo/analyzeSite", null);
        } catch (Exception e) {
            log.error("分析站点SEO失败", e);
            return PoetryResult.fail("分析站点SEO失败：" + e.getMessage());
        }
    }

    /**
     * AI分析站点SEO
     */
    @GetMapping("/aiAnalyzeSite")
    @LoginCheck(1)
    public PoetryResult aiAnalyzeSite() {
        try {
            return callPythonSeoApi("GET", "/seo/aiAnalyzeSite", null);
        } catch (Exception e) {
            log.error("AI分析站点SEO失败", e);
            return PoetryResult.fail("AI分析站点SEO失败：" + e.getMessage());
        }
    }

    /**
     * 检查AI API配置
     */
    @GetMapping("/checkAiApiConfig")
    @LoginCheck(1)
    public PoetryResult checkAiApiConfig() {
        try {
            return callPythonSeoApi("GET", "/seo/checkAiApiConfig", null);
        } catch (Exception e) {
            log.error("检查AI API配置失败", e);
            return PoetryResult.fail("检查AI API配置失败：" + e.getMessage());
        }
    }

    /**
     * 保存AI API配置
     */
    @PostMapping("/saveAiApiConfig")
    @LoginCheck(1)
    public PoetryResult saveAiApiConfig(@RequestBody Map<String, Object> configData) {
        try {
            return callPythonSeoApi("POST", "/seo/saveAiApiConfig", configData);
        } catch (Exception e) {
            log.error("保存AI API配置失败", e);
            return PoetryResult.fail("保存AI API配置失败：" + e.getMessage());
        }
    }

    /**
     * 批量处理图标
     */
    @PostMapping("/batchProcessIcons")
    @LoginCheck(1)
    public PoetryResult batchProcessIcons(@RequestBody Map<String, Object> requestData) {
        try {
            return callPythonSeoApi("POST", "/seo/batchProcessIcons", requestData);
        } catch (Exception e) {
            log.error("批量处理图标失败", e);
            return PoetryResult.fail("批量处理图标失败：" + e.getMessage());
        }
    }

    /**
     * 清理SEO缓存
     */
    @PostMapping("/clearCache")
    @LoginCheck(1)
    public PoetryResult clearCache() {
        try {
            return callPythonSeoApi("POST", "/seo/clearCache", new HashMap<>());
        } catch (Exception e) {
            log.error("清理SEO缓存失败", e);
            return PoetryResult.fail("清理SEO缓存失败：" + e.getMessage());
        }
    }

    /**
     * 提交文章到搜索引擎
     */
    @PostMapping("/submitArticle")
    @LoginCheck(1)
    public PoetryResult submitArticle(@RequestBody Map<String, Object> requestData) {
        try {
            return callPythonSeoApi("POST", "/seo/submitArticle", requestData);
        } catch (Exception e) {
            log.error("提交文章到搜索引擎失败", e);
            return PoetryResult.fail("提交文章到搜索引擎失败：" + e.getMessage());
        }
    }

    /**
     * 调用Python SEO API的通用方法
     */
    private PoetryResult callPythonSeoApi(String method, String endpoint, Map<String, Object> requestData) {
        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Internal-Service", "poetize-java");
            headers.set("X-Admin-Request", "true");
            headers.set("User-Agent", "poetize-java/1.0.0");

            String pythonServerUrl = System.getenv().getOrDefault("PYTHON_SERVICE_URL", "http://localhost:5000");
            String apiUrl = pythonServerUrl + endpoint;

            Map<String, Object> response;
            
            if ("GET".equals(method)) {
                HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
                response = restTemplate.getForObject(apiUrl, Map.class);
            } else {
                HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestData, headers);
                response = restTemplate.postForObject(apiUrl, requestEntity, Map.class);
            }

            if (response != null && "200".equals(String.valueOf(response.get("code")))) {
                log.info("成功调用Python SEO API: {}", endpoint);
                return PoetryResult.success(response.get("data"));
            } else {
                log.warn("Python SEO API响应异常: {}", response);
                return PoetryResult.fail("操作失败：" + (response != null ? response.get("message") : "未知错误"));
            }

        } catch (Exception e) {
            log.error("调用Python SEO API失败，endpoint: {}, 错误: {}", endpoint, e.getMessage(), e);
            return PoetryResult.fail("操作失败：" + e.getMessage());
        }
    }
}