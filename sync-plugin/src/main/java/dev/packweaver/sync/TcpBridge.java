package dev.packweaver.sync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import org.bukkit.Bukkit;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * TCP 桥接（与 Fabric Mod v1.0 完全同协议，规划书第 20 章独立进程连接）：
 * 按行 JSON 请求，逐行 JSON 应答。
 *   {"action":"ping"}
 *   {"action":"eval","command":"say hi"}
 *   {"action":"reload"}
 *   {"action":"stats"}
 */
public final class TcpBridge {
    private static final Gson GSON = new GsonBuilder().create();
    private final PackWeaverSyncPlugin plugin;
    private volatile ServerSocket socket;
    private volatile boolean running;

    public TcpBridge(PackWeaverSyncPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        running = true;
        Thread t = new Thread(this::acceptLoop, "PackWeaverSync-TCP");
        t.setDaemon(true);
        t.start();
    }

    public void stop() {
        running = false;
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }

    private void acceptLoop() {
        try {
            socket = new ServerSocket(plugin.tcpPort(), 4, InetAddress.getLoopbackAddress());
            while (running) {
                Socket client = socket.accept();
                Thread c = new Thread(() -> handle(client), "PackWeaverSync-TCP-Client");
                c.setDaemon(true);
                c.start();
            }
        } catch (IOException e) {
            if (running) {
                plugin.getLogger().warning("TCP 桥接异常: " + e.getMessage());
            }
        }
    }

    private void handle(Socket client) {
        try (Socket sock = client;
             BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
             OutputStreamWriter out = new OutputStreamWriter(sock.getOutputStream(), StandardCharsets.UTF_8)) {
            sock.setTcpNoDelay(true);
            String line;
            while (running && (line = in.readLine()) != null) {
                JsonObject resp;
                try {
                    JsonObject req = GSON.fromJson(line, JsonObject.class);
                    resp = handle(req == null ? new JsonObject() : req);
                } catch (Exception e) {
                    resp = err("请求解析失败: " + e.getMessage());
                }
                out.write(GSON.toJson(resp));
                out.write('\n');
                out.flush();
            }
        } catch (IOException ignored) {
            // 客户端断开
        }
    }

    private JsonObject handle(JsonObject req) {
        String action = req.has("action") ? req.get("action").getAsString() : "";
        switch (action) {
            case "ping" -> {
                JsonObject o = ok();
                o.addProperty("status", "pong");
                return o;
            }
            case "eval" -> {
                String command = req.has("command") ? req.get("command").getAsString() : "";
                if (command.isBlank()) {
                    return err("缺少 command 字段");
                }
                Boolean done = plugin.onMainThread(
                        () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command), 5000);
                if (done == null) {
                    return err("主线程执行超时");
                }
                JsonObject o = ok();
                o.addProperty("executed", done);
                return o;
            }
            case "reload" -> {
                Boolean done = plugin.onMainThread(
                        () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "minecraft:reload"), 10000);
                if (done == null) {
                    return err("重载超时");
                }
                return ok();
            }
            case "stats" -> {
                JsonObject o = ok();
                o.addProperty("mspt", Math.round(plugin.perf().averageMspt() * 100.0) / 100.0);
                o.addProperty("tps", Math.round(plugin.perf().tps() * 100.0) / 100.0);
                o.addProperty("status", plugin.perf().status());
                return o;
            }
            default -> {
                return err("未知 action: " + action);
            }
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
