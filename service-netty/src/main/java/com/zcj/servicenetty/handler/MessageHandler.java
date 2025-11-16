package com.zcj.servicenetty.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zcj.common.entity.ChatMessage;
import com.zcj.servicenetty.consumer.MessageConsumer;
import com.zcj.common.entity.Protocol;
import com.zcj.servicenetty.mapper.ChatMessageMapper;
import com.zcj.common.utils.RedisDistributedLock;
import io.netty.channel.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Component
@ChannelHandler.Sharable
@Slf4j
@RequiredArgsConstructor
public class MessageHandler extends ChannelInboundHandlerAdapter {

    private final StringRedisTemplate redisTemplate;
    private final KafkaTemplate<String, ChatMessage> kafkaTemplate;
    private final ChatMessageMapper chatMessageMapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static Integer EXPIRE_TIME = 3600; // 3600s = 1h

    final static private String incrementLua = """
                -- 调用 Redis GET 命令，获取 key 的值
                local value = redis.call('GET', KEYS[1])

                -- 判断：key 不存在（value == nil）→ 返回 -1
                if value == nil or value == false then
                    return -1
                end

                -- 原子自增：用 INCR 命令
                local newValue = redis.call('INCR', KEYS[1])

                -- 设置过期时间（ARGV[1] 为过期时间秒数，由 Java 端传入）
                redis.call('EXPIRE', KEYS[1], ARGV[1])

                -- 返回新值
                return newValue
            """;
    private static final DefaultRedisScript<Long> incrementLuaScript = new DefaultRedisScript<>(incrementLua, Long.class);

    /**
     * 处理客户端指令
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof Protocol protocol && protocol.hasType(Protocol.ORDER_MESSAGE)) {
            try {
                log.debug("[MessageHandler]: come [{}]", protocol.getMessageString());
                int originalLength = protocol.getLength();
                if (originalLength == 0) throw new RuntimeException("消息长度为0");
                Long sessionId = protocol.getSessionId();
                String queryKey = "maxMessageIdOfSession:" + sessionId;
                Long messageId = redisTemplate.execute(incrementLuaScript, Collections.singletonList(queryKey), EXPIRE_TIME.toString());
                if (messageId == null || messageId < 0) {
                    // 双锁检查，获取消息ID
                    RedisDistributedLock lock = new RedisDistributedLock(redisTemplate, "sessionId:" + sessionId);
                    try {
                        boolean hasLocked = lock.tryLock(60, TimeUnit.SECONDS);
                        if (!hasLocked) throw new RuntimeException("锁等待超时");
                        messageId = redisTemplate.execute(incrementLuaScript, Collections.singletonList(queryKey), EXPIRE_TIME.toString());
                        if (messageId == null || messageId < 0) {
                            messageId = chatMessageMapper.selectMaxMessageIdInSession(sessionId); // 该函数在数据不存在时，返回0
                            messageId = messageId + 1;
                            redisTemplate.opsForValue().setIfAbsent(queryKey, messageId.toString(), EXPIRE_TIME, TimeUnit.SECONDS);
                        }
                    } finally {
                        lock.unlock();
                    }
                }
                protocol.setMessageId(messageId);
                protocol.setTimeStamp(System.currentTimeMillis());
                ChatMessage chatMessage = Protocol2ChatMessage(protocol);
                kafkaTemplate.send(MessageConsumer.TOPIC, sessionId.toString(), chatMessage).whenComplete((re, ex) -> {
                    if (ex != null) {
                        // todo: 填充空消息
                        // 将错误写入日志，方便恢复数据
                        log.info("[消息已丢失, 需填充空消息]: sessionId: {}, messageId: {}", chatMessage.getSessionId(), chatMessage.getMessageId());
                        protocol.setContent("消息队列异常");
                        protocol.setType(Protocol.ORDER_ACK + Protocol.CONTENT_FAILED);
                        ctx.writeAndFlush(protocol);
                    } else {
                        // 向发送者返回 ACK 成功应答
                        protocol.setContent("");
                        protocol.setType(Protocol.ORDER_ACK, chatMessage.getType());
                        ctx.writeAndFlush(protocol);
                    }
                });
            } catch (Exception e){
                // 业务等待超时，服务器异常
                log.debug("[sendMessageToQueue]: 锁等待超时");
                protocol.setContent(e.getMessage());
                protocol.setType(Protocol.ORDER_ACK + Protocol.CONTENT_FAILED);
                ctx.writeAndFlush(protocol.toBuffer(ctx.alloc().buffer()));
            }

        } else {
            // 不是消息，往下放行
            ctx.fireChannelRead(msg);
        }
    }

    private ChatMessage Protocol2ChatMessage(Protocol protocol) {
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setSessionId(protocol.getSessionId());
        chatMessage.setMessageId(protocol.getMessageId());
        chatMessage.setFromId(protocol.getFromId());
        chatMessage.setIdentityId(protocol.getIdentityId());
        chatMessage.setType(protocol.getContentType());
        chatMessage.setContent(protocol.getMessageString());
        chatMessage.setStatus(ChatMessage.STATUS_SUCCESS);
        Long timestamp = System.currentTimeMillis();
        chatMessage.setCreatedAt(timestamp);
        chatMessage.setUpdatedAt(timestamp);
        return chatMessage;
    }

}
