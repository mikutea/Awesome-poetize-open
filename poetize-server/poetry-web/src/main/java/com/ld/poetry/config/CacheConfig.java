package com.ld.poetry.config;

import com.ld.poetry.constants.CacheConstants;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Spring Cache配置
 * 配置Redis作为Spring Cache的实现
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * 配置Redis缓存管理器
     */
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory) {
        // 默认缓存配置
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofSeconds(CacheConstants.DEFAULT_EXPIRE_TIME))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues();

        // 针对不同缓存名称的特定配置
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        
        // 文章相关缓存 - 长期缓存
        cacheConfigurations.put("articles", defaultConfig.entryTtl(Duration.ofSeconds(CacheConstants.LONG_EXPIRE_TIME)));
        cacheConfigurations.put("sortArticles", defaultConfig.entryTtl(Duration.ofSeconds(CacheConstants.LONG_EXPIRE_TIME)));
        
        // 用户相关缓存 - 中期缓存
        cacheConfigurations.put("users", defaultConfig.entryTtl(Duration.ofSeconds(CacheConstants.DEFAULT_EXPIRE_TIME)));
        
        // 评论相关缓存 - 短期缓存
        cacheConfigurations.put("comments", defaultConfig.entryTtl(Duration.ofSeconds(CacheConstants.SHORT_EXPIRE_TIME)));
        
        // 系统配置缓存 - 超长期缓存
        cacheConfigurations.put("sysConfig", defaultConfig.entryTtl(Duration.ofSeconds(CacheConstants.VERY_LONG_EXPIRE_TIME)));
        
        // 搜索结果缓存 - 短期缓存
        cacheConfigurations.put("searchResults", defaultConfig.entryTtl(Duration.ofSeconds(600))); // 10分钟

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }
}
