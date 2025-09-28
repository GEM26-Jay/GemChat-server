package com.zcj.servicechat.service;

import com.zcj.common.entity.ChatMessage;
import com.zcj.common.dto.ChatMessageSyncDTO;

import java.util.List;

public interface ChatMessageService {
    List<ChatMessage> info(Long sessionId, List<Long> messageIds);

    List<ChatMessage> sync(Long sessionId, Long lastMessageId);

    List<ChatMessage> syncBatch(List<ChatMessageSyncDTO> list);
}
