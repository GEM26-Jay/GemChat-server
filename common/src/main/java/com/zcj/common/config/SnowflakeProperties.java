package com.zcj.common.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "snowflake") // 对应配置文件中的前缀
public class SnowflakeProperties {
    private long roomId = 0; // 默认值0
    private long workerId = 0;     // 默认值0
}
