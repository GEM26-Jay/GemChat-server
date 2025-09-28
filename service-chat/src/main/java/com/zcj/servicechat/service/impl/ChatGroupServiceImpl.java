package com.zcj.servicechat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zcj.common.context.UserContext;
import com.zcj.common.dto.CreateGroupDTO;
import com.zcj.common.entity.ChatSession;
import com.zcj.common.entity.ChatGroup;
import com.zcj.common.entity.GroupMember;
import com.zcj.common.feign.NettyFeignClient;
import com.zcj.common.utils.SnowflakeIdGenerator;
import com.zcj.servicechat.mapper.ChatSessionMapper;
import com.zcj.servicechat.mapper.GroupMapper;
import com.zcj.servicechat.mapper.GroupMemberMapper;
import com.zcj.servicechat.service.ChatGroupService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Service
@AllArgsConstructor
public class ChatGroupServiceImpl implements ChatGroupService {
    final GroupMapper groupMapper;
    final GroupMemberMapper groupMemberMapper;
    final SnowflakeIdGenerator idGenerator;
    final NettyFeignClient nettyFeignClient;
    final ChatSessionMapper chatSessionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChatGroup add(CreateGroupDTO dto) {
        Long userId = UserContext.getId();
        long now = System.currentTimeMillis();
        ChatGroup group = new ChatGroup();
        group.setId(idGenerator.nextId());
        group.setName(dto.getGroupName());
        group.setCreatedAt(now);
        group.setUpdatedAt(now);
        group.setCreateUser(userId);
        int number = dto.getUserIds().size()+1;
        group.setNumber(number);
        group.setStatus(ChatGroup.GROUP_NORMAL);
        groupMapper.insert(group);

        GroupMember groupMember = new GroupMember();
        groupMember.setGroupId(group.getId());
        groupMember.setUserId(userId);
        groupMember.setStatus(GroupMember.GROUP_MEMBER_STATUS_NORMAL);
        groupMember.setRole(GroupMember.GROUP_MEMBER_ROLE_OWNER);
        groupMember.setCreatedAt(now);
        groupMember.setUpdatedAt(now);
        groupMemberMapper.insert(groupMember);

        for (Long id : dto.getUserIds()) {
            GroupMember member = new GroupMember();
            member.setGroupId(group.getId());
            member.setUserId(id);
            member.setStatus(GroupMember.GROUP_MEMBER_STATUS_NORMAL);
            member.setRole(GroupMember.GROUP_MEMBER_ROLE_NORMAL);
            member.setCreatedAt(now);
            member.setUpdatedAt(now);
            groupMemberMapper.insert(member);
        }

        // 为群聊创建会话
        ChatSession chatSession = new ChatSession();
        chatSession.setId(idGenerator.nextId());
        chatSession.setType(ChatSession.TYPE_GROUP);
        chatSession.setFirstId(group.getId());
        chatSession.setCreatedAt(now);
        chatSession.setUpdatedAt(now);
        chatSession.setStatus(ChatSession.STATUS_NORMAL);
        chatSessionMapper.insert(chatSession);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                nettyFeignClient.sync(userId, "chat_session");
                nettyFeignClient.sync(userId, "chat_group");
                nettyFeignClient.sync(userId, "group_member");
                nettyFeignClient.syncBatch(dto.getUserIds(), "chat_session");
                nettyFeignClient.syncBatch(dto.getUserIds(), "chat_group");
                nettyFeignClient.syncBatch(dto.getUserIds(), "group_member");
            }
        });
        return group;
    }

    @Override
    @Transactional()
    public void delete(long groupId, Long userId) {
        ChatGroup group = groupMapper.selectById(groupId);
        if (group == null || group.getStatus() == ChatGroup.GROUP_DELETED) {
            throw new RuntimeException("该群聊不存在");
        } else if (!Objects.equals(group.getCreateUser(), userId)) {
            throw new RuntimeException("您没有权限删除");
        }
        // 删除群组
        group.setStatus(ChatGroup.GROUP_DELETED);
        groupMapper.updateById(group);

        // 删除群成员
        GroupMember groupMember = new GroupMember();
        groupMember.setStatus(GroupMember.GROUP_MEMBER_STATUS_DELETED);
        groupMemberMapper.update(groupMember, new LambdaQueryWrapper<GroupMember>().eq(GroupMember::getGroupId, groupId));

        // 更新会话数据
        ChatSession cs = chatSessionMapper.selectOne(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getFirstId, groupId)
                .eq(ChatSession::getType, ChatSession.TYPE_GROUP));
        cs.setStatus(ChatSession.STATUS_DELETED);
        chatSessionMapper.updateById(cs);

        List<Long> memberIds = groupMemberMapper.selectList(new LambdaQueryWrapper<GroupMember>()
                .eq(GroupMember::getGroupId, groupId)).stream().map(GroupMember::getUserId).toList();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                nettyFeignClient.syncBatch(memberIds, "chat_session");
                nettyFeignClient.syncBatch(memberIds, "chat_group");
                nettyFeignClient.syncBatch(memberIds, "group_member");
            }
        });
    }

    @Override
    @Transactional()
    public ChatGroup update(ChatGroup group, Long userId) {
        LambdaQueryWrapper<GroupMember> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(GroupMember::getGroupId, group.getId());
        queryWrapper.eq(GroupMember::getUserId, userId);
        GroupMember groupMember = groupMemberMapper.selectOne(queryWrapper);
        if (groupMember == null || groupMember.getStatus() == GroupMember.GROUP_MEMBER_STATUS_DELETED) {
            throw new RuntimeException("您不是群成员，无法更新群信息");
        } else if (groupMember.getRole() == GroupMember.GROUP_MEMBER_ROLE_NORMAL) {
            throw new RuntimeException("您没有权限，无法更新群信息");
        }
        group.setUpdatedAt(System.currentTimeMillis());
        groupMapper.updateById(group);

        List<Long> memberIds = groupMemberMapper.selectList(
                new LambdaQueryWrapper<GroupMember>().eq(GroupMember::getGroupId, group.getId()))
                .stream().map(GroupMember::getUserId).toList();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                nettyFeignClient.syncBatch(memberIds, "chat_group");
            }
        });

        return group;
    }

    @Override
    public ChatGroup info(long groupId) {
        LambdaQueryWrapper<ChatGroup> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ChatGroup::getId, groupId);
        return groupMapper.selectOne(queryWrapper);
    }

    @Override
    public List<ChatGroup> sync(long lastUpdateAt, Long userId) {
        LambdaQueryWrapper<GroupMember> groupMemberQueryWrapper = new LambdaQueryWrapper<>();
        groupMemberQueryWrapper.eq(GroupMember::getUserId, userId);
        groupMemberQueryWrapper.select(GroupMember::getGroupId);
        List<Long> groupIds = groupMemberMapper.selectObjs(groupMemberQueryWrapper)
                .stream()
                .map((obj)-> (long)obj)
                .toList();
        LambdaQueryWrapper<ChatGroup> groupQueryWrapper = new LambdaQueryWrapper<>();
        if (!groupIds.isEmpty()) {
            groupQueryWrapper.in(ChatGroup::getId, groupIds);
            groupQueryWrapper.gt(ChatGroup::getUpdatedAt, lastUpdateAt);
            return groupMapper.selectList(groupQueryWrapper);
        }
        return Collections.emptyList();
    }
}
