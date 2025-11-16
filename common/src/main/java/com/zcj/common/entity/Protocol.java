package com.zcj.common.entity;

import io.netty.buffer.ByteBuf;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;

/**
 * 自定义协议实体类，用于网络通信中数据的封装与解析
 */
@Slf4j
@Data
public class Protocol {

    // 命令类型常量定义（高16位：系统命令）
    public static final int ORDER_SYSTEM = 1 << 16;       // 系统推送
    public static final int ORDER_AUTH = 2 << 16;         // 认证命令
    public static final int ORDER_SYNC = 3 << 16;         // 同步命令
    public static final int ORDER_MESSAGE = 4 << 16;      // 消息命令
    public static final int ORDER_ACK = 5 << 16;          // 消息响应

    // 内容类型（低16位：消息载体类型）
    public static final int CONTENT_FAILED = -1;       // 失败响应
    public static final int CONTENT_EMPTY = 0;         // 空消息(无意义)
    public static final int CONTENT_TEXT = 1;          // 字符串文本
    public static final int CONTENT_IMAGE = 2;         // 图片消息
    public static final int CONTENT_AUDIO = 3;         // 语音消息
    public static final int CONTENT_VIDEO = 4;         // 视频消息
    public static final int CONTENT_OTHER_FILE = 5;    // 其他文件类型
    public static final int CONTENT_LOCATION = 6;      // 位置消息

    // 长度字段偏移量：魔数(2) + 版本(2) + 类型(4) + fromId(8) + identityId(8) + sessionId(8) + messageId(8) + 时间戳(8) + 长度(4)
    public static final int LengthFiledBias = 2 + 2 + 4 + 8 + 8 + 8 + 8 + 8;

    // 协议魔数常量（short类型，2字节，用于数据包合法性校验）
    public static final short MAGIC_NUMBER = (short) 0xBABE;
    // 协议版本号（short类型，2字节），默认值为 1
    private short version = 1;

    // 消息命令类型（高16位+低16位组合，int类型4字节）
    private int type;
    // 发送方唯一标识（long类型8字节）
    private long fromId = 0;
    // 消息标识
    private long identityId = 0;
    // 目标会话ID
    private long sessionId = 0;
    // 消息ID
    private long messageId = 0;
    // 消息时间戳（long类型8字节）
    private long timeStamp = 0;
    // 消息体字节长度（int类型4字节）
    private int length;
    // 消息体内容
    private byte[] content;

    /**
     * 计算消息体长度并更新 length 字段
     */
    public void calculateLength() {
        this.length = (content == null ? 0 : content.length);
    }

    /**
     * 获取消息体的字符串表示形式
     */
    public String getMessageString() {
        if (content == null || content.length == 0) {
            return "";
        }
        return new String(content, StandardCharsets.UTF_8);
    }

    /**
     * 通过字符串设置消息体内容
     */
    public void setContent(String content) {
        if (content != null && !content.isEmpty()) {
            this.content = content.getBytes(StandardCharsets.UTF_8);
        } else {
            this.content = new byte[0];
        }
        calculateLength();
    }

    /**
     * 获取消息体的字节数组形式
     */
    public byte[] getMessageBytes() {
        if (content == null) {
            return new byte[0];
        }
        byte[] copy = new byte[content.length];
        System.arraycopy(content, 0, copy, 0, content.length);
        return copy;
    }

    /**
     * 通过字节数组设置消息体内容
     */
    public void setContent(byte[] message) {
        if (message != null && message.length > 0) {
            this.content = new byte[message.length];
            System.arraycopy(message, 0, this.content, 0, message.length);
        } else {
            this.content = new byte[0];
        }
        calculateLength();
    }

    /**
     * 通过 Netty ByteBuf 设置消息体内容
     */
    public void setContent(ByteBuf contentBuf) {
        if (contentBuf == null || !contentBuf.isReadable()) {
            this.content = new byte[0];
        } else {
            this.content = new byte[contentBuf.readableBytes()];
            contentBuf.readBytes(this.content);
        }
        calculateLength();
    }

    /**
     * 给消息体添加内容（ByteBuf 版本）
     */
    public void appendContent(ByteBuf data) {
        if (data == null || !data.isReadable()) {
            return;
        }
        byte[] dataBytes = new byte[data.readableBytes()];
        data.readBytes(dataBytes);
        appendContent(dataBytes);
    }

    /**
     * 给消息体添加内容（字符串版本）
     */
    public void appendContent(String data) {
        if (data == null || data.isEmpty()) {
            return;
        }
        appendContent(data.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 给消息体添加内容（字节数组版本）
     */
    public void appendContent(byte[] data) {
        if (data == null || data.length == 0) {
            return;
        }
        if (this.content == null || this.content.length == 0) {
            setContent(data);
            return;
        }
        byte[] merged = new byte[this.content.length + data.length];
        System.arraycopy(this.content, 0, merged, 0, this.content.length);
        System.arraycopy(data, 0, merged, this.content.length, data.length);
        this.content = merged;
        calculateLength();
    }

    /**
     * 将协议对象序列化为 Netty ByteBuf
     */
    public ByteBuf toBuffer(ByteBuf buf) {
        calculateLength();
        buf.writeShort(MAGIC_NUMBER);
        buf.writeShort(version);
        buf.writeInt(type);
        buf.writeLong(fromId);
        buf.writeLong(identityId);  // 修复：写入identityId
        buf.writeLong(sessionId);
        buf.writeLong(messageId);
        buf.writeLong(timeStamp);
        buf.writeInt(length);  // 修复：长度字段改为int类型

        if (length > 0 && content != null) {
            buf.writeBytes(content);
        }
        return buf;
    }

    /**
     * 从 Netty ByteBuf 反序列化为 Protocol 对象
     */
    public static Protocol fromBuffer(ByteBuf buf) {
        short magic = buf.readShort();
        if (magic != MAGIC_NUMBER) {
            throw new IllegalArgumentException(
                    String.format("协议魔数验证失败，预期: 0x%04X, 实际: 0x%04X", MAGIC_NUMBER, magic)
            );
        }

        Protocol protocol = new Protocol();
        protocol.setVersion(buf.readShort());
        protocol.setType(buf.readInt());
        protocol.setFromId(buf.readLong());
        protocol.setIdentityId(buf.readLong());  // 修复：读取identityId
        protocol.setSessionId(buf.readLong());
        protocol.setMessageId(buf.readLong());
        protocol.setTimeStamp(buf.readLong());
        int length = buf.readInt();  // 修复：长度字段改为int类型
        protocol.setLength(length);

        if (length > 0) {
            if (length > buf.readableBytes()) {
                throw new IllegalArgumentException(
                        String.format("消息体长度异常，声明: %d, 可用字节: %d", length, buf.readableBytes())
                );
            }
            byte[] contentBytes = new byte[length];
            buf.readBytes(contentBytes);
            protocol.setContent(contentBytes);
        } else {
            protocol.setContent(new byte[0]);
        }

        return protocol;
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
     * 重写 toString
     */
    @Override
    public String toString() {
        return "Protocol{" +
                "version=" + version +
                ", type=0x" + Integer.toHexString(type) +
                ", fromId=" + fromId +
                ", identityId=" + identityId +  // 修复：显示identityId
                ", sessionId=" + sessionId +    // 修复：显示sessionId
                ", messageId=" + messageId +
                ", timeStamp=" + timeStamp +
                ", length=" + length +
                ", contentLength=" + (content == null ? 0 : content.length) +
                ", orderType=0x" + Integer.toHexString(getOrderType()) +
                ", contentType=0x" + Integer.toHexString(getContentType()) +
                '}';
    }
}
