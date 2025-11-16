package com.zcj.servicenetty.controller;

import com.zcj.common.dto.SendRequestDTO;
import com.zcj.common.entity.ChatMessage;
import com.zcj.common.entity.Protocol;
import com.zcj.servicenetty.service.ChannelManager;
import com.zcj.common.vo.Result;
import io.netty.channel.Channel;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
@Tag(name = "Netty内部API接口")
@Slf4j
@RequiredArgsConstructor
public class AdminController {

    private final ChannelManager channelManager;

    @GetMapping("/pushSync")
    public Result<Void> sync(@RequestParam Long id,
                       @RequestParam String table) {
        log.info("/admin/sync id:{}, table:{}", id, table);
        Channel channel = channelManager.getChannel(id);
        if (channel != null) {
            Protocol protocol = new Protocol();
            protocol.setType(Protocol.ORDER_SYNC | Protocol.CONTENT_TEXT);
            protocol.setSessionId(id);
            protocol.setContent(table);
            protocol.setTimeStamp(System.currentTimeMillis());
            channel.writeAndFlush(protocol);
            return Result.success();
        } else {
            return Result.error("用户不在线");
        }
    }

    @GetMapping("/pushSyncBatch")
    public Result<Void> syncBatch(
            @RequestParam("ids") List<Long> ids,
            @RequestParam("table") String table){
        for (Long id : ids) {
            sync(id, table);
        }
        return Result.success();
    }

    @PostMapping("/send")
    public Result<Void> send(@RequestBody SendRequestDTO sendRequest) {
        List<Long> ids = sendRequest.getIds();
        ChatMessage message = sendRequest.getMessage();
        Protocol protocol = message.toProtocol();
        for (Long id : ids) {
            Channel channel = channelManager.getChannel(id);
            if (channel != null) {
                channel.writeAndFlush(protocol);
            }
        }
        return Result.success();
    }
}
