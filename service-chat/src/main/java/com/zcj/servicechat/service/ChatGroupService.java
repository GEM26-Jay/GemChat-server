package com.zcj.servicechat.service;

import com.zcj.common.dto.CreateGroupDTO;
import com.zcj.common.entity.ChatGroup;

import java.util.List;

public interface ChatGroupService {
    ChatGroup add(CreateGroupDTO createGroupDTO);

    void delete(long groupId, Long userId);

    ChatGroup update(ChatGroup group, Long userId);

    ChatGroup info(long groupId);

    List<ChatGroup> sync(long lastUpdateAt, Long userId);
}
