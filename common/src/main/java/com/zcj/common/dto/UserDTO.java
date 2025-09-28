package com.zcj.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

@Schema(description = "用户数据传输")
@Data
public class UserDTO {

    @Schema(description = "用户ID，主键",example = "1")
    private Long id;

    @Schema(description = "用户名", required = true, example = "john_doe")
    private String username;

    @Schema(description = "密码", required = true, example = "")
    private String password;

    @Schema(description = "邮箱", example = "john@example.com")
    private String email;

    @Schema(description = "手机号", example = "13800138000")
    private String phone;

    @Schema(description = "头像文件名", defaultValue = "default_avatar.png", example = "https://example.com/avatar.png")
    private String avatar;

    @Schema(description = "个性签名", defaultValue = "", example = "Hello World!")
    private String signature = "";

    @Schema(description = "性别：0未知，1男，2女", defaultValue = "0", example = "1")
    private Integer gender = 0;

    @Schema(description = "出生日期", example = "1990-01-01")
    private LocalDate birthdate;
}
