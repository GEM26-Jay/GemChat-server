package com.zcj.servicefile.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.profile.DefaultProfile;
import com.aliyuncs.profile.IClientProfile;
import jakarta.annotation.PreDestroy;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.Assert;

@Configuration
@RequiredArgsConstructor
public class AliyunOssConfig {

    private final AliyunOssProperties properties;
    private OSS ossClient; // 用于销毁OSS客户端

    // OSS客户端（使用OSS专用端点）
    @Bean
    public OSS ossClient() {
        validateOssConfig(properties);
        ossClient = new OSSClientBuilder().build(
                properties.getOssEndpoint(), // 这里使用OSS专用端点
                properties.getAccessKeyId(),
                properties.getAccessKeySecret()
        );
        return ossClient;
    }

    // ACS客户端（STS用，使用STS端点）
    @Bean
    public DefaultAcsClient acsClient() throws ClientException {
        validateAcsConfig(properties);
        String regionId = properties.getRegion();
        // 注册STS服务端点（使用配置的STS端点）
        DefaultProfile.addEndpoint(regionId, "Sts", properties.getStsEndpoint());
        IClientProfile profile = DefaultProfile.getProfile(
                regionId,
                properties.getAccessKeyId(),
                properties.getAccessKeySecret()
        );
        return new DefaultAcsClient(profile);
    }

    // 销毁OSS客户端
    @PreDestroy
    public void destroy() {
        if (ossClient != null) {
            ossClient.shutdown();
        }
    }

    // 校验OSS配置（确保OSS端点正确）
    private void validateOssConfig(AliyunOssProperties config) {
        Assert.hasText(config.getOssEndpoint(), "OSS端点（oss-endpoint）未配置，请检查aliyun.oss.oss-endpoint");
        Assert.hasText(config.getBucketName(), "存储桶名称（bucket-name）未配置");
        Assert.isTrue(config.getOssEndpoint().startsWith("oss-"), "OSS端点格式错误，应为oss-[region].aliyuncs.com");
        validateCommonConfig(config);
    }

    // 校验STS配置（确保STS端点正确）
    private void validateAcsConfig(AliyunOssProperties config) {
        Assert.hasText(config.getStsEndpoint(), "STS端点（sts-endpoint）未配置，请检查aliyun.oss.sts-endpoint");
        Assert.hasText(config.getRegion(), "地域（region）未配置");
        Assert.hasText(config.getRoleArn(), "角色ARN（role-arn）未配置");
        validateCommonConfig(config);
    }

    // 校验公共配置（AK等）
    private void validateCommonConfig(AliyunOssProperties config) {
        Assert.hasText(config.getAccessKeyId(), "AccessKeyId未配置");
        Assert.hasText(config.getAccessKeySecret(), "AccessKeySecret未配置");
    }
}
