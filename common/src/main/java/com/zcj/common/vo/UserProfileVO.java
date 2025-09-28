package com.zcj.common.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "用户资料视图")
public class UserProfileVO {

    @Schema(description = "用户ID", example = "123456")
    @JsonSerialize(using = ToStringSerializer.class) // 关键注解
    private Long id;

    @Schema(description = "用户名", example = "tech_enthusiast")
    private String username;

    @Schema(description = "邮箱（已脱敏）", example = "exa****@example.com")
    private String maskedEmail;

    @Schema(description = "手机号（已脱敏）", example = "138****1234")
    private String maskedPhone;

    @Schema(description = "头像文件名", example = "https://cdn.example.com/avatars/123456.jpg")
    private String avatar;

    @Schema(description = "个性签名", example = "热爱技术与分享")
    private String signature;

    @Schema(description = "性别：0未知，1男，2女", example = "1")
    private Integer gender;

    @Schema(description = "出生日期（yyyy-MM-dd）", example = "1990-01-01")
    private LocalDate birthdate;

    @Schema(description = "用户状态：0禁用，1正常，2冻结", example = "1")
    private Integer status;

    @Schema(description = "注册时间", example = "2023-01-01T10:00:00")
    private Long createdAt;

    @Schema(description = "修改时间", example = "2023-01-01T10:00:00")
    private Long updatedAt;

    // 构造方法（可根据需要添加）
    public UserProfileVO() {
    }

    // 敏感信息处理
    public void setEmail(String email) {
        this.maskedEmail = email != null ?
                email.replaceAll("(^\\w{3})[^@]*(@.*$)", "$1****$2") : null;
    }

    public void setPhone(String phone) {
        this.maskedPhone = phone != null ?
                phone.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2") : null;
    }

}
