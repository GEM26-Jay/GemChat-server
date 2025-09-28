package com.zcj.servicechat.controller;

import com.zcj.common.entity.GroupMember;
import com.zcj.common.dto.AddGroupMemberDTO;
import com.zcj.common.vo.Result;
import com.zcj.servicechat.service.GroupMemberService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/group/member")
@Tag(name = "群组成员管理接口")
@Slf4j
@AllArgsConstructor
public class GroupMemberController {

    private final GroupMemberService groupMemberService;

    @GetMapping("/add")
    public Result<GroupMember> addGroupMember(@RequestParam Long groupId, @RequestParam Long userId) {
        GroupMember result = groupMemberService.add(groupId, userId);
        return Result.success(result);
    }

    @PostMapping("/addBatch")
    public Result<Void> addBatchGroupMember(@RequestBody AddGroupMemberDTO dto) {
        Long groupId = dto.getGroupId();
        List<Long> userIds = dto.getUserIds();
        groupMemberService.addBatch(groupId, userIds);
        return Result.success();
    }

    @DeleteMapping("/delete")
    public Result<Void> deleteMember(@RequestParam Long groupId, @RequestParam Long userId) {
        assert userId != null;
        groupMemberService.delete(groupId, userId);
        return Result.success();
    }

    @PostMapping("/update")
    public Result<GroupMember> updateMember(@RequestBody GroupMember groupMember) {
        assert groupMember.getGroupId() != null && groupMember.getUserId() != null;
        GroupMember result = groupMemberService.update(groupMember);
        return Result.success(result);
    }

    @GetMapping("/sync")
    public Result<List<GroupMember>> syncGroupMember(@RequestParam Long lastUpdateAt) {
        assert lastUpdateAt != null;
        List<GroupMember> result = groupMemberService.sync(lastUpdateAt);
        return Result.success(result);
    }
}
