package com.zcj.servicechat.service;

import com.zcj.common.entity.GroupMember;

import java.util.List;

public interface GroupMemberService {
    GroupMember add(Long groupId, Long userId);

    void delete(Long groupId, Long userId);

    List<GroupMember> sync(Long lastUpdateAt);

    GroupMember update(GroupMember groupMember);

    void addBatch(Long groupId, List<Long> userIds);
}
