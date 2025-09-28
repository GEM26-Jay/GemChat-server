package com.zcj.servicenetty.server;

import com.zcj.common.entity.ChatMessage;
import com.zcj.servicenetty.config.AsyncMessageSaveServerProperties;
import com.zcj.servicenetty.mapper.ChatMessageMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.Objects;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 异步消息存储服务
 * 负责消息的内存缓存、内存映射文件持久化、批量写入数据库
 * 采用三级存储机制确保消息可靠性：内存队列 -> 内存映射文件 -> 数据库
 */
@Component
@Slf4j
@RequiredArgsConstructor
@Data
public class AsyncMessageSaveServer {

    // 依赖注入（构造函数注入，符合Spring最佳实践）
    private final ChatMessageMapper chatMessageMapper;
    private final AsyncMessageSaveServerProperties properties;

    // 核心组件
    private LinkedBlockingQueue<ChatMessage> messageQueue;
    private ThreadPoolExecutor workExecutor;
    private ScheduledExecutorService scheduler;
    private final ConcurrentLinkedDeque<ChatMessage> memoryCache = new ConcurrentLinkedDeque<>();
    private final Map<String, Runnable> successCallBack = new ConcurrentHashMap<>();
    private final Map<String, Runnable> failedCallBack = new ConcurrentHashMap<>();

    // 内部管理器实例
    private DeadFileLogManager deadFileLogManager;
    private RedoLogManager redoLogManager;

    // 服务状态标识（原子类保证线程安全）
    private final AtomicBoolean isRunning = new AtomicBoolean(true);

    /**
     * 死信文件管理器
     * 负责死信消息的持久化、文件轮转和资源管理
     */
    private class DeadFileLogManager {

        // 核心成员变量：新增FileChannel引用（用于安全释放映射）
        private volatile MappedByteBuffer deadLogMappedBuffer;
        private volatile File currentDeadFile;
        private volatile FileChannel currentDeadFileChannel; // 新增：持有当前文件的Channel
        private volatile String currentDeadFileDate; // 格式：yyyyMMdd
        private int currentFileIndex = 0; // 同一天内的文件索引
        private final Object lock = new Object(); // 保证线程安全的锁对象
        private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd"); // 复用Formatter，避免重复创建


        public DeadFileLogManager() {
            try {
                initCurrentDeadFile();
                log.info("死信文件管理器初始化完成，当前文件：{}", currentDeadFile.getAbsolutePath());
            } catch (IOException e) {
                // 初始化失败时清理已创建的资源，避免泄漏
                cleanup();
                throw new RuntimeException("初始化死信文件管理器失败", e);
            }
        }

        /**
         * 将消息写入死信队列
         * @param message 待写入的消息（非null）
         * @return 是否写入成功
         */
        public boolean write(ChatMessage message) {
            // 前置校验：消息非null
            if (message == null) {
                log.warn("尝试写入空消息到死信文件，已忽略");
                return false;
            }

            // 序列化消息：一行一条（确保格式统一，便于后续解析）
            String msgLogStr = message.toLogString() + System.lineSeparator();
            byte[] data = msgLogStr.getBytes(StandardCharsets.UTF_8);
            // 提前校验数据长度：若超过单文件最大容量，直接返回失败（避免无效切换）
            if (data.length > properties.getDeadLetterFileMaxSize()) {
                log.error("死信消息长度超过单文件最大容量（{}B），无法写入（messageId: {}）",
                        properties.getDeadLetterFileMaxSize(), message.getMessageId());
                return false;
            }

            synchronized (lock) {
                try {
                    // 1. 检查并切换文件（日期变更或剩余空间不足）
                    if (needSwitchFile(data.length)) {
                        if (!switchFile()) {
                            log.error("文件切换失败，无法写入死信消息（messageId: {}）", message.getMessageId());
                            return false;
                        }
                    }

                    // 2. 最终校验缓冲区状态（防止切换后异常）
                    if (deadLogMappedBuffer == null || currentDeadFileChannel == null || !currentDeadFileChannel.isOpen()) {
                        log.error("死信缓冲区/Channel不可用，无法写入消息（messageId: {}）", message.getMessageId());
                        return false;
                    }

                    // 3. 写入数据并强制刷盘（死信消息需确保持久化，避免丢失）
                    deadLogMappedBuffer.put(data);
                    deadLogMappedBuffer.force(); // 关键：强制将内存数据刷到磁盘

                    log.info("死信消息写入成功（messageId: {}，文件: {}，写入字节数: {}）",
                            message.getMessageId(), currentDeadFile.getAbsolutePath(), data.length);
                    return true;
                } catch (Exception e) {
                    log.error("写入死信消息失败（messageId: {}）", message.getMessageId(), e);
                    return false;
                }
            }
        }

        /**
         * 切换死信日志文件（文件名格式：death-log-yyyyMMdd-index.dat）
         * 触发条件：日期变更 或 当前文件剩余空间不足
         * @return 是否切换成功
         */
        public boolean switchFile() {
            synchronized (lock) {
                // 保存旧文件信息，用于日志打印和异常恢复
                File oldDeadFile = currentDeadFile;
                try {
                    log.info("开始切换死信文件（当前文件：{}）", oldDeadFile != null ? oldDeadFile.getAbsolutePath() : "无");

                    // 1. 释放旧的内存映射和Channel（核心：通过关闭Channel释放映射）
                    releaseOldResources();

                    // 2. 计算新文件的日期和索引
                    String today = getCurrentDate();
                    if (!Objects.equals(today, currentDeadFileDate)) {
                        currentDeadFileDate = today;
                        currentFileIndex = 0; // 日期变更，重置索引
                    } else {
                        currentFileIndex++; // 同一天，递增索引
                    }

                    // 3. 创建新的死信文件
                    currentDeadFile = createDeadFile(currentDeadFileDate, currentFileIndex);

                    // 4. 打开新文件的Channel并创建内存映射（关键：不提前关闭Channel）
                    currentDeadFileChannel = openFileChannel(currentDeadFile);
                    deadLogMappedBuffer = createMappedBuffer(currentDeadFileChannel, currentDeadFile);

                    log.info("死信文件切换完成，新文件：{}", currentDeadFile.getAbsolutePath());
                    return true;
                } catch (Exception e) {
                    log.error("切换死信文件失败（尝试切换至：{}）",
                            currentDeadFileDate + "-" + currentFileIndex, e);
                    // 切换失败时尝试恢复旧文件（避免管理器不可用）
                    tryRestoreOldFile(oldDeadFile);
                    return false;
                }
            }
        }

        /**
         * 检查是否需要切换文件
         * @param needByte 当前消息需要的字节数（用于精确判断剩余空间）
         * @return 是否需要切换
         */
        private boolean needSwitchFile(int needByte) {
            // 条件1：日期变更（跨天）
            if (!Objects.equals(getCurrentDate(), currentDeadFileDate)) {
                log.debug("死信文件需要切换：日期变更（当前：{}，目标：{}）", currentDeadFileDate, getCurrentDate());
                return true;
            }

            // 条件2：缓冲区未初始化 或 剩余空间不足当前消息
            synchronized (lock) {
                boolean spaceNotEnough = deadLogMappedBuffer == null
                        || deadLogMappedBuffer.remaining() < needByte;
                if (spaceNotEnough) {
                    log.debug("死信文件需要切换：剩余空间不足（需{}B，剩余{}B）",
                            needByte, deadLogMappedBuffer != null ? deadLogMappedBuffer.remaining() : 0);
                }
                return spaceNotEnough;
            }
        }

        /**
         * 初始化当前死信文件（首次启动或清理后调用）
         */
        private void initCurrentDeadFile() throws IOException {
            synchronized (lock) {
                String today = getCurrentDate();
                currentDeadFileDate = today;
                currentDeadFile = createDeadFile(today, 0);
                // 打开Channel并创建映射（确保Channel不提前关闭）
                currentDeadFileChannel = openFileChannel(currentDeadFile);
                deadLogMappedBuffer = createMappedBuffer(currentDeadFileChannel, currentDeadFile);
            }
        }

        /**
         * 创建死信文件（确保目录存在，文件不存在则创建）
         * @param date 日期（yyyyMMdd）
         * @param index 文件索引
         * @return 创建好的死信文件
         */
        private File createDeadFile(String date, int index) throws IOException {
            // 1. 确保死信目录存在（含父目录）
            File deadDir = new File(properties.getDeadLetterPath());
            if (!deadDir.exists()) {
                if (!deadDir.mkdirs()) { // mkdirs()创建多级目录
                    throw new IOException("无法创建死信目录（含父目录）：" + deadDir.getAbsolutePath());
                }
                log.debug("已创建死信目录：{}", deadDir.getAbsolutePath());
            }

            // 2. 生成文件名并创建文件
            String fileName = String.format("death-log-%s-%d.dat", date, index);
            File deadFile = new File(deadDir, fileName);
            if (!deadFile.exists()) {
                if (!deadFile.createNewFile()) {
                    throw new IOException("无法创建死信文件：" + deadFile.getAbsolutePath());
                }
                log.debug("已创建新死信文件：{}", deadFile.getAbsolutePath());
            }

            return deadFile;
        }

        /**
         * 打开文件的FileChannel（读写模式，用于创建内存映射）
         * @param file 目标文件
         * @return 打开的FileChannel（需手动关闭）
         */
        private FileChannel openFileChannel(File file) throws IOException {
            // 注意：RandomAccessFile需保持打开状态（关闭会导致Channel失效），由currentDeadFileChannel持有引用
            RandomAccessFile raf = new RandomAccessFile(file, "rw");
            FileChannel channel = raf.getChannel();
            log.debug("已打开死信文件Channel：{}（文件：{}）", channel, file.getAbsolutePath());
            return channel;
        }

        /**
         * 创建内存映射缓冲区（基于已打开的FileChannel，避免提前关闭）
         * @param channel 已打开的FileChannel
         * @param file 目标文件（用于日志打印）
         * @return 读写模式的MappedByteBuffer
         */
        private MappedByteBuffer createMappedBuffer(FileChannel channel, File file) throws IOException {
            long maxFileSize = properties.getDeadLetterFileMaxSize();
            // 1. 设置文件大小（确保映射长度与文件大小一致，避免扩容问题）
            try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
                raf.setLength(maxFileSize);
            }

            // 2. 创建内存映射（从0位置映射整个文件，读写模式）
            MappedByteBuffer buffer = channel.map(
                    FileChannel.MapMode.READ_WRITE,
                    0,
                    maxFileSize
            );

            log.debug("已创建死信文件内存映射：文件={}，映射大小={}B，缓冲区容量={}B",
                    file.getAbsolutePath(), maxFileSize, buffer.capacity());
            return buffer;
        }

        /**
         * 释放旧的资源（内存映射+FileChannel）
         * 核心：通过关闭Channel触发操作系统munmap，安全释放映射
         */
        private void releaseOldResources() {
            synchronized (lock) {
                // 1. 释放MappedByteBuffer：先刷盘，再置空
                if (deadLogMappedBuffer != null) {
                    try {
                        deadLogMappedBuffer.force(); // 最后一次刷盘，确保数据不丢失
                        log.debug("已强制刷盘旧死信缓冲区");
                    } catch (Exception e) {
                        log.warn("强制刷盘旧死信缓冲区失败", e);
                    } finally {
                        deadLogMappedBuffer = null; // 置空，帮助GC回收
                    }
                }

                // 2. 关闭FileChannel（核心：触发munmap释放映射）
                if (currentDeadFileChannel != null) {
                    try {
                        if (currentDeadFileChannel.isOpen()) {
                            currentDeadFileChannel.close();
                            log.debug("已关闭旧死信文件Channel");
                        }
                    } catch (IOException e) {
                        log.warn("关闭旧死信文件Channel失败", e);
                    } finally {
                        currentDeadFileChannel = null; // 置空，避免重复关闭
                    }
                }
            }
        }

        /**
         * 切换文件失败时，尝试恢复旧文件（避免管理器不可用）
         * @param oldFile 切换前的旧文件
         */
        private void tryRestoreOldFile(File oldFile) {
            if (oldFile == null || !oldFile.exists()) {
                log.warn("旧死信文件不存在，无法恢复");
                return;
            }

            try {
                log.info("开始恢复旧死信文件：{}", oldFile.getAbsolutePath());
                currentDeadFile = oldFile;
                currentDeadFileChannel = openFileChannel(oldFile);
                deadLogMappedBuffer = createMappedBuffer(currentDeadFileChannel, oldFile);
                currentDeadFileDate = getDateFromFileName(oldFile.getName()); // 从文件名提取日期
                currentFileIndex = getIndexFromFileName(oldFile.getName()); // 从文件名提取索引
                log.info("旧死信文件恢复成功：{}", oldFile.getAbsolutePath());
            } catch (Exception e) {
                log.error("恢复旧死信文件失败", e);
                // 恢复失败时清理，避免残留无效资源
                cleanup();
            }
        }

        /**
         * 从死信文件名提取日期（如：death-log-20240520-0.dat → 20240520）
         * @param fileName 死信文件名
         * @return 提取的日期（yyyyMMdd）
         */
        private String getDateFromFileName(String fileName) {
            // 文件名格式：death-log-yyyyMMdd-index.dat → 分割后取第2段
            String[] parts = fileName.split("-");
            if (parts.length < 3) {
                throw new IllegalArgumentException("无效的死信文件名格式：" + fileName);
            }
            return parts[2];
        }

        /**
         * 从死信文件名提取索引（如：death-log-20240520-0.dat → 0）
         * @param fileName 死信文件名
         * @return 提取的文件索引
         */
        private int getIndexFromFileName(String fileName) {
            // 文件名格式：death-log-yyyyMMdd-index.dat → 分割后取第3段的前缀数字
            String[] parts = fileName.split("-");
            if (parts.length < 3) {
                throw new IllegalArgumentException("无效的死信文件名格式：" + fileName);
            }
            String indexPart = parts[3].split("\\.")[0]; // 去掉后缀.dat
            return Integer.parseInt(indexPart);
        }

        /**
         * 获取当前日期（yyyyMMdd格式）
         * 复用DateTimeFormatter，避免重复创建对象
         */
        private String getCurrentDate() {
            return LocalDate.now().format(dateFormatter);
        }

        /**
         * 清理所有资源（应用关闭或管理器销毁时调用）
         * 确保内存映射和FileChannel被释放，避免泄漏
         */
        public void cleanup() {
            synchronized (lock) {
                log.info("开始清理死信文件管理器资源");
                releaseOldResources(); // 释放核心资源
                currentDeadFile = null;
                currentDeadFileDate = null;
                currentFileIndex = 0;
                log.info("死信文件管理器资源清理完成");
            }
        }

        /**
         * （可选）获取当前死信文件的只读视图（供外部读取，避免修改）
         * @return 只读的ByteBuffer，或null（若缓冲区未初始化）
         */
        public ByteBuffer getCurrentDeadFileReadOnlyBuffer() {
            synchronized (lock) {
                if (deadLogMappedBuffer == null) {
                    return null;
                }
                // 返回只读视图，外部无法修改内容
                return deadLogMappedBuffer.asReadOnlyBuffer();
            }
        }
    }

    /**
     * 重做日志管理器
     * 负责消息的持久化日志记录和双文件轮转
     */
    private class RedoLogManager {

        private final File logFile1;
        private final File logFile2;
        private volatile MappedByteBuffer currentMappedBuffer;
        private volatile File currentFile;
        private volatile FileChannel currentFileChannel;
        private final Object lock = new Object(); // 保证线程安全的同步锁

        public RedoLogManager() {
            try {
                // 确保日志目录存在
                File logDir = new File(properties.getLogPath());
                if (!logDir.exists() && !logDir.mkdirs()) {
                    throw new IOException("无法创建redo log目录: " + logDir.getAbsolutePath());
                }

                // 创建两个轮转文件
                logFile1 = new File(logDir, "redo-log-1.dat");
                logFile2 = new File(logDir, "redo-log-2.dat");
                ensureFileExists(logFile1);
                ensureFileExists(logFile2);

                // 初始化当前文件、Channel和映射（默认使用第一个文件）
                currentFile = logFile1;
                // 修正：创建映射时同时保存FileChannel
                currentFileChannel = openFileChannel(currentFile);
                currentMappedBuffer = createMappedBuffer(currentFileChannel, currentFile);

                log.info("Redo日志初始化完成，文件1: {}, 文件2: {}",
                        logFile1.getAbsolutePath(), logFile2.getAbsolutePath());
            } catch (IOException e) {
                // 初始化失败时清理资源，避免泄漏
                cleanup();
                throw new RuntimeException("初始化RedoLogManager失败", e);
            }
        }

        /**
         * 将消息写入redo log
         * @param message 待写入的消息字节数组
         * @return 是否写入成功
         */
        public boolean write(byte[] message) {
            if (message == null || message.length == 0) {
                log.warn("尝试写入空消息到redo log，已忽略");
                return false;
            }

            try {
                synchronized (lock) {
                    // 检查缓冲区是否可用（避免空指针或已关闭的情况）
                    if (currentMappedBuffer == null || !currentMappedBuffer.hasRemaining()) {
                        log.error("redo log缓冲区不可用，无法写入消息");
                        return false;
                    }
                    // 写入数据并强制刷盘（Redo Log核心：确保数据持久化，避免丢失）
                    currentMappedBuffer.put(message);

                    if (properties.getSaveStrategy() == 2){
                        // 强制刷盘
                        currentMappedBuffer.force();
                    }

                    log.trace("消息成功写入redo log（文件: {}，写入字节数: {}，剩余空间: {}）",
                            currentFile.getName(), message.length, currentMappedBuffer.remaining());
                    return true;
                }
            } catch (Exception e) {
                log.error("写入redo log失败", e);
                return false;
            }
        }

        /**
         * 判断是否可以写入指定大小的消息
         * @param needByte 需要的字节数
         * @return 是否可以写入
         */
        public boolean canWrite(int needByte) {
            if (needByte <= 0) {
                return true; // 空消息视为可写（实际写入时会过滤）
            }

            synchronized (lock) {
                return currentMappedBuffer != null && currentMappedBuffer.remaining() >= needByte;
            }
        }

        /**
         * 切换日志文件（双文件轮转）
         * @return 是否切换成功
         */
        public boolean switchFile() {
            synchronized (lock) {
                // 保存旧文件引用，用于日志打印（避免切换后被覆盖）
                File oldFile = currentFile;
                try {
                    log.info("开始切换redo log文件（当前文件: {}）", oldFile.getName());

                    // 1. 释放旧的内存映射和FileChannel
                    unmapBuffer();

                    // 2. 切换到另一个文件
                    currentFile = (currentFile == logFile1) ? logFile2 : logFile1;

                    // 3. 重置新文件大小（清空旧数据）
                    resetFileSize(currentFile);

                    // 4. 打开新文件的Channel并创建映射
                    currentFileChannel = openFileChannel(currentFile);
                    currentMappedBuffer = createMappedBuffer(currentFileChannel, currentFile);

                    log.info("redo log文件切换完成（新文件: {}，映射大小: {}MB）",
                            currentFile.getName(), properties.getMappedFileSize() / (1024 * 1024));
                    return true;
                } catch (Exception e) {
                    log.error("切换redo log文件失败（当前文件: {}）", oldFile.getName(), e);
                    // 切换失败时尝试恢复旧状态（避免资源不可用）
                    try {
                        currentFile = oldFile;
                        currentFileChannel = openFileChannel(oldFile);
                        currentMappedBuffer = createMappedBuffer(currentFileChannel, oldFile);
                    } catch (IOException recoverEx) {
                        log.error("切换失败后恢复旧文件状态也失败", recoverEx);
                    }
                    return false;
                }
            }
        }

        /**
         * 确保文件存在，不存在则创建
         */
        private void ensureFileExists(File file) throws IOException {
            if (!file.exists()) {
                // 父目录不存在时先创建父目录（避免单文件创建失败）
                File parentDir = file.getParentFile();
                if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
                    throw new IOException("无法创建redo log父目录: " + parentDir.getAbsolutePath());
                }
                if (!file.createNewFile()) {
                    throw new IOException("无法创建redo log文件: " + file.getAbsolutePath());
                }
                log.debug("已创建新的redo log文件: {}", file.getAbsolutePath());
            }
        }

        /**
         * 重置文件大小（切换时清空文件，确保从0开始写入）
         */
        private void resetFileSize(File file) throws IOException {
            // 单独使用RandomAccessFile重置大小（避免影响当前Channel）
            try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
                long targetSize = properties.getMappedFileSize();
                long oldSize = raf.length();
                if (oldSize != targetSize) {
                    raf.setLength(targetSize);
                    log.debug("重置redo log文件大小（文件: {}，旧大小: {}B，新大小: {}B）",
                            file.getName(), oldSize, targetSize);
                }
            }
        }

        /**
         * 打开文件的FileChannel（独立方法，便于复用和异常处理）
         */
        private FileChannel openFileChannel(File file) throws IOException {
            // 使用RandomAccessFile打开读写模式的Channel（支持mmap）
            RandomAccessFile raf = new RandomAccessFile(file, "rw");
            FileChannel channel = raf.getChannel();
            log.debug("已打开redo log文件Channel（文件: {}，Channel: {}）",
                    file.getName(), channel);
            return channel;
        }

        /**
         * 创建内存映射缓冲区（依赖已打开的FileChannel）
         */
        private MappedByteBuffer createMappedBuffer(FileChannel channel, File file) throws IOException {
            long fileSize = properties.getMappedFileSize();
            // 核心：通过Channel创建映射（READ_WRITE模式，从0位置映射整个文件大小）
            MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_WRITE, 0, fileSize);
            log.debug("已创建redo log内存映射（文件: {}，映射大小: {}B，缓冲区容量: {}B）",
                    file.getName(), fileSize, buffer.capacity());
            return buffer;
        }

        /**
         * 释放内存映射缓冲区（核心修正：通过关闭FileChannel触发映射释放）
         */
        private void unmapBuffer() {
            synchronized (lock) {
                // 1. 先处理MappedByteBuffer（强制刷盘，避免数据丢失）
                if (currentMappedBuffer != null) {
                    try {
                        currentMappedBuffer.force(); // 最后一次刷盘，确保所有数据持久化
                        log.debug("已强制刷盘redo log缓冲区（文件: {}）", currentFile.getName());
                    } catch (Exception e) {
                        log.warn("强制刷盘redo log缓冲区失败", e);
                    }
                    // 置空引用，帮助GC回收
                    currentMappedBuffer = null;
                }

                // 2. 关闭FileChannel（核心：Channel关闭时会触发操作系统munmap，释放映射）
                if (currentFileChannel != null) {
                    try {
                        if (currentFileChannel.isOpen()) {
                            currentFileChannel.close();
                            log.debug("已关闭redo log文件Channel（文件: {}）", currentFile.getName());
                        }
                    } catch (IOException e) {
                        log.warn("关闭redo log文件Channel失败", e);
                    } finally {
                        // 无论关闭成功与否，都置空引用（避免重复操作）
                        currentFileChannel = null;
                    }
                }

                log.debug("已完成redo log内存映射释放（文件: {}）", currentFile.getName());
            }
        }

        /**
         * 获取剩余可用空间（字节数）
         */
        public int getRemainingSpace() {
            synchronized (lock) {
                return currentMappedBuffer != null ? currentMappedBuffer.remaining() : 0;
            }
        }

        /**
         * 获取当前映射的缓冲区（仅供读取，避免外部修改）
         */
        public ByteBuffer getCurrentMappedBuffer() {
            synchronized (lock) {
                // 返回只读视图，避免外部修改缓冲区内容
                return currentMappedBuffer != null ? currentMappedBuffer.asReadOnlyBuffer() : null;
            }
        }

        /**
         * 清理所有资源（JVM退出或管理器销毁时调用）
         */
        public void cleanup() {
            synchronized (lock) {
                log.info("开始清理RedoLogManager资源");
                // 释放映射和Channel
                unmapBuffer();
                // 置空所有引用，帮助GC回收
                currentFile = null;
                log.info("RedoLogManager资源清理完成");
            }
        }
    }


    /**
     * 初始化服务（依赖注入完成后执行）
     */
    @PostConstruct
    public void init() {
        try {
            // 1. 初始化配置相关组件
            initConfigRelatedComponents();
            // 2. 初始化日志管理器
            deadFileLogManager = new DeadFileLogManager();
            redoLogManager = new RedoLogManager();
            // 3. 启动消息处理线程
            startMessageProcessThread();
            // 4. 启动定时批量写入任务
            startScheduledBatchWriteTask();

            log.info("异步消息存储服务初始化完成！配置信息：{}", properties);
        } catch (Exception e) {
            log.error("异步消息存储服务初始化失败，服务将终止", e);
            destroy(); // 初始化失败时主动清理资源
            throw new RuntimeException("服务初始化异常", e);
        }
    }

    /**
     * 提交消息到队列（带成功/失败回调，确保业务感知结果）
     * @param message 待存储消息（必须包含合法 messageId）
     * @param success 持久化成功回调（执行在工作线程，避免阻塞业务线程）
     * @param failure 持久化失败回调（含具体失败原因）
     * @return true：消息提交到队列成功；false：提交失败
     */
    public boolean submit(ChatMessage message, Runnable success, Runnable failure) {
        // 1. 校验服务状态
        if (!isRunning.get()) {
            return false;
        }
        // 2. 校验消息合法性
        if (message.getSessionId() == null || message.getMessageId() == null) {
            return false;
        }
        // 3. 非阻塞提交到队列
        boolean offerSuccess = messageQueue.offer(message);
        String key = buildKey(message);

        if (offerSuccess) {
            if (success != null) {
                successCallBack.put(key, success);
            }
            if (failure != null) {
                failedCallBack.put(key, failure);
            }
            log.debug("消息提交队列成功 sessionId: {}, messageId: {}, 当前队列大小: {}）",
                    message.getSessionId(), message.getMessageId(), messageQueue.size());
            return true;
        } else {
            log.error("消息提交失败：队列已满 sessionId: {}, messageId: {}，队列容量：{}",
                    message.getSessionId(), message.getMessageId(), properties.getMaxQueueCapacity());
            return false;
        }
    }

    /**
     * 处理队列中的消息（循环阻塞获取，直到服务停止）
     */
    private void processMessages() {
        log.info("消息处理线程启动（线程名：{}）", Thread.currentThread().getName());
        while (isRunning.get()) {
            try {
                // 阻塞获取消息（队列空时自动等待，避免CPU空转）
                ChatMessage message = messageQueue.take();
                processSingleMessage(message); // 处理单条消息（持久化+缓存）
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // 保留中断标记
                log.info("消息处理线程被中断（线程名：{}）", Thread.currentThread().getName());
                break; // 中断后退出循环
            } catch (Exception e) {
                log.error("消息处理异常，将继续处理下一条消息", e);
            }
        }
        log.info("消息处理线程停止（线程名：{}）", Thread.currentThread().getName());
    }


    /**
     * 处理单条消息（写入内存映射文件+内存缓存，确保持久化）
     */
    private void processSingleMessage(ChatMessage message) throws IOException {
        assert message.getSessionId() != null && message.getMessageId() != null;
        String key = buildKey(message);
        try {
            // 1. 序列化消息（调用 ChatMessage 的 toLogString，含 content Base64 编码）
            String msgLogStr = message.toLogString() + System.lineSeparator(); // 跨系统换行符
            byte[] data = msgLogStr.getBytes(StandardCharsets.UTF_8);

            // 2. 检查映射文件空间，不足则切换文件
            synchronized (redoLogManager) {
                if (!redoLogManager.canWrite(data.length)){
                    redoLogManager.switchFile();
                    batchWriteToDb();
                }
            }
            if (properties.getSaveStrategy() > 0){
                if (!redoLogManager.write(data)){
                    throw new RuntimeException("映射文件写入失败");
                };
            }

            if (!memoryCache.add(message)){
                throw new RuntimeException("内存缓存，写入失败");
            };

            // 5. 持久化成功回调（持久化成功，则已保证消息可靠性）
            executeSuccessCallback(key);

            log.debug("消息处理完成（sessionId: {}, messageId: {}，已写入映射文件+内存缓存）",
                    message.getSessionId(), message.getMessageId());
        } catch (Exception e) {
            log.error("处理单条消息失败（messageId: {}）", message.getMessageId(), e);
            executeFailureCallback(key);
            deadFileLogManager.write(message);
        } finally {
            cleanUpCallbacks(key);
        }
    }

    /**
     * 批量写入数据库（线程安全，失败重试+死信降级）
     */
    private void batchWriteToDb() {
        List<ChatMessage> toSave;

        // 1. 原子性提取内存缓存数据（避免多线程并发修改）
        synchronized (memoryCache) {
            if (memoryCache.isEmpty()) {
                log.trace("批量写入数据库：内存缓存为空，跳过");
                return;
            }
            toSave = new ArrayList<>(memoryCache); // 转移数据，避免直接操作原列表
            memoryCache.clear();
        }

        workExecutor.submit(()->{
            long start = System.currentTimeMillis();
            try {
                chatMessageMapper.batchInsert(toSave);
                log.info("批量写入数据库成功（条数：{}，耗时：{}ms，messageId范围：{}-{}）",
                        toSave.size(), System.currentTimeMillis() - start,
                        toSave.get(0).getMessageId(), toSave.get(toSave.size() - 1).getMessageId());
            } catch (Exception e) {
                log.error("批量写入数据库失败（条数：{}，将重试）", toSave.size(), e);
                // 失败处理：可将失败消息写入死信队列
                toSave.forEach(msg -> deadFileLogManager.write(msg));
            }
        });
    }

    /**
     * 服务销毁（清理线程池、映射文件、缓存等资源）
     */
    @PreDestroy
    public void destroy() {
        if (!isRunning.compareAndSet(true, false)) {
            log.warn("服务已处于停止状态，无需重复销毁");
            return;
        }

        log.debug("开始销毁异步消息存储服务...");

        // 1. 停止线程池（等待剩余任务完成，避免任务丢失）
        stopThreadPool(workExecutor, "工作线程池");
        stopThreadPool(scheduler, "定时线程池");

        // 2. 清理管理器资源
        if (deadFileLogManager != null) {
            deadFileLogManager.cleanup();
        }
        if (redoLogManager != null) {
            redoLogManager.cleanup();
        }

        // 3. 最后一次批量写入数据库（确保缓存数据不丢失）
        batchWriteToDb();

        log.debug("异步消息存储服务已停止，资源已清理");
    }


    // ------------------------------ 辅助方法 ------------------------------

    /**
     * 初始化配置相关组件（队列、缓存、线程池）
     */
    private void initConfigRelatedComponents() {
        // 消息队列（容量由配置决定）
        messageQueue = new LinkedBlockingQueue<>(properties.getMaxQueueCapacity());

        // 工作线程池
        workExecutor = new ThreadPoolExecutor(
                properties.getThreadPoolSize(),
                properties.getMaxThreadPoolSize(),
                60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(properties.getMaxQueueCapacity()),
                new NamedThreadFactory("async-msg-worker")
        );

        // 允许核心线程超时，在空闲时回收
        workExecutor.allowCoreThreadTimeOut(true);

        // 定时线程池
        scheduler = Executors.newSingleThreadScheduledExecutor(
                new NamedThreadFactory("async-msg-scheduler")
        );
    }

    /**
     * 启动消息处理线程
     */
    private void startMessageProcessThread() {
        Thread processThread = new Thread(this::processMessages, "async-msg-processor");
        processThread.setDaemon(false);
        processThread.start();
        log.debug("消息处理线程已启动");
    }

    /**
     * 启动定时批量写入任务（作为批量阈值触发的补充）
     */
    private void startScheduledBatchWriteTask() {
        // 定时执行，确保即使消息量少也能定期写入数据库
        scheduler.scheduleAtFixedRate(() -> {
            try {
                if (!memoryCache.isEmpty()) {
                    log.debug("定时任务触发批量写入数据库（当前缓存大小：{}）", memoryCache.size());
                    batchWriteToDb();
                }
            } catch (Exception e) {
                log.error("定时批量写入任务执行异常", e);
            }
        }, properties.getScheduledInterval(), properties.getScheduledInterval(), TimeUnit.SECONDS);

        log.info("定时批量写入任务已启动，间隔：{}秒", properties.getScheduledInterval());
    }

    /**
     * 停止线程池
     */
    private void stopThreadPool(ExecutorService executor, String name) {
        if (executor == null) {
            return;
        }

        try {
            log.info("开始停止{}...", name);
            executor.shutdown(); // 禁止新任务提交

            // 等待任务完成，超时则强制关闭
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                log.warn("{}停止超时，将强制关闭", name);
                List<Runnable> remaining = executor.shutdownNow();
                log.warn("{}强制关闭后，仍有{}个任务未执行", name, remaining.size());
            }
            log.info("{}已停止", name);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("停止{}时被中断", name, e);
        }
    }

    /**
     * 构建消息唯一标识
     */
    private String buildKey(ChatMessage message) {
        return message.getSessionId() + ":" + message.getMessageId();
    }

    /**
     * 执行成功回调
     */
    private void executeSuccessCallback(String key) {
        Runnable callback = successCallBack.get(key);
        if (callback != null) {
            try {
                workExecutor.submit(callback);
            } catch (Exception e) {
                log.error("执行成功回调失败", e);
            }
        }
    }

    /**
     * 执行失败回调
     */
    private void executeFailureCallback(String key) {
        Runnable callback = failedCallBack.get(key);
        if (callback != null) {
            try {
                workExecutor.submit(callback);
            } catch (Exception e) {
                log.error("执行失败回调失败", e);
            }
        }
    }

    /**
     * 清理回调
     */
    private void cleanUpCallbacks(String key) {
        successCallBack.remove(key);
        failedCallBack.remove(key);
    }

    /**
     * 命名线程工厂，便于线程跟踪
     */
    private static class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger counter = new AtomicInteger(1);

        public NamedThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setName(prefix + "-" + counter.getAndIncrement());
            thread.setDaemon(false); // 非守护线程
            return thread;
        }
    }
}