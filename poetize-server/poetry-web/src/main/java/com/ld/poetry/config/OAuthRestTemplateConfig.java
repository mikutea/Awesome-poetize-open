package com.ld.poetry.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;
import java.net.Proxy;

/**
 * OAuth专用RestTemplate配置
 * 配置超时时间、连接池、代理等网络设置
 */
@Slf4j
@Configuration
public class OAuthRestTemplateConfig {

    /**
     * 创建OAuth专用的RestTemplate
     * 配置适当的超时时间和网络设置
     */
    @Bean("oauthRestTemplate")
    public RestTemplate oauthRestTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setRequestFactory(createRequestFactory());
        
        log.info("OAuth RestTemplate配置完成");
        return restTemplate;
    }

    /**
     * 创建HTTP请求工厂，配置超时和代理设置
     */
    private ClientHttpRequestFactory createRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();

        // 优化超时设置：减少连接超时，避免长时间等待
        factory.setConnectTimeout(10000);  // 10秒连接超时

        // 优化读取超时：GitHub API通常响应很快
        factory.setReadTimeout(15000);     // 15秒读取超时

        // 检查是否需要配置代理
        configureProxy(factory);

        log.info("OAuth HTTP请求工厂配置: connectTimeout=10s, readTimeout=15s");
        return factory;
    }

    /**
     * 配置代理设置（如果需要）
     */
    private void configureProxy(SimpleClientHttpRequestFactory factory) {
        // 从系统属性或环境变量中获取代理配置
        String proxyHost = System.getProperty("oauth.proxy.host", System.getenv("OAUTH_PROXY_HOST"));
        String proxyPortStr = System.getProperty("oauth.proxy.port", System.getenv("OAUTH_PROXY_PORT"));
        
        if (proxyHost != null && !proxyHost.trim().isEmpty() && proxyPortStr != null) {
            try {
                int proxyPort = Integer.parseInt(proxyPortStr.trim());
                Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost.trim(), proxyPort));
                factory.setProxy(proxy);
                log.info("OAuth代理配置: {}:{}", proxyHost, proxyPort);
            } catch (NumberFormatException e) {
                log.warn("OAuth代理端口配置错误: {}", proxyPortStr, e);
            }
        } else {
            log.info("OAuth未配置代理，使用直连");
        }
    }
}
