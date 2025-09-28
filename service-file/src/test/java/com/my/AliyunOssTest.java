package com.my;

import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import com.aliyun.oss.common.comm.Protocol;
import com.aliyun.oss.common.comm.SignVersion;
import com.aliyun.oss.model.Bucket;
import com.aliyuncs.exceptions.ClientException;
import com.zcj.servicefile.config.AliyunOssProperties;
import com.zcj.servicefile.ServiceFileApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

@SpringBootTest(classes = ServiceFileApplication.class)
public class AliyunOssTest {

    @Autowired
    AliyunOssProperties aliyunOssProperties;

    @Test
    public void testInit() throws ClientException {
        // 创建 ClientBuilderConfiguration 实例，用于配置 OSS 客户端参数
        ClientBuilderConfiguration clientBuilderConfiguration = new ClientBuilderConfiguration();
        // 设置签名算法版本为 V4
        clientBuilderConfiguration.setSignatureVersion(SignVersion.V4);
        // 设置使用 HTTPS 协议访问 OSS，保证传输安全性
        clientBuilderConfiguration.setProtocol(Protocol.HTTPS);

        // 创建 OSS 客户端实例
        OSS ossClient = OSSClientBuilder.create()
                // 以华东1（杭州）地域的外网访问域名为例，Endpoint填写为oss-cn-hangzhou.aliyuncs.com
                .endpoint(aliyunOssProperties.getEndpoint())
                // 从环境变量中获取访问凭证（需提前配置 OSS_ACCESS_KEY_ID 和 OSS_ACCESS_KEY_SECRET）
                .credentialsProvider(new DefaultCredentialProvider(
                        aliyunOssProperties.getAccessKeyId(),
                        aliyunOssProperties.getAccessKeySecret()
                ))// 设置客户端配置
                .clientConfiguration(clientBuilderConfiguration)
                // 以华东1（杭州）地域为例，Region填写为cn-hangzhou
                .region("cn-hangzhou")
                .build();

        try {
            // 列出当前用户的所有 Bucket
            List<Bucket> buckets = ossClient.listBuckets();
            // 遍历打印每个 Bucket 的名称
            for (Bucket bucket : buckets) {
                System.out.println(bucket.getName());
            }
        } finally {
            // 当OSSClient实例不再使用时，调用shutdown方法以释放资源
            ossClient.shutdown();
        }
    }
}
