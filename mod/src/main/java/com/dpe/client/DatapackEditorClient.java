package com.dpe.client;

import com.dpe.common.block.EditorState;
import com.dpe.common.config.UserConfig;
import com.dpe.common.editor.DatapackExporter;
import com.dpe.common.protocol.Message;
import com.dpe.common.protocol.SyncStateMessage;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.WorldSavePath;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
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
                client.setScreen(openEditorSmart(state, client));
            }
        });
    }

    /**
     * 智能选择编辑器入口（Task 10）：
     * 单机且已存在 {@code dpe-<ns>} 真实数据包 → IDE（直接编辑真实文件，避免从空积木起步的漂移）；
     * 否则 → 积木编辑器（默认创作模式）。
     */
    private static Screen openEditorSmart(EditorState state, MinecraftClient mc) {
        String ns = state.getActiveDatapackNamespace();
        if (ns == null || ns.isBlank()) {
            ns = DEFAULT_NAMESPACE;
            state.setActiveDatapackNamespace(ns);
        }
        if (realDatapackExists(ns, mc)) {
            return new IdeEditorScreen(state);
        }
        return new EditorScreen(state);
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

    /**
     * 世界 datapacks 目录下是否已存在 {@code dpe-<ns>} 真实数据包（含 pack.mcmeta）。
     * 用于智能 IDE 入口判断（Task 10）。
     */
    public static boolean realDatapackExists(String ns, MinecraftClient mc) {
        if (ns == null || ns.isBlank()) {
            ns = DEFAULT_NAMESPACE;
        }
        Path dpDir = worldDatapacksDir(mc);
        if (dpDir == null) {
            return false;
        }
        return Files.isRegularFile(dpDir.resolve("dpe-" + ns).resolve("pack.mcmeta"));
    }

    /**
     * 在世界 datapacks 目录生成骨架数据包（Task 10）：
     * {@code dpe-<ns>/pack.mcmeta} + {@code data/<ns>/functions/internal/tick.mcfunction}。
     * 已存在则跳过对应文件（幂等），便于首次切到 IDE 时创建真实文件树。
     * @return 生成的数据包目录；非单机或失败返回 null。
     */
    public static Path generateSkeleton(String ns, MinecraftClient mc) {
        if (ns == null || ns.isBlank()) {
            ns = DEFAULT_NAMESPACE;
        }
        Path dpDir = worldDatapacksDir(mc);
        if (dpDir == null) {
            return null;
        }
        try {
            Path target = dpDir.resolve("dpe-" + ns);
            Path mcmeta = target.resolve("pack.mcmeta");
            if (!Files.exists(mcmeta)) {
                Files.createDirectories(target);
                String mcmetaJson = "{\"pack\":{\"pack_format\":" + DatapackExporter.PACK_FORMAT
                        + ",\"description\":\"DPE skeleton (" + ns + ")\"}}";
                Files.writeString(mcmeta, mcmetaJson);
            }
            Path tick = target.resolve("data/" + ns + "/functions/internal/tick.mcfunction");
            if (!Files.exists(tick)) {
                Files.createDirectories(tick.getParent());
                Files.writeString(tick, "# 每刻触发\n# 在此添加命令\n");
            }
            return target;
        } catch (Exception e) {
            return null;
        }
    }
}
