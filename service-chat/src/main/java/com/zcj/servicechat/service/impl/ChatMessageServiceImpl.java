package com.zcj.servicechat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zcj.common.context.UserContext;
import com.zcj.common.entity.ChatMessage;
import com.zcj.common.dto.ChatMessageSyncDTO;
import com.zcj.servicechat.mapper.ChatMessageMapper;
import com.zcj.servicechat.service.ChatMessageService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
@AllArgsConstructor
public class ChatMessageServiceImpl implements ChatMessageService {

    final ChatMessageMapper chatMessageMapper;

    @Override
    public List<ChatMessage> info(Long sessionId, List<Long> messageIds) {
        LambdaQueryWrapper<ChatMessage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ChatMessage::getSessionId, sessionId);
        queryWrapper.in(ChatMessage::getMessageId, messageIds);
        return chatMessageMapper.selectList(queryWrapper);
    }

    @Override
    public List<ChatMessage> sync(Long sessionId, Long lastMessageId) {
        LambdaQueryWrapper<ChatMessage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ChatMessage::getSessionId, sessionId);
        queryWrapper.gt(ChatMessage::getMessageId, lastMessageId);
        return chatMessageMapper.selectList(queryWrapper);
    }

    @Override
    public List<ChatMessage> syncBatch(List<ChatMessageSyncDTO> list) {
        Long id = UserContext.getId();
        if (list.isEmpty()) {
            return Collections.emptyList();
        }
        for (ChatMessageSyncDTO vo : list) {
            if (vo.getLastMessageId() == null) {
                vo.setLastMessageId(-1L);
            }
        }
        return chatMessageMapper.selectBatchBySessionAndLastId(list);
    }
}
