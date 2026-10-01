package dev.packweaver.sync;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

/**
 * HTTP 桥接（与 Fabric Mod 同协议，规划书第 20 章）：
 *   GET  /pw/ping        连通性
 *   GET  /pw/stats       MSPT/TPS/在线人数
 *   GET  /pw/list        列出存档 datapacks 目录内容（双向同步）
 *   GET  /pw/pack?name=  下载数据包文件（仅 zip，防穿越）
 *   POST /pw/eval        {"command":"say hi"} 主线程执行控制台命令
 *   POST /pw/reload      主线程执行 minecraft:reload（数据包热重载，不重启服务器）
 *   POST /pw/deploy?ns=x 请求体为数据包 zip，写入存档并自动重载
 */
public final class HttpBridge {
    private static final Gson GSON = new GsonBuilder().create();
    private final PackWeaverSyncPlugin plugin;
    private HttpServer server;

    public HttpBridge(PackWeaverSyncPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        try {
            InetAddress bind = "0.0.0.0".equals(plugin.getBindAddress())
                    ? null : InetAddress.getLoopbackAddress();
            server = HttpServer.create(new InetSocketAddress(bind, plugin.httpPort()), 0);
            server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
            server.createContext("/pw", this::route);
            server.start();
            if (!plugin.getAuthToken().isBlank() && bind != null) {
                plugin.getLogger().warning("已设置 auth-token 但监听地址为回环 —— 局域网访问请将 bind-address 设为 0.0.0.0");
            }
        } catch (IOException e) {
            plugin.getLogger().warning("HTTP 桥接启动失败: " + e.getMessage());
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void route(HttpExchange ex) throws IOException {
        try {
            if ("OPTIONS".equals(ex.getRequestMethod())) {
                cors(ex);
                ex.sendResponseHeaders(204, -1);
                return;
            }
            cors(ex);
            // 鉴权：配置了 auth-token 时，除 OPTIONS 外全部端点需携带 X-PW-Token 请求头
            String token = plugin.getAuthToken();
            if (token != null && !token.isBlank()
                    && !token.equals(ex.getRequestHeaders().getFirst("X-PW-Token"))) {
                err(ex, 401, "缺少或错误的 X-PW-Token 请求头");
                return;
            }
            String path = ex.getRequestURI().getPath();
            // 注意：POST 请求体只允许在使用处读取一次（预读会饿死后续端点）
            switch (path) {
                case "/pw/ping" -> {
                    JsonObject o = ok();
                    o.addProperty("mod", "packweaver-sync");
                    o.addProperty("version", "1.1.0");
                    o.addProperty("server", Bukkit.getName());
                    send(ex, 200, o);
                }
                case "/pw/stats" -> send(ex, 200, stats());
                case "/pw/list" -> send(ex, 200, list());
                case "/pw/pack" -> pack(ex);
                case "/pw/backups" -> backups(ex);
                case "/pw/backup" -> backupDownload(ex);
                case "/pw/eval" -> eval(ex);
                case "/pw/reload" -> reload(ex);
                case "/pw/deploy" -> deploy(ex);
                default -> err(ex, 404, "未知端点: " + path);
            }
        } catch (Exception e) {
            err(ex, 500, String.valueOf(e.getMessage()));
        } finally {
            ex.close();
        }
    }

    private JsonObject stats() {
        JsonObject o = ok();
        JsonObject s = new JsonObject();
        s.addProperty("mspt_avg", Math.round(plugin.perf().averageMspt() * 100.0) / 100.0);
        s.addProperty("mspt_max", Math.round(plugin.perf().maxMspt() * 100.0) / 100.0);
        s.addProperty("tps", Math.round(plugin.perf().tps() * 100.0) / 100.0);
        s.addProperty("status", plugin.perf().status());
        s.addProperty("players", Bukkit.getOnlinePlayers().size());
        o.add("stats", s);
        return o;
    }

    private Path datapacksDir() {
        if (Bukkit.getWorlds().isEmpty()) {
            return null;
        }
        return Bukkit.getWorlds().get(0).getWorldFolder().toPath().resolve("datapacks");
    }

    private JsonObject list() throws IOException {
        JsonObject o = ok();
        JsonArray arr = new JsonArray();
        Path dir = datapacksDir();
        if (dir != null && Files.isDirectory(dir)) {
            try (Stream<Path> s = Files.list(dir)) {
                // 列出 zip 包与文件夹型数据包
                s.filter(p -> Files.isDirectory(p) || p.getFileName().toString().endsWith(".zip"))
                        .forEach(p -> {
                            JsonObject f = new JsonObject();
                            f.addProperty("name", p.getFileName().toString());
                            f.addProperty("type", Files.isDirectory(p) ? "dir" : "zip");
                            if (Files.isRegularFile(p)) {
                                try {
                                    f.addProperty("bytes", Files.size(p));
                                } catch (IOException ignored) {
                                }
                            }
                            arr.add(f);
                        });
            }
        }
        o.add("packs", arr);
        return o;
    }

    private void pack(HttpExchange ex) throws IOException {
        String name = queryParam(ex, "name");
        String safe = sanitize(name);
        Path dir = datapacksDir();
        Path target = dir == null ? null : dir.resolve(safe + ".zip").normalize();
        if (target == null || dir == null || !target.normalize().startsWith(dir.normalize())
                || !Files.exists(target)) {
            err(ex, 404, "数据包不存在: " + safe + "（dir=" + dir + " exists="
                    + (target != null && Files.exists(target)) + "）");
            return;
        }
        byte[] data = Files.readAllBytes(target);
        ex.getResponseHeaders().add("Content-Type", "application/zip");
        ex.sendResponseHeaders(200, data.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(data);
        }
    }

    private void eval(HttpExchange ex) throws IOException {
        JsonObject body = readJson(ex);
        String command = body.has("command") ? body.get("command").getAsString() : "";
        if (command.isBlank()) {
            err(ex, 400, "缺少 command 字段");
            return;
        }
        Boolean done = plugin.onMainThread(
                () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command), 5000);
        if (done == null) {
            err(ex, 504, "主线程执行超时");
            return;
        }
        JsonObject o = ok();
        o.addProperty("executed", done);
        send(ex, 200, o);
    }

    private void reload(HttpExchange ex) throws IOException {
        // 注意：Bukkit 的 "reload" 是重载插件；数据包热重载用命名空间命令 minecraft:reload
        Boolean done = plugin.onMainThread(
                () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "minecraft:reload"), 10000);
        if (done == null) {
            err(ex, 504, "重载超时");
            return;
        }
        send(ex, 200, ok());
    }

    private void deploy(HttpExchange ex) throws IOException {
        String ns = sanitize(queryParam(ex, "ns"));
        // 体积上限（config max-deploy-mb，默认 64MB），防止异常请求占满内存
        long maxBytes = plugin.getMaxDeployMb() * 1024L * 1024L;
        String declared = ex.getRequestHeaders().getFirst("Content-Length");
        if (declared != null && Long.parseLong(declared) > maxBytes) {
            err(ex, 413, "数据包超过 " + plugin.getMaxDeployMb() + "MB 上限");
            return;
        }
        byte[] zip = ex.getRequestBody().readAllBytes();
        if (zip.length == 0) {
            err(ex, 400, "请求体为空");
            return;
        }
        if (zip.length > maxBytes) {
            err(ex, 413, "数据包超过 " + plugin.getMaxDeployMb() + "MB 上限");
            return;
        }
        Path dir = datapacksDir();
        if (dir == null) {
            err(ex, 503, "世界尚未加载");
            return;
        }
        Files.createDirectories(dir);
        Path target = dir.resolve("packweaver-" + ns + ".zip");
        // 同名重复部署前自动备份旧包（保留 backups-keep 份）
        if (Files.exists(target)) {
            backupCopy(dir, ns, target);
        }
        // 先写临时文件再原子替换，避免重载时读到半截 zip；
        // 已启用数据包的 zip 被服务器持有 —— 先 disable 释放句柄，替换后 enable
        Path tmpFile = dir.resolve("packweaver-" + ns + ".zip.tmp");
        Files.write(tmpFile, zip);
        boolean replaced = false;
        try {
            Files.move(tmpFile, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException locked) {
            plugin.onMainThread(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "datapack disable \"file/packweaver-" + ns + ".zip\""), 10000);
            Files.deleteIfExists(target);
            try {
                Files.move(tmpFile, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                        java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.move(tmpFile, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            plugin.onMainThread(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    "datapack enable \"file/packweaver-" + ns + ".zip\""), 10000);
            replaced = true;
        }
        Boolean done = plugin.onMainThread(
                () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "minecraft:reload"), 30000);
        JsonObject o = ok();
        o.addProperty("deployed", target.getFileName().toString());
        o.addProperty("bytes", zip.length);
        o.addProperty("replaced", replaced);
        o.addProperty("reloaded", done != null && done);
        send(ex, 200, o);
        plugin.getLogger().info("[Bridge] 部署 " + target.getFileName() + "（" + zip.length + " 字节）");
    }

    private static String queryParam(HttpExchange ex, String key) {
        String q = ex.getRequestURI().getQuery();
        if (q == null) {
            return "";
        }
        for (String kv : q.split("&")) {
            if (kv.startsWith(key + "=")) {
                return kv.substring(key.length() + 1);
            }
        }
        return "";
    }

    /** 备份旧部署包到 plugins/PackWeaverSync/backups/&lt;ns&gt;/，超量清理。 */
    private void backupCopy(Path dir, String ns, Path target) throws IOException {
        Path bdir = plugin.getDataFolder().toPath().resolve("backups").resolve(ns);
        Files.createDirectories(bdir);
        String stamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
        Files.copy(target, bdir.resolve(stamp + ".zip"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        List<Path> files = new java.util.ArrayList<>();
        try (Stream<Path> s = Files.list(bdir)) {
            s.filter(p -> p.getFileName().toString().matches("\\d{8}_\\d{6}\\.zip")).forEach(files::add);
        }
        files.sort(java.util.Comparator.comparing(p -> p.getFileName().toString()));
        int extra = files.size() - plugin.getBackupsKeep();
        for (int i = 0; i < extra; i++) {
            Files.deleteIfExists(files.get(i));
        }
    }

    /** GET /pw/backups?ns=xx —— 列出该命名空间的部署备份。 */
    private void backups(HttpExchange ex) throws IOException {
        String ns = sanitize(queryParam(ex, "ns"));
        Path bdir = plugin.getDataFolder().toPath().resolve("backups").resolve(ns);
        JsonObject o = ok();
        JsonArray arr = new JsonArray();
        if (Files.isDirectory(bdir)) {
            try (Stream<Path> s = Files.list(bdir)) {
                s.map(p -> p.getFileName().toString())
                        .filter(n -> n.matches("\\d{8}_\\d{6}\\.zip"))
                        .sorted(java.util.Comparator.reverseOrder())
                        .forEach(arr::add);
            }
        }
        o.add("backups", arr);
        send(ex, 200, o);
    }

    /** GET /pw/backup?ns=xx&file=yyyyMMdd_HHmmss.zip —— 下载指定备份。 */
    private void backupDownload(HttpExchange ex) throws IOException {
        String ns = sanitize(queryParam(ex, "ns"));
        String file = queryParam(ex, "file");
        if (!file.matches("\\d{8}_\\d{6}\\.zip")) {
            err(ex, 400, "备份文件名不合法");
            return;
        }
        Path bdir = plugin.getDataFolder().toPath().resolve("backups").resolve(ns);
        Path target = bdir.resolve(file).normalize();
        if (!target.startsWith(bdir) || !Files.exists(target)) {
            err(ex, 404, "备份不存在");
            return;
        }
        byte[] data = Files.readAllBytes(target);
        ex.getResponseHeaders().add("Content-Type", "application/zip");
        ex.sendResponseHeaders(200, data.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(data);
        }
    }

    /** 命名空间/文件名白名单清洗，防路径穿越。 */
    private static String sanitize(String raw) {
        String s = (raw == null || raw.isBlank()) ? "project" : raw;
        String cleaned = s.replaceAll("[^a-zA-Z0-9_-]", "");
        return cleaned.isEmpty() ? "project" : cleaned.substring(0, Math.min(cleaned.length(), 32));
    }

    private static JsonObject readJson(HttpExchange ex) throws IOException {
        String raw = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (raw.isBlank()) {
            return new JsonObject();
        }
        try {
            return GSON.fromJson(raw, JsonObject.class);
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    private static void cors(HttpExchange ex) {
        var h = ex.getResponseHeaders();
        h.add("Access-Control-Allow-Origin", "*");
        h.add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        h.add("Access-Control-Allow-Headers", "Content-Type");
    }

    private static JsonObject ok() {
        JsonObject o = new JsonObject();
        o.addProperty("ok", true);
        return o;
    }

    private static void err(HttpExchange ex, int code, String msg) throws IOException {
        JsonObject o = new JsonObject();
        o.addProperty("ok", false);
        o.addProperty("error", msg);
        send(ex, code, o);
    }

    private static void send(HttpExchange ex, int code, JsonObject body) throws IOException {
        byte[] data = GSON.toJson(body).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, data.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(data);
        }
    }
}
