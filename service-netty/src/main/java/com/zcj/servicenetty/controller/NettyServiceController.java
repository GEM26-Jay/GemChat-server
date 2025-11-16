package com.zcj.servicenetty.controller;

import com.zcj.common.utils.NetUtil;
import com.zcj.common.vo.Result;
import com.zcj.servicenetty.config.NettyProperties;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.SocketException;

@RestController
@RequestMapping("/api/netty")
@Tag(name = "Netty的API接口")
@Slf4j
@RequiredArgsConstructor
public class NettyServiceController {

    private final NettyProperties nettyProperties;

    @GetMapping("/getAddr")
    public Result<String> getAddr() {
        try {
            // 构建服务地址（IP:端口）
            String address = NetUtil.getLocalIp() + ":" + nettyProperties.getPort();
            return Result.success(address);
        }catch (SocketException e) {
            return Result.error(e.toString());
        }
    }

}
