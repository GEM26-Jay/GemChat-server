package com.zcj.servicenetty.controller;

import com.zcj.common.vo.Result;
import com.zcj.servicenetty.config.NettyProperties;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/netty")
@Tag(name = "Netty的API接口")
@Slf4j
@AllArgsConstructor
public class NettyServiceController {

    private LoadBalancerClient loadBalancerClient;
    private NettyProperties nettyProperties;

    @GetMapping("/getAddr")
    public Result<String> getAddr() {
        ServiceInstance instance = loadBalancerClient.choose("service-netty");
        if (instance == null) {
            return Result.error("未找到service-netty服务");
        }

        // 构建服务地址（IP:端口）
        String address = instance.getHost() + ":" + nettyProperties.getPort();
        return Result.success(address);
    }

}
