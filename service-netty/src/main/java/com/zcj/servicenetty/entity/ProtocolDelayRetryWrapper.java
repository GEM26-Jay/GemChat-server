package com.zcj.servicenetty.entity;

import io.netty.channel.Channel;
import lombok.Data;

import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

@Data
public class ProtocolDelayRetryWrapper implements Delayed, UniqueIdentifiable {
    // 用户连接
    Channel channel;
    // 协议消息本体
    private Protocol protocol;
    // 基础延迟时间（秒），默认2秒，可外部修改
    private int baseDelayTime = 2;
    // 当前重试次数
    private int currentRetry;
    // 任务到期时间戳（毫秒）
    private long expireTimeMillis;

    public ProtocolDelayRetryWrapper(Protocol protocol, Channel channel) {
        this(protocol, channel, 1);
    }

    public ProtocolDelayRetryWrapper(Protocol protocol, Channel channel, int currentRetry) {
        this.protocol = protocol;
        this.channel = channel;
        reset(currentRetry);
    }

    /**
     * 重置延迟时间
     * 根据当前重试次数计算新的到期时间（指数退避策略）
     * @param currentRetry 当前重试次数
     */
    public void reset(int currentRetry) {
        this.currentRetry = currentRetry;
        // 计算延迟时间：baseDelayTime^currentRetry 秒，转换为毫秒
        long delayMillis = (long) (Math.pow(baseDelayTime, currentRetry) * 1000);
        this.expireTimeMillis = System.currentTimeMillis() + delayMillis;
    }

    /**
     * 获取剩余延迟时间
     * @param unit 时间单位
     * @return 转换为指定单位的剩余延迟时间
     */
    @Override
    public long getDelay(TimeUnit unit) {
        long remaining = expireTimeMillis - System.currentTimeMillis();
        return unit.convert(remaining, TimeUnit.MILLISECONDS);
    }

    /**
     * 按到期时间排序
     * @param o 另一个延迟对象
     * @return 比较结果
     */
    @Override
    public int compareTo(Delayed o) {
        return Long.compare(this.getDelay(TimeUnit.MILLISECONDS), o.getDelay(TimeUnit.MILLISECONDS));
    }

    /**
     * 生成全局唯一标识
     * @return 唯一标识字符串
     */
    @Override
    public String getUniqueId() {
        return protocol.getFromId() + ":" + protocol.getToId() + ":" + protocol.getTimeStamp();
    }
}