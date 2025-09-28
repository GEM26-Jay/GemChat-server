package com.zcj.servicechat.service;

import com.zcj.common.entity.ChatSession;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ChatSessionService {

    @Transactional(rollbackFor = Exception.class)
    ChatSession addGroupSession(Long groupId, Long ownerId);

    List<ChatSession> syncSingleChatSession(Long userId, Long lastUpdateAt);

    List<ChatSession> syncGroupChatSession(Long userId, Long lastUpdateAt);

    ChatSession addSingleSession(Long firstId, Long secondId);
}
