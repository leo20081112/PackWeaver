package com.dpe.client;

import com.dpe.common.block.EditorState;
import com.dpe.common.config.UserConfig;
import com.dpe.common.protocol.Message;
import com.dpe.common.protocol.SyncStateMessage;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.WorldSavePath;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Path;

/**
 * 客户端入口：注册按键绑定（默认 K）打开编辑器，注册网络通道接收服务端同步。
 * 配置文件位于 {@code config/dpe/config.json}（{@link UserConfig}）。
 */
public final class DatapackEditorClient implements ClientModInitializer {

    /** 默认数据包命名空间（离线编辑时使用）。 */
    private static final String DEFAULT_NAMESPACE = "dpe";
    /** 配置文件路径（相对游戏运行目录）。 */
    public static final String CONFIG_RELATIVE_PATH = "config/dpe/config.json";

    private static KeyBinding openEditorKey;
    private static volatile UserConfig config;

    @Override
    public void onInitializeClient() {
        // 加载用户配置
        MinecraftClient mc = MinecraftClient.getInstance();
        Path configPath = configPath(mc);
        config = UserConfig.load(configPath);

        // 注册自定义 Payload 与 S2C 接收器
        ClientNetworking.register();
        ClientNetworking.registerReceiver(message -> {
            // 收到任意服务端消息：视为服务端装了插件（用于 ReloadService 路由）
            ReloadService.markServerPluginPresent();
            handleServerMessage(message, mc);
        });

        // 按键绑定：从配置中读取 openEditor 键（默认 K = GLFW_KEY_K）
        int openKey = configKey(config, "openEditor", GLFW.GLFW_KEY_K);
        openEditorKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.dpe.open_editor",
                InputUtil.Type.KEYSYM,
                openKey,
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

    /** 处理服务端消息。 */
    private static void handleServerMessage(Message message, MinecraftClient mc) {
        if (message instanceof SyncStateMessage sync) {
            if (mc != null && mc.currentScreen instanceof EditorScreen screen) {
                screen.applySync(sync.editorStateJson(), sync.revision());
            }
        }
    }

    /** 从配置中读取按键（兼容加载失败时回退默认）。 */
    private static int configKey(UserConfig cfg, String action, int fallback) {
        if (cfg == null || cfg.keyBindings == null) {
            return fallback;
        }
        return switch (action) {
            case "openEditor" -> cfg.keyBindings.openEditor;
            case "switchMode" -> cfg.keyBindings.switchMode;
            case "reload" -> cfg.keyBindings.reload;
            case "save" -> cfg.keyBindings.save;
            case "help" -> cfg.keyBindings.help;
            case "togglePalette" -> cfg.keyBindings.togglePalette;
            default -> fallback;
        };
    }

    /** 获取配置文件路径。 */
    public static Path configPath(MinecraftClient mc) {
        if (mc == null || mc.runDirectory == null) {
            return Path.of(CONFIG_RELATIVE_PATH);
        }
        return mc.runDirectory.toPath().resolve(CONFIG_RELATIVE_PATH);
    }

    /** 获取当前配置（已加载，可能为 defaults）。 */
    public static UserConfig config() {
        if (config == null) {
            config = UserConfig.defaults();
        }
        return config;
    }

    /** 保存当前配置到默认路径。 */
    public static void saveConfig() {
        MinecraftClient mc = MinecraftClient.getInstance();
        try {
            config().save(configPath(mc));
        } catch (Exception ignored) {
            // 保存失败忽略
        }
    }

    /**
     * 获取当前世界 datapacks 目录（仅单机集成服务器可用）。
     * @return 路径；非单机返回 null。
     */
    public static Path worldDatapacksDir(MinecraftClient mc) {
        if (mc == null) {
            return null;
        }
        IntegratedServer server = mc.getServer();
        if (server == null) {
            return null;
        }
        try {
            return server.getSavePath(WorldSavePath.DATAPACKS);
        } catch (Throwable t) {
            return null;
        }
    }
}
