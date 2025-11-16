package com.zcj.common.utils;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;

public class NetUtil {

    public static String getLocalIp() throws SocketException {
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while (interfaces.hasMoreElements()) {
            NetworkInterface ni = interfaces.nextElement();
            // 跳过虚拟网卡和禁用的网卡
            if (ni.isLoopback() || ni.isVirtual() || !ni.isUp()) {
                continue;
            }
            Enumeration<InetAddress> addresses = ni.getInetAddresses();
            while (addresses.hasMoreElements()) {
                InetAddress addr = addresses.nextElement();
                // 只返回IPv4地址（排除IPv6）
                if (!addr.getHostAddress().contains(":")) {
                    return addr.getHostAddress();
                }
            }
        }
        // 若未找到有效IP，返回回环地址
        return "127.0.0.1";
    }

}
