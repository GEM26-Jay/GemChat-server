package com.zcj.common.dto;

import lombok.Data;

@Data
public class ChatMessageSyncDTO {
    Long sessionId;
    Long lastMessageId;
}
