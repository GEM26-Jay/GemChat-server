package com.zcj.servicefile.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "aliyun.oss")
public class AliyunOssProperties {

    private String ossEndpoint;
    private String stsEndpoint;
    private String region;
    private String accessKeyId;
    private String accessKeySecret;
    private String roleArn;
    private String bucketName;

}
