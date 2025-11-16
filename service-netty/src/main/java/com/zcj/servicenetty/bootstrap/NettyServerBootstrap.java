package com.zcj.servicenetty.bootstrap;

import com.zcj.servicenetty.config.NettyProperties;
import com.zcj.servicenetty.service.ChannelManager;
import com.zcj.servicenetty.service.NettyServerChannelInitializer;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NettyServerBootstrap {

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;

    private final NettyProperties properties;
    private final NettyServerChannelInitializer channelInitializer;
    private final ChannelManager channelManager;


    public void start() {
        // 启动独立线程执行Netty逻辑，避免阻塞Spring Boot主线程
        new Thread(() -> {
            bossGroup = new NioEventLoopGroup(1);
            workerGroup = new NioEventLoopGroup();
            try {
                ServerBootstrap bootstrap = new ServerBootstrap();
                bootstrap.group(bossGroup, workerGroup)
                        .channel(NioServerSocketChannel.class)
                        .childHandler(channelInitializer);

                // 异步绑定端口，通过回调处理结果
                ChannelFuture bindFuture = bootstrap.bind(properties.getPort());
                bindFuture.addListener(future -> {
                    if (future.isSuccess()) {
                        log.info("Netty 消息服务器启动，监听端口：{}", properties.getPort());
                        // 保存关闭未来，用于后续优雅关闭
                        ChannelFuture serverCloseFuture = bindFuture.channel().closeFuture();
                        // 监听服务器通道关闭事件，清理资源
                        serverCloseFuture.addListener(closeFuture -> {
                            shutdown();
                            log.info("Netty 服务器通道已关闭");
                        });
                    } else {
                        log.error("Netty 服务器启动失败", future.cause());
                        shutdown(); // 启动失败时关闭资源
                    }
                });

                // 阻塞等待服务器关闭（仅阻塞当前独立线程，不影响主线程）
                bindFuture.channel().closeFuture().sync();
            } catch (InterruptedException e) {
                log.error("Netty 服务器运行中发生中断", e);
                Thread.currentThread().interrupt(); // 保留中断状态
            } finally {
                shutdown(); // 线程退出前确保资源释放
            }
        }, "netty-server-thread").start();

    }

    public void shutdown() {
        if (bossGroup != null) {
            bossGroup.shutdownGracefully();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully();
        }
        channelManager.clean();
        log.info("Netty 消息服务器关闭");
    }
}