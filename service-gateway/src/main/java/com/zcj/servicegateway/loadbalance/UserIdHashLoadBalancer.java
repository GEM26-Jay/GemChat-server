package com.zcj.servicegateway.loadbalance;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.*;
import org.springframework.cloud.loadbalancer.core.ReactorLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
public class UserIdHashLoadBalancer implements ReactorLoadBalancer<ServiceInstance> {

    private final String serviceId;
    private final ObjectProvider<ServiceInstanceListSupplier> supplierProvider;

    public UserIdHashLoadBalancer(ObjectProvider<ServiceInstanceListSupplier> supplierProvider, String serviceId) {
        this.serviceId = serviceId;
        this.supplierProvider = supplierProvider;
    }

    @Override
    public Mono<Response<ServiceInstance>> choose(Request request) {
        ServiceInstanceListSupplier supplier = supplierProvider.getIfAvailable();
        if (supplier == null) {
            return Mono.just(new EmptyResponse());
        }

        return supplier.get(request).next()
                .map(instances -> chooseInstance(instances, request));
    }

    private Response<ServiceInstance> chooseInstance(List<ServiceInstance> instances, Request request) {
        if (instances.isEmpty()) {
            return new EmptyResponse();
        }

        // 只有一个实例时直接返回
        if (instances.size() == 1) {
            return new DefaultResponse(instances.get(0));
        }

        String userId = extractUserIdFromRequest(request);
        ServiceInstance chosen;

        if (userId == null) {
            // fallback 策略：轮询
            int index = (int) (System.currentTimeMillis() % instances.size());
            chosen = instances.get(index);
            log.warn("未找到 X-User-Id，使用 fallback 实例: {}", chosen.getUri());
        } else {
            int index = Math.abs(userId.hashCode() % instances.size());
            chosen = instances.get(index);
            log.info("UserId={} → hash index={} → 选择实例 {}", userId, index, chosen.getUri());
        }

        return new DefaultResponse(chosen);
    }

    private String extractUserIdFromRequest(Request request) {
        Object context = request.getContext();

        if (context instanceof ServerWebExchange) {
            ServerWebExchange exchange = (ServerWebExchange) context;
            return exchange.getRequest().getHeaders().getFirst("X-User-Id");
        }

        return null;
    }
}