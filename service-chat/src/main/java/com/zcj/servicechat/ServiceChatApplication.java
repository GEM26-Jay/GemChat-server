package com.zcj.servicechat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableFeignClients(basePackages = "com.zcj.common.feign")
@SpringBootApplication
public class ServiceChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServiceChatApplication.class, args);
    }

}
