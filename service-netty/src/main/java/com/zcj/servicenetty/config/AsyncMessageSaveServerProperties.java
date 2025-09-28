package com.zcj.servicenetty.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 异步消息存储服务配置类
 * 统一管理配置参数，避免硬编码
 */
@Data
@Component
@ConfigurationProperties(prefix = "message.async-save") // 前缀与配置文件对应
public class AsyncMessageSaveServerProperties {

    /** 消息队列最大容量（默认10000条） */
    private int maxQueueCapacity = 1000000;

    /** 定时写入数据库间隔（默认10秒） */
    private long scheduledInterval = 5;

    /** 日志文件存储路径（默认 message-logs/） */
    private String logPath = "message-logs/";

    /** 内存映射文件大小（默认100MB） */
    private long mappedFileSize = 1024 * 1024 * 10;

    /** 死信日志存储路径（默认 message-logs/dead-letter/） */
    private String deadLetterPath = "message-logs/dead-letter/";

    // 线程池线程数量
    private int threadPoolSize = 20;

    // 最大线程池线程数量
    private int maxThreadPoolSize = 40;

    private int deadLetterFileMaxSize = 1024 * 1024 * 10;

    // 需要持久化数据（0: 不持久化，1: 系统异步刷盘，2: 每次单独刷盘）
    private int saveStrategy = 2;

}