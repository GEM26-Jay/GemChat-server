package com.zcj.servicenetty.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;
import java.util.concurrent.TimeUnit;

@Configuration
public class CaffeineConfig {

    @Bean()
    public Cache<String, Set<Long>> session_member_cache() {
        return Caffeine.newBuilder()
                .maximumSize(1000000)                     // 最大缓存条数
                .expireAfterWrite(5, TimeUnit.MINUTES) // 写入后10分钟过期
                .build();
    }

    @Bean()
    public Cache<Long, String> user_route_cache() {
        return Caffeine.newBuilder()
                .maximumSize(1000000)                     // 最大缓存条数
                .expireAfterWrite(5, TimeUnit.MINUTES) // 写入后10分钟过期
                .build();
    }
}
