package com.zcj.common.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Schema(description = "聊天会话表")
@Data
@TableName("chat_session")
public class ChatSession {

    /********************* 会话类型常量 *********************/
    public static final int TYPE_SINGLE = 1;
    public static final int TYPE_GROUP = 2;

    /********************* 会话状态常量 *********************/
    public static final int STATUS_NORMAL = 1;
    public static final int STATUS_DELETED = 2;

    @Schema(description = "会话ID，主键")
    @JsonSerialize(using = ToStringSerializer.class)
    @TableId
    private Long id;

    @Schema(description = "会话类型：1-单聊，2-群聊")
    private Integer type;

    @Schema(description = "如果是单聊，则为第一个用户的ID，如果是群聊，则是群聊ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long firstId;

    @Schema(description = "如果是单聊，则为第二个用户的ID，如果是群聊，则为空")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long secondId;

    @Schema(description = "最后一条消息ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long lastMessageId;

    @Schema(description = "最后一条消息内容摘要")
    private String lastMessageContent;

    @Schema(description = "最后一条消息时间戳")
    private Long lastMessageTime;

    @Schema(description = "会话状态：1-正常")
    private Integer status;

    @Schema(description = "创建时间戳")
    private Long createdAt;

    @Schema(description = "更新时间戳")
    private Long updatedAt;

}