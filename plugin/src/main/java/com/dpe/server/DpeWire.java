package com.dpe.server;

import com.dpe.common.protocol.Message;
import com.dpe.common.protocol.MessageCodec;

import java.nio.charset.StandardCharsets;

/**
 * dpe:msg 插件消息通道的线格式编解码。
 *
 * <p>与 mod 端 {@code ClientNetworking.DpePayload} 保持一致：mod 使用
 * {@code PacketByteBuf.writeString/readString} 编码字符串，即「varint 长度前缀 + UTF-8 字节」。
 * 因此 Paper 侧发送也按此格式拼装，接收时优先按 varint 长度前缀读取；
 * 为兼容裸 UTF-8 JSON 负载，解码失败时回退为整体 UTF-8 解析。</p>
 */
final class DpeWire {

    private DpeWire() {
    }

    /** 编码 Message 为 dpe:msg 通道字节（varint 长度 + UTF-8 JSON）。 */
    static byte[] encode(Message msg) {
        byte[] body = MessageCodec.toJson(msg).getBytes(StandardCharsets.UTF_8);
        byte[] header = varIntEncode(body.length);
        byte[] out = new byte[header.length + body.length];
        System.arraycopy(header, 0, out, 0, header.length);
        System.arraycopy(body, 0, out, header.length, body.length);
        return out;
    }

    /** 解码 dpe:msg 通道字节为 Message；兼容 varint 前缀与裸 UTF-8 JSON。 */
    static Message decode(byte[] data) {
        return MessageCodec.fromJson(readStringCompat(data));
    }

    /** 先按 varint 长度前缀读取（mod 格式）；不匹配则回退整体 UTF-8 JSON。 */
    private static String readStringCompat(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }
        try {
            int[] cursor = {0};
            int len = readVarInt(data, cursor);
            if (len >= 0 && cursor[0] + len <= data.length) {
                return new String(data, cursor[0], len, StandardCharsets.UTF_8);
            }
        } catch (RuntimeException ignored) {
            // 回退到裸 UTF-8
        }
        return new String(data, StandardCharsets.UTF_8);
    }

    private static byte[] varIntEncode(int value) {
        byte[] tmp = new byte[5];
        int count = 0;
        while (true) {
            if ((value & ~0x7F) == 0) {
                tmp[count++] = (byte) value;
                break;
            }
            tmp[count++] = (byte) ((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        byte[] out = new byte[count];
        System.arraycopy(tmp, 0, out, 0, count);
        return out;
    }

    private static int readVarInt(byte[] data, int[] cursor) {
        int result = 0;
        int shift = 0;
        int i = cursor[0];
        while (true) {
            if (i >= data.length) {
                throw new IllegalArgumentException("varint underflow");
            }
            byte b = data[i++];
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                cursor[0] = i;
                return result;
            }
            shift += 7;
            if (shift >= 35) {
                throw new IllegalArgumentException("varint too large");
            }
        }
    }
}
