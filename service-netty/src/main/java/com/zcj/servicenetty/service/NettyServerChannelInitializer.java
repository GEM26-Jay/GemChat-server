package com.zcj.servicenetty.service;

import com.zcj.servicenetty.handler.*;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@AllArgsConstructor
public class NettyServerChannelInitializer extends ChannelInitializer<SocketChannel> {

    @Autowired
    private ProtocolEncoder protocolEncoder;
    @Autowired
    private ObjectProvider<AuthHandler> authHandlerProvider;
    @Autowired
    private MessageHandler messageHandler;

    @Override
    protected void initChannel(SocketChannel ch) throws Exception {
        ChannelPipeline pipeline = ch.pipeline();
        pipeline.addLast(new ProtocolFrameDecoder());
        pipeline.addLast(protocolEncoder);
        pipeline.addLast(authHandlerProvider.getObject());
        pipeline.addLast(new IdempotentHandler());
        pipeline.addLast(messageHandler);
    }
}
