package com.ld.poetry.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private SecurityFilter securityFilter;

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    /**
     * 注册安全过滤器
     */
    @Bean
    public FilterRegistrationBean<SecurityFilter> securityFilterRegistration() {
        FilterRegistrationBean<SecurityFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(securityFilter);
        registration.addUrlPatterns("/*");
        registration.setName("securityFilter");
        registration.setOrder(1); // 设置过滤器优先级，数字越小优先级越高
        return registration;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 只处理用户上传的文件资源，不处理前端静态资源
        registry.addResourceHandler("/static/**")
                .addResourceLocations("file:/app/static/")
                .setCachePeriod(3600);
    }
} 