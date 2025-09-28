package com.zcj.servicechat.controller;

import com.zcj.common.context.UserContext;
import com.zcj.common.entity.ChatSession;
import com.zcj.common.vo.Result;
import com.zcj.servicechat.service.ChatSessionService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat/session")
@Tag(name = "聊天会话管理接口")
@Slf4j
@AllArgsConstructor
public class ChatSessionController {

    final private ChatSessionService chatSessionService;

    @GetMapping("/addSingle")
    public Result<ChatSession> addChatSessionSingle(@RequestParam Long firstId,
                                              @RequestParam Long secondId) {
        ChatSession resultSession = chatSessionService.addSingleSession(firstId, secondId);
        return Result.success(resultSession);
    }

    @GetMapping("/addGroup")
    public Result<ChatSession> addChatSessionGroup(@RequestParam Long groupId,
                                                   @RequestParam Long ownerId) {
        ChatSession resultSession = chatSessionService.addGroupSession(groupId, ownerId);
        return Result.success(resultSession);
    }

    @GetMapping("/sync/single")
    public Result<List<ChatSession>> syncSingleChatSession(
            @RequestParam(required = false) Long lastUpdateAt) {
        Long userId = UserContext.getId();
        if (lastUpdateAt == null) {
            lastUpdateAt = 0L;
        }
        List<ChatSession> sessions = chatSessionService.syncSingleChatSession(userId, lastUpdateAt);
        return Result.success(sessions);
    }

    @GetMapping("/sync/group")
    public Result<List<ChatSession>> syncGroupChatSession(
            @RequestParam(required = false) Long lastUpdateAt) {
        Long userId = UserContext.getId();
        if (lastUpdateAt == null) {
            lastUpdateAt = 0L;
        }
        List<ChatSession> sessions = chatSessionService.syncGroupChatSession(userId, lastUpdateAt);
        return Result.success(sessions);
    }

    @GetMapping("/sync")
    public Result<List<ChatSession>> syncChatSession(
            @RequestParam(required = false) Long lastUpdateAt) {
        Long userId = UserContext.getId();
        if (lastUpdateAt == null) {
            lastUpdateAt = 0L;
        }
        List<ChatSession> singleChatSessions = chatSessionService.syncSingleChatSession(userId, lastUpdateAt);
        List<ChatSession> groupChatSessions = chatSessionService.syncGroupChatSession(userId, lastUpdateAt);
        singleChatSessions.addAll(groupChatSessions);
        return Result.success(singleChatSessions);
    }
}
