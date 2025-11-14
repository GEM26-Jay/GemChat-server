package com.zcj.servicenetty.handler;

import com.zcj.servicenetty.entity.Protocol;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

public class ProtocolFrameDecoder extends LengthFieldBasedFrameDecoder {
    // 最大消息长度（考虑到length是int类型）
    private static final int MAX_FRAME_LENGTH = Integer.MAX_VALUE;
    // length字段的偏移量
    private static final int LENGTH_FIELD_OFFSET = Protocol.LengthFiledBias;
    // length字段本身占用4字节（int类型）
    private static final int LENGTH_FIELD_LENGTH = 4;
    // 长度调整值：0，因为length已经准确表示消息体长度
    private static final int LENGTH_ADJUSTMENT = 0;
    // 需要跳过的初始字节数：0（我们需要验证魔数）
    private static final int INITIAL_BYTES_TO_STRIP = 0;

    public ProtocolFrameDecoder() {
        super(MAX_FRAME_LENGTH, LENGTH_FIELD_OFFSET, LENGTH_FIELD_LENGTH,
                LENGTH_ADJUSTMENT, INITIAL_BYTES_TO_STRIP);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {

        // 1. 调用父类解码获取完整帧（父类已处理缓冲区引用计数）
        ByteBuf frame = (ByteBuf) super.decode(ctx, in);
        if (frame == null) {
            return null; // 帧不完整
        }

        try {
            // 2. 验证魔数（读取前先检查缓冲区是否有足够字节）
            if (frame.readableBytes() < 2) {
                ctx.close();
                return null;
            }
            int magic = frame.readShort();
            if (magic != Protocol.MAGIC_NUMBER) {
                ctx.close(); // 魔数不匹配，关闭连接
                return null;
            }

            // 3. 重置读指针，确保fromBuffer能从头读取
            frame.resetReaderIndex();

            // 4. 转换为Protocol对象（确保fromBuffer内部不会释放frame）
            return Protocol.fromBuffer(frame);
        } finally {
            // 5. 释放frame（父类返回的frame需要手动释放，避免泄漏）
            frame.release();
        }
    }
}
