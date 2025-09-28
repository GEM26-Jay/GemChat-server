package com.zcj.common.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Schema(description = "群聊用户表")
@Data
@TableName("group_member")
public class GroupMember implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /********************* 群组成员状态常量 *********************/
    public static final int GROUP_MEMBER_STATUS_NORMAL = 0;
    public static final int GROUP_MEMBER_STATUS_DISABLE = 1;
    public static final int GROUP_MEMBER_STATUS_DELETED = 2;

    /********************* 群组成员角色常量 *********************/
    public static final int GROUP_MEMBER_ROLE_OWNER = 1;
    public static final int GROUP_MEMBER_ROLE_ADMIN = 2;
    public static final int GROUP_MEMBER_ROLE_NORMAL = 3;

    @Schema(description = "群聊ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long groupId;

    @Schema(description = "用户ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    @Schema(description = "用户备注名")
    private String remark;

    @Schema(description = "用户状态")
    private Integer status;

    @Schema(description = "用户角色")
    private Integer role;

    @Schema(description = "创建时间", example = "时间戳")
    private Long createdAt;

    @Schema(description = "更新时间", example = "时间戳")
    private Long updatedAt;

}
