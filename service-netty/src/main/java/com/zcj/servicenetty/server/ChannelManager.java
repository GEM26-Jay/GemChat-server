package com.zcj.servicenetty.server;

import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 连接管理器：线程安全地管理用户ID与Channel的映射关系
 */
@Component
public class ChannelManager {
    // 用户ID -> Channel的映射（ConcurrentHashMap确保线程安全）
    private final Map<Long, Channel> userChannelMap = new ConcurrentHashMap<>();

    // 所有已验证的连接集合（方便广播等操作）
    private final ChannelGroup allChannels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);

    // 用户ID属性键（用于Channel绑定用户ID）
    public final AttributeKey<Long> USER_ID_ATTR = AttributeKey.newInstance("userId");

    /**
     * 绑定用户ID与Channel（双向绑定）
     * @param userId 用户唯一标识
     * @param channel 连接通道
     */
    public void bind(Long userId, Channel channel) {
        if (userId == null || channel == null) {
            return;
        }
        // 1. 存储用户ID到Channel的映射
        userChannelMap.put(userId, channel);
        // 2. 在Channel上绑定用户ID属性（反向映射）
        channel.attr(USER_ID_ATTR).set(userId);
        // 3. 将通道加入全局管理组
        allChannels.add(channel);
    }

    /**
     * 根据用户ID解除绑定
     * @param userId 用户唯一标识
     */
    public void unbind(Long userId) {
        if (userId == null) {
            return;
        }
        // 1. 从映射中移除并获取对应的Channel
        Channel channel = userChannelMap.remove(userId);
        if (channel != null) {
            // 2. 清除Channel上的用户ID属性
            channel.attr(USER_ID_ATTR).set(null);
            // 3. 从全局管理组中移除
            allChannels.remove(channel);
        }
    }

    /**
     * 根据Channel解除绑定（连接关闭时调用）
     * @param channel 连接通道
     */
    public void unbind(Channel channel) {
        if (channel == null) {
            return;
        }
        // 1. 从Channel属性中获取用户ID（无需遍历映射）
        Long userId = channel.attr(USER_ID_ATTR).get();
        if (userId != null) {
            // 2. 移除用户ID与Channel的映射
            userChannelMap.remove(userId);
            // 3. 清除Channel上的用户ID属性
            channel.attr(USER_ID_ATTR).set(null);
        }
        // 4. 从全局管理组中移除
        allChannels.remove(channel);
    }

    /**
     * 根据用户ID获取对应的Channel
     * @param userId 用户唯一标识
     * @return 对应的Channel，若不存在或已关闭则返回null
     */
    public Channel getChannel(Long userId) {
        if (userId == null) {
            return null;
        }
        Channel channel = userChannelMap.get(userId);
        // 校验通道是否活跃（避免返回已关闭的通道）
        return (channel != null && channel.isActive()) ? channel : null;
    }

    /**
     * 根据Channel获取对应的用户ID
     * @param channel 连接通道
     * @return 对应的用户ID，若未绑定则返回null
     */
    public Long getUserId(Channel channel) {
        return channel != null ? channel.attr(USER_ID_ATTR).get() : null;
    }

    /**
     * 清理所有绑定关系（用于服务器重置场景）
     */
    public void clean() {
        // 1. 清除所有Channel的用户ID属性
        allChannels.forEach(channel -> channel.attr(USER_ID_ATTR).set(null));
        // 2. 清空映射和全局管理组
        userChannelMap.clear();
        allChannels.clear();
    }

    /**
     * 获取所有活跃的连接通道
     * @return 通道组
     */
    public ChannelGroup getAllActiveChannels() {
        return allChannels;
    }

    /**
     * 检查用户是否在线
     * @param userId 用户唯一标识
     * @return 在线状态（true：在线，false：离线）
     */
    public boolean isOnline(Long userId) {
        return getChannel(userId) != null;
    }
}
