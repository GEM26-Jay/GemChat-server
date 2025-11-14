package com.zcj.common.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;


@Schema(description = "头像仓库")
@Data
@TableName("avatar_box")
public class AvatarBox implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /********************* 文件状态常量 *********************/
    public static final int STATUS_UPLOADING = 0;
    public static final int STATUS_SUCCESS = 1;
    public static final int STATUS_FAILED = 2;
    public static final int STATUS_DELETED = 3;

    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    @TableId
    private Long id;

    @Schema(description = "原始文件名", example = "project_report.pdf")
    private String name;

    @Schema(description = "文件指纹(SHA256), 用于内容去重", required = true)
    private String fingerprint;

    @Schema(description = "文件大小(字节)", example = "1048576")
    private Long size;

    @Schema(description = "文件MIME类型", example = "application/pdf")
    private String mimeType;

    @Schema(description = "文件的物理存储路径")
    private String location;

    @Schema(description = "被引用次数", example = "5")
    private Integer referCount = 0;

    @Schema(description = "状态: 0-正在上传，1-成功上传，2-上传失败，3-已删除", example = "1")
    private Integer status = STATUS_UPLOADING;

    @Schema(description = "上传用户", example = "1")
    private Long fromId;

    @Schema(description = "创建时间戳", example = "1672531200000")
    private Long createdAt;

    @Schema(description = "更新时间戳", example = "1672531260000")
    private Long updatedAt;

}