package com.zcj.servicefile.service.impl;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.GenericRequest;
import com.zcj.servicefile.config.AliyunOssProperties;
import com.zcj.servicefile.service.OssService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@AllArgsConstructor
public class OssServiceImpl implements OssService {

    private final AliyunOssProperties aliyunOssProperties;
    private final OSS client;

    /**
     * 检查OSS中指定目录下的文件是否存在
     *
     * @param dir      目录（如"images/"，空字符串表示根目录，自动处理斜杠）
     * @param fileName 文件名（如"test.jpg"）
     * @return true：存在；false：不存在
     */
    @Override
    public boolean isExist(String dir, String fileName) {
        // 校验文件名非空
        if (!StringUtils.hasText(fileName)) {
            return false;
        }
        // 构建完整的对象Key（目录+文件名）
        String objectKey = buildObjectKey(dir, fileName);
        try {
            // 调用OSS SDK检查文件存在性
            return client.doesObjectExist(aliyunOssProperties.getBucketName(), objectKey);
        } catch (OSSException e) {
            // 处理OSS服务端异常（如权限不足、Bucket不存在等）
            throw new RuntimeException("检查文件存在性失败，objectKey: " + objectKey, e);
        }
    }

    /**
     * 删除OSS中指定目录下的文件
     *
     * @param dir      目录（如"docs/"）
     * @param fileName 文件名（如"old.pdf"）
     * @return true：删除成功；false：文件不存在
     */
    @Override
    public boolean delete(String dir, String fileName) {
        // 校验文件名非空
        if (!StringUtils.hasText(fileName)) {
            return false;
        }
        String objectKey = buildObjectKey(dir, fileName);
        String bucketName = aliyunOssProperties.getBucketName();

        try {
            // 先检查文件是否存在（避免无效删除操作）
            if (!client.doesObjectExist(bucketName, objectKey)) {
                return false;
            }
            // 执行删除操作
            client.deleteObject(new GenericRequest(bucketName, objectKey));
            return true;
        } catch (OSSException e) {
            // 处理删除失败的异常（如文件被锁定、权限不足等）
            throw new RuntimeException("删除文件失败，objectKey: " + objectKey, e);
        }
    }

    /**
     * 构建OSS对象的完整路径（objectKey）
     * 处理目录斜杠问题，确保格式正确（如"images" + "test.jpg" → "images/test.jpg"）
     */
    private String buildObjectKey(String dir, String fileName) {
        // 处理目录为空的情况
        if (!StringUtils.hasText(dir)) {
            return fileName;
        }
        // 确保目录以斜杠结尾，避免拼接后路径错误
        String normalizedDir = dir.endsWith("/") ? dir : dir + "/";
        // 拼接目录和文件名（自动处理文件名前的斜杠，避免重复）
        return normalizedDir + fileName.replaceFirst("^/", "");
    }
}