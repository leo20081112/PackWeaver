package dev.packweaver.bridge.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Util;
import dev.packweaver.bridge.PackWeaverBridge;
import dev.packweaver.bridge.bridge.BridgeServer;
import dev.packweaver.bridge.perf.PerfTracker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 叠加层窗口管理器（规划书扩展 D「游戏内多窗口系统」）。
 * 布局持久化到 config/packweaver-overlay.json。
 */
public final class OverlayManager {
    private static final OverlayManager INSTANCE = new OverlayManager();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG = net.fabricmc.loader.api.FabricLoader.getInstance()
            .getGameDir().resolve("config").resolve("packweaver-overlay.json");

    private final Map<String, OverlayWindow> windows = new LinkedHashMap<>();
    private boolean visible = true;

    private OverlayManager() {
        windows.put("stats", new OverlayWindow("stats", "性能", 8, 8, 150, 58, true));
        windows.put("coords", new OverlayWindow("coords", "坐标", 8, 74, 150, 46, true));
        windows.put("bridge", new OverlayWindow("bridge", "桥接日志", 8, 128, 300, 96, false));
        windows.put("help", new OverlayWindow("help", "帮助", 8, 232, 300, 78, false));
        load();
    }

    public static OverlayManager getInstance() {
        return INSTANCE;
    }

    public Map<String, OverlayWindow> getWindows() {
        return windows;
    }

    public boolean isVisible() {
        return visible;
    }

    public void toggleVisible() {
        visible = !visible;
    }

    public void save() {
        try {
            Files.createDirectories(CONFIG.getParent());
            Files.writeString(CONFIG, GSON.toJson(windows));
        } catch (IOException e) {
            PackWeaverBridge.LOGGER.warn("无法保存叠加层布局: {}", e.getMessage());
        }
    }

    private void load() {
        if (!Files.exists(CONFIG)) {
            return;
        }
        try {
            Map<String, OverlayWindow> saved = GSON.fromJson(Files.readString(CONFIG),
                    new TypeToken<Map<String, OverlayWindow>>() { }.getType());
            if (saved != null) {
                for (Map.Entry<String, OverlayWindow> e : saved.entrySet()) {
                    OverlayWindow local = windows.get(e.getKey());
                    if (local != null && e.getValue() != null) {
                        OverlayWindow s = e.getValue();
                        local.x = s.x;
                        local.y = s.y;
                        local.width = Math.max(80, s.width);
                        local.height = Math.max(40, s.height);
                        local.enabled = s.enabled;
                    }
                }
            }
        } catch (Exception e) {
            PackWeaverBridge.LOGGER.warn("无法读取叠加层布局: {}", e.getMessage());
        }
    }

    /** 供窗口渲染使用的内容行。 */
    public static List<String> contentOf(OverlayWindow window) {
        MinecraftClient client = MinecraftClient.getInstance();
        List<String> lines = new ArrayList<>();
        switch (window.id) {
            case "stats" -> {
                lines.add(String.format("FPS: %d", client.getCurrentFps()));
                lines.add(String.format("MSPT: %.1fms (%s)", PerfTracker.averageMspt(), PerfTracker.status()));
                lines.add(String.format("TPS: %.1f", PerfTracker.tps()));
            }
            case "coords" -> {
                if (client.player != null && client.world != null) {
                    var p = client.player.getPos();
                    lines.add(String.format("XYZ: %.1f / %.1f / %.1f", p.x, p.y, p.z));
                    lines.add("维度: " + client.world.getRegistryKey().getValue());
                } else {
                    lines.add("未进入世界");
                }
            }
            case "bridge" -> {
                BridgeServer bridge = BridgeServer.getInstance();
                lines.add("TCP 127.0.0.1:" + bridge.getPort() + (bridge.isRunning() ? " [运行中]" : " [停止]"));
                List<String> log = bridge.getRecentLog();
                int start = Math.max(0, log.size() - (window.height - 20) / 11);
                for (int i = start; i < log.size(); i++) {
                    lines.add(log.get(i));
                }
            }
            case "help" -> {
                lines.add("F12 显示/隐藏叠加层");
                lines.add("Shift+F12 编辑窗口布局");
                lines.add("/pw stats 性能 / /pw reload 重载");
                lines.add("/pw copier give 坐标复制器");
            }
            default -> lines.add("(无内容)");
        }
        return lines;
    }
}
