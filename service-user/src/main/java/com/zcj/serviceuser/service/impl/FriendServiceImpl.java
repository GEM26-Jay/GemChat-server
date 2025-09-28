package com.zcj.serviceuser.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zcj.common.context.UserContext;
import com.zcj.common.entity.FriendRequest;
import com.zcj.common.entity.UserFriend;
import com.zcj.common.feign.NettyFeignClient;
import com.zcj.common.feign.ChatServiceFeignClient;
import com.zcj.serviceuser.mapper.FriendRequireMapper;
import com.zcj.serviceuser.mapper.UserFriendMapper;
import com.zcj.serviceuser.service.FriendService;
import com.zcj.common.utils.SnowflakeIdGenerator;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@AllArgsConstructor
public class FriendServiceImpl implements FriendService {

    final private FriendRequireMapper friendRequireMapper;
    final private UserFriendMapper userFriendMapper;
    final private SnowflakeIdGenerator idGenerator;
    final private NettyFeignClient nettyFeignClient;
    final private ChatServiceFeignClient chatServiceFeignClient;

    @Override
    @Transactional
    public FriendRequest applyRequest(FriendRequest request) {
        request.setId(idGenerator.nextId());
        request.setStatus(FriendRequest.APPLYING);
        request.setCreatedAt(System.currentTimeMillis());
        request.setUpdatedAt(System.currentTimeMillis());
        friendRequireMapper.insert(request);
        nettyFeignClient.sync(request.getToId(), "friend_request");
        return request;
    }

    @Override
    @Transactional
    public FriendRequest updateRequest(FriendRequest request) {
        FriendRequest original = friendRequireMapper.selectById(request.getId());
        if (original == null) {
            throw new RuntimeException("original FriendRequest not exist");
        }
        if (original.getStatus() != FriendRequest.APPLYING) {
            throw new RuntimeException("状态不允许回退");
        }

        // 记录需要同步的用户ID（用于事务提交后使用）
        Long fromId = request.getFromId();
        Long toId = request.getToId();
        boolean needSyncUserFriend = false;

        if (request.getStatus() == FriendRequest.PASSED) {
            // 验证通过, 添加好友列表
            UserFriend fromUser = new UserFriend();
            fromUser.setId(idGenerator.nextId());
            fromUser.setUserId(fromId);
            fromUser.setFriendId(toId);
            fromUser.setRemark(request.getFromRemark());
            Long time = System.currentTimeMillis();
            fromUser.setCreatedAt(time);
            fromUser.setUpdatedAt(time);

            UserFriend toUser = new UserFriend();
            toUser.setId(idGenerator.nextId());
            toUser.setUserId(toId);
            toUser.setFriendId(fromId);
            toUser.setRemark(request.getToRemark());
            toUser.setCreatedAt(time);
            toUser.setUpdatedAt(time);

            LambdaQueryWrapper<UserFriend> queryWrapper = new LambdaQueryWrapper<>();
            queryWrapper.and(wq1 -> wq1
                    .eq(UserFriend::getUserId, fromId)
                    .eq(UserFriend::getFriendId, toId)
            ).or(wq2 -> wq2
                    .eq(UserFriend::getUserId, toId)
                    .eq(UserFriend::getFriendId, fromId)
            );

            List<UserFriend> list = userFriendMapper.selectList(queryWrapper);
            if (list.size() > 0){
                assert list.size() == 2;
                UserFriend selfItem = null;
                UserFriend otherItem = null;
                for (UserFriend item : list) {
                    if (item.getFriendId().equals(toId)
                            && item.getUserId().equals(fromId)) {
                        selfItem = item;
                    } else {
                        otherItem = item;
                    }
                }
                assert selfItem != null && otherItem != null;
                fromUser.setId(selfItem.getId());
                toUser.setId(otherItem.getId());
                userFriendMapper.updateById(fromUser);
                userFriendMapper.updateById(toUser);
            } else {
                userFriendMapper.insert(fromUser);
                userFriendMapper.insert(toUser);
            }
            // 成为了好友则创建聊天会话
            chatServiceFeignClient.addChatSessionSingle(fromId, toId);

            needSyncUserFriend = true; // 标记需要发送好友列表同步信号
        }

        request.setUpdatedAt(System.currentTimeMillis());
        friendRequireMapper.updateById(request);

        // 注册事务同步器：在事务提交后执行同步逻辑
        boolean finalNeedSyncUserFriend = needSyncUserFriend;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // 事务提交后执行同步操作
                if (finalNeedSyncUserFriend) {
                    // 发送好友列表同步信号
                    nettyFeignClient.sync(fromId, "user_friend");
                    nettyFeignClient.sync(toId, "user_friend");
                }
                // 发送好友请求状态同步信号（无论是否通过，状态变更都需要同步）
                nettyFeignClient.sync(fromId, "friend_request");
            }
        });

        return request;
    }

    @Override
    @Transactional
    public List<FriendRequest> syncRequest(Long latestUpdateAt) {
        Long id = UserContext.getId();
        LambdaQueryWrapper<FriendRequest> queryWrapper = new LambdaQueryWrapper<>();

        // 时间筛选条件
        if (latestUpdateAt != null) {
            queryWrapper.gt(FriendRequest::getUpdatedAt, latestUpdateAt);
        }

        // 查询当前用户是发送方或接收方的请求
        queryWrapper.and(wrapper ->
                wrapper.eq(FriendRequest::getFromId, id)
                        .or()
                        .eq(FriendRequest::getToId, id)
        );

        return friendRequireMapper.selectList(queryWrapper);
    }

    @Override
    @Transactional
    public List<UserFriend> syncUserFriend(Long latestUpdateAt) {
        Long id = UserContext.getId();
        LambdaQueryWrapper<UserFriend> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserFriend::getUserId, id);
        if (latestUpdateAt != null) {
            queryWrapper.gt(UserFriend::getUpdatedAt, latestUpdateAt);
        }
        return userFriendMapper.selectList(queryWrapper);
    }

    @Override
    @Transactional
    public List<UserFriend> updateBlockStatus(UserFriend userFriend) {
        LambdaQueryWrapper<UserFriend> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.and(wq1 -> wq1
                .eq(UserFriend::getUserId, userFriend.getUserId())
                .eq(UserFriend::getFriendId, userFriend.getFriendId())
        ).or(wq2 -> wq2
                .eq(UserFriend::getUserId, userFriend.getFriendId())
                .eq(UserFriend::getFriendId, userFriend.getUserId())
        );

        List<UserFriend> list = userFriendMapper.selectList(queryWrapper);
        assert list.size() == 2;
        UserFriend selfItem = null;
        UserFriend otherItem = null;
        for (UserFriend item : list) {
            if (item.getFriendId().equals(userFriend.getFriendId())
                    && item.getUserId().equals(userFriend.getUserId())) {
                selfItem = item;
            } else {
                otherItem = item;
            }
        }
        assert selfItem != null && otherItem != null;

        Long time = System.currentTimeMillis();
        // 拉黑状态被修改
        switch (selfItem.getBlockStatus()) {
            case UserFriend.BLOCK_NO -> {
                if (userFriend.getBlockStatus() == UserFriend.BLOCK_POST) {
                    selfItem.setBlockStatus(UserFriend.BLOCK_POST);
                    selfItem.setUpdatedAt(time);
                    otherItem.setBlockStatus(UserFriend.BLOCK_GET);
                    otherItem.setUpdatedAt(time);
                } else {
                    throw new RuntimeException("不支持的操作");
                }
            }
            case UserFriend.BLOCK_GET -> {
                if (userFriend.getBlockStatus() == UserFriend.BLOCK_POST) {
                    selfItem.setBlockStatus(UserFriend.BLOCK_MUTUAL);
                    selfItem.setUpdatedAt(time);
                    otherItem.setBlockStatus(UserFriend.BLOCK_MUTUAL);
                    otherItem.setUpdatedAt(time);
                } else {
                    throw new RuntimeException("不支持的操作");
                }
            }
            case UserFriend.BLOCK_POST -> {
                if (userFriend.getBlockStatus() == UserFriend.BLOCK_NO) {
                    selfItem.setBlockStatus(UserFriend.BLOCK_NO);
                    selfItem.setUpdatedAt(time);
                    otherItem.setBlockStatus(UserFriend.BLOCK_NO);
                    otherItem.setUpdatedAt(time);
                } else {
                    throw new RuntimeException("不支持的操作");
                }
            }
            case UserFriend.BLOCK_MUTUAL -> {
                if (userFriend.getBlockStatus() == UserFriend.BLOCK_NO) {
                    selfItem.setBlockStatus(UserFriend.BLOCK_GET);
                    selfItem.setUpdatedAt(time);
                    otherItem.setBlockStatus(UserFriend.BLOCK_POST);
                    otherItem.setUpdatedAt(time);
                } else {
                    throw new RuntimeException("不支持的操作");
                }
            }

        }
        userFriendMapper.updateById(selfItem);
        userFriendMapper.updateById(otherItem);
        List<UserFriend> result = new ArrayList<>();
        result.add(selfItem);
        result.add(otherItem);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // 事务提交后执行同步操作
                nettyFeignClient.sync(userFriend.getFriendId(), "user_friend");
            }
        });

        return result;
    }

    @Transactional
    @Override
    public UserFriend updateRemark(UserFriend userFriend) {
        LambdaQueryWrapper<UserFriend> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserFriend::getUserId, userFriend.getUserId());
        queryWrapper.eq(UserFriend::getFriendId, userFriend.getFriendId());
        UserFriend raw = userFriendMapper.selectOne(queryWrapper);
        if (raw == null) {
            throw new RuntimeException("不存在好友信息");
        } else {
            raw.setRemark(userFriend.getRemark());
            raw.setUpdatedAt(System.currentTimeMillis());
            userFriendMapper.updateById(raw);
        }
        return raw;
    }

    @Transactional
    @Override
    public List<UserFriend> updateDeleteStatus(UserFriend userFriend) {
        LambdaQueryWrapper<UserFriend> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.and(wq1 -> wq1
                .eq(UserFriend::getUserId, userFriend.getUserId())
                .eq(UserFriend::getFriendId, userFriend.getFriendId())
        ).or(wq2 -> wq2
                .eq(UserFriend::getUserId, userFriend.getFriendId())
                .eq(UserFriend::getFriendId, userFriend.getUserId())
        );

        List<UserFriend> list = userFriendMapper.selectList(queryWrapper);
        assert list.size() == 2;
        UserFriend selfItem = null;
        UserFriend otherItem = null;
        for (UserFriend item : list) {
            if (item.getFriendId().equals(userFriend.getFriendId())
                    && item.getUserId().equals(userFriend.getUserId())) {
                selfItem = item;
            } else {
                otherItem = item;
            }
        }
        assert selfItem != null && otherItem != null;

        Long time = System.currentTimeMillis();
        // 拉黑状态被修改
        switch (selfItem.getDeleteStatus()) {
            case UserFriend.DELETE_NO -> {
                if (userFriend.getDeleteStatus() == UserFriend.DELETE_POST) {
                    selfItem.setDeleteStatus(UserFriend.DELETE_POST);
                    selfItem.setUpdatedAt(time);
                    otherItem.setDeleteStatus(UserFriend.DELETE_GET);
                    otherItem.setUpdatedAt(time);
                } else {
                    throw new RuntimeException("不支持的操作");
                }
            }
            case UserFriend.DELETE_GET -> {
                if (userFriend.getDeleteStatus() == UserFriend.DELETE_POST) {
                    selfItem.setDeleteStatus(UserFriend.DELETE_MUTUAL);
                    selfItem.setUpdatedAt(time);
                    otherItem.setDeleteStatus(UserFriend.DELETE_MUTUAL);
                    otherItem.setUpdatedAt(time);
                } else {
                    throw new RuntimeException("不支持的操作");
                }
            }
        }
        userFriendMapper.updateById(selfItem);
        userFriendMapper.updateById(otherItem);
        List<UserFriend> result = new ArrayList<>();
        result.add(selfItem);
        result.add(otherItem);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                // 发送好友列表同步信号
                nettyFeignClient.sync(userFriend.getFriendId(), "user_friend");
            }
        });

        return result;
    }
}
