package com.zcj.common.autoconfig;

import com.zcj.common.handler.GlobalExceptionHandler;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HandlerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean // 若服务中无自定义的 GlobalExceptionHandler，则创建该 Bean
    public GlobalExceptionHandler globalExceptionHandler() {
        return new GlobalExceptionHandler();
    }

}
