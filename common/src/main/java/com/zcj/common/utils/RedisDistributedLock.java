package com.zcj.common.utils;

import cn.hutool.core.lang.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.util.Assert;

import java.util.Collections;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.LockSupport;

/**
 * Redis分布式锁实现类
 */
@Slf4j
public class RedisDistributedLock implements Lock {

    // ========================== 静态配置（可通过配置中心动态调整）==========================
    private static final long DEFAULT_LOCK_EXPIRE = 30; // 30s
    private static final long DEFAULT_RETRY_INTERVAL = 500; // 500ms
    private static final ScheduledExecutorService RENEW_EXECUTOR = Executors.newScheduledThreadPool(1);

    // 加锁Lua脚本：原子实现“判断锁状态-加锁-重入计数”逻辑
    private static final String LOCK_SCRIPT = """
            local lockKey = KEYS[1]
            local lockHolder = ARGV[1]
            local lockExpire = ARGV[2]
            
            -- 1. 获取当前锁值（nil表示锁不存在）
            local currentLockValue = redis.call('GET', lockKey)
            
            -- 2. 场景1：锁不存在，首次加锁（重入次数初始化为1）
            if currentLockValue == false or currentLockValue == nil then
                redis.call('SET', lockKey, lockHolder .. ':1', 'NX', 'EX', lockExpire)
                return 1  -- 1=首次加锁成功
            end
            
            -- 3. 解析锁值（格式：holder:reentrantCount），防御异常格式
            local separatorIdx = string.find(currentLockValue, ':')
            if not separatorIdx then return 0 end  -- 格式异常，加锁失败
            local holderInLock = string.sub(currentLockValue, 1, separatorIdx - 1)
            local reentrantCount = tonumber(string.sub(currentLockValue, separatorIdx + 1))
            if not reentrantCount then return 0 end  -- 计数非数字，加锁失败
            
            -- 4. 场景2：锁存在且持有者匹配，实现可重入（计数+1）
            if holderInLock == lockHolder then
                local newCount = reentrantCount + 1
                redis.call('SET', lockKey, lockHolder .. ':' .. newCount, 'EX', lockExpire)
                return newCount  -- >1=重入加锁成功（返回最新计数）
            end
            
            -- 5. 场景3：锁被其他持有者占用，加锁失败
            return 0
            """;

    // 解锁Lua脚本：原子实现“验证持有者-重入计数减1-删除锁”逻辑
    private static final String UNLOCK_SCRIPT = """
            local lockKey = KEYS[1]
            local lockHolder = ARGV[1]
            local lockExpire = ARGV[2]
            
            -- 1. 获取当前锁值（nil表示锁已过期/被删除）
            local currentLockValue = redis.call('GET', lockKey)
            if currentLockValue == false or currentLockValue == nil then return 2 end  -- 2=锁已不存在
            
            -- 2. 解析锁值并验证持有者
            local separatorIdx = string.find(currentLockValue, ':')
            if not separatorIdx then
                redis.call('DEL', lockKey)  -- 清理异常锁值
                return 3  -- 3=锁格式异常（已清理）
            end
            local holderInLock = string.sub(currentLockValue, 1, separatorIdx - 1)
            local reentrantCount = tonumber(string.sub(currentLockValue, separatorIdx + 1))
            if not reentrantCount then
                redis.call('DEL', lockKey)  -- 清理异常锁值
                return 3
            end
            
            -- 3. 场景1：持有者不匹配，拒绝解锁（防止误删他人锁）
            if holderInLock ~= lockHolder then return 0 end  -- 0=解锁失败
            
            -- 4. 场景2：持有者匹配，处理重入计数
            if reentrantCount > 1 then
                -- 计数>1：仅减计数，不删锁（续期仍生效）
                local newCount = reentrantCount - 1
                redis.call('SET', lockKey, lockHolder .. ':' .. newCount, 'EX', lockExpire)
                return 1  -- 1=计数减1成功（锁未释放）
            else
                -- 计数=1：删除锁，完全释放资源
                redis.call('DEL', lockKey)
                return 4  -- 4=锁删除成功（完全释放）
            end
            """;

    // 续期Lua脚本：仅给当前持有者的锁续期（避免续期他人锁）
    private static final String RENEW_SCRIPT = """
            local lockKey = KEYS[1]
            local holderPrefix = ARGV[1]  -- 格式：holder:（兼容任意重入计数）
            local lockExpire = ARGV[2]
            
            -- 1. 锁不存在则无需续期
            local currentLockValue = redis.call('GET', lockKey)
            if currentLockValue == false or currentLockValue == nil then return 0 end
            
            -- 2. 验证持有者前缀（匹配任意重入计数）
            local prefixLen = string.len(holderPrefix)
            if string.sub(currentLockValue, 1, prefixLen) == holderPrefix then
                redis.call('EXPIRE', lockKey, lockExpire)
                return 1  -- 1=续期成功
            end
            
            -- 3. 持有者不匹配，拒绝续期
            return 0
            """;

    // 脚本实例：提前初始化，避免重复创建（线程安全）
    private static final DefaultRedisScript<Long> LOCK_SCRIPT_INSTANCE = new DefaultRedisScript<>(LOCK_SCRIPT, Long.class);
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT_INSTANCE = new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);
    private static final DefaultRedisScript<Long> RENEW_SCRIPT_INSTANCE = new DefaultRedisScript<>(RENEW_SCRIPT, Long.class);


    // ========================== 实例属性 ==========================
    private final StringRedisTemplate redisTemplate;
    private final String lockKey;          // 最终Redis键（格式：lock:业务键）
    private final String identityId;       // 持有者标识（UUID，全局唯一）
    private final long lockExpire;         // 锁过期时间（秒，支持自定义）
    private ScheduledFuture<?> renewFuture;// 续期任务


    // ========================== 构造方法 ==========================
    public RedisDistributedLock(StringRedisTemplate redisTemplate, String businessKey) {
        this(redisTemplate, businessKey, DEFAULT_LOCK_EXPIRE);
    }

    public RedisDistributedLock(StringRedisTemplate redisTemplate, String businessKey, long lockExpire) {
        // 参数校验：避免非法配置导致的潜在问题
        Assert.notNull(redisTemplate, "StringRedisTemplate 不能为null");
        Assert.hasText(businessKey, "业务键 businessKey 不能为空");
        Assert.isTrue(lockExpire > 0, "锁过期时间必须大于0秒");

        this.redisTemplate = redisTemplate;
        this.lockKey = "lock:" + businessKey;  // 统一前缀，避免键冲突
        // 生成持有者标识：UUID（全局唯一）
        this.identityId = UUID.randomUUID().toString();
        this.lockExpire = lockExpire;
    }


    // ========================== Lock接口核心方法实现 ==========================

    /**
     * 阻塞加锁：无限重试直到获取锁
     * 特性：加锁成功后自动启动续期，支持重入
     */
    @Override
    public void lock() {
        while (true) {
            // 单次加锁尝试：成功则标记状态并启动续期
            if (tryLockOnce()) {
                log.debug("[RedisDistributedLock:lock] 加锁成功");
                startRenewTask();
                return;
            }
            // LockSupport比sleep更轻量）
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(DEFAULT_RETRY_INTERVAL));
        }
    }

    /**
     * 可中断加锁：获取锁过程中支持线程中断（中断时抛出异常）
     */
    @Override
    public void lockInterruptibly() throws InterruptedException {
        while (true) {
            // 1. 检测线程中断状态：已中断则抛出异常
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException(
                        String.format("线程[%d]获取锁[%s]过程中被中断", Thread.currentThread().getId(), lockKey)
                );
            }

            // 2. 单次加锁尝试：成功则标记状态并启动续期
            if (tryLockOnce()) {
                log.debug("[RedisDistributedLock:lockInterruptibly] 加锁成功");
                startRenewTask();
                return;
            }

            // 3. 加锁失败：休眠重试
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(DEFAULT_RETRY_INTERVAL));
        }
    }

    /**
     * 非阻塞加锁：仅尝试一次，成功返回true，失败返回false
     */
    @Override
    public boolean tryLock() {
        boolean success = tryLockOnce();
        if (success) {
            log.debug("[RedisDistributedLock:tryLock] 加锁成功");
            startRenewTask();
        }
        return success;
    }

    /**
     * 带超时的非阻塞加锁：超时前重试，超时后返回false（支持中断）
     */
    @Override
    public boolean tryLock(long time, TimeUnit unit) throws InterruptedException {
        Assert.notNull(unit, "时间单位 TimeUnit 不能为null");
        Assert.isTrue(time >= 0, "超时时间不能为负数");

        final long timeoutNanos = unit.toNanos(time);
        final long startNanos = System.nanoTime();  // 记录开始时间（纳秒级）

        while (true) {
            // 1. 检测超时：已超时则返回失败
            if (System.nanoTime() - startNanos > timeoutNanos) {
                return false;
            }

            // 2. 检测中断：已中断则抛出异常
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException(
                        String.format("线程[%d]获取锁[%s]超时前被中断", Thread.currentThread().getId(), lockKey)
                );
            }

            // 3. 单次加锁尝试：成功则标记状态并启动续期
            if (tryLockOnce()) {
                log.debug("[RedisDistributedLock:tryLock] 加锁成功");
                startRenewTask();
                return true;
            }

            // 4. 计算剩余时间：避免休眠超时
            long remainNanos = timeoutNanos - (System.nanoTime() - startNanos);
            long sleepNanos = Math.min(TimeUnit.MILLISECONDS.toNanos(DEFAULT_RETRY_INTERVAL), remainNanos);
            LockSupport.parkNanos(sleepNanos);
        }
    }

    @Override
    public void unlock() {
        // 未加锁状态下解锁
        if (renewFuture == null) {
            throw new IllegalMonitorStateException(
                    String.format("线程[%d]未持有锁[%s]，无法执行解锁操作", Thread.currentThread().getId(), lockKey)
            );
        }

        // 执行解锁Lua脚本（原子操作）
        Long result = redisTemplate.execute(
                UNLOCK_SCRIPT_INSTANCE,
                Collections.singletonList(lockKey),
                identityId,
                String.valueOf(lockExpire)
        );

        if (result != null && result == 4) {
            cancelRenewTask();
            log.debug("[RedisDistributedLock:unlock] 解锁成功");
        }
    }

    /**
     * Condition：分布式锁暂不支持
     */
    @Override
    public Condition newCondition() {
        throw new UnsupportedOperationException("Redis分布式锁暂不支持Condition功能");
    }

    // ========================== 辅助方法 ==========================

    /**
     * 单次加锁尝试（原子操作，复用加锁脚本）
     */
    private boolean tryLockOnce() {
        try {
            Long result = redisTemplate.execute(
                    LOCK_SCRIPT_INSTANCE,
                    Collections.singletonList(lockKey),
                    identityId,
                    String.valueOf(lockExpire)
            );
            // 结果判断：result>0表示加锁/重入成功（1=首次加锁，>1=重入）
            return result != null && result > 0;
        } catch (Exception e) {
            // 加锁异常（如Redis连接超时）：抛出运行时异常，避免静默失败
            throw new RuntimeException(
                    String.format("线程[%d]尝试加锁[%s]异常", Thread.currentThread().getId(), lockKey), e
            );
        }
    }

    /**
     * 启动续期任务：加锁成功后执行，续期周期为锁过期时间的1/2（平衡安全性与性能）
     */
    private void startRenewTask() {
        // 避免重复启动续期任务
        if (renewFuture != null) {
            return;
        }

        // 续期任务逻辑：仅给当前持有者的锁续期
        Runnable renewTask = () -> {
            try {
                Long result = redisTemplate.execute(
                        RENEW_SCRIPT_INSTANCE,
                        Collections.singletonList(lockKey),
                        identityId + ":",  // 前缀匹配（兼容任意重入计数）
                        String.valueOf(lockExpire)
                );
                log.debug("线程{}, 进行分布式锁续期: {}", Thread.currentThread().getId(), lockExpire);
                // 续期失败（如锁已被删除）：自动取消续期任务
                if (result == null || result == 0) {
                    log.debug("线程{}, 进行分布式锁续期失败", Thread.currentThread().getId());
                    cancelRenewTask();
                }
            } catch (Exception e) {
                // 续期异常：取消任务避免资源泄漏，抛出异常便于排查
                cancelRenewTask();
                log.error("[RedisDistributedLock: startRenewTask] 出现错误{}", e.getMessage());
                throw new RuntimeException(String.format("锁[%s]续期异常", lockKey), e);
            }
        };

        // 提交续期任务
        long halfExpire = lockExpire / 2;
        // 确保续期周期至少为 1 秒（避免 0）
        halfExpire = Math.max(halfExpire , 1);
        this.renewFuture = RENEW_EXECUTOR.scheduleAtFixedRate(
                renewTask,
                halfExpire ,
                halfExpire ,
                TimeUnit.SECONDS
        );
        log.debug("[RedisDistributedLock: startRenewTask] 开启续期任务");
    }

    /**
     * 取消续期任务：解锁时调用
     */
    private void cancelRenewTask() {
        log.debug("[RedisDistributedLock: cancelRenewTask] 取消续期任务");
        if (renewFuture != null) {
            renewFuture.cancel(true);
            renewFuture = null;
        }
    }

}