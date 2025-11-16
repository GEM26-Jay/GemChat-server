package com.zcj.servicenetty.consumer;

import com.github.benmanes.caffeine.cache.Cache;
import com.zcj.common.entity.ChatMessage;
import com.zcj.common.entity.Protocol;
import com.zcj.servicenetty.mapper.ChatMessageMapper;
import com.zcj.servicenetty.service.ChannelManager;
import com.zcj.servicenetty.service.MessageRouterService;
import io.netty.channel.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class MessageConsumer {

    private final Cache<String, Set<Long>> session_member_cache;
    private final StringRedisTemplate redisTemplate;
    private final ChatMessageMapper chatMessageMapper;
    private final ChannelManager channelManager;

    public final static String TOPIC = "message";
    private final MessageRouterService messageRouterService;

    @KafkaListener(topics = TOPIC, groupId = "message-consumers")
    public void consume(List<ConsumerRecord<String, ChatMessage>> records, Acknowledgment ack) {
        List<ChatMessage> waitingList = new ArrayList<>();
        for (ConsumerRecord<String, ChatMessage> record : records) {
            waitingList.add(record.value());
        }

        if (save(waitingList)) {
            dispatch(waitingList);
        }
        // 幂等性问题
        ack.acknowledge();
    }

    public boolean save(List<ChatMessage> messages) {
        chatMessageMapper.batchInsert(messages);
        return true;
    }

    // ========================= 分发消息 ==========================
    public void dispatch(List<ChatMessage> messages) {
        for (ChatMessage message : messages) {
            Long sessionId = message.getSessionId();
            Protocol protocol = message.toProtocol();
            List<Long> remain = new ArrayList<>();
            for (Long memberId : getMemberOfSession(sessionId)) {
                if (Objects.equals(message.getFromId(), memberId)) continue;
                Channel channel = channelManager.getChannel(memberId);
                if (channel != null) {
                    // 本服务器存在用户
                    channel.writeAndFlush(protocol);
                } else {
                    // 本服务器不存在用户
                    remain.add(memberId);
                }
            }
            // 消息转发
            messageRouterService.dispatch(remain, message);
        }
    }

    private Set<Long> getMemberOfSession(Long sessionId) {
        // 1. 从本地缓存获取会话成员
        return session_member_cache.get(sessionId.toString(), key -> {
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
            return redisMembers.stream()
                    .map(Long::parseLong)
                    .collect(Collectors.toSet());
        });
    }
}
