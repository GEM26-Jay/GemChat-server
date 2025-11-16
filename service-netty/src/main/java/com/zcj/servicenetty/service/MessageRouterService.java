package com.zcj.servicenetty.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.zcj.common.dto.SendRequestDTO;
import com.zcj.common.entity.ChatMessage;
import com.zcj.common.entity.Protocol;
import com.zcj.common.feign.NettyFeignClient;
import feign.Feign;
import feign.codec.Decoder;
import feign.codec.Encoder;
import lombok.RequiredArgsConstructor;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.cloud.openfeign.FeignClientsConfiguration;
import org.springframework.context.annotation.Import;

@Service
@RequiredArgsConstructor
@Import(FeignClientsConfiguration.class)
public class MessageRouterService {

    private final Cache<Long, String> user_route_cache;
    private final StringRedisTemplate redisTemplate;
    public static final String USER_ROUTE_KEY = "netty:user_route";

    private final Encoder encoder;
    private final Decoder decoder;
    private final Map<String, NettyFeignClient> clientCache = new ConcurrentHashMap<>();

    public boolean dispatch(List<Long> ids, ChatMessage message) {
        HashMap<String, List<Long>> map = new HashMap<>();
        for (Long id : ids) {
            String ipAddr = user_route_cache.get(id, (key) -> {
                // redis缓存 用户TCP连接地址表
                Object o = redisTemplate.opsForHash().get(USER_ROUTE_KEY, id.toString());
                if (o == null) return null;
                else return o.toString();
            });
            if (ipAddr != null) {
                List<Long> orDefault = map.getOrDefault(ipAddr, new ArrayList<>());
                orDefault.add(id);
                map.put(ipAddr, orDefault);
            }
        }
        for (Map.Entry<String, List<Long>> entry : map.entrySet()) {
            String ipAddr = entry.getKey();
            List<Long> subIds = entry.getValue();
            NettyFeignClient feignClient = getFeignClient(ipAddr);
            feignClient.send(new SendRequestDTO(subIds, message));
        }
        return true;
    }

    public NettyFeignClient getFeignClient(String ipAddr) {
        String url = "http://" + ipAddr;
        return clientCache.computeIfAbsent(url, u ->
                Feign.builder()
                        .contract(new SpringMvcContract())
                        .encoder(encoder)
                        .decoder(decoder)
                        .target(NettyFeignClient.class, u)
        );
    }
}
