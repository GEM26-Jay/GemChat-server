package com.zcj.servicegateway.filter;

import com.zcj.common.utils.JWTUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.util.Map;

import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenValidateFilter implements GlobalFilter, Ordered {

    private final JWTUtil jwtUtil;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        String path = exchange.getRequest().getPath().value();

        // 1. 放行无需验证的路径
        if (path.contains("/api/user/login")
                || path.contains("/api/user/register")
                || path.contains("/api/avatar/")
        ) {
            return chain.filter(exchange);
        }

        // 2. 获取 Authorization
        String token = exchange.getRequest().getHeaders().getFirst("Authorization");
        if (token == null) {
            log.warn("token 不存在, path={}", path);
            exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
            return exchange.getResponse().setComplete();
        }

        // Bearer 去掉前缀
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }

        try {
            // 3. 校验 Token
            if (!jwtUtil.validate(token)) {
                log.warn("Token 无效, path={}", path);
                exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                return exchange.getResponse().setComplete();
            }

            // 4. 获取 payload
            Map<String, Object> payload = jwtUtil.getPayloadIfValid(token);
            if (payload != null && payload.containsKey("userId")) {

                String userId = payload.get("userId").toString();

                // **必须使用 mutate + exchange.mutate 才能写入 header**
                ServerHttpRequest newRequest = exchange.getRequest().mutate()
                        .header("X-User-Id", userId)
                        .build();

                exchange.getAttributes().put("X-User-Id", userId);

                log.info("过滤器中设置的用户ID：{}", userId);

                return chain.filter(
                        exchange.mutate().request(newRequest).build()
                );
            }

            return chain.filter(exchange);

        } catch (Exception e) {
            log.warn("Token处理异常, path={}, error={}", path, e.getMessage());
            exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return exchange.getResponse().setComplete();
        }
    }

    // 全局过滤器执行优先级（越小越靠前）
    @Override
    public int getOrder() {
        return -200;
    }
}
