package com.zcj.servicegateway.filter;

import com.zcj.common.utils.JWTUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;

import java.util.Map;

@Configuration
@Slf4j
public class TokenValidateFilterConfig {

    private final JWTUtil jwtUtil;

    // 注入JWT工具类
    public TokenValidateFilterConfig(JWTUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    // 定义全局过滤器（优先级：Order值越小越先执行）
    @Bean
    @Order(-100) // 确保在路由转发前执行
    public GlobalFilter tokenValidateFilter() {
        return (exchange, chain) -> {

            // 1. 获取请求路径，排除不需要校验的接口（如登录、注册）
            String path = exchange.getRequest().getPath().value();
            if (path.contains("/api/user/login") ||
                path.contains("/api/user/register") ||
                path.contains("/api/file/uploadAvatar") ||
                path.contains("/api/netty/getAddr")
            ) {
                return chain.filter(exchange); // 直接放行
            }

            // 2. 从请求头获取token
            String token = exchange.getRequest().getHeaders().getFirst("Authorization");

            // 3. 参数校验：检查token是否存在且格式正确
            if (token == null) {
                log.warn("请求参数错误：token不存在或格式错误，path: {}", path);
                exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
                return exchange.getResponse().setComplete();
            }

            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }

            // 4. 验证token有效性
            try {
                if(jwtUtil.validate(token)) {
                    // 5. 验证通过，获取payload信息
                    Map<String, Object> payload = jwtUtil.getPayloadIfValid(token);
                    if (payload != null) {
                        // 将常用的用户信息写入新的Header
                        if (payload.containsKey("userId")) {
                            exchange.getRequest().mutate()
                                    .header("X-User-Id", payload.get("userId").toString())
                                    .build();
                        }
                    }

                    log.debug("Token验证通过，path: {}", path);
                    return chain.filter(exchange);
                } else {
                    // 6. token无效（签名错误、过期等）
                    log.warn("Token无效，path: {}", path);
                    exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                    return exchange.getResponse().setComplete();
                }

            } catch (Exception e) {
                log.warn("Token处理异常，path: {}, 错误: {}", path, e.getMessage());
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

        };
    }
}
