package com.zcj.servicenetty.controller;

import com.zcj.servicenetty.entity.Protocol;
import com.zcj.servicenetty.server.ChannelManager;
import com.zcj.common.vo.Result;
import io.netty.channel.Channel;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
@Tag(name = "Netty内部API接口")
@Slf4j
public class AdminController {

    @GetMapping("/pushSync")
    public Result<Void> sync(@RequestParam Long id,
                       @RequestParam String table) {
        log.info("/admin/sync id:{}, table:{}", id, table);
        Channel channel = ChannelManager.getChannel(id);
        if (channel != null) {
            Protocol protocol = new Protocol();
            protocol.setType(Protocol.ORDER_SYNC | Protocol.CONTENT_TEXT);
            protocol.setToId(id);
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
}
