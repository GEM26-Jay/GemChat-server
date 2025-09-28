package com.zcj.common.config;

import lombok.Data;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@ConfigurationProperties(prefix = "jwt")
@ConditionalOnProperty(
        prefix = "jwt",
        name = {"secret-key", "ttl-millis"}
)
@Component
public class JWTProperties {
    private String secretKey;
    private Long ttlMillis;
}
