package com.zcj.common.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.*;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * 用户实体类，对应数据库中的user表
 */
@Schema(description = "用户基本信息")
@Data
@TableName("user")
public class User implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /********************* 性别常量 *********************/
    public static final int GENDER_UNKNOWN = 0;
    public static final int GENDER_MALE = 1;
    public static final int GENDER_FEMALE = 2;

    /********************* 用户状态常量 *********************/
    public static final int STATUS_DISABLED = 0;
    public static final int STATUS_NORMAL = 1;
    public static final int STATUS_FROZEN = 2;
    public static final int STATUS_DELETED = 3;

    @Schema(description = "用户ID，主键", example = "1")
    @JsonSerialize(using = ToStringSerializer.class)
    @TableId
    private Long id;

    @Schema(description = "用户名", required = true, example = "john_doe")
    private String username;

    @Schema(description = "加密后的密码（使用BCrypt等算法）", required = true, example = "$2a$10$xxxxxxxxxxxxxx")
    private String passwordHash;

    @Schema(description = "邮箱", example = "john@example.com")
    private String email;

    @Schema(description = "手机号", example = "13800138000")
    private String phone;

    @Schema(description = "头像URL", defaultValue = "default_avatar.png")
    private String avatar = "default_avatar.png";

    @Schema(description = "个性签名", defaultValue = "", example = "Hello World!")
    private String signature = "";

    @Schema(description = "性别：0未知，1男，2女", defaultValue = "0", example = "1")
    private Integer gender = 0;

    @Schema(description = "出生日期", example = "1990-01-01")
    private LocalDate birthdate;

    @Schema(description = "用户状态：0禁用，1正常，2冻结", defaultValue = "1", example = "1")
    private Integer status = 1;

    @Schema(description = "创建时间", example = "时间戳")
    private Long createdAt;

    @Schema(description = "更新时间", example = "时间戳")
    private Long updatedAt;

}