package com.zcj.servicenetty.config;

import com.zcj.common.utils.NetUtil;
import com.zcj.servicenetty.handler.AuthHandler;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import java.net.SocketException;

@Configuration
public class ServerConfig {

    // 从配置文件读取端口号
    @Value("${server.port}")
    private String serverPort;

    // 服务启动时执行（仅一次）
    @PostConstruct
    public void initLocalAddr() throws SocketException {
        // 拼接IP:端口
        String localIp = NetUtil.getLocalIp(); // 你的NetUtil工具类
        String localAddr = localIp + ":" + serverPort;
        // 调用AuthHandler的静态方法设置静态变量
        AuthHandler.setLocalAddr(localAddr);
    }
}
