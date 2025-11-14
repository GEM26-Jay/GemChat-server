package com.zcj.servicenetty.handler;

import com.zcj.servicenetty.entity.Protocol;
import com.zcj.servicenetty.server.ChannelManager;
import com.zcj.common.utils.JWTUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * 连接验证处理器：仅处理首次连接的验证请求，验证通过后移除自身
 */
@Slf4j
@Component
@Scope("prototype")
public class AuthHandler extends ChannelInboundHandlerAdapter {

    private boolean isLogin = false;

    @Autowired
    private JWTUtil jwtUtil;
    @Autowired
    private ChannelManager channelManager;

    /**
     * 处理客户端发送的消息（首次应为验证请求）
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Protocol protocol) {
            if (isLogin && !protocol.hasType(Protocol.ORDER_AUTH)) {
                ctx.fireChannelRead(msg);
            } else if (protocol.hasType(Protocol.ORDER_AUTH)) {
                // 1. 解析用户ID和token（实际场景应从消息体中解析）
                Long userId = protocol.getFromId();  // 假设发送者ID为用户ID
                String token = protocol.getMessageString();  // 假设消息体为token

                // 2. 验证token
                boolean valid = validateToken(userId, token);
                if (valid) {
                    log.info("用户 {} 验证通过，绑定连接", userId);

                    // 3. 验证通过：绑定用户ID与Channel
                    channelManager.bind(userId, ctx.channel());
                    isLogin = true;
                    Protocol re = new Protocol();
                    re.setType(Protocol.ORDER_AUTH, Protocol.CONTENT_EMPTY);
                    ctx.writeAndFlush(re);
                } else {
                    // 验证失败：关闭连接
                    log.warn("用户 {} 验证失败，关闭连接", userId);
                    ctx.close();
                }
            } else {
                log.warn("未验证的连接发送非验证消息");
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
        channelManager.unbind(ctx.channel());
        log.debug("连接关闭，已解除用户绑定");
    }

    /**
     * 发生异常时，关闭连接
     */
    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        log.error("验证过程发生异常", cause);
        channelManager.unbind(ctx.channel());
        ctx.close();
    }
}
