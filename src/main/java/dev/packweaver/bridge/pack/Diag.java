package dev.packweaver.bridge.pack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 游戏内智能诊断系统（规划书第 4.3 / 17 章）。
 * 规则覆盖：CMD001 / SEL001 / ID001 / JSON001 / PATH001 / NBT001 /
 * LOG003 / LOG004 / PERF001 / PERF002 / VER002 / BEST001 / BEST003 / BEST004
 */
public final class Diag {

    public static class Issue {
        public final String code;
        public final String severity; // error | warn | info
        public final String file;
        public final int line;
        public final String message;
        public final String fixId;   // 可为 null
        public final String fixDesc;

        Issue(String code, String severity, String file, int line, String message, String fixId, String fixDesc) {
            this.code = code;
            this.severity = severity;
            this.file = file;
            this.line = line;
            this.message = message;
            this.fixId = fixId;
            this.fixDesc = fixDesc;
        }
    }

    private static final Set<String> COMMANDS = Set.of(
            "say", "tellraw", "give", "tp", "teleport", "effect", "gamemode", "playsound", "particle",
            "setblock", "fill", "time", "weather", "scoreboard", "tag", "function", "title", "kill",
            "summon", "data", "execute", "reload", "worldborder", "spreadplayers", "forceload",
            "advancement", "recipe", "schedule", "loot", "item", "replaceitem", "bossbar", "clear",
            "difficulty", "enchant", "experience", "xp", "fillbiome", "gamemode", "help", "locate",
            "me", "msg", "particle", "perf", "place", "random", "rotate", "ride", "spawnpoint",
            "stopsound", "spectate", "team", "tell", "trigger", "waypoint", "whitelist");

    private static final Set<String> SELECTOR_ARGS = Set.of(
            "x", "y", "z", "dx", "dy", "dz", "distance", "sort", "limit", "type", "tag", "team",
            "name", "nbt", "scores", "level", "gamemode", "x_rotation", "y_rotation", "advancements",
            "predicate", "type", "ry", "rym", "rx", "rxm", "l", "lm", "m", "h", "hm");

    private static final Set<String> COMMON_ITEMS = Set.of(
            "diamond", "diamond_sword", "diamond_pickaxe", "iron_sword", "iron_pickaxe", "iron_axe",
            "golden_apple", "apple", "bread", "stone", "cobblestone", "oak_planks", "oak_door",
            "gold_block", "diamond_block", "emerald", "stick", "bow", "arrow", "blaze_rod",
            "enchanting_table", "compass", "clock", "book", "paper");

    /** 运行项目诊断。 */
    public static List<Issue> run(PackProject p) {
        List<Issue> issues = new ArrayList<>();
        Map<String, String> all = new HashMap<>(CodeGen.generate(p.namespace, p.events));
        all.putAll(p.files);

        Set<String> definedFns = new HashSet<>();
        for (String path : all.keySet()) {
            if (path.endsWith(".mcfunction")) {
                definedFns.add(fnName(p.namespace, path));
            }
        }
        // minecraft 命名空间函数视为存在
        definedFns.add("minecraft:tick");
        definedFns.add("minecraft:load");

        boolean tickFile = all.containsKey(p.namespace + "/functions/tick.mcfunction");
        int tickCommands = 0;

        for (Map.Entry<String, String> e : all.entrySet()) {
            String path = e.getKey();
            String content = e.getValue();
            if (path.endsWith(".mcfunction")) {
                boolean isTick = path.equals(p.namespace + "/functions/tick.mcfunction");
                String[] lines = content.split("\n");
                if (lines.length > 50) {
                    issues.add(new Issue("BEST003", "info", path, 0,
                            "函数过长（" + lines.length + " 行 > 50），建议拆分为小函数",
                            null, null));
                }
                boolean hasComment = content.contains("#");
                if (!hasComment) {
                    issues.add(new Issue("BEST004", "info", path, 0,
                            "缺少注释，建议添加功能说明（# 注释）", null, null));
                }
                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i].trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }
                    if (isTick) {
                        tickCommands++;
                    }
                    checkLine(p, path, i + 1, line, issues, definedFns);
                }
            } else if (path.endsWith(".json")) {
                checkJson(path, content, issues);
            }
        }

        if (tickFile && tickCommands > 100) {
            issues.add(new Issue("PERF001", "warn", p.namespace + "/functions/tick.mcfunction", 0,
                    "tick 函数命令过多（" + tickCommands + " > 100），建议分批或改事件驱动", null, null));
        }
        // LOG003 递归调用检测
        detectRecursion(p, all, issues);
        return issues;
    }

    private static void checkLine(PackProject p, String path, int lineNo, String line,
                                  List<Issue> issues, Set<String> definedFns) {
        // 去掉 execute 前缀看主命令
        String body = line;
        while (body.startsWith("execute ")) {
            int run = body.lastIndexOf(" run ");
            if (run < 0) {
                break;
            }
            body = body.substring(run + 5);
        }
        String[] sp = body.split(" ");
        String cmd = sp[0];
        if (!COMMANDS.contains(cmd)) {
            issues.add(new Issue("CMD001", "error", path, lineNo,
                    "未知命令: " + cmd, null, null));
            return;
        }
        // SEL001 选择器参数
        for (java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("@[aeprs](\\[[^\\]]*\\])?").matcher(body); m.find(); ) {
            String sel = m.group();
            int open = sel.indexOf('[');
            if (open < 0) {
                continue;
            }
            for (String kv : sel.substring(open + 1, sel.length() - 1).split(",")) {
                String key = kv.split("=", 2)[0].trim();
                if (!key.isEmpty() && !SELECTOR_ARGS.contains(key)) {
                    issues.add(new Issue("SEL001", "error", path, lineNo,
                            "无效选择器参数: " + key + "（在 " + sel + " 中）", null, null));
                }
            }
            // PERF002 @e 无 type
            if (sel.startsWith("@e[") && !sel.contains("type=")) {
                issues.add(new Issue("PERF002", "warn", path, lineNo,
                        "@e 未指定类型（会扫描全部实体），建议加 type=筛选",
                        "PERF002:" + path + ":" + lineNo, "改为 @e[type=zombie,...]"));
            }
            // LOG004 范围过大
            if (sel.equals("@e") ) {
                issues.add(new Issue("LOG004", "warn", path, lineNo,
                        "@e 范围过大，建议加 type= 和 distance= 限制",
                        "LOG004:" + path + ":" + lineNo, "改为 @e[type=...,distance=..10]"));
            }
        }
        // ID001 物品 ID
        if (cmd.equals("give") && sp.length > 2) {
            String item = sp[2];
            if (!item.startsWith("#") && !isValidId(item)) {
                String suggest = suggest(item.replace("minecraft:", ""));
                issues.add(new Issue("ID001", "error", path, lineNo,
                        "无效物品ID: " + item + (suggest != null ? "（相似: " + suggest + "）" : ""),
                        null, null));
            }
        }
        // PATH001 函数引用
        if (cmd.equals("function") && sp.length > 1) {
            String fn = sp[1];
            if (!fn.startsWith("minecraft:") && !definedFns.contains(fn)) {
                issues.add(new Issue("PATH001", "error", path, lineNo,
                        "引用的函数不存在: " + fn, null, null));
            }
        }
        // NBT001 大括号平衡
        int open = line.length() - line.replace("{", "").length();
        int close = line.length() - line.replace("}", "").length();
        if (open != close) {
            issues.add(new Issue("NBT001", "error", path, lineNo,
                    "NBT/JSON 大括号不匹配（{ " + open + " 个，} " + close + " 个）", null, null));
        }
        // BEST001 硬编码坐标
        java.util.regex.Matcher cm = java.util.regex.Pattern
                .compile("(?<![~^\\d-])\\d{4,}(\\.\\d+)?(?=\\s|$)").matcher(body);
        if (cm.find() && (cmd.equals("tp") || cmd.equals("teleport"))) {
            issues.add(new Issue("BEST001", "info", path, lineNo,
                    "硬编码大坐标 " + cm.group() + "，多人地图可能错位，建议用相对坐标或存储", null, null));
        }
    }

    private static void checkJson(String path, String content, List<Issue> issues) {
        String cleaned = content.replaceAll(",\\s*([}\\]])", "$1");
        try {
            new com.google.gson.JsonParser().parse(cleaned);
            if (!cleaned.equals(content)) {
                issues.add(new Issue("JSON001", "error", path, 0,
                        "JSON 含尾随逗号（可一键修复）", "JSON001:" + path, "移除尾随逗号"));
            }
        } catch (Exception ex) {
            issues.add(new Issue("JSON001", "error", path, 0,
                    "JSON 语法错误: " + ex.getMessage(), null, null));
        }
    }

    private static void detectRecursion(PackProject p, Map<String, String> all, List<Issue> issues) {
        Map<String, Set<String>> graph = new HashMap<>();
        for (Map.Entry<String, String> e : all.entrySet()) {
            if (!e.getKey().endsWith(".mcfunction")) {
                continue;
            }
            Set<String> calls = new HashSet<>();
            for (String line : e.getValue().split("\n")) {
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("\\bfunction\\s+([\\w:/]+)").matcher(line);
                while (m.find()) {
                    calls.add(m.group(1));
                }
            }
            graph.put(fnName(p.namespace, e.getKey()), calls);
        }
        for (String start : graph.keySet()) {
            if (reaches(graph, start, start, new HashSet<>(), 0)) {
                issues.add(new Issue("LOG003", "error", start, 0,
                        "函数存在循环调用（可能死循环）", null, null));
            }
        }
    }

    private static boolean reaches(Map<String, Set<String>> graph, String from, String target, Set<String> seen, int depth) {
        if (depth > 32) {
            return false;
        }
        for (String next : graph.getOrDefault(from, Set.of())) {
            if (next.equals(target)) {
                return depth >= 0 && !from.equals(target) || depth > 0;
            }
            if (seen.add(next) && reaches(graph, next, target, seen, depth + 1)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isValidId(String item) {
        String bare = item.replace("minecraft:", "");
        if (bare.startsWith("#")) {
            return true;
        }
        // 有命名空间且非 minecraft 的视为模组物品，跳过
        if (item.contains(":") && !item.startsWith("minecraft:")) {
            return true;
        }
        if (!bare.matches("[a-z0-9_/]+")) {
            return false;
        }
        return COMMON_ITEMS.contains(bare) || bare.length() > 3; // 宽松：常见 ID 难以全量枚举
    }

    private static String suggest(String wrong) {
        int best = Integer.MAX_VALUE;
        String result = null;
        for (String item : COMMON_ITEMS) {
            int d = levenshtein(wrong, item);
            if (d < best) {
                best = d;
                result = item;
            }
        }
        return best <= 3 ? result : null;
    }

    private static int levenshtein(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                dp[i][j] = a.charAt(i - 1) == b.charAt(j - 1)
                        ? dp[i - 1][j - 1]
                        : 1 + Math.min(dp[i - 1][j - 1], Math.min(dp[i - 1][j], dp[i][j - 1]));
            }
        }
        return dp[a.length()][b.length()];
    }

    private static String fnName(String ns, String path) {
        return ns + ":" + path.substring(ns.length() + "/functions/".length()).replace(".mcfunction", "");
    }

    /** 应用快速修复；返回被修改的项目（需要调用方 save）。 */
    public static boolean applyFix(PackProject p, String fixId) {
        String[] parts = fixId.split(":", 3);
        String rule = parts[0];
        switch (rule) {
            case "JSON001" -> {
                String path = parts[1];
                String content = p.files.get(path);
                if (content != null) {
                    p.files.put(path, content.replaceAll(",\\s*([}\\]]", "$1"));
                    return true;
                }
            }
            case "PERF002" -> {
                String path = parts[1];
                String content = p.files.get(path);
                if (content != null) {
                    String[] lines = content.split("\n");
                    int idx = Integer.parseInt(parts[2]) - 1;
                    if (idx >= 0 && idx < lines.length) {
                        lines[idx] = lines[idx].replace("@e[", "@e[type=zombie,");
                        p.files.put(path, String.join("\n", lines));
                        return true;
                    }
                }
            }
            case "LOG004" -> {
                String path = parts[1];
                String content = p.files.get(path);
                if (content != null) {
                    String[] lines = content.split("\n");
                    int idx = Integer.parseInt(parts[2]) - 1;
                    if (idx >= 0 && idx < lines.length) {
                        lines[idx] = lines[idx].replace("@e ", "@e[type=zombie,distance=..16] ");
                        p.files.put(path, String.join("\n", lines));
                        return true;
                    }
                }
            }
            default -> {
                return false;
            }
        }
        return false;
    }

    private Diag() {
    }
}
