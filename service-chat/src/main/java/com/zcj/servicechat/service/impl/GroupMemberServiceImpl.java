package com.zcj.servicechat.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zcj.common.context.UserContext;
import com.zcj.common.entity.ChatGroup;
import com.zcj.common.entity.GroupMember;
import com.zcj.common.feign.NettyFeignClient;
import com.zcj.servicechat.mapper.GroupMapper;
import com.zcj.servicechat.mapper.GroupMemberMapper;
import com.zcj.servicechat.service.GroupMemberService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class GroupMemberServiceImpl implements GroupMemberService {

    final GroupMemberMapper groupMemberMapper;
    final NettyFeignClient nettyFeignClient;
    final GroupMapper groupMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupMember add(Long groupId, Long userId) {
        long opUserId = UserContext.getId();
        LambdaQueryWrapper<GroupMember> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(GroupMember::getGroupId, groupId);
        queryWrapper.eq(GroupMember::getUserId, opUserId);
        GroupMember opMember = groupMemberMapper.selectOne(queryWrapper);
        if (opMember == null) {
            throw new RuntimeException("您不在群聊中，无法添加成员");
        } else if (opMember.getRole() == GroupMember.GROUP_MEMBER_ROLE_NORMAL) {
            throw new RuntimeException("您没有权限，无法添加成员");
        }

        queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(GroupMember::getGroupId, groupId);
        queryWrapper.eq(GroupMember::getUserId, userId);

        GroupMember groupMember = groupMemberMapper.selectOne(queryWrapper);
        boolean isExist = false;

        if (groupMember != null) {
            isExist = true;
            if (groupMember.getStatus() != GroupMember.GROUP_MEMBER_STATUS_DELETED) {
                throw new RuntimeException("该用户已存在群聊中");
            }
        } else {
            groupMember = new GroupMember();
        }

        groupMember.setGroupId(groupId);
        groupMember.setUserId(userId);
        groupMember.setRole(GroupMember.GROUP_MEMBER_ROLE_NORMAL);
        groupMember.setStatus(GroupMember.GROUP_MEMBER_STATUS_NORMAL);
        long now = System.currentTimeMillis();
        groupMember.setUpdatedAt(now);
        groupMember.setCreatedAt(now);

        if (isExist) {
            groupMemberMapper.update(groupMember, queryWrapper);
        } else {
            groupMemberMapper.insert(groupMember);
        }

        while (true) {
            ChatGroup oldGroup = groupMapper.selectById(groupId);
            ChatGroup newGroup = new ChatGroup();
            newGroup.setNumber(oldGroup.getNumber()+1);
            newGroup.setUpdatedAt(System.currentTimeMillis());
            LambdaUpdateWrapper<ChatGroup> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(ChatGroup::getId, oldGroup.getId());
            updateWrapper.set(ChatGroup::getUpdatedAt, oldGroup.getUpdatedAt());
            int updated = groupMapper.update(newGroup, updateWrapper);
            if (updated > 0) {
                break;
            }
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                nettyFeignClient.sync(userId, "chat_group");
                nettyFeignClient.sync(userId, "group_member");
                nettyFeignClient.sync(userId, "chat_session");
                List<Long> memberIds = groupMemberMapper.selectList(
                                new LambdaQueryWrapper<GroupMember>().eq(GroupMember::getGroupId, groupId))
                        .stream().map(GroupMember::getUserId).toList();
                nettyFeignClient.syncBatch(memberIds, "chat_group");
                nettyFeignClient.syncBatch(memberIds, "group_member");
            }
        });

        return groupMember;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long groupId, Long userId) {
        long opUserId = UserContext.getId();
        if (opUserId != userId){
            LambdaQueryWrapper<GroupMember> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(GroupMember::getGroupId, groupId);
            queryWrapper.eq(GroupMember::getUserId, opUserId);
            GroupMember opMember = groupMemberMapper.selectOne(queryWrapper);
            if (opMember == null) {
                throw new RuntimeException("您不在群聊中，无法删除成员");
            } else if (opMember.getRole() == GroupMember.GROUP_MEMBER_ROLE_NORMAL) {
                throw new RuntimeException("您没有权限，无法删除成员");
            }
        }
        // 查找全体成员
        List<Long> memberIds = groupMemberMapper.selectList(
                        new LambdaQueryWrapper<GroupMember>().eq(GroupMember::getGroupId, groupId))
                .stream().map(GroupMember::getUserId).toList();

        LambdaQueryWrapper<GroupMember> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(GroupMember::getGroupId, groupId);
        queryWrapper.eq(GroupMember::getUserId, userId);
        GroupMember updateGroupMember = new GroupMember();
        updateGroupMember.setStatus(GroupMember.GROUP_MEMBER_STATUS_DELETED);
        groupMemberMapper.update(updateGroupMember, queryWrapper);

        while (true) {
            ChatGroup oldGroup = groupMapper.selectById(groupId);
            ChatGroup newGroup = new ChatGroup();
            newGroup.setNumber(oldGroup.getNumber()-1);
            newGroup.setUpdatedAt(System.currentTimeMillis());
            LambdaUpdateWrapper<ChatGroup> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(ChatGroup::getId, oldGroup.getId());
            updateWrapper.set(ChatGroup::getUpdatedAt, oldGroup.getUpdatedAt());
            int updated = groupMapper.update(newGroup, updateWrapper);
            if (updated > 0) {
                break;
            }
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                nettyFeignClient.syncBatch(memberIds, "group_member");
                nettyFeignClient.syncBatch(memberIds, "group_member");
            }
        });
    }

    @Override
    public List<GroupMember> sync(Long lastUpdateAt) {
        // 1. 获取当前用户ID
        long userId = UserContext.getId();

        // 2. 查询当前用户所属的所有群组ID
        LambdaQueryWrapper<GroupMember> groupIdQuery = new LambdaQueryWrapper<>();
        groupIdQuery.eq(GroupMember::getUserId, userId)
                .select(GroupMember::getGroupId);

        List<GroupMember> groupMemberList = groupMemberMapper.selectList(groupIdQuery);
        if (groupMemberList.isEmpty()) {
            return Collections.emptyList();  // 避免空集合导致的后续查询问题
        }

        // 提取群组ID列表
        List<Long> groupIds = groupMemberList.stream()
                .map(GroupMember::getGroupId)
                .distinct() // 去重
                .collect(Collectors.toList());

        // 3. 查询这些群组中更新时间在lastUpdateAt之后的成员
        LambdaQueryWrapper<GroupMember> memberQuery = new LambdaQueryWrapper<>();
        memberQuery.in(GroupMember::getGroupId, groupIds)
                .gt(GroupMember::getUpdatedAt, lastUpdateAt)
                .orderByAsc(GroupMember::getUpdatedAt);

        return groupMemberMapper.selectList(memberQuery);
    }

    /*
    *  用于用户修改自己的群聊备注
    */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public GroupMember update(GroupMember groupMember) {
        LambdaQueryWrapper<GroupMember> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(GroupMember::getGroupId, groupMember.getGroupId());
        queryWrapper.eq(GroupMember::getUserId, groupMember.getUserId());
        GroupMember opMember = new GroupMember();
        opMember.setRemark(groupMember.getRemark());
        long time = System.currentTimeMillis();
        opMember.setUpdatedAt(time);
        groupMemberMapper.update(opMember, queryWrapper);
        groupMember.setUpdatedAt(time);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                List<Long> memberIds = groupMemberMapper.selectList(
                                new LambdaQueryWrapper<GroupMember>().eq(GroupMember::getGroupId, groupMember.getGroupId()))
                        .stream().map(GroupMember::getUserId).toList();
                nettyFeignClient.syncBatch(memberIds, "group_member");
            }
        });
        return groupMember;
    }

    @Override
    public void addBatch(Long groupId, List<Long> userIds) {
        long opUserId = UserContext.getId();
        LambdaQueryWrapper<GroupMember> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(GroupMember::getGroupId, groupId);
        queryWrapper.eq(GroupMember::getUserId, opUserId);
        GroupMember opMember = groupMemberMapper.selectOne(queryWrapper);
        if (opMember == null) {
            throw new RuntimeException("您不在群聊中，无法添加成员");
        } else if (opMember.getRole() == GroupMember.GROUP_MEMBER_ROLE_NORMAL) {
            throw new RuntimeException("您没有权限，无法添加成员");
        }

        int number = userIds.size();
        Iterator<Long> iterator = userIds.iterator();
        while (iterator.hasNext()) {
            long userId = iterator.next();
            queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.eq(GroupMember::getGroupId, groupId);
            queryWrapper.eq(GroupMember::getUserId, userId);

            GroupMember groupMember = groupMemberMapper.selectOne(queryWrapper);
            boolean isExist = false;

            if (groupMember != null) {
                isExist = true;
                if (groupMember.getStatus() != GroupMember.GROUP_MEMBER_STATUS_DELETED) {
                    number--;
                    iterator.remove();
                    continue;
                }
            } else {
                groupMember = new GroupMember();
            }

            groupMember.setGroupId(groupId);
            groupMember.setUserId(userId);
            groupMember.setRole(GroupMember.GROUP_MEMBER_ROLE_NORMAL);
            groupMember.setStatus(GroupMember.GROUP_MEMBER_STATUS_NORMAL);
            long now = System.currentTimeMillis();
            groupMember.setUpdatedAt(now);
            groupMember.setCreatedAt(now);

            if (isExist) {
                groupMemberMapper.update(groupMember, queryWrapper);
            } else {
                groupMemberMapper.insert(groupMember);
            }
        }

        //  自旋，增加数量
        while (true) {
            ChatGroup oldGroup = groupMapper.selectById(groupId);
            ChatGroup newGroup = new ChatGroup();
            newGroup.setNumber(oldGroup.getNumber()+number);
            newGroup.setUpdatedAt(System.currentTimeMillis());
            LambdaUpdateWrapper<ChatGroup> updateWrapper = new LambdaUpdateWrapper<>();
            updateWrapper.eq(ChatGroup::getId, oldGroup.getId());
            updateWrapper.set(ChatGroup::getUpdatedAt, oldGroup.getUpdatedAt());
            int updated = groupMapper.update(newGroup, updateWrapper);
            if (updated > 0) {
                break;
            }
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                List<Long> memberIds = groupMemberMapper.selectList(
                                new LambdaQueryWrapper<GroupMember>().eq(GroupMember::getGroupId, groupId))
                        .stream().map(GroupMember::getUserId).toList();
                nettyFeignClient.syncBatch(memberIds, "group_member");
                nettyFeignClient.syncBatch(memberIds, "chat_group");
            }
        });
    }
}
