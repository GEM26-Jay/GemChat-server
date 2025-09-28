package com.zcj.common.autoconfig;

import com.zcj.common.config.JWTProperties;
import com.zcj.common.config.SnowflakeProperties;
import com.zcj.common.utils.JWTUtil;
import com.zcj.common.utils.SnowflakeIdGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
@ComponentScan(basePackages = {"com.zcj.common.config"})
@ConfigurationPropertiesScan("com.zcj.common.config")
@AutoConfigureAfter(name = "com.zcj.common.config.JWTProperties")
public class UtilsAutoConfiguration {

    /**
     * 雪花算法工具配置，数据中心ID，机器ID
     */
    @Bean
    @ConditionalOnBean(SnowflakeProperties.class)
    public SnowflakeIdGenerator idGenerator(SnowflakeProperties snowflakeProperties) {
        log.info("snowflake idGenerator 已加载");
        return new SnowflakeIdGenerator(snowflakeProperties.getRoomId(), snowflakeProperties.getWorkerId());
    }

    /**
     * JWT验证配置
     */
    @Bean
    @ConditionalOnBean(JWTProperties.class)
    public JWTUtil jwtUtil(JWTProperties jwtProperties){
        log.info("jwt util 已加载, secretKey: {}, ttlMills: {}", jwtProperties.getSecretKey(), jwtProperties.getTtlMillis());
        return new JWTUtil(jwtProperties.getSecretKey(), jwtProperties.getTtlMillis());
    }
}
