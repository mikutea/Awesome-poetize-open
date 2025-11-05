package com.ld.poetry.utils.storage;

import com.ld.poetry.entity.Resource;
import com.ld.poetry.handle.PoetryRuntimeException;
import com.ld.poetry.service.ResourceService;
import com.ld.poetry.service.SysConfigService;
import com.ld.poetry.vo.FileVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 兰空图床存储服务
 */
@Slf4j
@Component
public class LskyUtil implements StoreService {

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private SysConfigService sysConfigService;

    @Autowired
    private RestTemplate restTemplate;

    private String getUrl() {
        return sysConfigService.getConfigValueByKey("lsky.url");
    }

    private String getToken() {
        return sysConfigService.getConfigValueByKey("lsky.token");
    }

    private String getStrategyId() {
        return sysConfigService.getConfigValueByKey("lsky.strategy_id");
    }

    private boolean isEnabled() {
        String enable = sysConfigService.getConfigValueByKey("lsky.enable");
        return "true".equalsIgnoreCase(enable);
    }

    @Override
    public void deleteFile(List<String> files) {
        if (!isEnabled()) {
            log.warn("兰空图床未启用，忽略删除操作");
            return;
        }

        if (CollectionUtils.isEmpty(files)) {
            return;
        }

        for (String filePath : files) {
            try {
                // 尝试从数据库中获取图片密钥
                Resource resource = resourceService.lambdaQuery()
                        .eq(Resource::getPath, filePath)
                        .eq(Resource::getStoreType, StoreEnum.LSKY.getCode())
                        .one();
                
                if (resource != null && resource.getOriginalName() != null) {
                    // 从originalName中提取key
                    String key = resource.getOriginalName();
                    
                    HttpHeaders headers = new HttpHeaders();
                    headers.set("Authorization", "Bearer " + getToken());
                    headers.set("Accept", "application/json");
                    
                    HttpEntity<String> entity = new HttpEntity<>(headers);
                    
                    // 删除图片
                    ResponseEntity<Map> response = restTemplate.exchange(
                            getUrl() + "/images/" + key,
                            HttpMethod.DELETE,
                            entity,
                            Map.class
                    );
                    
                    if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                        Map<String, Object> responseData = response.getBody();
                        if (Boolean.TRUE.equals(responseData.get("status"))) {
                            log.info("兰空图床文件删除成功：" + filePath);
                            resourceService.lambdaUpdate().eq(Resource::getPath, filePath).remove();
                        } else {
                            log.error("兰空图床文件删除失败：" + filePath + "，原因：" + responseData.get("message"));
                        }
                    } else {
                        log.error("兰空图床文件删除失败，HTTP状态码：" + response.getStatusCode());
                    }
                } else {
                    log.error("未找到兰空图床图片记录或密钥：" + filePath);
                }
            } catch (Exception e) {
                log.error("兰空图床文件删除异常：" + filePath, e);
            }
        }
    }

    @Override
    public FileVO saveFile(FileVO fileVO) {
        if (!isEnabled()) {
            throw new PoetryRuntimeException("兰空图床未启用，无法上传文件");
        }

        try {
            MultipartFile file = fileVO.getFile();
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.set("Authorization", "Bearer " + getToken());
            headers.set("Accept", "application/json");
            
            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            
            // 添加文件
            ByteArrayResource fileResource = new ByteArrayResource(file.getBytes()) {
                @Override
                public String getFilename() {
                    return file.getOriginalFilename();
                }
            };
            body.add("file", fileResource);
            
            // 添加存储策略ID（如果有）
            String strategyId = getStrategyId();
            if (StringUtils.hasText(strategyId)) {
                body.add("strategy_id", strategyId);
            }
            
            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            
            // 上传图片
            ResponseEntity<Map> response = restTemplate.exchange(
                    getUrl() + "/upload",
                    HttpMethod.POST,
                    requestEntity,
                    Map.class
            );
            
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> responseBody = response.getBody();
                
                if (Boolean.TRUE.equals(responseBody.get("status"))) {
                    Map<String, Object> responseData = (Map<String, Object>) responseBody.get("data");
                    if (responseData != null) {
                        // 获取图片URL和其他信息
                        String key = (String) responseData.get("key");
                        Map<String, Object> links = (Map<String, Object>) responseData.get("links");
                        String imageUrl = (String) links.get("url");
                        
                        FileVO result = new FileVO();
                        result.setAbsolutePath(imageUrl);
                        result.setVisitPath(imageUrl);
                        // 将key存储在originalName中，用于后续删除
                        fileVO.setOriginalName(key);
                        
                        return result;
                    }
                } else {
                    throw new PoetryRuntimeException("兰空图床上传失败：" + responseBody.get("message"));
                }
            }
            
            throw new PoetryRuntimeException("兰空图床上传失败，请检查网络或API配置");
        } catch (IOException e) {
            log.error("兰空图床上传IO异常", e);
            throw new PoetryRuntimeException("兰空图床上传IO异常：" + e.getMessage());
        } catch (Exception e) {
            log.error("兰空图床上传异常", e);
            throw new PoetryRuntimeException("兰空图床上传异常：" + e.getMessage());
        }
    }

    @Override
    public String getStoreName() {
        return StoreEnum.LSKY.getCode();
    }
} 