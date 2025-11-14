package com.zcj.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Schema(description = "聊天消息表")
@Data
@TableName("chat_message")
public class ChatMessage {

    /********************* 消息类型常量 *********************/
    public static final int TYPE_TEXT = 1;          // 文本消息
    public static final int TYPE_IMAGE = 2;         // 图片消息
    public static final int TYPE_AUDIO = 3;         // 音频消息
    public static final int TYPE_VIDEO = 4;         // 视频消息
    public static final int TYPE_OTHER_FILE = 5;    // 其他文件
    public static final int TYPE_LOCATION = 6;      // 位置信息

    /********************* 消息状态常量 *********************/
    public static final int STATUS_SUCCESS = 1;     // 正常
    public static final int STATUS_DELETE = 2;      // 已删除
    public static final int STATUS_DRAWBACK = 3;    // 已撤回

    @Schema(description = "会话ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sessionId;

    @Schema(description = "消息ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long messageId;

    @Schema(description = "消息类型", example = "TYPE_TEXT | TYPE_IMAGE | TYPE_FILE 等")
    private Integer type;

    @Schema(description = "发送者ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fromId;

    @Schema(description = "消息内容")
    private String content;

    @Schema(description = "消息状态")
    private Integer status;

    @Schema(description = "引用消息ID（回复功能）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long replyToId;

    @Schema(description = "发送时间戳")
    private Long createdAt;

    @Schema(description = "更新时间戳")
    private Long updatedAt;

    @TableField(exist = false)
    @Schema(description = "消息发送者标识")
    private Long identityId;

}