package com.zcj.servicenetty.handler;

import com.zcj.common.entity.Protocol;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@ChannelHandler.Sharable
@Slf4j
public class ProtocolEncoder extends MessageToByteEncoder<Protocol> {
    @Override
    protected void encode(ChannelHandlerContext ctx, Protocol msg, ByteBuf out) throws Exception {
        log.info("消息发送: [{}]", msg);
        msg.toBuffer(out);
    }
}
