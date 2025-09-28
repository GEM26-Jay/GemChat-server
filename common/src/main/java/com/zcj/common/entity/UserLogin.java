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
 * 用户登录记录实体类，对应数据库中的 user_login 表
 */
@Schema(description = "用户登录记录信息")
@Data
@TableName("user_login")
public class UserLogin implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /********************* 状态常量 *********************/
    public static final int TYPE_SUCCESS = 1;
    public static final int TYPE_FAILED = 2;

    @Schema(description = "记录ID，主键", example = "1")
    @JsonSerialize(using = ToStringSerializer.class)
    @TableId
    private Long id;

    @Schema(description = "关联用户ID", required = true, example = "1")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    @Schema(description = "登录状态", example = "1")
    private Integer status;

    @Schema(description = "登录平台", example = "Web、Android、iOS 等")
    private String platform;

    @Schema(description = "登录IP地址", example = "192.168.1.1")
    private String loginIp;

    @Schema(description = "设备哈希值，用于区分设备", example = "a1b2c3d4e5f67890")
    private String deviceHash;

    @Schema(description = "备注信息，可记录异常登录等情况", example = "正常登录")
    private String remark;

    @Schema(description = "登录时间戳", example = "1692508800000")
    private Long createdAt;
}