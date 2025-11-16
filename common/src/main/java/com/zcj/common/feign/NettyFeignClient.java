package com.zcj.common.feign;

import com.zcj.common.dto.SendRequestDTO;
import com.zcj.common.entity.Protocol;
import com.zcj.common.vo.Result;
import io.netty.channel.Channel;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Component
@FeignClient(name = "service-netty", url = "${placeholder.url:http://localhost}")
public interface NettyFeignClient {

    @GetMapping("/admin/pushSync")
    Result<Void> sync(
            @RequestParam("id") Long id,
            @RequestParam("table") String table
    );

    @GetMapping("/admin/pushSyncBatch")
    Result<Void> syncBatch(
            @RequestParam("ids") List<Long> ids,
            @RequestParam("table") String table
    );

    @PostMapping("/admin/send")
    Result<Void> send(@RequestBody SendRequestDTO sendRequest);
}
