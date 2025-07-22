package com.ld.poetry.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * 验证码验证服务
 * 负责调用Python验证服务验证验证码token的有效性
 */
@Service
@Slf4j
public class CaptchaVerificationService {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${PYTHON_SERVICE_URL:http://localhost:5000}")
    private String pythonServiceBaseUrl;

    /**
     * 验证验证码token的有效性
     * 
     * @param verificationToken 验证码token
     * @return 验证结果，true表示验证通过，false表示验证失败
     */
    public boolean verifyToken(String verificationToken) {
        // 如果token为空，认为不需要验证码验证
        if (!StringUtils.hasText(verificationToken)) {
            log.debug("验证码token为空，跳过验证");
            return true;
        }

        try {
            log.info("开始验证验证码token: {}", verificationToken.substring(0, Math.min(verificationToken.length(), 10)) + "...");
            
            // 构建请求URL
            String url = pythonServiceBaseUrl + "/captcha/verify-token";
            
            // 构建请求体
            String requestBody = String.format("{\"token\":\"%s\"}", verificationToken);
            
            // 设置请求头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<String> request = new HttpEntity<>(requestBody, headers);
            
            // 发送请求
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                // 解析响应
                JsonNode responseNode = objectMapper.readTree(response.getBody());
                
                // 检查响应格式
                if (responseNode.has("code") && responseNode.get("code").asInt() == 200) {
                    JsonNode dataNode = responseNode.get("data");
                    if (dataNode != null && dataNode.has("valid")) {
                        boolean isValid = dataNode.get("valid").asBoolean();
                        log.info("验证码token验证结果: {}", isValid ? "通过" : "失败");
                        return isValid;
                    }
                }
                
                log.warn("验证码服务返回格式异常: {}", response.getBody());
                return false;
            } else {
                log.warn("验证码服务返回状态码异常: {}", response.getStatusCode());
                return false;
            }
            
        } catch (Exception e) {
            log.error("调用验证码验证服务失败: {}", e.getMessage(), e);
            
            // 降级策略：如果验证服务不可用，根据配置决定是否允许通过
            return handleVerificationServiceFailure(verificationToken);
        }
    }

    /**
     * 处理验证服务不可用的情况
     * 
     * @param verificationToken 验证码token
     * @return 降级策略的验证结果
     */
    private boolean handleVerificationServiceFailure(String verificationToken) {
        // 降级策略：验证服务不可用时，如果有token则认为用户已经通过了前端验证
        // 这样可以避免因为服务问题导致用户无法正常使用
        boolean allowOnFailure = StringUtils.hasText(verificationToken);
        
        log.warn("验证码服务不可用，采用降级策略: {} (token存在: {})", 
                allowOnFailure ? "允许通过" : "拒绝", StringUtils.hasText(verificationToken));
        
        return allowOnFailure;
    }

    /**
     * 检查验证码服务是否可用
     * 
     * @return 服务可用性状态
     */
    public boolean isVerificationServiceAvailable() {
        try {
            String url = pythonServiceBaseUrl + "/health";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            return response.getStatusCode() == HttpStatus.OK;
        } catch (Exception e) {
            log.debug("验证码服务健康检查失败: {}", e.getMessage());
            return false;
        }
    }
}
