package com.zcj.servicenetty.entity;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;

/**
 * 自定义协议实体类，用于网络通信中数据的封装与解析
 */
@Slf4j
@Data
public class Protocol implements AutoCloseable {

    // 命令类型常量定义（高16位：系统命令）
    public static final int ORDER_SYSTEM_PUSH = 1 << 16;  // 系统推送命令
    public static final int ORDER_AUTH = 2 << 16;         // 认证命令
    public static final int ORDER_SYNC = 3 << 16;         // 同步命令
    public static final int ORDER_HEART = 4 << 16;        // 心跳命令
    public static final int ORDER_MESSAGE = 5 << 16;      // 消息命令

    // 内容类型（低16位：消息载体类型）
    public static final int CONTENT_FAILED_INFO = -1;     // 发送失败应答
    public static final int CONTENT_TEXT = 1;          // 文本消息
    public static final int CONTENT_IMAGE = 2;         // 图片消息
    public static final int CONTENT_FILE = 3;          // 文件消息
    public static final int CONTENT_VOICE = 4;         // 语音消息
    public static final int CONTENT_VIDEO = 5;         // 视频消息
    public static final int CONTENT_LOCATION = 6;      // 位置消息
    public static final int CONTENT_ACK = 99;              // 响应信号

    // 协议魔数常量（short类型，2字节，用于数据包合法性校验）
    public static final short MAGIC_NUMBER = (short) 0xBABE;

    // 协议版本号（short类型，2字节），默认值为 1
    private short version = 1;
    // 消息命令类型（高16位+中8位+低8位组合，int类型4字节）
    private int type;
    // 发送方唯一标识（long类型8字节）
    private long fromId = 0;
    // 接收方唯一标识（long类型8字节）
    private long toId = 0;
    // 消息时间戳（long类型8字节）
    private long timeStamp = 0;
    // 消息体字节长度（short类型2字节）
    private short length;
    // 消息体内容（改用 byte[] 存储，避免 ByteBuf 引用计数问题）
    private byte[] content;


    /**
     * 计算消息体长度并更新 length 字段
     * （byte[] 长度直接作为消息体长度，无需依赖可读字节数）
     */
    public void calculateLength() {
        this.length = (short) (content == null ? 0 : content.length);
    }

    /**
     * 获取消息体的字符串表示形式（线程安全，无引用计数问题）
     */
    public String getMessageString() {
        if (content == null || content.length == 0) {
            return "";
        }
        // byte[] 转字符串，直接使用 UTF-8 编码
        return new String(content, StandardCharsets.UTF_8);
    }

    /**
     * 通过字符串设置消息体内容，自动转换为 UTF-8 编码的 byte[]
     */
    public void setContent(String content) {
        if (content != null && !content.isEmpty()) {
            this.content = content.getBytes(StandardCharsets.UTF_8);
        } else {
            this.content = new byte[0]; // 空内容用空数组表示，避免 null
        }
        calculateLength(); // 同步更新长度
    }

    /**
     * 获取消息体的字节数组形式（直接返回副本，避免外部修改内部数组）
     * （byte[] 是值类型，返回副本可保证内部数据安全性）
     */
    public byte[] getMessageBytes() {
        if (content == null) {
            return new byte[0];
        }
        // 创建副本返回，避免外部修改影响内部数据
        byte[] copy = new byte[content.length];
        System.arraycopy(content, 0, copy, 0, content.length);
        return copy;
    }

    /**
     * 通过字节数组设置消息体内容（接收外部 byte[]，存储副本避免外部修改）
     */
    public void setContent(byte[] message) {
        if (message != null && message.length > 0) {
            // 复制外部数组，避免外部修改影响内部数据
            this.content = new byte[message.length];
            System.arraycopy(message, 0, this.content, 0, message.length);
        } else {
            this.content = new byte[0];
        }
        calculateLength(); // 同步更新长度
    }

    /**
     * 通过 Netty ByteBuf 设置消息体内容（兼容原 Netty 交互逻辑）
     */
    public void setContent(ByteBuf contentBuf) {
        if (contentBuf == null || !contentBuf.isReadable()) {
            this.content = new byte[0];
        } else {
            // 从 ByteBuf 中读取所有可读字节到 byte[]
            this.content = new byte[contentBuf.readableBytes()];
            contentBuf.readBytes(this.content); // 读取后不影响原 Buf 引用计数（仅读数据）
        }
        calculateLength(); // 同步更新长度
    }

    /**
     * 给消息体添加内容（ByteBuf 版本，兼容原逻辑）
     */
    public void appendContent(ByteBuf data) {
        if (data == null || !data.isReadable()) {
            return;
        }
        // 先将 ByteBuf 转为 byte[]
        byte[] dataBytes = new byte[data.readableBytes()];
        data.readBytes(dataBytes);
        appendContent(dataBytes); // 调用 byte[] 版本的追加方法
    }

    /**
     * 给消息体添加内容（字符串版本）
     */
    public void appendContent(String data) {
        if (data == null || data.isEmpty()) {
            return;
        }
        // 字符串转 byte[] 后追加
        appendContent(data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 给消息体添加内容（字节数组版本，核心追加逻辑）
     */
    public void appendContent(byte[] data) {
        if (data == null || data.length == 0) {
            return;
        }

        // 原内容为空：直接用新数据作为内容
        if (this.content == null || this.content.length == 0) {
            setContent(data);
            return;
        }

        // 原内容非空：合并两个数组（原内容 + 新数据）
        byte[] merged = new byte[this.content.length + data.length];
        System.arraycopy(this.content, 0, merged, 0, this.content.length); // 复制原内容
        System.arraycopy(data, 0, merged, this.content.length, data.length); // 复制新数据
        this.content = merged;

        calculateLength(); // 同步更新长度
    }

    /**
     * 将协议对象序列化为 Netty ByteBuf（供网络发送）
     * （byte[] 转 ByteBuf，仅在发送时临时创建 Buf，避免长期持有）
     */
    public ByteBuf toBuffer(ByteBuf buf) {
        calculateLength(); // 确保长度是最新的

        // 写入协议固定字段（魔数、版本、类型等）
        buf.writeShort(MAGIC_NUMBER);
        buf.writeShort(version);
        buf.writeInt(type);
        buf.writeLong(fromId);
        buf.writeLong(toId);
        buf.writeLong(timeStamp);
        buf.writeShort(length);

        // 写入消息体（byte[] 直接写入 Buf）
        if (length > 0 && content != null) {
            buf.writeBytes(content);
        }

        return buf;
    }

    /**
     * 从 Netty ByteBuf 反序列化为 Protocol 对象（供接收时解析）
     * （从 Buf 中读取字节到 byte[]，脱离 Buf 依赖）
     */
    public static Protocol fromBuffer(ByteBuf buf) {
        // 验证魔数（确保是合法协议包）
        short magic = buf.readShort();
        if (magic != MAGIC_NUMBER) {
            throw new IllegalArgumentException(
                    String.format("协议魔数验证失败，预期: 0x%04X, 实际: 0x%04X", MAGIC_NUMBER, magic)
            );
        }

        Protocol protocol = new Protocol();

        // 读取固定字段
        protocol.setVersion(buf.readShort());
        protocol.setType(buf.readInt());
        protocol.setFromId(buf.readLong());
        protocol.setToId(buf.readLong());
        protocol.setTimeStamp(buf.readLong());
        short length = buf.readShort();
        protocol.setLength(length);

        // 读取消息体（从 Buf 中读取指定长度的字节到 byte[]）
        if (length > 0) {
            if (length > buf.readableBytes()) {
                throw new IllegalArgumentException(
                        String.format("消息体长度异常，声明: %d, 可用字节: %d", length, buf.readableBytes())
                );
            }
            // 读取 length 个字节到 byte[]
            byte[] contentBytes = new byte[length];
            buf.readBytes(contentBytes);
            protocol.setContent(contentBytes);
        } else {
            protocol.setContent(new byte[0]); // 空内容用空数组
        }

        return protocol;
    }

    /**
     * 安全释放资源（byte[] 无引用计数，仅做空值处理，兼容原 AutoCloseable 接口）
     */
    public void releaseContent() {
        this.content = null; // 置空让 GC 回收，无特殊资源需要释放
    }

    /**
     * 判断当前 type 是否包含目标标志
     */
    public boolean hasType(int target) {
        return (type & target) == target;
    }

    /**
     * 获取内容类型（低16位）
     */
    public int getContentType() {
        return this.type & 0x0000FFFF;
    }

    /**
     * 获取命令类型（高16位）
     */
    public int getOrderType() {
        return this.type & 0xFFFF0000;
    }

    /**
     * 设置命令类型和内容类型
     */
    public void setType(int orderType, int contentType) {
        this.type = (orderType & 0xFFFF0000) | (contentType & 0x0000FFFF);
    }

    /**
     * 重写 toString，避免打印大内容时的性能问题
     */
    @Override
    public String toString() {
        return "Protocol{" +
                "version=" + version +
                ", type=0x" + Integer.toHexString(type) +
                ", fromId=" + fromId +
                ", toId=" + toId +
                ", timeStamp=" + timeStamp +
                ", length=" + length +
                ", contentLength=" + (content == null ? 0 : content.length) + // 只打印长度，不打印具体内容
                ", orderType=0x" + Integer.toHexString(getOrderType()) +
                ", contentType=0x" + Integer.toHexString(getContentType()) +
                '}';
    }

    /**
     * AutoCloseable 接口实现（兼容原资源释放逻辑，无实际操作）
     */
    @Override
    public void close() throws Exception {
        releaseContent();
    }
}