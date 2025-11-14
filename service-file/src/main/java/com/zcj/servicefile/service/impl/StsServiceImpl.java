package com.zcj.servicefile.service.impl;

import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.auth.sts.AssumeRoleRequest;
import com.aliyuncs.auth.sts.AssumeRoleResponse;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.http.MethodType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zcj.common.context.UserContext;
import com.zcj.common.vo.FileTokenVO;
import com.zcj.servicefile.config.AliyunOssProperties;
import com.zcj.servicefile.service.StsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class StsServiceImpl implements StsService {

    private final AliyunOssProperties aliyunOssProperties;
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final DefaultAcsClient client;

    // 常量定义
    private static final List<String> OSS_UPLOAD_ACTIONS = Arrays.asList(
            "oss:PutObject",          // 小文件直接上传
            "oss:InitiateMultipartUpload", // 初始化分片上传
            "oss:UploadPart",         // 上传分片
            "oss:CompleteMultipartUpload", // 合并分片
            "oss:AbortMultipartUpload"    // 中断分片（异常清理）
    );
    private static final String OSS_DOWNLOAD_ACTION = "oss:GetObject"; // 下载仅需单个Action
    private static final long UPLOAD_TOKEN_EXPIRE_SECONDS = 900L;  // 15分钟（分片上传需足够时间）
    private static final long DOWNLOAD_TOKEN_EXPIRE_SECONDS = 900L;  // 15分钟

    @Override
    public FileTokenVO getUploadToken(String dirName, String fileName) {
        Long userId = UserContext.getId();
        log.info("用户[{}]请求上传令牌，目录:[{}]，原始文件名:[{}]", userId, dirName, fileName);

        Assert.hasText(dirName, "目录名不能为空");
        Assert.hasText(fileName, "文件名不能为空");

        try {
            String ossInnerPath = String.format("%s/%s", dirName, fileName);
            String httpAccessPath = generateHttpAccessPath(ossInnerPath);

            // 生成权限策略（精确到当前文件路径）
            String policy = generatePolicyForSpecificFile(ossInnerPath, OSS_UPLOAD_ACTIONS);

            // 获取阿里云STS临时凭证
            AssumeRoleResponse stsResponse = getAliyunStsResponse(
                    policy,
                    "user-upload-" + userId + "-" + System.currentTimeMillis(),
                    UPLOAD_TOKEN_EXPIRE_SECONDS
            );

            // 6. 构建并返回 FileTokenVO（严格匹配VO字段）
            return buildFileTokenVO(
                    stsResponse,
                    fileName,
                    ossInnerPath,
                    httpAccessPath,
                    false, // 上传时文件未存在（无需校验）
                    0L     // 上传时文件大小未知，设为0
            );
        } catch (Exception e) {
            log.error("用户[{}]获取上传STS令牌失败", userId, e);
            throw new RuntimeException("获取上传权限失败，请稍后重试");
        }
    }

    @Override
    public FileTokenVO getDownloadToken(String dirName, String fileName) {
        Long userId = UserContext.getId();
        log.info("用户[{}]请求下载令牌，目录:[{}]，文件名:[{}]", userId, dirName, fileName);

        Assert.hasText(dirName, "目录名不能为空");
        Assert.hasText(fileName, "文件名不能为空");

        // 生成 OSS 桶内具体路径（不含HTTP前缀）
        String ossInnerPath = String.format("%s/%s", dirName, fileName);
        // 生成 HTTP 访问路径
        String httpAccessPath = generateHttpAccessPath(ossInnerPath);

        try {
            // 生成权限策略（仅允许下载当前文件）
            String policy = generatePolicyForSpecificFile(ossInnerPath, Collections.singletonList(OSS_DOWNLOAD_ACTION));

            // 获取阿里云STS临时凭证（无需永久AccessKey）
            AssumeRoleResponse stsResponse = getAliyunStsResponse(
                    policy,
                    "user-download-" + userId + "-" + System.currentTimeMillis(),
                    DOWNLOAD_TOKEN_EXPIRE_SECONDS
            );

            // 6. 构建并返回 FileTokenVO（填充所有字段）
            return buildFileTokenVO(
                    stsResponse,
                    fileName,
                    ossInnerPath,
                    httpAccessPath,
                    false, // 暂标记为不存在，由客户端后续校验
                    0L   // 暂设为0，由客户端下载后更新
            );
        } catch (Exception e) {
            log.error("用户[{}]获取下载STS令牌失败，文件:[{}]", userId, ossInnerPath, e);
            throw new RuntimeException("获取下载权限失败，请稍后重试");
        }
    }

    /**
     * 生成 HTTP 访问路径（供业务层访问文件，如前端预览）
     */
    private String generateHttpAccessPath(String ossInnerPath) {
        String bucketName = aliyunOssProperties.getBucketName();
        String endpoint = aliyunOssProperties.getStsEndpoint();
        Assert.hasText(bucketName, "OSS桶名称未配置");
        Assert.hasText(endpoint, "OSS端点未配置");
        // https://zhouchunjie-chat.oss-cn-hangzhou.aliyuncs.com/avatars
        return String.format("https://%s.%s.aliyuncs.com/%s", bucketName, endpoint, ossInnerPath);
    }

    /**
     * 生成具体文件的权限策略（支持传入多个Action，精确到文件路径）
     */
    private String generatePolicyForSpecificFile(String ossInnerPath, List<String> actions) throws JsonProcessingException {
        String bucketName = aliyunOssProperties.getBucketName();
        Assert.hasText(bucketName, "OSS桶名称未配置");
        Assert.hasText(ossInnerPath, "文件路径不能为空");
        Assert.notEmpty(actions, "权限动作列表不能为空");

        // 构建最小权限策略（遵循阿里云Policy规范）
        Map<String, Object> policy = new HashMap<>(2);
        policy.put("Version", "1"); // OSS策略版本固定为1

        List<Map<String, Object>> statements = new ArrayList<>(1);
        Map<String, Object> statement = new HashMap<>(3);
        statement.put("Effect", "Allow"); // 允许访问
        statement.put("Action", actions); // 传入权限动作列表（上传多Action，下载单Action）
        // 资源路径：精确到具体文件（格式：acs:oss:*:*:桶名/桶内路径）
        statement.put("Resource", Collections.singletonList(
                String.format("acs:oss:*:*:%s/%s", bucketName, ossInnerPath)
        ));

        statements.add(statement);
        policy.put("Statement", statements);

        String policyString = objectMapper.writeValueAsString(policy);
        log.debug("生成OSS精确权限策略: {}", policyString);
        return policyString;
    }

    /**
     * 获取阿里云STS临时凭证响应
     */
    private AssumeRoleResponse getAliyunStsResponse(String policy, String roleSessionName, Long durationSeconds)
            throws ClientException {

        // 3. 构建STS请求
        AssumeRoleRequest request = new AssumeRoleRequest();
        request.setSysMethod(MethodType.POST);
        request.setRoleArn(aliyunOssProperties.getRoleArn()); // 从Nacos获取角色ARN
        request.setRoleSessionName(roleSessionName);
        request.setPolicy(policy);
        request.setDurationSeconds(durationSeconds);

        log.debug("请求阿里云STS服务（Nacos配置凭证），角色会话名: {}", roleSessionName);
        return client.getAcsResponse(request);
    }

    /**
     * 构建 FileTokenVO（严格匹配VO字段，无永久AccessKey相关逻辑）
     */
    private FileTokenVO buildFileTokenVO(
            AssumeRoleResponse stsResponse,
            String fileName,
            String ossInnerPath,
            String httpAccessPath,
            boolean exist,
            Long size
    ) {
        AssumeRoleResponse.Credentials credentials = stsResponse.getCredentials();
        FileTokenVO tokenVO = new FileTokenVO();

        // 1. 文件基础信息（VO的Info部分）
        tokenVO.setExist(exist);       // 上传时false，下载时默认false（客户端后续校验）
        tokenVO.setName(fileName);     // OSS文件名
        tokenVO.setPath(ossInnerPath); // OSS桶内具体路径（供SDK操作）
        tokenVO.setSize(size);         // 上传时0，下载时0（客户端下载后获取实际大小）

        // 2. 临时凭证信息（VO的Token部分）
        tokenVO.setRegion("oss-"+aliyunOssProperties.getRegion()); // OSS地域（如oss-cn-hangzhou）
        tokenVO.setBucket(aliyunOssProperties.getBucketName()); // OSS桶名
        tokenVO.setAccessKeyId(credentials.getAccessKeyId()); // 临时AccessKey ID
        tokenVO.setAccessKeySecret(credentials.getAccessKeySecret()); // 临时AccessKey Secret
        tokenVO.setSecurityToken(credentials.getSecurityToken()); // 安全令牌（STS核心字段）
        tokenVO.setExpiration(credentials.getExpiration()); // 凭证过期时间（UTC格式）

        // 3. 业务访问信息
        tokenVO.setHttpAccessPath(httpAccessPath); // 完整HTTP访问路径

        return tokenVO;
    }

}