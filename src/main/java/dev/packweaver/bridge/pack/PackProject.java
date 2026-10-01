package dev.packweaver.bridge.pack;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 项目模型：一个项目 = 存档 datapacks/ 下的一份数据包 + 积木 AST 存储。
 * 事件积木保存于 packweaver.json（MC 会忽略该文件），保证双模式共享同一数据源。
 */
public class PackProject {
    public static final String META_FILE = "packweaver.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public String name;
    public String namespace;
    /** 事件积木列表（每事件一棵树），双模式共享 */
    public List<BlockNode> events = new ArrayList<>();
    /** 手写文件（相对 data/ 路径 → 内容），IDE 模式直接编辑 */
    public Map<String, String> files = new TreeMap<>();
    /** 日志断点（规划书 16.2）：文件路径 → 1 起始行号列表 */
    public Map<String, List<Integer>> debugLines = new TreeMap<>();
    /** 条件断点（16.2 条件断点）：文件路径 → (行号字符串 → execute if 条件) */
    public Map<String, Map<String, String>> debugConds = new TreeMap<>();
    /** 函数执行轨迹（规划书 16.3）：开启后每个函数入口注入一条轨迹输出 */
    public boolean traceMode = false;

    private static Path datapacksDir() {
        var server = MinecraftClient.getInstance().getServer();
        if (server == null) {
            return null;
        }
        return server.getSavePath(WorldSavePath.DATAPACKS);
    }

    public static Path projectDir(String ns) {
        Path dir = datapacksDir();
        return dir == null ? null : dir.resolve("packweaver-" + ns);
    }

    public static PackProject load(String ns) throws IOException {
        Path dir = projectDir(ns);
        if (dir == null || !Files.exists(dir.resolve(META_FILE))) {
            throw new IOException("项目不存在: " + ns);
        }
        PackProject p = GSON.fromJson(Files.readString(dir.resolve(META_FILE)), PackProject.class);
        if (p.events == null) {
            p.events = new ArrayList<>();
        }
        if (p.files == null) {
            p.files = new TreeMap<>();
        }
        if (p.debugLines == null) {
            p.debugLines = new TreeMap<>();
        }
        if (p.debugConds == null) {
            p.debugConds = new TreeMap<>();
        }
        return p;
    }

    /** 列出存档中的全部 PackWeaver 项目命名空间。 */
    public static List<String> listProjects() {
        List<String> out = new ArrayList<>();
        Path dir = datapacksDir();
        if (dir == null || !Files.isDirectory(dir)) {
            return out;
        }
        try (var stream = Files.newDirectoryStream(dir, "packweaver-*")) {
            for (Path p : stream) {
                if (Files.exists(p.resolve(META_FILE))) {
                    out.add(p.getFileName().toString().substring("packweaver-".length()));
                }
            }
        } catch (IOException ignored) {
        }
        return out;
    }

    /** 保存：写出 pack.mcmeta、事件积木生成的函数、手写文件、AST 元数据。 */
    public void save() throws IOException {
        Path dir = projectDir(namespace);
        if (dir == null) {
            throw new IOException("未进入世界（找不到存档 datapacks 目录）");
        }
        // 积木 → 函数
        Map<String, String> generated = CodeGen.generate(namespace, events);
        // 手写文件覆盖同名生成文件（保证 IDE 模式修改不丢失）
        Map<String, String> all = new TreeMap<>(generated);
        all.putAll(files);

        writeIfChanged(dir.resolve("pack.mcmeta"), packMcmeta());
        for (Map.Entry<String, String> e : all.entrySet()) {
            // 调试注入（规划书 16.2/16.3）：日志断点 + 函数入口轨迹；
            // 输出仅对携带 pw_debugger 标签的玩家可见（16.6 单人调试）
            writeIfChanged(dir.resolve("data").resolve(e.getKey()), applyDebug(e.getKey(), e.getValue()));
        }
        Files.writeString(dir.resolve(META_FILE), GSON.toJson(this));
    }

    /** 行号断点 + 轨迹注入；倒序插入保证行号不漂移。 */
    private String applyDebug(String path, String content) {
        if (!path.endsWith(".mcfunction")) {
            return content;
        }
        String fn = namespace + ":" + path
                .substring((namespace + "/functions/").length())
                .replace(".mcfunction", "");
        List<String> lines = new ArrayList<>(List.of(content.split("\n", -1)));
        List<Integer> marks = debugLines.get(path);
        if (marks != null && !marks.isEmpty()) {
            List<Integer> sorted = new ArrayList<>(marks);
            sorted.sort(java.util.Comparator.reverseOrder());
            for (int m : sorted) {
                int idx = m - 1;
                if (idx >= 0 && idx < lines.size()) {
                    Map<String, String> conds = debugConds.get(path);
                    String cond = conds != null ? conds.get(String.valueOf(m)) : null;
                    lines.add(idx, debugTell("行" + m + " " + fn, "gold", cond));
                }
            }
        }
        if (traceMode) {
            lines.add(0, debugTell("→ " + fn, "yellow", null));
        }
        return String.join("\n", lines);
    }

    private String debugTell(String msg, String color, String cond) {
        String condPart = (cond == null || cond.isBlank()) ? "" : "if " + cond.trim() + " ";
        return "execute " + condPart + "if entity @a[tag=pw_debugger] run tellraw @a[tag=pw_debugger] "
                + "{\"text\":\"[PW] " + msg + "\",\"color\":\"" + color + "\"}";
    }

    private static String packMcmeta() {
        // 1.20.1 的 pack_format 为 15；后续版本可按需扩展
        int format = 15;
        return "{\"pack\": {\"pack_format\": " + format + ", \"description\": \"Generated by PackWeaver\"}}\n";
    }

    private static void writeIfChanged(Path target, String content) throws IOException {
        if (Files.exists(target) && Files.readString(target).equals(content)) {
            return;
        }
        Files.createDirectories(target.getParent());
        Files.writeString(target, content, StandardCharsets.UTF_8);
    }

    /** 读取项目内某个函数的当前代码（用于 IDE 模式打开）。 */
    public String readFunction(String fn) throws IOException {
        return readRaw(namespace + "/functions/" + fn + ".mcfunction");
    }

    /** 读取项目内任意数据文件（相对 data/ 的路径，如 ns/advancements/join.json）。 */
    public String readRaw(String relPath) throws IOException {
        Path dir = projectDir(namespace);
        Path f = dir.resolve("data").resolve(relPath);
        if (Files.exists(f)) {
            return Files.readString(f);
        }
        return files.getOrDefault(relPath, "");
    }

    /** IDE 模式保存函数：写文件并把该函数标记为手写（技术模式会显示为代码已自定义）。 */
    public void writeFunction(String fn, String content) throws IOException {
        writeRaw(namespace + "/functions/" + fn + ".mcfunction", content);
    }

    /** 保存任意数据文件（函数/JSON 等）。 */
    public void writeRaw(String relPath, String content) throws IOException {
        files.put(relPath, content);
        save();
    }

    /** 项目内全部文件（生成 + 手写），相对 data/ 的路径。 */
    public java.util.Set<String> allFiles() {
        java.util.Set<String> out = new java.util.TreeSet<>(CodeGen.generate(namespace, events).keySet());
        out.addAll(files.keySet());
        return out;
    }

    /** 导出 zip（pack.mcmeta 在 zip 根，data/ 在下）。返回文件路径。 */
    public Path exportZip() throws IOException {
        Path dir = projectDir(namespace);
        Path out = dir.getParent().resolve("PackWeaver-" + namespace + "-export.zip");
        Map<String, String> all = new TreeMap<>(CodeGen.generate(namespace, events));
        all.putAll(files);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(out))) {
            zip.putNextEntry(new ZipEntry("pack.mcmeta"));
            zip.write(packMcmeta().getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            for (Map.Entry<String, String> e : all.entrySet()) {
                zip.putNextEntry(new ZipEntry("data/" + e.getKey()));
                zip.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return out;
    }

    public void delete() throws IOException {
        Path dir = projectDir(namespace);
        if (dir != null && Files.exists(dir)) {
            try (var walk = Files.walk(dir)) {
                walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException ignored) {
                    }
                });
            }
        }
    }

    public static Gson gson() {
        return GSON;
    }

    public static TypeToken<PackProject> type() {
        return new TypeToken<>() {
        };
    }
}
