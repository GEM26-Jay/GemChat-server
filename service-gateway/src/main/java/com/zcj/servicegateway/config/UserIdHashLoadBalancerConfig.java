package com.zcj.servicegateway.config;

import com.zcj.servicegateway.loadbalance.UserIdHashLoadBalancer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.loadbalancer.core.ReactorLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.cloud.loadbalancer.support.LoadBalancerClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;


// todo: 根据用户ID 进行负载均衡
//@Configuration
// 为特定服务配置 UserIdHashLoadBalancer
//@LoadBalancerClient(name = "service-netty", configuration = UserIdHashLoadBalancerConfig.class)
public class UserIdHashLoadBalancerConfig {

    @Bean
    public ReactorLoadBalancer<ServiceInstance> userIdLoadBalancer(
            Environment environment,
            LoadBalancerClientFactory factory) {

        String serviceId = LoadBalancerClientFactory.getName(environment);
        System.out.println("=== 配置 UserIdHashLoadBalancer，serviceId: " + serviceId + " ===");

        ObjectProvider<ServiceInstanceListSupplier> supplierProvider = factory
                .getLazyProvider(serviceId, ServiceInstanceListSupplier.class);

        return new UserIdHashLoadBalancer(supplierProvider, serviceId);
    }
}
