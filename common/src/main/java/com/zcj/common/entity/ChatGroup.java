package com.zcj.common.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Schema(description = "群聊表")
@Data
@TableName("chat_group")
public class ChatGroup implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /********************* 群组状态常量 *********************/
    public static final int GROUP_NORMAL = 0;
    public static final int GROUP_DELETED = 1;
    public static final int GENDER_DISABLE = 2;

    @Schema(description = "群聊ID，主键", example = "1")
    @JsonSerialize(using = ToStringSerializer.class)
    @TableId
    private Long id;

    @Schema(description = "群聊名", required = true, example = "john_doe")
    private String name;

    @Schema(description = "创建者ID", required = true, example = "john_doe")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long createUser;

    @Schema(description = "头像名")
    private String avatar = "default_avatar.png";

    @Schema(description = "群签名", defaultValue = "", example = "Hello World!")
    private String signature = "";

    @Schema(description = "群成员数量", defaultValue = "")
    private Integer number;

    @Schema(description = "群组状态", defaultValue = "")
    private Integer status = GROUP_NORMAL;

    @Schema(description = "创建时间", example = "时间戳")
    private Long createdAt;

    @Schema(description = "更新时间", example = "时间戳")
    private Long updatedAt;

}
