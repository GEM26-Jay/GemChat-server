package com.zcj.servicechat;

import com.zcj.common.utils.RedisDistributedLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * RedisDistributedLock 测试类：覆盖基础功能、分布式竞争、异常场景
 */
@SpringBootTest
class RedisDistributedLockTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    // 测试用锁键（每次测试前清空，避免干扰）
    private static final String TEST_LOCK_KEY = "test:business:key";
    // 线程池（模拟多线程竞争）
    private ExecutorService executorService;

    /**
     * 测试前初始化：清空Redis中残留的锁键 + 初始化线程池
     */
    @BeforeEach
    void setUp() {
        // 清理历史锁键（避免前序测试残留影响）
        redisTemplate.delete("lock:" + TEST_LOCK_KEY);
        // 初始化线程池（核心线程数10，模拟分布式环境多线程）
        executorService = Executors.newFixedThreadPool(10);
    }

    /**
     * 测试后销毁：关闭线程池 + 最终清理锁键
     */
    @AfterEach
    void tearDown() throws InterruptedException {
        // 关闭线程池（等待任务结束，避免资源泄漏）
        executorService.shutdown();
        if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
            executorService.shutdownNow();
        }
        // 最终清理锁键
        redisTemplate.delete("lock:" + TEST_LOCK_KEY);
    }


    // ========================== 1. 基础功能测试 ==========================

    /**
     * 测试：非阻塞加锁（tryLock）成功 + 解锁成功
     */
    @Test
    void testTryLockAndUnlock_Success() {
        // 1. 创建锁实例（过期时间10秒）
        RedisDistributedLock lock = new RedisDistributedLock(redisTemplate, TEST_LOCK_KEY, 10);

        // 2. 尝试加锁（非阻塞）
        boolean lockSuccess = lock.tryLock();
        assertThat(lockSuccess).isTrue(); // 加锁应成功

        // 3. 验证Redis中锁键存在（格式：holder:1）
        String lockValue = redisTemplate.opsForValue().get("lock:" + TEST_LOCK_KEY);
        assertThat(lockValue).isNotNull();
        assertThat(lockValue.split(":").length).isEqualTo(2); // 格式校验：holder:count

        // 4. 解锁
        lock.unlock();

        // 5. 验证锁键已删除
        lockValue = redisTemplate.opsForValue().get("lock:" + TEST_LOCK_KEY);
        assertThat(lockValue).isNull();
    }

    /**
     * 测试：可重入性（同一线程多次加锁成功，计数正确）
     */
    @Test
    void testLockReentrancy_Success() {
        RedisDistributedLock lock = new RedisDistributedLock(redisTemplate, TEST_LOCK_KEY, 10);

        // 第一次加锁（非阻塞）
        assertThat(lock.tryLock()).isTrue();
        String lockValue = redisTemplate.opsForValue().get("lock:" + TEST_LOCK_KEY);
        assertThat(lockValue.split(":")[1]).isEqualTo("1"); // 计数=1

        // 第二次加锁（重入）
        assertThat(lock.tryLock()).isTrue();
        lockValue = redisTemplate.opsForValue().get("lock:" + TEST_LOCK_KEY);
        assertThat(lockValue.split(":")[1]).isEqualTo("2"); // 计数=2

        // 第一次解锁（计数减1，锁不删除）
        lock.unlock();
        lockValue = redisTemplate.opsForValue().get("lock:" + TEST_LOCK_KEY);
        assertThat(lockValue).isNotNull();
        assertThat(lockValue.split(":")[1]).isEqualTo("1"); // 计数=1

        // 第二次解锁（计数=0，锁删除）
        lock.unlock();
        lockValue = redisTemplate.opsForValue().get("lock:" + TEST_LOCK_KEY);
        assertThat(lockValue).isNull();
    }

    /**
     * 测试：未加锁时解锁 → 抛 IllegalMonitorStateException
     */
    @Test
    void testUnlockWithoutLock_ThrowException() {
        RedisDistributedLock lock = new RedisDistributedLock(redisTemplate, TEST_LOCK_KEY, 10);

        // 未加锁直接解锁，应抛出异常
        assertThatThrownBy(lock::unlock)
                .isInstanceOf(IllegalMonitorStateException.class)
                .hasMessageContaining("未持有锁");
    }

    /**
     * 测试：带超时的加锁（tryLock(time, unit)）→ 超时后返回false
     */
    @Test
    void testTryLockWithTimeout_TimeoutReturnFalse() throws InterruptedException {
        // 1. 线程1先持有锁
        RedisDistributedLock lock1 = new RedisDistributedLock(redisTemplate, TEST_LOCK_KEY, 10);
        assertThat(lock1.tryLock()).isTrue();

        // 2. 线程2尝试加锁（超时1秒）
        RedisDistributedLock lock2 = new RedisDistributedLock(redisTemplate, TEST_LOCK_KEY, 10);
        long start = System.currentTimeMillis();
        boolean lock2Success = lock2.tryLock(1, TimeUnit.SECONDS); // 超时1秒
        long cost = System.currentTimeMillis() - start;

        // 3. 验证结果：加锁失败，耗时≈1秒
        assertThat(lock2Success).isFalse();
        assertThat(cost).isBetween(900L, 1500L); // 允许1.5秒内的误差

        // 4. 释放线程1的锁
        lock1.unlock();
    }


    // ========================== 2. 分布式竞争测试（核心） ==========================

    /**
     * 测试：多线程竞争同一锁 → 保证原子性（模拟秒杀场景，避免超卖）
     * 预期：1000个线程各执行1次计数，最终结果=1000（无并发安全问题）
     */
    @Test
    void testMultiThreadCompetition_Atomic() throws InterruptedException {
        // 共享计数器（模拟库存）
        AtomicInteger counter = new AtomicInteger(0);
        int threadCount = 1000; // 1000个竞争线程
        CountDownLatch countDownLatch = new CountDownLatch(threadCount); // 等待所有线程结束

        // 每个线程逻辑：加锁 → 计数+1 → 解锁
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                RedisDistributedLock lock = null;
                try {
                    lock = new RedisDistributedLock(redisTemplate, TEST_LOCK_KEY, 10);
                    // 阻塞加锁（直到获取到锁）
                    lock.lock();
                    // 原子操作（若锁失效，此处会出现超卖）
                    counter.incrementAndGet();
                } finally {
                    // 确保解锁（避免死锁）
                    if (lock != null) {
                        try {
                            lock.unlock();
                        } catch (IllegalMonitorStateException e) {
                            // 忽略未持有锁的解锁异常（极端情况线程中断导致加锁失败）
                        }
                    }
                    countDownLatch.countDown();
                }
            });
        }

        // 等待所有线程执行完毕（最多等待30秒）
        assertThat(countDownLatch.await(30, TimeUnit.SECONDS)).isTrue();

        // 验证结果：计数器=1000（无超卖，锁原子性生效）
        assertThat(counter.get()).isEqualTo(threadCount);
    }

    /**
     * 测试：锁过期自动释放 → 避免死锁（模拟线程加锁后崩溃，锁超时后其他线程可获取）
     */
    @Test
    void testLockExpire_AutomaticRelease() throws InterruptedException {
        // 1. 线程1加锁后不解锁（模拟崩溃）
        RedisDistributedLock lock1 = new RedisDistributedLock(redisTemplate, TEST_LOCK_KEY, 2); // 过期时间2秒
        assertThat(lock1.tryLock()).isTrue();

        // 2. 等待3秒（确保锁过期）
        TimeUnit.SECONDS.sleep(3);

        // 3. 线程2尝试加锁（应成功，锁已过期释放）
        RedisDistributedLock lock2 = new RedisDistributedLock(redisTemplate, TEST_LOCK_KEY, 10);
        boolean lock2Success = lock2.tryLock();
        assertThat(lock2Success).isTrue();

        // 4. 释放线程2的锁
        lock2.unlock();
    }


    // ========================== 3. 续期功能测试 ==========================

    /**
     * 测试：加锁后自动续期 → 锁不会过期（模拟长时间任务，续期生效）
     */
    @Test
    void testLockRenew_KeepAlive() throws InterruptedException {
        // 1. 创建锁（过期时间3秒，续期周期=3/2+1=2秒）
        RedisDistributedLock lock = new RedisDistributedLock(redisTemplate, TEST_LOCK_KEY, 3);
        assertThat(lock.tryLock()).isTrue();

        // 2. 循环等待10秒（远超初始过期时间，验证续期生效）
        for (int i = 0; i < 10; i++) {
            TimeUnit.SECONDS.sleep(2);
            // 每次等待后检查锁是否存在（续期应生效，锁不会过期）
            String lockValue = redisTemplate.opsForValue().get("lock:" + TEST_LOCK_KEY);
            assertThat(lockValue).isNotNull();
        }

        // 3. 解锁后验证锁删除
        lock.unlock();
        String lockValue = redisTemplate.opsForValue().get("lock:" + TEST_LOCK_KEY);
        assertThat(lockValue).isNull();
    }


    // ========================== 4. 中断功能测试 ==========================

    /**
     * 测试：可中断加锁（lockInterruptibly）→ 线程中断时抛异常
     */
    @Test
    void testLockInterruptibly_ThrowOnInterrupt() throws InterruptedException {
        // 1. 线程1先持有锁
        RedisDistributedLock lock1 = new RedisDistributedLock(redisTemplate, TEST_LOCK_KEY, 10);
        assertThat(lock1.tryLock()).isTrue();

        // 2. 线程2尝试可中断加锁，同时中断线程2
        AtomicReference<Exception> exceptionRef = new AtomicReference<>();
        Thread thread2 = new Thread(() -> {
            RedisDistributedLock lock2 = new RedisDistributedLock(redisTemplate, TEST_LOCK_KEY, 10);
            try {
                lock2.lockInterruptibly(); // 可中断加锁
            } catch (InterruptedException e) {
                exceptionRef.set(e); // 捕获中断异常
            }
        });

        thread2.start();
        TimeUnit.MILLISECONDS.sleep(100); // 确保线程2进入加锁阻塞
        thread2.interrupt(); // 中断线程2
        thread2.join(); // 等待线程2结束

        // 3. 验证线程2被中断，抛出InterruptedException
        assertThat(exceptionRef.get()).isInstanceOf(InterruptedException.class);

        // 4. 释放线程1的锁
        lock1.unlock();
    }
}