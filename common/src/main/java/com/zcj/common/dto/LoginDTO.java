package com.zcj.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;


@Schema(description = "用户登录数据传输")
@Data
public class LoginDTO {

    @Schema(description = "账号", required = true, example = "手机号/邮箱")
    private String account;

    @Schema(description = "密码", required = true, example = "")
    private String password;

    @Schema(description = "登录平台", example = "Web、Android、iOS 等")
    private String platform;

    @Schema(description = "设备哈希值，用于区分设备", example = "a1b2c3d4e5f67890")
    private String deviceHash;

}