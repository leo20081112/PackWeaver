package dev.packweaver.bridge.bridge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.server.MinecraftServer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * TCP JSON 桥接服务器（规划书第 20 章「服务器端 PackWeaver / 独立进程连接」）。
 *
 * 桌面端 PackWeaver 通过本机 TCP 连接发送按行分隔的 JSON 指令：
 *   {"action":"ping"}
 *   {"action":"eval","command":"say hello"}
 *   {"action":"reload"}
 *   {"action":"stats"}
 * 每条指令都会返回一行 JSON 应答。
 *
 * 安全策略：默认只监听 127.0.0.1 回环地址，仅允许本机的桌面端连接；
 * eval 指令使用 4 级权限命令源，仅供本地开发环境使用。
 */
public final class BridgeServer {
    private static final BridgeServer INSTANCE = new BridgeServer();
    private static final Gson GSON = new GsonBuilder().create();

    private volatile MinecraftServer server;
    private volatile ServerSocket serverSocket;
    private volatile boolean running;
    private final List<String> recentLog = new ArrayList<>();

    private BridgeServer() {
    }

    public static BridgeServer getInstance() {
        return INSTANCE;
    }

    public int getPort() {
        return dev.packweaver.bridge.PackWeaverBridge.BRIDGE_DEFAULT_PORT;
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        ServerLifecycleEvents.SERVER_STARTING.register(s -> server = s);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            server = null;
            log("服务器已停止");
        });
        Thread acceptor = new Thread(this::acceptLoop, "PackWeaver-Bridge-Acceptor");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    private void acceptLoop() {
        try {
            serverSocket = new ServerSocket(getPort(), 4, InetAddress.getLoopbackAddress());
            log("桥接服务器已启动于 127.0.0.1:" + getPort());
            while (running) {
                Socket client = serverSocket.accept();
                Thread t = new Thread(() -> handleClient(client), "PackWeaver-Bridge-Client");
                t.setDaemon(true);
                t.start();
            }
        } catch (IOException e) {
            if (running) {
                log("桥接服务器异常: " + e.getMessage());
            }
        }
    }

    private void handleClient(Socket client) {
        try (Socket sock = client;
             BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8));
             OutputStreamWriter out = new OutputStreamWriter(sock.getOutputStream(), StandardCharsets.UTF_8)) {
            sock.setTcpNoDelay(true);
            String line;
            while ((line = in.readLine()) != null) {
                String response = GSON.toJson(handle(GSON.fromJson(line, JsonObject.class)));
                out.write(response);
                out.write('\n');
                out.flush();
            }
        } catch (Exception e) {
            log("客户端连接断开: " + e.getMessage());
        }
    }

    private JsonObject handle(JsonObject request) {
        JsonObject response = new JsonObject();
        String action = request.has("action") ? request.get("action").getAsString() : "";
        try {
            switch (action) {
                case "ping" -> {
                    response.addProperty("ok", true);
                    response.addProperty("status", "pong");
                    log("收到 ping");
                }
                case "eval" -> {
                    String command = request.get("command").getAsString();
                    MinecraftServer s = server;
                    if (s == null) {
                        response.addProperty("ok", false);
                        response.addProperty("error", "服务器未运行");
                    } else {
                        // getCommandSource() 即控制台命令源，天然拥有 4 级权限
                        int result = s.getCommandManager().executeWithPrefix(
                                s.getCommandSource().withSilent(), command);
                        response.addProperty("ok", true);
                        response.addProperty("result", result);
                        log("执行命令: " + command);
                    }
                }
                case "reload" -> {
                    MinecraftServer s = server;
                    if (s == null) {
                        response.addProperty("ok", false);
                        response.addProperty("error", "服务器未运行");
                    } else {
                        // 在主线程执行 /reload，避免并发修改数据包状态
                        s.execute(() -> s.getCommandManager().executeWithPrefix(s.getCommandSource(), "reload"));
                        response.addProperty("ok", true);
                        log("触发数据包热重载");
                    }
                }
                case "stats" -> {
                    response.addProperty("ok", true);
                    response.add("stats", dev.packweaver.bridge.perf.PerfTracker.report());
                }
                default -> {
                    response.addProperty("ok", false);
                    response.addProperty("error", "未知 action: " + action);
                }
            }
        } catch (Exception e) {
            response.addProperty("ok", false);
            response.addProperty("error", String.valueOf(e.getMessage()));
        }
        return response;
    }

    public boolean isRunning() {
        return running && serverSocket != null && !serverSocket.isClosed();
    }

    /** 最近 50 条桥接日志，供 F12 叠加层展示。 */
    public List<String> getRecentLog() {
        synchronized (recentLog) {
            return new ArrayList<>(recentLog);
        }
    }

    private void log(String message) {
        dev.packweaver.bridge.PackWeaverBridge.LOGGER.info("[Bridge] {}", message);
        synchronized (recentLog) {
            recentLog.add("[" + System.currentTimeMillis() + "] " + message);
            while (recentLog.size() > 50) {
                recentLog.remove(0);
            }
        }
    }
}
