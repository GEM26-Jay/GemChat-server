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
    public static final int TYPE_FILE = 3;          // 文件消息
    public static final int TYPE_VOICE = 4;         // 语音消息
    public static final int TYPE_VIDEO = 5;         // 视频消息
    public static final int TYPE_LOCATION = 6;      // 位置消息
    public static final int TYPE_CUSTOM = 99;       // 自定义消息

    /********************* 消息状态常量 *********************/
    public static final int STATUS_SENDING = 1;     // 发送中
    public static final int STATUS_FAILED = 2;   // 已送达
    public static final int STATUS_SUCCESS = 3;        // 已读
    public static final int STATUS_DRAWBACK = 4;      // 发送失败
    public static final int STATUS_DELETED = 5;     // 已删除

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

    @Schema(description = "接收者ID（用户ID或群ID）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long toId;

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
    private int retryCount;

    /**
     * 序列化：content字段Base64编码，所有字段用逗号分隔，一行存储一个对象
     */
    public String toLogString() {
        // 对content进行Base64编码（处理null值）
        String encodedContent = (content != null)
                ? Base64.getEncoder().encodeToString(content.getBytes(StandardCharsets.UTF_8))
                : "";

        // 按固定顺序拼接字段，用逗号分隔
        return String.join(",",
                "sessionId:",
                toStringOrEmpty(sessionId),
                "messageId:",
                toStringOrEmpty(messageId),
                toStringOrEmpty(type),
                toStringOrEmpty(fromId),
                toStringOrEmpty(toId),
                encodedContent,  // 编码后的content
                toStringOrEmpty(status),
                toStringOrEmpty(replyToId),
                toStringOrEmpty(createdAt),
                toStringOrEmpty(updatedAt),
                String.valueOf(retryCount)
        );
    }

    /**
     * 反序列化：从逗号分隔的字符串解析，content字段Base64解码
     */
    public static ChatMessage fromLogString(String logString) {
        if (logString == null || logString.trim().isEmpty()) {
            return null;
        }

        String[] parts = logString.split(",", -1);  // -1确保空字符串也会被保留
        if (parts.length < 11) {  // 最少需要11个字段
            throw new IllegalArgumentException("无效的日志格式: " + logString);
        }

        ChatMessage message = new ChatMessage();
        try {
            message.setSessionId(parseLong(parts[0]));
            message.setMessageId(parseLong(parts[1]));
            message.setType(parseInteger(parts[2]));
            message.setFromId(parseLong(parts[3]));
            message.setToId(parseLong(parts[4]));

            // 解码content（处理空字符串）
            if (!parts[5].isEmpty()) {
                byte[] contentBytes = Base64.getDecoder().decode(parts[5]);
                message.setContent(new String(contentBytes, StandardCharsets.UTF_8));
            } else {
                message.setContent(null);
            }

            message.setStatus(parseInteger(parts[6]));
            message.setReplyToId(parseLong(parts[7]));
            message.setCreatedAt(parseLong(parts[8]));
            message.setUpdatedAt(parseLong(parts[9]));
            message.setRetryCount(parseInteger(parts[10]));
        } catch (IllegalArgumentException e) {  // Base64解码失败会抛出此异常
            throw new RuntimeException("解析日志字符串失败: " + logString, e);
        }

        return message;
    }

    // 辅助方法：对象转字符串（null转为空字符串）
    private String toStringOrEmpty(Object obj) {
        return obj != null ? obj.toString() : "";
    }

    // 辅助方法：安全解析Long
    private static Long parseLong(String str) {
        if (str == null || str.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(str);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // 辅助方法：安全解析Integer
    private static Integer parseInteger(String str) {
        if (str == null || str.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(str);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}