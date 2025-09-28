package com.zcj.serviceuser.service;

import com.zcj.common.entity.FriendRequest;
import com.zcj.common.entity.UserFriend;

import java.util.List;

public interface FriendService {
    FriendRequest applyRequest(FriendRequest request);

    List<FriendRequest> syncRequest(Long latestUpdateAt);

    FriendRequest updateRequest(FriendRequest request);

    List<UserFriend> syncUserFriend(Long latestUpdateAt);

    List<UserFriend> updateBlockStatus(UserFriend userFriend);

    UserFriend updateRemark(UserFriend userFriend);

    List<UserFriend> updateDeleteStatus(UserFriend userFriend);
}
