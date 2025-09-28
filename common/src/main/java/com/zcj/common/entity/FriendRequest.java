package com.zcj.common.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;


@Data
@Schema(description = "好友申请实体")
@TableName("friend_request")
public class FriendRequest {

    /********************* 好友申请状态常量 *********************/
    public static final int APPLYING = 0;
    public static final int PASSED = 1;
    public static final int Rejected = 2;

    @Schema(description = "申请ID，主键", example = "123456")
    @JsonSerialize(using = ToStringSerializer.class)
    @TableId
    private Long id;

    @Schema(description = "用户ID", example = "10001", required = true)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long fromId;

    @Schema(description = "目标用户ID", example = "10002", required = true)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long toId;

    @Schema(description = "fromId的好友备注", example = "大学同学")
    private String fromRemark = "";

    @Schema(description = "toId的好友备注", example = "大学同学")
    private String toRemark = "";

    @Schema(description = "申请陈述", example = "我是张三，请通过好友申请")
    private String statement = "";

    @Schema(description = "申请状态: [1: 正在申请, 2: 已通过, 3: 已拒绝]")
    private Integer status = APPLYING;

    @Schema(description = "创建时间", example = "时间戳")
    private Long createdAt;

    @Schema(description = "更新时间", example = "时间戳")
    private Long updatedAt;
}
