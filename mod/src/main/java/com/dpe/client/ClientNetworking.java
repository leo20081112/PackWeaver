package com.dpe.client;

import com.dpe.common.protocol.Message;
import com.dpe.common.protocol.MessageCodec;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.function.Consumer;

/**
 * 客户端网络封装：注册自定义 Payload，提供 send/receive。
 * 负载为 {@link MessageCodec} 编码的 JSON 字符串。
 */
public final class ClientNetworking {

    /** DPE 通道标识。 */
    public static final String CHANNEL_NAMESPACE = "dpe";
    public static final String CHANNEL_PATH = "msg";

    /** 自定义 Payload，承载 JSON 字符串。 */
    public record DpePayload(String json) implements CustomPayload {
        public static final CustomPayload.Id<DpePayload> ID =
                new CustomPayload.Id<>(Identifier.of(CHANNEL_NAMESPACE, CHANNEL_PATH));

        /** 用于编解码的 PacketCodec：构造器解码，write 实例方法编码。 */
        public static final PacketCodec<PacketByteBuf, DpePayload> CODEC =
                CustomPayload.codecOf(DpePayload::write, DpePayload::new);

        /** 解码构造器。允许较大字符串以容纳完整编辑器状态。 */
        public DpePayload(PacketByteBuf buf) {
            this(buf.readString(1_000_000));
        }

        /** 编码实例方法。 */
        public void write(PacketByteBuf buf) {
            buf.writeString(json == null ? "" : json);
        }

        @Override
        public CustomPayload.Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    private static Consumer<Message> receiver;

    private ClientNetworking() {
    }

    /** 注册 C2S / S2C Payload 类型。应在 onInitializeClient 中调用。 */
    public static void register() {
        PayloadTypeRegistry.playC2S().register(DpePayload.ID, DpePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(DpePayload.ID, DpePayload.CODEC);
    }

    /** 注册 S2C 接收器，收到消息后回调。 */
    public static void registerReceiver(Consumer<Message> callback) {
        receiver = callback;
        ClientPlayNetworking.registerGlobalReceiver(DpePayload.ID, (payload, context) -> {
            try {
                Message msg = MessageCodec.fromJson(payload.json());
                Consumer<Message> r = receiver;
                if (r != null) {
                    // 在客户端主线程执行回调
                    context.client().execute(() -> {
                        Consumer<Message> rr = receiver;
                        if (rr != null) {
                            rr.accept(msg);
                        }
                    });
                }
            } catch (Exception ignored) {
                // 解析失败忽略
            }
        });
    }

    /** 发送一条消息到服务端。 */
    public static void send(Message msg) {
        ClientPlayNetworking.send(new DpePayload(MessageCodec.toJson(msg)));
    }

    /** 当前是否可发送（已连接且服务端支持该通道）。 */
    public static boolean canSend() {
        return ClientPlayNetworking.canSend(DpePayload.ID);
    }
}
