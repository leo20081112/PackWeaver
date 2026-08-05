package com.dpe.client;

import com.dpe.common.block.EditorState;
import com.dpe.common.protocol.SyncStateMessage;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

/**
 * 客户端入口：注册按键绑定（默认 K）打开编辑器，注册网络通道接收服务端同步。
 */
public final class DatapackEditorClient implements ClientModInitializer {

    /** 默认数据包命名空间（离线编辑时使用）。 */
    private static final String DEFAULT_NAMESPACE = "dpe";

    private static KeyBinding openEditorKey;

    @Override
    public void onInitializeClient() {
        // 注册自定义 Payload 与 S2C 接收器
        ClientNetworking.register();
        ClientNetworking.registerReceiver(message -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (message instanceof SyncStateMessage sync) {
                if (mc != null && mc.currentScreen instanceof EditorScreen screen) {
                    screen.applySync(sync.editorStateJson(), sync.revision());
                }
            }
        });

        // 按键绑定：默认 K
        openEditorKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.dpe.open_editor",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                "key.categories.dpe"
        ));

        // 每客户端 tick 检测按键
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openEditorKey != null && openEditorKey.wasPressed()) {
                EditorState state = new EditorState();
                if (state.getActiveDatapackNamespace() == null
                        || state.getActiveDatapackNamespace().isBlank()) {
                    state.setActiveDatapackNamespace(DEFAULT_NAMESPACE);
                }
                client.setScreen(new EditorScreen(state));
            }
        });
    }
}
