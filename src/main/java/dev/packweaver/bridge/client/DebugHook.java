package dev.packweaver.bridge.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * TEMP 临时 MCP 调试钩子（仅自动化测试使用，正式版可整体移除）。
 *
 * 激活条件：游戏目录（gameDir）存在 <b>packweaver-debug.flag</b> 文件。
 * 激活后向 HTTP 桥接注册 /pw/debug 路由，暴露：
 *   GET  /pw/debug/state                游戏状态（世界/界面/玩家/FPS/项目列表）
 *   GET  /pw/debug/log                  最近日志环形缓冲（200 条）
 *   POST /pw/debug/exec_server {"command"}   集成服务器控制台命令
 *   POST /pw/debug/chat        {"text"}      以玩家身份发送聊天/命令
 *   POST /pw/debug/action      {"action":...,"ns":...,"tpl":...}
 *       action: open_project | open_graph | open_ide | open_diag | open_wiki |
 *               close_screen | toggle_overlay | screenshot |
 *               create_project | delete_project | diag | export
 */
public final class DebugHook {
    private static final Gson GSON = new GsonBuilder().create();
    private static final Deque<String> LOG_RING = new ArrayDeque<>();
    private static volatile boolean registered;

    private DebugHook() {
    }

    public static void init() {
        if (registered) {
            return;
        }
        Path flag = net.fabricmc.loader.api.FabricLoader.getInstance()
                .getGameDir().resolve("packweaver-debug.flag");
        boolean byProperty = Boolean.getBoolean("packweaver.debug");
        boolean byFlag = Files.exists(flag);
        dev.packweaver.bridge.PackWeaverBridge.LOGGER.info(
                "[PackWeaver] TEMP 调试钩子检查: gameDir={} flag={} property={}",
                net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir(), byFlag, byProperty);
        if (!byFlag && !byProperty) {
            return;
        }
        try {
            attachLogAppender();
            HttpBridgeRegister.doRegister();
            registered = true;
            dev.packweaver.bridge.PackWeaverBridge.LOGGER.info(
                    "[PackWeaver] TEMP 调试钩子已激活：/pw/debug/*");
        } catch (Throwable t) {
            dev.packweaver.bridge.PackWeaverBridge.LOGGER.warn(
                    "[PackWeaver] TEMP 调试钩子激活失败: {}", t.toString());
        }
    }

    /** 避免服务器环境加载 HttpBridgeServer 引用链 —— 经由桥接侧注册。 */
    private static final class HttpBridgeRegister {
        static void doRegister() {
            dev.packweaver.bridge.bridge.HttpBridgeServer.registerExternalHandler(
                    "/pw/debug", DebugHook::handle);
        }
    }

    private static void attachLogAppender() {
        AbstractAppender appender = new AbstractAppender("PackWeaverDebug", null, null,
                true, Property.EMPTY_ARRAY) {
            @Override
            public void append(LogEvent event) {
                synchronized (LOG_RING) {
                    LOG_RING.addLast(event.getLevel().name() + " [" + event.getLoggerName()
                            + "] " + event.getMessage().getFormattedMessage());
                    while (LOG_RING.size() > 200) {
                        LOG_RING.removeFirst();
                    }
                }
            }
        };
        appender.start();
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        ctx.getConfiguration().addAppender(appender);
        ctx.getRootLogger().addAppender(appender);
        ctx.updateLoggers();
    }

    private static String handle(HttpExchange exchange) {
        String path = exchange.getRequestURI().getPath();
        try {
            if ("/pw/debug/state".equals(path)) {
                return GSON.toJson(state());
            }
            if ("/pw/debug/log".equals(path)) {
                JsonObject o = new JsonObject();
                JsonArray arr = new JsonArray();
                synchronized (LOG_RING) {
                    for (String line : LOG_RING) {
                        arr.add(line);
                    }
                }
                o.add("lines", arr);
                return GSON.toJson(o);
            }
            if ("POST".equals(exchange.getRequestMethod())) {
                JsonObject body = readBody(exchange);
                if ("/pw/debug/exec_server".equals(path)) {
                    return GSON.toJson(execServer(body.has("command")
                            ? body.get("command").getAsString() : ""));
                }
                if ("/pw/debug/chat".equals(path)) {
                    return GSON.toJson(chat(body.has("text")
                            ? body.get("text").getAsString() : ""));
                }
                if ("/pw/debug/action".equals(path)) {
                    return GSON.toJson(action(body));
                }
            }
            return GSON.toJson(err("未知调试端点: " + path));
        } catch (Throwable t) {
            return GSON.toJson(err(String.valueOf(t)));
        }
        // 注意：不要在这里 close —— 外层 HttpBridgeServer.route 统一关闭，
        // 提前 close 会丢弃包装层尚未发送的响应。
    }

    // ---------------- 动作实现（均在客户端线程执行） ----------------

    private interface ClientCall<T> {
        T run(MinecraftClient client) throws Exception;
    }

    private static <T> T onClient(ClientCall<T> call) {
        MinecraftClient client = MinecraftClient.getInstance();
        CompletableFuture<T> f = new CompletableFuture<>();
        client.execute(() -> {
            try {
                f.complete(call.run(client));
            } catch (Throwable t) {
                f.completeExceptionally(t);
            }
        });
        try {
            return f.get(8, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("客户端执行超时/失败: " + e.getMessage(), e);
        }
    }

    private static JsonObject state() {
        return onClient(client -> {
            JsonObject o = new JsonObject();
            o.addProperty("env", "client");
            o.addProperty("worldLoaded", client.world != null && client.player != null);
            o.addProperty("integratedServer", client.getServer() != null);
            o.addProperty("screen", client.currentScreen == null
                    ? "in_game_hud" : client.currentScreen.getClass().getSimpleName());
            o.addProperty("fps", client.getCurrentFps());
            if (client.player != null) {
                o.addProperty("pos", String.format("%.1f %.1f %.1f",
                        client.player.getX(), client.player.getY(), client.player.getZ()));
                o.addProperty("health", client.player.getHealth());
            }
            if (client.world != null) {
                o.addProperty("dimension", client.world.getRegistryKey().getValue().toString());
            }
            try {
                JsonArray projects = new JsonArray();
                for (String ns : dev.packweaver.bridge.pack.PackProject.listProjects()) {
                    projects.add(ns);
                }
                o.add("projects", projects);
            } catch (Throwable t) {
                o.addProperty("projects_error", String.valueOf(t));
            }
            return o;
        });
    }

    private static JsonObject execServer(String command) {
        if (command.isBlank()) {
            return err("缺少 command");
        }
        return onClient(client -> {
            var server = client.getServer();
            if (server == null) {
                return err("集成服务器未运行（请先进入世界）");
            }
            int result = server.getCommandManager()
                    .executeWithPrefix(server.getCommandSource().withSilent(), command);
            JsonObject o = ok();
            o.addProperty("result", result);
            return o;
        });
    }

    private static JsonObject chat(String text) {
        if (text.isBlank()) {
            return err("缺少 text");
        }
        return onClient(client -> {
            if (client.player == null) {
                return err("未进入世界");
            }
            client.inGameHud.getChatHud().addToMessageHistory(text);
            if (text.startsWith("/")) {
                // 客户端命令树没有的命令（如 /tag /function）需转发服务器执行
                String cmd = text.substring(1);
                var dispatcher = client.getNetworkHandler().getCommandDispatcher();
                if (dispatcher.getRoot().getChild(cmd.split(" ")[0]) != null) {
                    client.player.networkHandler.sendCommand(cmd);
                } else {
                    var server = client.getServer();
                    if (server != null) {
                        server.getCommandManager().executeWithPrefix(
                                server.getCommandSource().withSilent(), cmd);
                    } else {
                        client.player.networkHandler.sendChatMessage(text);
                    }
                }
            } else {
                client.player.networkHandler.sendChatMessage(text);
            }
            return ok();
        });
    }

    private static JsonObject action(JsonObject body) {
        String action = body.has("action") ? body.get("action").getAsString() : "";
        String ns = body.has("ns") ? body.get("ns").getAsString() : "";
        String tpl = body.has("tpl") ? body.get("tpl").getAsString() : "hello";
        // 不需要客户端线程的数据操作（断点/轨迹）直接处理
        if ("set_breakpoints".equals(action)) {
            try {
                dev.packweaver.bridge.pack.PackProject p =
                        dev.packweaver.bridge.pack.PackProject.load(ns);
                String path = body.has("path") ? body.get("path").getAsString() : "";
                if (path.isBlank()) {
                    return err("缺少 path");
                }
                List<Integer> lines = new ArrayList<>();
                if (body.has("lines") && body.get("lines").isJsonArray()) {
                    for (var el : body.getAsJsonArray("lines")) {
                        lines.add(el.getAsInt());
                    }
                }
                if (lines.isEmpty()) {
                    p.debugLines.remove(path);
                } else {
                    p.debugLines.put(path, lines);
                }
                p.save();
                JsonObject o = ok();
                o.addProperty("path", path);
                JsonArray arr = new JsonArray();
                for (int ln : p.debugLines.getOrDefault(path, List.of())) {
                    arr.add(ln);
                }
                o.add("lines", arr);
                return o;
            } catch (IOException e) {
                return err("操作失败: " + e.getMessage());
            }
        }
        if ("toggle_trace".equals(action)) {
            try {
                dev.packweaver.bridge.pack.PackProject p =
                        dev.packweaver.bridge.pack.PackProject.load(ns);
                p.traceMode = body.has("on") && body.get("on").getAsBoolean();
                p.save();
                JsonObject o = ok();
                o.addProperty("traceMode", p.traceMode);
                return o;
            } catch (IOException e) {
                return err("操作失败: " + e.getMessage());
            }
        }
        return onClient(client -> runAction(client, action, ns, tpl));
    }

    private static JsonObject runAction(MinecraftClient mc, String action, String ns, String tpl) throws IOException {
        switch (action) {
            case "open_project" -> {
                mc.setScreen(new dev.packweaver.bridge.gui.ProjectScreen());
                return ok();
            }
            case "open_graph" -> {
                if (mc.getServer() == null) {
                    return err("未进入世界");
                }
                List<String> all = dev.packweaver.bridge.pack.PackProject.listProjects();
                if (all.isEmpty()) {
                    return err("没有项目");
                }
                mc.setScreen(new dev.packweaver.bridge.gui.graph.BlockGraphScreen(
                        dev.packweaver.bridge.pack.PackProject.load(ns.isEmpty() ? all.get(0) : ns)));
                return ok();
            }
            case "open_ide" -> {
                if (mc.getServer() == null) {
                    return err("未进入世界");
                }
                List<String> all = dev.packweaver.bridge.pack.PackProject.listProjects();
                if (all.isEmpty()) {
                    return err("没有项目");
                }
                mc.setScreen(new dev.packweaver.bridge.gui.CodeEditorScreen(
                        dev.packweaver.bridge.pack.PackProject.load(ns.isEmpty() ? all.get(0) : ns), "tick"));
                return ok();
            }
            case "open_diag" -> {
                if (mc.getServer() == null) {
                    return err("未进入世界");
                }
                List<String> all = dev.packweaver.bridge.pack.PackProject.listProjects();
                if (all.isEmpty()) {
                    return err("没有项目");
                }
                mc.setScreen(new dev.packweaver.bridge.gui.DiagScreen(
                        dev.packweaver.bridge.pack.PackProject.load(ns.isEmpty() ? all.get(0) : ns), null));
                return ok();
            }
                case "open_wiki" -> {
                    mc.setScreen(new dev.packweaver.bridge.gui.WikiScreen());
                    return ok();
                }
                case "open_debugctl" -> {
                    if (mc.getServer() == null) {
                        return err("未进入世界");
                    }
                    List<String> all = dev.packweaver.bridge.pack.PackProject.listProjects();
                    if (all.isEmpty()) {
                        return err("没有项目");
                    }
                    mc.setScreen(new dev.packweaver.bridge.gui.DebugControlScreen(
                            dev.packweaver.bridge.pack.PackProject.load(ns.isEmpty() ? all.get(0) : ns), null));
                    return ok();
                }
                case "set_breakpoints" -> {
                    // 已移至 action() 的非线程路径处理
                    return ok();
                }
                case "toggle_trace" -> {
                    // 已移至 action() 的非线程路径处理
                    return ok();
                }
            case "close_screen" -> {
                mc.setScreen(null);
                return ok();
            }
            case "toggle_overlay" -> {
                OverlayManager.getInstance().toggleVisible();
                return ok();
            }
            case "screenshot" -> {
                Path dir = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir()
                        .resolve("packweaver-debug-shots");
                Files.createDirectories(dir);
                ScreenshotRecorder.saveScreenshot(dir.toFile(), mc.getFramebuffer(), t -> { });
                JsonObject o = ok();
                o.addProperty("savedTo", dir.toString());
                return o;
            }
            case "create_project" -> {
                if (mc.getServer() == null) {
                    return err("未进入世界");
                }
                if (!ns.matches("[a-z][a-z0-9_]*")) {
                    return err("命名空间不合法: " + ns);
                }
                dev.packweaver.bridge.pack.PackProject p = new dev.packweaver.bridge.pack.PackProject();
                p.name = "测试项目 " + ns;
                p.namespace = ns;
                dev.packweaver.bridge.pack.Templates.apply(p, tpl);
                try {
                    p.save();
                } catch (IOException e) {
                    return err("保存失败: " + e.getMessage());
                }
                JsonObject o = ok();
                JsonArray files = new JsonArray();
                for (String f : p.allFiles()) {
                    files.add(f);
                }
                o.add("files", files);
                return o;
            }
            case "delete_project" -> {
                try {
                    dev.packweaver.bridge.pack.PackProject.load(ns).delete();
                } catch (IOException e) {
                    return err("删除失败: " + e.getMessage());
                }
                return ok();
            }
            case "diag" -> {
                if (mc.getServer() == null) {
                    return err("未进入世界");
                }
                try {
                    dev.packweaver.bridge.pack.PackProject p =
                            dev.packweaver.bridge.pack.PackProject.load(ns);
                    JsonArray issues = new JsonArray();
                    for (dev.packweaver.bridge.pack.Diag.Issue i : dev.packweaver.bridge.pack.Diag.run(p)) {
                        JsonObject is = new JsonObject();
                        is.addProperty("code", i.code);
                        is.addProperty("severity", i.severity);
                        is.addProperty("message", i.message);
                        issues.add(is);
                    }
                    JsonObject o = ok();
                    o.add("issues", issues);
                    return o;
                } catch (IOException e) {
                    return err("读取项目失败: " + e.getMessage());
                }
            }
            case "export" -> {
                try {
                    Path zip = dev.packweaver.bridge.pack.PackProject.load(ns).exportZip();
                    JsonObject o = ok();
                    o.addProperty("zip", zip.toString());
                    return o;
                } catch (IOException e) {
                    return err("导出失败: " + e.getMessage());
                }
            }
            default -> {
                return err("未知 action: " + action);
            }
        }
    }

    private static JsonObject readBody(HttpExchange exchange) throws IOException {
        String raw = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (raw.isBlank()) {
            return new JsonObject();
        }
        try {
            return GSON.fromJson(raw, JsonObject.class);
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    private static JsonObject ok() {
        JsonObject o = new JsonObject();
        o.addProperty("ok", true);
        return o;
    }

    private static JsonObject err(String msg) {
        JsonObject o = new JsonObject();
        o.addProperty("ok", false);
        o.addProperty("error", msg);
        return o;
    }
}
