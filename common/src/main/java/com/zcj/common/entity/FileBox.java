package com.zcj.common.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 文件映射实体类，对应数据库中的 file_map 表
 * 用于基于内容哈希的文件去重存储
 */
@Schema(description = "文件仓库")
@Data
@TableName("file_box")
public class FileBox implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /********************* 文件状态常量 *********************/
    public static final int STATUS_NORMAL = 1;
    public static final int STATUS_DELETED = 0;

    @Schema(description = "主键ID")
    @JsonSerialize(using = ToStringSerializer.class)
    @TableId
    private Long id;

    @Schema(description = "原始文件名", required = true, example = "project_report.pdf")
    private String name;

    @Schema(description = "文件指纹(SHA256), 用于内容去重", required = true)
    private String fingerprint;

    @Schema(description = "文件大小(字节)", required = true, example = "1048576")
    private Long size;

    @Schema(description = "文件MIME类型", required = true, example = "application/pdf")
    private String mimeType;

    @Schema(description = "文件的物理存储路径")
    private String location;

    @Schema(description = "被引用次数", defaultValue = "0", example = "5")
    private Integer referCount = 0;

    @Schema(description = "状态: 1-正常, 0-已删除", defaultValue = "1", example = "1")
    private Integer status = STATUS_NORMAL;

    @Schema(description = "创建时间戳", example = "1672531200000")
    private Long createdAt;

    @Schema(description = "更新时间戳", example = "1672531260000")
    private Long updatedAt;

}