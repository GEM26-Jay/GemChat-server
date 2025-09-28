package com.zcj.common.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户关系表（好友关系）实体类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户关系表（好友关系）")
public class UserFriend implements Serializable {

    /********************* 黑名单状态常量 *********************/
    public static final int BLOCK_NO = 0;
    public static final int BLOCK_POST = 1;
    public static final int BLOCK_GET = 2;
    public static final int BLOCK_MUTUAL = 3;

    /********************* 删除状态常量 *********************/
    public static final int DELETE_NO = 0;
    public static final int DELETE_POST = 1;
    public static final int DELETE_GET = 2;
    public static final int DELETE_MUTUAL = 3;

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "关系ID，主键", example = "1")
    @JsonSerialize(using = ToStringSerializer.class)
    @TableId
    private Long id;

    @Schema(description = "用户ID", required = true, example = "1001")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    @Schema(description = "好友ID", required = true, example = "1002")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long friendId;

    @Schema(description = "黑名单类型：0正常，1已拉黑，2被拉黑，3相互拉黑", defaultValue = "1", example = "0")
    private Integer blockStatus = BLOCK_NO;

    @Schema(description = "删除类型：0正常，1已删除，2被删除，3相互删除", defaultValue = "1", example = "0")
    private Integer deleteStatus = DELETE_NO;

    @Schema(description = "好友备注", defaultValue = "", example = "张三")
    private String remark = "";

    @Schema(description = "创建时间", example = "时间戳")
    private Long createdAt;

    @Schema(description = "更新时间", example = "时间戳")
    private Long updatedAt;
}
