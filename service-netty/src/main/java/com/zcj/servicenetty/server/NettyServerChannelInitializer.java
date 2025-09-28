package com.zcj.servicenetty.server;

import com.zcj.servicenetty.handler.AuthHandler;
import com.zcj.servicenetty.handler.MessageHandler;
import com.zcj.servicenetty.handler.ProtocolEncoder;
import com.zcj.servicenetty.handler.ProtocolFrameDecoder;
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
        pipeline.addLast(authHandlerProvider.getObject());
        pipeline.addLast(messageHandler);
        pipeline.addLast(protocolEncoder);
    }
}
