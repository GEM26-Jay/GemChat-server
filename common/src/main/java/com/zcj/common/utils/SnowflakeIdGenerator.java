package com.zcj.common.utils;

import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 雪花算法ID生成器（优化版）
 * 结构：1位符号位 + 41位时间戳 + 5位数据中心ID + 5位机器ID + 12位序列号
 */
@Data
public class SnowflakeIdGenerator {
    private static final Logger log = LoggerFactory.getLogger(SnowflakeIdGenerator.class);

    // 起始时间戳（2023-01-01）
    private static final long EPOCH = 1672531200000L;

    // 各部分位数
    private static final long SEQUENCE_BITS = 12;
    private static final long MACHINE_ID_BITS = 5;
    private static final long DATACENTER_ID_BITS = 5;

    // 各部分最大值
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;
    private static final long MAX_MACHINE_ID = (1L << MACHINE_ID_BITS) - 1;
    private static final long MAX_DATACENTER_ID = (1L << DATACENTER_ID_BITS) - 1;

    // 位移量
    private static final long MACHINE_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + MACHINE_ID_BITS;
    private static final long TIMESTAMP_SHIFT = SEQUENCE_BITS + MACHINE_ID_BITS + DATACENTER_ID_BITS;

    // 时钟回拨最大容忍时间（毫秒）
    private static final long MAX_BACKWARD_MS = 5;

    private long roomId;
    private long workerId;
    private final AtomicLong sequence = new AtomicLong(0);
    private final AtomicLong lastTimestamp = new AtomicLong(-1L);

    // 时钟回拨处理
    private long lastBackwardTimestamp = 0;
    private int backwardCount = 0;

    public SnowflakeIdGenerator(long roomId, long workerId) {
        if (roomId < 0 || roomId > MAX_DATACENTER_ID) {
            throw new IllegalArgumentException("Datacenter ID must be between 0 and " + MAX_DATACENTER_ID);
        }
        if (workerId < 0 || workerId > MAX_MACHINE_ID) {
            throw new IllegalArgumentException("Machine ID must be between 0 and " + MAX_MACHINE_ID);
        }

        this.roomId = roomId;
        this.workerId = workerId;

        log.info("Snowflake ID generator initialized: roomId={}, workerId={}",
                roomId, workerId);
    }

    /**
     * 生成分布式唯一ID
     */
    public long nextId() {
        long currentTime = System.currentTimeMillis();

        // 处理时钟回拨
        long lastTime = lastTimestamp.get();
        if (currentTime < lastTime) {
            handleClockBackward(currentTime, lastTime);
        }

        // 同一毫秒内，序列号递增
        if (currentTime == lastTime) {
            long seq = sequence.incrementAndGet() & MAX_SEQUENCE;
            if (seq == 0) { // 序列号用尽，等待下一毫秒
                currentTime = waitForNextMillis(lastTime);
            }
        } else {
            // 时间戳改变，重置序列号
            sequence.set(0);
        }

        // 更新最后时间戳
        lastTimestamp.set(currentTime);

        // 生成ID
        return ((currentTime - EPOCH) << TIMESTAMP_SHIFT) |
                (roomId << DATACENTER_ID_SHIFT) |
                (workerId << MACHINE_ID_SHIFT) |
                sequence.get();
    }

    /**
     * 处理时钟回拨
     */
    private void handleClockBackward(long currentTime, long lastTime) {
        long offset = lastTime - currentTime;

        if (offset <= MAX_BACKWARD_MS) {
            // 容忍范围内的时钟回拨：等待时钟恢复或使用备用时间
            try {
                Thread.sleep(offset + 1); // 等待时钟追上
                currentTime = System.currentTimeMillis();
                if (currentTime < lastTime) {
                    // 等待后仍未恢复，使用备用时间
                    currentTime = lastTime + 1;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                currentTime = lastTime + 1;
            }
        } else {
            // 超出容忍范围：记录告警并使用备用时间
            long now = System.currentTimeMillis();
            if (now - lastBackwardTimestamp > 60000) { // 每分钟只记录一次告警
                lastBackwardTimestamp = now;
                backwardCount++;
                log.error("Clock moved backwards too much: {}ms, count={}", offset, backwardCount);
            }
            currentTime = lastTime + 1;
        }
    }

    /**
     * 等待下一毫秒
     */
    private long waitForNextMillis(long lastTime) {
        long time;
        do {
            time = System.currentTimeMillis();
        } while (time <= lastTime);
        return time;
    }

    /**
     * 从ID中解析时间戳（毫秒）
     */
    public static long extractTimestamp(long id) {
        return (id >>> TIMESTAMP_SHIFT) + EPOCH;
    }

    /**
     * 从ID中解析数据中心ID
     */
    public static long extractDatacenterId(long id) {
        return (id >>> DATACENTER_ID_SHIFT) & MAX_DATACENTER_ID;
    }

    /**
     * 从ID中解析机器ID
     */
    public static long extractMachineId(long id) {
        return (id >>> MACHINE_ID_SHIFT) & MAX_MACHINE_ID;
    }

    /**
     * 从ID中解析序列号
     */
    public static long extractSequence(long id) {
        return id & MAX_SEQUENCE;
    }
}
