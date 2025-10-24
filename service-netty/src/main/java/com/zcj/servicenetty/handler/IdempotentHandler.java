package com.zcj.servicenetty.handler;

import com.zcj.servicenetty.entity.Protocol;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

public class IdempotentHandler extends ChannelInboundHandlerAdapter {
    private long lastMessageTime = 0;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Protocol protocol) {
            long timeStamp = protocol.getTimeStamp();
            if (timeStamp > lastMessageTime) {
                lastMessageTime = timeStamp;
                ctx.fireChannelRead(msg);
            }
        } else {
            ctx.fireChannelRead(msg);
        }
    }
}
