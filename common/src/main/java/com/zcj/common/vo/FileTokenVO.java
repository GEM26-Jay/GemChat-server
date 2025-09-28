package com.zcj.common.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FileTokenVO {
    // 文件是否已经存在
    private boolean exist;

    // ============= Info =============
    // OSS文件名
    private String name;
    // OSS 桶内具体路径
    private String path;
    // OSS文件大小
    @JsonSerialize(using = ToStringSerializer.class)
    private Long size;

    // ============= Token =============
    private String region;
    private String bucket;
    // 临时AccessKey ID
    private String accessKeyId;
    // 临时AccessKey Secret
    private String accessKeySecret;
    // 安全令牌
    private String securityToken;
    // 过期时间
    private String expiration;
    // 完整 HTTP 访问路径
    private String httpAccessPath;
}
