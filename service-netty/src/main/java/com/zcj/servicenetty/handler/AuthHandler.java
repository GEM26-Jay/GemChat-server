package com.zcj.servicenetty.handler;

import com.zcj.common.entity.Protocol;
import com.zcj.common.utils.NetUtil;
import com.zcj.servicenetty.service.ChannelManager;
import com.zcj.common.utils.JWTUtil;
import com.zcj.servicenetty.service.MessageRouterService;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.Data;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.net.SocketException;

/**
 * 连接验证处理器
 */
@Slf4j
@Component
@Scope("prototype")
public class AuthHandler extends ChannelInboundHandlerAdapter {

    private final JWTUtil jwtUtil;
    private final ChannelManager channelManager;
    private final StringRedisTemplate redisTemplate;

    @Setter
    private static String localAddr;
    private boolean isLogin = false;

    public AuthHandler(JWTUtil jwtUtil,
                       ChannelManager channelManager,
                       StringRedisTemplate redisTemplate) throws SocketException {
        this.jwtUtil = jwtUtil;
        this.channelManager = channelManager;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 处理客户端发送的消息（首次应为验证请求）
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Protocol protocol) {
            if (isLogin && !protocol.hasType(Protocol.ORDER_AUTH)) {
                // 已经验证通过，放行
                ctx.fireChannelRead(msg);
            } else if (protocol.hasType(Protocol.ORDER_AUTH)) {
                try {
                    // 1. 解析用户ID和token（实际场景应从消息体中解析）
                    Long userId = protocol.getFromId();  // 假设发送者ID为用户ID
                    String token = protocol.getMessageString();  // 假设消息体为token
                    // 2. 验证token
                    boolean valid = validateToken(userId, token);
                    if (!valid) throw new RuntimeException("用户身份验证失败");
                    // 3. 验证通过：绑定用户ID与Channel
                    log.info("用户 {} 验证通过，绑定连接", userId);
                    channelManager.bind(userId, ctx.channel());
                    // 4. 注册用户-服务路由
                    redisTemplate.opsForHash().put(MessageRouterService.USER_ROUTE_KEY,
                                    userId.toString(), localAddr);
                    isLogin = true;
                } catch (Exception e) {
                    log.warn("验证发生错误，关闭连接; cause: {}", e.toString());
                    ctx.close();
                }
            } else {
                log.warn("未验证的连接发送非验证消息");
                ctx.close();
            }
        } else {
            log.warn("服务器内部错误，关闭连接");
            ctx.close();
        }
    }

    /**
     * 实际的token验证逻辑
     */
    private boolean validateToken(Long userId, String token) {
        Object id = jwtUtil.getClaimIfValid(token, "userId");
        if (id instanceof Integer) {
            return userId.equals(((Integer) id).longValue());
        }
        return userId.equals(id);
    }

    /**
     * 连接关闭时，自动解除绑定
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        // 清除本地连接数据
        Long userId = channelManager.unbind(ctx.channel());
        // 清除redis用户连接数据
        if (userId != null) {
            redisTemplate.opsForHash().delete(MessageRouterService.USER_ROUTE_KEY,
                    userId.toString());
        }
        log.debug("连接关闭，已解除用户绑定");
    }

    /**
     * 发生异常时，关闭连接
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("验证过程发生异常", cause);
        Long userId = channelManager.unbind(ctx.channel());
        if (userId != null) {
            redisTemplate.opsForHash().delete(MessageRouterService.USER_ROUTE_KEY,
                    userId.toString());
        }
        ctx.close();
    }
}
