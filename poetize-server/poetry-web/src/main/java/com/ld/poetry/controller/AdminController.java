package com.ld.poetry.controller;

import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ld.poetry.aop.LoginCheck;
import com.ld.poetry.config.PoetryResult;
import com.ld.poetry.dao.TreeHoleMapper;
import com.ld.poetry.dao.WebInfoMapper;
import com.ld.poetry.entity.*;
import com.ld.poetry.vo.BaseRequestVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;
import com.ld.poetry.enums.PoetryEnum;
import org.springframework.core.env.Environment;
import lombok.extern.slf4j.Slf4j;

/**
 * <p>
 * 后台 前端控制器
 * </p>
 *
 * @author sara
 * @since 2021-08-13
 */
@RestController
@RequestMapping("/admin")
@Slf4j
public class AdminController {

    @Autowired
    private WebInfoMapper webInfoMapper;

    @Autowired
    private TreeHoleMapper treeHoleMapper;

    @Autowired
    private Environment env;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private com.ld.poetry.service.PasswordUpgradeService passwordUpgradeService;

    /**
     * 获取网站信息
     */
    @GetMapping("/webInfo/getAdminWebInfo")
    @LoginCheck(0)
    public PoetryResult<WebInfo> getWebInfo() {
        LambdaQueryChainWrapper<WebInfo> wrapper = new LambdaQueryChainWrapper<>(webInfoMapper);
        List<WebInfo> list = wrapper.list();
        if (!CollectionUtils.isEmpty(list)) {
            return PoetryResult.success(list.get(0));
        } else {
            return PoetryResult.success();
        }
    }

    /**
     * Boss查询树洞
     */
    @PostMapping("/treeHole/boss/list")
    @LoginCheck(0)
    public PoetryResult<Page> listBossTreeHole(@RequestBody BaseRequestVO baseRequestVO) {
        LambdaQueryChainWrapper<TreeHole> wrapper = new LambdaQueryChainWrapper<>(treeHoleMapper);
        Page<TreeHole> page = new Page<>(baseRequestVO.getCurrent(), baseRequestVO.getSize());
        Page<TreeHole> resultPage = wrapper.orderByDesc(TreeHole::getCreateTime).page(page);
        return PoetryResult.success(resultPage);
    }

    @PostMapping("/updateSeoConfig")
    @LoginCheck(1)
    public PoetryResult updateSeoConfig(@RequestBody Map<String, Object> seoConfig) {
        log.info("收到SEO配置更新请求: {}", seoConfig);
        try {
            String pythonServerUrl = env.getProperty("PYTHON_SERVICE_URL", "http://localhost:5000");
            String seoApiUrl = pythonServerUrl + "/python/seo/updateSeoConfig";
            
            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.add("X-Internal-Service", "poetize-java");
            headers.add("X-Admin-Request", "true");
            
            // 创建请求实体
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(seoConfig, headers);
            
            log.info("转发SEO配置更新请求到Python服务: {}", seoApiUrl);
            
            // 发送请求到Python服务
            Map<String, Object> response = restTemplate.postForObject(
                seoApiUrl,
                requestEntity,
                Map.class
            );
            
            log.info("Python服务SEO配置更新响应: {}", response);
            
            if (response != null && response.containsKey("code")) {
                int code = Integer.parseInt(response.get("code").toString());
                if (code == 200) {
                    return PoetryResult.success(response.get("data"));
                } else {
                    return PoetryResult.fail(response.get("message") != null ? response.get("message").toString() : "SEO配置更新失败");
                }
            } else {
                return PoetryResult.fail("Python服务响应格式错误");
            }
        } catch (Exception e) {
            log.error("SEO配置更新失败", e);
            return PoetryResult.fail("SEO配置更新失败: " + e.getMessage());
        }
    }

    /**
     * 获取密码升级统计信息
     */
    @GetMapping("/password/upgrade/statistics")
    @LoginCheck(0)
    public PoetryResult<Map<String, Object>> getPasswordUpgradeStatistics() {
        try {
            Map<String, Object> statistics = passwordUpgradeService.getUpgradeStatistics();
            return PoetryResult.success(statistics);
        } catch (Exception e) {
            log.error("获取密码升级统计失败", e);
            return PoetryResult.fail("获取密码升级统计失败: " + e.getMessage());
        }
    }

    /**
     * 获取密码安全报告
     */
    @GetMapping("/password/security/report")
    @LoginCheck(0)
    public PoetryResult<String> getPasswordSecurityReport() {
        try {
            String report = passwordUpgradeService.generateSecurityReport();
            return PoetryResult.success(report);
        } catch (Exception e) {
            log.error("生成密码安全报告失败", e);
            return PoetryResult.fail("生成密码安全报告失败: " + e.getMessage());
        }
    }
}
