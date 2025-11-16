package com.zcj.servicegateway.filter;

import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Component
public class GlobalCorsFilter extends CorsWebFilter {

    public GlobalCorsFilter() {
        super(buildSource());
    }

    private static UrlBasedCorsConfigurationSource buildSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.addAllowedOriginPattern("*"); // 推荐用 pattern
        config.addAllowedMethod("*");
        config.addAllowedHeader("*");

        // 允许 Cookie 必须配合 addAllowedOriginPattern 或 指定域名
        config.setAllowCredentials(true);

        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }
}
