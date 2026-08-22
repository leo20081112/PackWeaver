package dev.packweaver.bridge.bridge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;

/**
 * HTTP 桥接服务器：供 PackWeaver Web IDE 调用（规划书第 18 / 20 章热重载与部署）。
 *
 * 端点（均带 CORS 头，浏览器可跨源访问）：
 *   GET  /pw/ping          —— 连通性测试
 *   GET  /pw/stats         —— MSPT / TPS 性能报告
 *   POST /pw/eval          —— {"command":"say hi"} 在服务器主线程执行命令
 *   POST /pw/reload        —— 触发数据包热重载
 *   POST /pw/deploy?ns=xx  —— 请求体为数据包 zip 字节，
 *                             写入当前存档 datapacks/packweaver-xx.zip 并自动重载
 *
 * 安全：仅监听 127.0.0.1，外网不可访问；部署文件名做白名单清洗。
 */
public final class HttpBridgeServer {
    private static final Gson GSON = new GsonBuilder().create();
    private volatile HttpServer httpServer;
    private volatile MinecraftServer server;

    public void start() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTING.register(s -> server = s);
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPED.register(s -> server = null);
        try {
            httpServer = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(),
                    dev.packweaver.bridge.PackWeaverBridge.HTTP_BRIDGE_PORT), 0);
            httpServer.setExecutor(Executors.newCachedThreadPool());
            httpServer.createContext("/pw", this::route);
            httpServer.start();
            dev.packweaver.bridge.PackWeaverBridge.LOGGER.info(
                    "[Bridge] HTTP 桥接已启动于 http://127.0.0.1:{}/pw/",
                    dev.packweaver.bridge.PackWeaverBridge.HTTP_BRIDGE_PORT);
        } catch (IOException e) {
            dev.packweaver.bridge.PackWeaverBridge.LOGGER.warn("[Bridge] HTTP 桥接启动失败: {}", e.getMessage());
        }
    }

    private void route(HttpExchange exchange) throws IOException {
        try {
            if ("OPTIONS".equals(exchange.getRequestMethod())) {
                cors(exchange);
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            cors(exchange);
            String path = exchange.getRequestURI().getPath();
            String method = exchange.getRequestMethod();
            JsonObject body = "POST".equals(method) ? readJson(exchange) : new JsonObject();

            if ("/pw/ping".equals(path)) {
                JsonObject o = ok();
                o.addProperty("mod", "packweaver-bridge");
                o.addProperty("version", "1.1.0");
                send(exchange, 200, o);
            } else if ("/pw/stats".equals(path)) {
                JsonObject o = ok();
                o.add("stats", dev.packweaver.bridge.perf.PerfTracker.report());
                send(exchange, 200, o);
            } else if ("/pw/eval".equals(path)) {
                if (server == null) {
                    err(exchange, 503, "服务器未运行（请先进入世界）");
                    return;
                }
                String command = body.has("command") ? body.get("command").getAsString() : "";
                if (command.isBlank()) {
                    err(exchange, 400, "缺少 command 字段");
                    return;
                }
                int result = server.getCommandManager()
                        .executeWithPrefix(server.getCommandSource().withSilent(), command);
                JsonObject o = ok();
                o.addProperty("result", result);
                send(exchange, 200, o);
            } else if ("/pw/reload".equals(path)) {
                if (server == null) {
                    err(exchange, 503, "服务器未运行（请先进入世界）");
                    return;
                }
                server.execute(() -> server.getCommandManager()
                        .executeWithPrefix(server.getCommandSource(), "reload"));
                send(exchange, 200, ok());
            } else if ("/pw/deploy".equals(path)) {
                deploy(exchange);
            } else {
                err(exchange, 404, "未知端点: " + path);
            }
        } catch (Exception e) {
            err(exchange, 500, String.valueOf(e.getMessage()));
        } finally {
            exchange.close();
        }
    }

    private void deploy(HttpExchange exchange) throws IOException {
        if (server == null) {
            err(exchange, 503, "服务器未运行（请先进入世界）");
            return;
        }
        String ns = sanitize(exchange.getRequestURI().getQuery());
        byte[] zip = exchange.getRequestBody().readAllBytes();
        if (zip.length == 0) {
            err(exchange, 400, "请求体为空");
            return;
        }
        Path datapacks = server.getSavePath(WorldSavePath.DATAPACKS);
        Path target = datapacks.resolve("packweaver-" + ns + ".zip");
        Files.createDirectories(datapacks);
        Files.write(target, zip);
        server.execute(() -> server.getCommandManager()
                .executeWithPrefix(server.getCommandSource(), "reload"));
        JsonObject o = ok();
        o.addProperty("deployed", target.getFileName().toString());
        o.addProperty("bytes", zip.length);
        send(exchange, 200, o);
        dev.packweaver.bridge.PackWeaverBridge.LOGGER.info(
                "[Bridge] HTTP 部署: {} ({} 字节)", target.getFileName(), zip.length);
    }

    /** 命名空间白名单清洗，防止路径穿越。 */
    private static String sanitize(String query) {
        String ns = "project";
        if (query != null) {
            for (String kv : query.split("&")) {
                if (kv.startsWith("ns=")) {
                    ns = kv.substring(3);
                }
            }
        }
        return ns.replaceAll("[^a-z0-9_]", "").substring(0, Math.min(ns.length(), 32));
    }

    private static JsonObject readJson(HttpExchange exchange) throws IOException {
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

    private static void cors(HttpExchange exchange) {
        var h = exchange.getResponseHeaders();
        h.add("Access-Control-Allow-Origin", "*");
        h.add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        h.add("Access-Control-Allow-Headers", "Content-Type");
    }

    private static JsonObject ok() {
        JsonObject o = new JsonObject();
        o.addProperty("ok", true);
        return o;
    }

    private static void err(HttpExchange exchange, int code, String message) throws IOException {
        JsonObject o = new JsonObject();
        o.addProperty("ok", false);
        o.addProperty("error", message);
        send(exchange, code, o);
    }

    private static void send(HttpExchange exchange, int code, JsonObject body) throws IOException {
        byte[] data = body == null ? new byte[0] : GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        if (data.length > 0) {
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        }
        exchange.sendResponseHeaders(code, data.length == 0 ? -1 : data.length);
        if (data.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(data);
            }
        }
    }
}
