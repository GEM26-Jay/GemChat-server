package com.zcj.servicenetty.handler;

import com.github.benmanes.caffeine.cache.Cache;
import com.zcj.common.entity.ChatMessage;
import com.zcj.servicenetty.entity.Protocol;
import com.zcj.servicenetty.entity.ProtocolDelayRetryWrapper;
import com.zcj.servicenetty.mapper.ChatMessageMapper;
import com.zcj.servicenetty.server.AsyncMessageSaveServer;
import com.zcj.servicenetty.server.ChannelManager;
import com.zcj.common.utils.RedisDistributedLock;
import com.zcj.servicenetty.server.RetryManageServer;
import io.netty.channel.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@ChannelHandler.Sharable
@Slf4j
public class MessageHandler extends ChannelInboundHandlerAdapter {

    final StringRedisTemplate redisTemplate;
    final ChatMessageMapper chatMessageMapper;
    final AsyncMessageSaveServer asyncMessageSaveServer;
    final RetryManageServer retryManageServer;
    final Cache<String, Set<Long>> sessionCache;

    public MessageHandler(StringRedisTemplate redisTemplate,
                          ChatMessageMapper chatMessageMapper,
                          AsyncMessageSaveServer asyncMessageSaveServer,
                          RetryManageServer retryManageServer,
                          @Qualifier("session_member_cache") Cache<String, Set<Long>> sessionCache) {
        this.redisTemplate = redisTemplate;
        this.chatMessageMapper = chatMessageMapper;
        this.asyncMessageSaveServer = asyncMessageSaveServer;
        this.retryManageServer = retryManageServer;
        this.sessionCache = sessionCache;
    }

    /**
     * 处理客户端指令
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof Protocol protocol && protocol.hasType(Protocol.ORDER_MESSAGE)) {
            log.debug("[MessageHandler]: come [{}]", protocol.getMessageString());
            int originalLength = protocol.getLength();
            if (originalLength == 0) {
                return;
            }
            Long sessionId = protocol.getToId();
            RedisDistributedLock lock = new RedisDistributedLock(redisTemplate, "sessionId:" + sessionId.toString());
            boolean hasLocked = lock.tryLock(3, TimeUnit.SECONDS);
            if (hasLocked) {
                Long messageId = getMaxMessageIdOfSession(sessionId);
                lock.unlock();
                // 给消息队列发送消息，存储日志和数据库
                if (sendMessageToQueue(protocol, messageId)) {
                    return;
                }
            }
            log.info("[sendMessageToQueue]: failed [{}]", String.valueOf(msg));
            // 给原用户发送反馈消息: 失败
            protocol.setType(Protocol.ORDER_MESSAGE + Protocol.CONTENT_FAILED_INFO);
            ctx.writeAndFlush(protocol.toBuffer(ctx.alloc().buffer()));
        } else {
            ctx.fireChannelRead(msg);
        }
    }

    private boolean sendMessageToQueue(Protocol protocol, Long messageId) {
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.setSessionId(protocol.getToId());
        chatMessage.setMessageId(messageId);
        chatMessage.setFromId(protocol.getFromId());
        chatMessage.setToId(protocol.getToId());
        chatMessage.setContent(protocol.getMessageString());
        chatMessage.setStatus(ChatMessage.STATUS_SUCCESS);
        chatMessage.setType(protocol.getContentType());
        chatMessage.setCreatedAt(protocol.getTimeStamp());
        chatMessage.setUpdatedAt(protocol.getTimeStamp());
        return asyncMessageSaveServer.submit(chatMessage, () -> {
            protocol.setIdentityId(messageId);
            sendMessageToSession(chatMessage.getSessionId(), protocol);
        }, () -> {
            protocol.setType(Protocol.CONTENT_ACK + Protocol.CONTENT_FAILED_INFO);
            sendMessage(protocol.getFromId(), protocol);
        });
    }

    private void sendMessage(Long userId, Protocol protocol) {
        // 如果目标用户在线发送消息
        Channel channel = ChannelManager.getChannel(userId);

        if (channel != null) {
            // 给目标用户发送消息
            ChannelFuture channelFuture = channel.writeAndFlush(protocol.toBuffer(channel.alloc().buffer()));
            channelFuture.addListener(future -> {
                if (!future.isSuccess()) {
                    // 写入失败的处理逻辑
                    log.info("[sendMessage]: failed [{}]", String.valueOf(protocol));
                    retryManageServer.addRetryWrapper(new ProtocolDelayRetryWrapper(protocol, channel));
                } else {
                    log.debug("[sendMessage]: success [{}, {}]", userId, protocol.getMessageString());
                }
            });
        }
    }

    private void sendMessageToSession(Long sessionId, Protocol protocol) {
        // 1. 从本地缓存获取会话成员
        Set<Long> members = sessionCache.get(sessionId.toString(), key -> {
            // 如果本地缓存没有，再查 Redis 或 DB
            String redisKey = "membersIdOfSession:" + sessionId;
            Set<String> redisMembers = redisTemplate.opsForSet().members(redisKey);

            if (redisMembers == null || redisMembers.isEmpty()) {
                // Redis 也没有，从数据库查询
                List<Long> dbMembers = chatMessageMapper.selectMemberIdsInSession(sessionId);
                if (dbMembers == null || dbMembers.isEmpty()) {
                    log.debug("会话[{}]无任何成员，无需发送消息", sessionId);
                    return Set.of(); // 返回空集合，缓存起来
                }

                // 写入 Redis
                redisTemplate.opsForSet().add(
                        redisKey,
                        dbMembers.stream()
                                .map(String::valueOf)
                                .toArray(String[]::new)
                );
                redisTemplate.expire(redisKey, 1, TimeUnit.HOURS);

                return Set.copyOf(dbMembers); // 写入本地缓存
            }

            // Redis 有数据，转换成 Long Set 返回
            Set<Long> memberSet = redisMembers.stream()
                    .map(Long::parseLong)
                    .collect(Collectors.toSet());
            return memberSet;
        });

        if (members.isEmpty()) {
            // 本地缓存和 DB 都为空，直接返回
            return;
        }

        // 2. 遍历成员发送消息
        for (Long memberId : members) {
            sendMessage(memberId, protocol);
        }
    }


    final static private String incrementLua = """
                -- 调用 Redis GET 命令，获取 key 的值（用 redis.call()，抛异常提示错误）
                local value = redis.call('GET', KEYS[1])
            
                -- 判断：key 不存在（value == nil）→ 返回 -1
                if value == nil or value == false then
                    return -1
                end
            
                -- 原子自增：用 INCR 命令（比 SET 更高效，且原生保证原子性）
                local newValue = redis.call('INCR', KEYS[1])
            
                -- 返回新值
                return newValue
            """;

    private static final DefaultRedisScript<Long> incrementLuaScript = new DefaultRedisScript<>(incrementLua, Long.class);

    private Long getMaxMessageIdOfSession(Long sessionId) {
        String queryKey = "maxMessageIdOfSession:" + sessionId;
        Long messageId = redisTemplate.execute(incrementLuaScript, Collections.singletonList(queryKey));

        if (messageId == null || messageId < 0) {
            log.info("[getMaxMessageIdOfSession]: redis查询数据失败, {}", queryKey);
            messageId = chatMessageMapper.selectMaxMessageIdInSession(sessionId);
            messageId = messageId + 1;
            redisTemplate.opsForValue().setIfAbsent(queryKey, messageId.toString(), 600, TimeUnit.SECONDS);
        } else {
            // 自增后重新设置过期时间
            redisTemplate.expire(queryKey, 600, TimeUnit.SECONDS);
        }

        return messageId;
    }

}
