package com.zcj.common.feign;

import com.zcj.common.vo.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Component
@FeignClient(name = "service-netty")
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
}
