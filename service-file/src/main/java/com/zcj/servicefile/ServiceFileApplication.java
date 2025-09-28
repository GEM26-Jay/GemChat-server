package com.zcj.servicefile;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@EnableDiscoveryClient
@Slf4j
@SpringBootApplication()
public class ServiceFileApplication {
    public static void main(String[] args) {
        SpringApplication.run(ServiceFileApplication.class, args);
        log.info("server started");
    }
}
