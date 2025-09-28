package com.zcj.servicechat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zcj.common.entity.ChatSession;
import com.zcj.common.entity.GroupMember;
import com.zcj.common.feign.NettyFeignClient;
import com.zcj.common.utils.SnowflakeIdGenerator;
import com.zcj.servicechat.mapper.ChatMessageMapper;
import com.zcj.servicechat.mapper.ChatSessionMapper;
import com.zcj.servicechat.mapper.GroupMemberMapper;
import com.zcj.servicechat.service.ChatSessionService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class ChatSessionServiceImpl implements ChatSessionService {

    final ChatSessionMapper chatSessionMapper;
    final SnowflakeIdGenerator snowflakeIdGenerator;
    final GroupMemberMapper groupMemberMapper;
    final NettyFeignClient nettyFeignClient;
    private final ChatMessageMapper chatMessageMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatSession addSingleSession(Long firstId, Long secondId) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                nettyFeignClient.sync(firstId, "chat_session");
                nettyFeignClient.sync(secondId, "chat_session");
            }
        });

        LambdaQueryWrapper<ChatSession> queryWrapper = new LambdaQueryWrapper<>();
        // 1. 类型为单聊
        queryWrapper.eq(ChatSession::getType, ChatSession.TYPE_SINGLE);
        // 2. 是否已存在会话记录
        queryWrapper.and(wrapper -> wrapper
                .eq(ChatSession::getFirstId, firstId)
                .eq(ChatSession::getSecondId, secondId)
                .or()
                .eq(ChatSession::getSecondId, secondId)
                .eq(ChatSession::getFirstId, firstId)
        );

        ChatSession one = chatSessionMapper.selectOne(queryWrapper);
        if (one != null) {
            // 已存在会话，不再创建
            return one;
        }

        long nowTimeMills = System.currentTimeMillis();
        ChatSession chatSession = new ChatSession();
        chatSession.setId(snowflakeIdGenerator.nextId());
        chatSession.setType(ChatSession.TYPE_SINGLE);
        chatSession.setFirstId(firstId);
        chatSession.setSecondId(secondId);
        chatSession.setCreatedAt(nowTimeMills);
        chatSession.setUpdatedAt(nowTimeMills);
        chatSessionMapper.insert(chatSession);
        return chatSession;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatSession addGroupSession(Long groupId, Long ownerId) {
        LambdaQueryWrapper<ChatSession> queryWrapper = new LambdaQueryWrapper<>();
        // 1. 类型为单聊
        queryWrapper.eq(ChatSession::getType, ChatSession.TYPE_GROUP);
        // 2. 是否已存在会话记录
        queryWrapper.eq(ChatSession::getFirstId, groupId);

        ChatSession one = chatSessionMapper.selectOne(queryWrapper);
        if (one != null) {
            // 已存在会话信息，不再创建
            return one;
        }

        long nowTimeMills = System.currentTimeMillis();
        ChatSession chatSession = new ChatSession();
        chatSession.setId(snowflakeIdGenerator.nextId());
        chatSession.setType(ChatSession.TYPE_GROUP);
        chatSession.setFirstId(groupId);
        chatSession.setCreatedAt(nowTimeMills);
        chatSession.setUpdatedAt(nowTimeMills);
        chatSessionMapper.insert(chatSession);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                nettyFeignClient.sync(ownerId, "chat_session");
            }
        });
        return chatSession;
    }

    @Override
    public List<ChatSession> syncSingleChatSession(Long userId, Long lastUpdateAt) {
        LambdaQueryWrapper<ChatSession> queryWrapper = new LambdaQueryWrapper<>();
        // 1. 类型为单聊
        queryWrapper.eq(ChatSession::getType, ChatSession.TYPE_SINGLE);
        // 2. 同时满足：(firstId = userId 或者 secondId = userId)
        queryWrapper.and(wrapper -> wrapper
                .eq(ChatSession::getFirstId, userId)
                .or()
                .eq(ChatSession::getSecondId, userId)
        );
        // 3. 查询最后更新时间大于 lastUpdateAt 的会话
        queryWrapper.gt(ChatSession::getUpdatedAt, lastUpdateAt);

        List<ChatSession> sessions = chatSessionMapper.selectList(queryWrapper);
        if (sessions.isEmpty()) {
            return new ArrayList<>();
        } else {
            return sessions;
        }

    }

    @Override
    public List<ChatSession> syncGroupChatSession(Long userId, Long lastUpdateAt) {
        // 查找该用户所有的Group
        LambdaQueryWrapper<GroupMember> groupQueryWrapper = new LambdaQueryWrapper<>();
        groupQueryWrapper.eq(GroupMember::getUserId, userId);
        groupQueryWrapper.ne(GroupMember::getStatus, GroupMember.GROUP_MEMBER_STATUS_DELETED);
        groupQueryWrapper.select(GroupMember::getGroupId);
        List<Long> groupIds = groupMemberMapper.selectObjs(groupQueryWrapper)
                .stream().map((o) -> (long) o).toList();
        if (groupIds.isEmpty()) {
            return new ArrayList<>();
        }
        LambdaQueryWrapper<ChatSession> queryWrapper = new LambdaQueryWrapper<>();
        // 1. 类型为群聊
        queryWrapper.eq(ChatSession::getType, ChatSession.TYPE_GROUP);
        // 2. 满足：(firstId in groupIds)
        queryWrapper.in(ChatSession::getFirstId, groupIds);
        // 3. 查询最后更新时间大于 lastUpdateAt 的会话
        queryWrapper.gt(ChatSession::getUpdatedAt, lastUpdateAt);

        return chatSessionMapper.selectList(queryWrapper);
    }

}
