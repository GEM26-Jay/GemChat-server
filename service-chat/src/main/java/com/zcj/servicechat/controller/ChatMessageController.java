package com.zcj.servicechat.controller;

import com.zcj.common.entity.ChatMessage;
import com.zcj.common.dto.ChatMessageSyncDTO;
import com.zcj.common.vo.Result;
import com.zcj.servicechat.service.ChatMessageService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat/message")
@Tag(name = "聊天信息管理接口")
@Slf4j
@AllArgsConstructor
public class ChatMessageController {
    private final ChatMessageService chatMessageService;

    @GetMapping("/sync")
    public Result<List<ChatMessage>> sync(@RequestParam Long sessionId,
                                          @RequestParam Long lastMessageId) {
        List<ChatMessage> result = chatMessageService.sync(sessionId, lastMessageId);
        return Result.success(result);
    }

    @PostMapping("/syncBatch")
    public Result<List<ChatMessage>> syncBatch(@RequestBody List<ChatMessageSyncDTO> list) {
        List<ChatMessage> result = chatMessageService.syncBatch(list);
        return Result.success(result);
    }

    @GetMapping("/info")
    public Result<List<ChatMessage>> info(@RequestParam Long sessionId,
                                          @RequestParam List<Long> messageIds) {
        List<ChatMessage> result = chatMessageService.info(sessionId, messageIds);
        return Result.success(result);
    }
}
