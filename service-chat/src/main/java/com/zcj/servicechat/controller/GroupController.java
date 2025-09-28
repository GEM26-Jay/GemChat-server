package com.zcj.servicechat.controller;

import com.zcj.common.context.UserContext;
import com.zcj.common.dto.CreateGroupDTO;
import com.zcj.common.entity.ChatGroup;
import com.zcj.common.vo.Result;
import com.zcj.servicechat.service.ChatGroupService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/group")
@Tag(name = "群组管理接口")
@Slf4j
@AllArgsConstructor
public class GroupController {

    private final ChatGroupService groupService;

    @PostMapping("/add")
    public Result<ChatGroup> addGroup(@RequestBody CreateGroupDTO dto) {
        ChatGroup result = groupService.add(dto);
        return Result.success(result);
    }

    @DeleteMapping("/delete")
    public Result<Void> deleteGroup(@RequestParam Long groupId) {
        assert groupId != null;
        Long userId = UserContext.getId();
        groupService.delete(groupId, userId);
        return Result.success();
    }

    @PostMapping("/update")
    public Result<ChatGroup> updateGroup(@RequestBody ChatGroup group) {
        assert group.getId() != null;
        Long userId = UserContext.getId();
        ChatGroup result = groupService.update(group, userId);
        return Result.success(result);
    }

    @GetMapping("/info")
    public Result<ChatGroup> infoGroup(@RequestParam Long groupId) {
        assert groupId != null;
        ChatGroup result = groupService.info(groupId);
        return Result.success(result);
    }

    @GetMapping("/sync")
    public Result<List<ChatGroup>> syncGroup(@RequestParam Long lastUpdateAt) {
        assert lastUpdateAt != null;
        Long userId = UserContext.getId();
        List<ChatGroup> result = groupService.sync(lastUpdateAt, userId);
        return Result.success(result);
    }
}
