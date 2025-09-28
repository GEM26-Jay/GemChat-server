package com.zcj.common.dto;

import lombok.Data;

import java.util.List;

@Data
public class AddGroupMemberDTO {
    Long groupId;
    List<Long> userIds;
}
