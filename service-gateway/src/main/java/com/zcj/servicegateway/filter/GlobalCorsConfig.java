package com.zcj.servicegateway.filter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
public class GlobalCorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter() {
        // 1. 创建跨域配置对象
        CorsConfiguration config = new CorsConfiguration();

        // 允许的前端域名（生产环境建议指定具体域名，如 http://localhost:8080）
        config.addAllowedOrigin("*");
        // 允许的请求方法（GET/POST/PUT/DELETE 等）
        config.addAllowedMethod("*");
        // 允许的请求头（如 Authorization、Content-Type）
        config.addAllowedHeader("*");
        // 允许携带 Cookie（如需传递登录态）
        config.setAllowCredentials(true);
        // 预检请求的有效期（单位：秒），避免频繁发送 OPTIONS 请求
        config.setMaxAge(3600L);

        // 2. 配置跨域规则适用的路径（/** 表示所有路径）
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        // 3. 创建并返回跨域过滤器
        return new CorsWebFilter(source);
    }
}
