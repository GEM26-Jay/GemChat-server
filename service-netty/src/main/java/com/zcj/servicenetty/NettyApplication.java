package com.zcj.servicenetty;

import com.zcj.servicenetty.bootstrap.NettyServerBootstrap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@EnableDiscoveryClient
@EnableFeignClients
@Slf4j
@SpringBootApplication()
public class NettyApplication implements CommandLineRunner {

    @Autowired
    private NettyServerBootstrap serverBootstrap;

    public static void main(String[] args) {
        // 启动 Spring Boot 应用，初始化容器
        SpringApplication.run(NettyApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        serverBootstrap.start();
    }
}
