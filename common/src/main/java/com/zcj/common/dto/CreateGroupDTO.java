package com.zcj.common.dto;

import lombok.Data;

import java.util.List;

@Data
public class CreateGroupDTO {
    private String groupName;
    private List<Long> userIds;
}
