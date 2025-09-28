package com.zcj.servicenetty.server;

import com.zcj.servicenetty.entity.HashDelayQueue;
import com.zcj.servicenetty.entity.Protocol;
import com.zcj.servicenetty.entity.ProtocolDelayRetryWrapper;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class RetryManageServer {
    // 重试线程池
    private final ExecutorService retryExecutor = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors()/2,
            r -> {
                return new Thread(r, "retry-worker");
            }
    );

    private final HashDelayQueue<ProtocolDelayRetryWrapper> delayQueue = new HashDelayQueue<>();
    private final int maxRetry = 5;
    private volatile boolean isRunning = true;

    public void start() {
        retryExecutor.execute(() -> {
            while (isRunning) {
                try {
                    // 阻塞获取到期的重试任务（HashDelayQueue需实现take()方法）
                    ProtocolDelayRetryWrapper wrapper = delayQueue.take();
                    processRetry(wrapper);
                } catch (InterruptedException e) {
                    // 线程被中断时退出循环
                    if (!isRunning) {
                        break;
                    }
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    // 捕获其他异常，避免处理器线程退出
                    e.printStackTrace();
                }
            }
        });
    }

    /**
     * 处理单个重试任务
     */
    private void processRetry(ProtocolDelayRetryWrapper wrapper) {
        Protocol protocol = wrapper.getProtocol();
        Channel channel = wrapper.getChannel();
        // 检查通道状态（非IO线程调用，无需显式切换EventLoop）
        if (channel == null || !channel.isActive()) {
            return;
        }
        log.info("[RetryManageServer]: 重试: 用户ID: {}, 消息: {}, 重试次数: {}",
                channel.attr(ChannelManager.USER_ID_ATTR), protocol.getMessageString(), wrapper.getCurrentRetry());
        // 直接调用writeAndFlush（Netty会自动提交到EventLoop）
        channel.writeAndFlush(protocol).addListener((ChannelFutureListener) future -> {
            if (!future.isSuccess()) {
                // 发送失败，准备下一次重试
                int nextRetry = wrapper.getCurrentRetry() + 1;
                if (nextRetry <= maxRetry) {
                    wrapper.reset(nextRetry);
                    addRetryWrapper(wrapper);
                } else {
                    log.info("[RetryManageServer]: 重试失败: 用户ID: {}, 消息: {}, 关闭通道",
                            channel.attr(ChannelManager.USER_ID_ATTR), protocol.getMessageString());
                    // 达到最大重试次数，关闭通道
                    channel.close().addListener(f ->
                            ChannelManager.unbind(channel)
                    );
                }
            }
            // 发送成功则任务完成，无需处理
        });
    }

    public void addRetryWrapper(ProtocolDelayRetryWrapper wrapper) {
        if (isRunning) {
            delayQueue.put(wrapper);
        }
    }

    public void removeWrapper(String identity) {
        delayQueue.remove(identity);
    }

    /**
     * 关闭重试服务（优雅停止线程池）
     */
    public void shutdown() {
        isRunning = false;
        retryExecutor.shutdown();
        try {
            // 等待现有任务完成（最多等待1秒）
            if (!retryExecutor.awaitTermination(1, TimeUnit.SECONDS)) {
                retryExecutor.shutdownNow(); // 强制终止
            }
        } catch (InterruptedException e) {
            retryExecutor.shutdownNow();
        }
    }
}
