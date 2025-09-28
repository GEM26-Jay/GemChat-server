package com.zcj.serviceuser.controller;

import com.zcj.common.entity.FriendRequest;
import com.zcj.common.entity.UserFriend;
import com.zcj.serviceuser.service.FriendService;
import com.zcj.common.vo.Result;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/friend")
@Tag(name = "好友管理接口")
@Slf4j
@AllArgsConstructor
public class FriendController {

    final private FriendService friendService;

    /**
     * 好友申请
     */
    @PostMapping("/request/apply")
    public Result<FriendRequest> applyRequest(@RequestBody FriendRequest request) {
        FriendRequest result = friendService.applyRequest(request);
        return Result.success(result);
    }

    /**
     * 好友数据同步
     */
    @PostMapping("/request/update")
    public Result<FriendRequest> updateRequest(@RequestBody FriendRequest userFriend) {
        FriendRequest result = friendService.updateRequest(userFriend);
        return Result.success(result);
    }

    /**
     * 好友数据同步
     */
    @GetMapping("/request/sync")
    public Result<List<FriendRequest>> syncRequest(@RequestParam(required = false) Long latestAt) {
        List<FriendRequest> list = friendService.syncRequest(latestAt);
        return Result.success(list);
    }

    @GetMapping("/sync")
    public Result<List<UserFriend>> syncUserFriend(@RequestParam(required = false) Long latestAt) {
        List<UserFriend> list = friendService.syncUserFriend(latestAt);
        return Result.success(list);
    }

    @PostMapping("/updateRemark")
    public Result<UserFriend> updateRemark(@RequestBody UserFriend userFriend) {
        UserFriend result = friendService.updateRemark(userFriend);
        return Result.success(result);
    }

    @PostMapping("/updateBlock")
    public Result<List<UserFriend>> updateBlockStatus(@RequestBody UserFriend userFriend) {
        List<UserFriend> result = friendService.updateBlockStatus(userFriend);
        return Result.success(result);
    }

    @PostMapping("/updateDelete")
    public Result<List<UserFriend>> updateDeleteStatus(@RequestBody UserFriend userFriend) {
        List<UserFriend> result = friendService.updateDeleteStatus(userFriend);
        return Result.success(result);
    }

}
