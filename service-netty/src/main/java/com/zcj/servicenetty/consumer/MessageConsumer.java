package com.zcj.servicenetty.consumer;

import com.github.benmanes.caffeine.cache.Cache;
import com.zcj.common.entity.ChatMessage;
import com.zcj.servicenetty.entity.Protocol;
import com.zcj.servicenetty.mapper.ChatMessageMapper;
import com.zcj.servicenetty.server.ChannelManager;
import io.netty.channel.Channel;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@Slf4j
public class MessageConsumer {

    private final Cache<String, Set<Long>> session_member_cache;
    private final StringRedisTemplate redisTemplate;
    private final ChatMessageMapper chatMessageMapper;
    private final ChannelManager channelManager;

    public MessageConsumer(Cache<String, Set<Long>> sessionMemberCache,
                           StringRedisTemplate redisTemplate,
                           ChatMessageMapper chatMessageMapper,
                           ChannelManager channelManager) {
        session_member_cache = sessionMemberCache;
        this.redisTemplate = redisTemplate;
        this.chatMessageMapper = chatMessageMapper;
        this.channelManager = channelManager;
    }

    public final static String TOPIC = "message";

    @KafkaListener(topics = TOPIC, groupId = "message-consumers")
    @Transactional
    public void consume(List<ConsumerRecord<String, ChatMessage>> records, Acknowledgment ack) {
        List<ChatMessage> batch = new ArrayList<>();
        for (ConsumerRecord<String, ChatMessage> record : records) {
            batch.add(record.value());
        }
        if (save(batch)) {
            dispatcher(batch);
        }
        ack.acknowledge();
    }

    public boolean save(List<ChatMessage> messages) {
        chatMessageMapper.batchInsert(messages);
        return true;
    }
    // 分发消息，尽力而为
    public void dispatcher(List<ChatMessage> messages) {
        for (ChatMessage message : messages) {
            Long sessionId = message.getSessionId();
            Set<Long> memberIds = getMemberOfSession(sessionId);
            for (Long memberId : memberIds) {
                Channel channel = channelManager.getChannel(memberId);
                if (channel!=null) {
                    Protocol protocol = ChatMessage2Protocol(message);
                    if (memberId.equals(message.getFromId())) {
                        protocol.setContent("");
                        protocol.setType(Protocol.ORDER_ACK, message.getType());
                    }

                    channel.writeAndFlush(protocol);
                }
            }
        }
    }

    private static Protocol ChatMessage2Protocol(ChatMessage message) {
        Protocol protocol = new Protocol();
        protocol.setSessionId(message.getSessionId());
        protocol.setMessageId(message.getMessageId());
        protocol.setFromId(message.getFromId());
        protocol.setIdentityId(message.getIdentityId());
        protocol.setType(Protocol.ORDER_MESSAGE, message.getType());
        protocol.setTimeStamp(message.getCreatedAt());
        protocol.setContent(message.getContent());
        return protocol;
    }

    private Set<Long> getMemberOfSession(Long sessionId) {
        // 1. 从本地缓存获取会话成员
        Set<Long> members = session_member_cache.get(sessionId.toString(), key -> {
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

        return members;
    }

}
