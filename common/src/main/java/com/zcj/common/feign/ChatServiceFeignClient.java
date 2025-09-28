package com.zcj.common.feign;

import com.zcj.common.entity.ChatSession;
import com.zcj.common.vo.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Component
@FeignClient(name = "service-chat")
public interface ChatServiceFeignClient {

    @GetMapping("/api/chat/session/addSingle")
    public Result<ChatSession> addChatSessionSingle(@RequestParam("firstId") Long firstId,
                                                    @RequestParam("secondId") Long secondId);

    @GetMapping("/api/chat/session/addGroup")
    public Result<ChatSession> addChatSessionGroup(@RequestParam("groupId") Long groupId,
                                                   @RequestParam("ownerId") Long ownerId);
}
