package dev.packweaver.bridge.pack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 积木 AST ⇄ mcfunction 双向转换（规划书第 1.3.2 / 11 章双模式同步）。
 *
 * 语义：
 * - event_load 体在服务器上以控制台身份执行
 * - event_tick / event_join / event_death 体对每个玩家执行（@s = 该玩家）
 * - ctrl_if / ctrl_foreach 的子分支拆分为独立函数，保证生成的是合法 mcfunction
 */
public final class CodeGen {

    /** event 类型 → 事件函数名 */
    public static String eventFunction(String type) {
        return switch (type) {
            case "event_tick" -> "tick";
            case "event_load" -> "load";
            case "event_join" -> "on_join";
            case "event_death" -> "on_death";
            default -> null;
        };
    }

    /**
     * 生成整个项目的 data 文件（相对 data/ 的路径 → 内容）。
     * blocks: 事件积木列表（每个事件一棵树）。
     */
    public static Map<String, String> generate(String ns, List<BlockNode> events) {
        Map<String, String> out = new HashMap<>();
        int[] counter = {0};
        boolean hasTick = false, hasLoad = false, hasJoin = false, hasDeath = false;

        for (BlockNode ev : events) {
            String fn = eventFunction(ev.type);
            if (fn == null) {
                continue;
            }
            List<String> lines = new ArrayList<>();
            emitBody(ns, fn, ev.children, lines, out, counter);
            out.put(ns + "/functions/" + fn + ".mcfunction", join(lines));

            switch (ev.type) {
                case "event_tick" -> hasTick = true;
                case "event_load" -> hasLoad = true;
                case "event_join" -> {
                    hasJoin = true;
                    // 进度触发器（规划书 A.1.3），配 joined 标签防重复
                    out.put(ns + "/advancements/join.json", join(List.of(
                            "{",
                            "  \"criteria\": { \"join\": { \"trigger\": \"minecraft:tick\" } },",
                            "  \"rewards\": { \"function\": \"" + ns + ":on_join\" }",
                            "}")));
                    out.put(ns + "/functions/on_join_guard.mcfunction", join(List.of(
                            "execute unless entity @s[tag=pw_joined_" + ns + "] run function " + ns + ":on_join_first",
                            "tag @s add pw_joined_" + ns)));
                    out.put(ns + "/functions/on_join_first.mcfunction", join(List.of(
                            "function " + ns + ":on_join")));
                }
                case "event_death" -> {
                    hasDeath = true;
                    out.put(ns + "/advancements/death.json", join(List.of(
                            "{",
                            "  \"criteria\": { \"death\": { \"trigger\": \"minecraft:entity_killed_player\" } },",
                            "  \"rewards\": { \"function\": \"" + ns + ":on_death\" }",
                            "}")));
                }
                default -> {
                }
            }
        }

        if (hasTick) {
            out.put("minecraft/tags/functions/tick.json", "{\"values\": [\"" + ns + ":tick\"]}");
        }
        if (hasLoad) {
            out.put("minecraft/tags/functions/load.json", "{\"values\": [\"" + ns + ":load\"]}");
        }
        // join/death 的实际入口换成 guard 版本，避免 tick 进度每 tick 重复触发
        if (hasJoin) {
            Map<String, String> patched = new HashMap<>();
            out.forEach((k, v) -> patched.put(k,
                    k.equals(ns + "/advancements/join.json")
                            ? v.replace(ns + ":on_join\"", ns + ":on_join_guard\"")
                            : v));
            out.putAll(patched);
        }
        return out;
    }

    private static void emitBody(String ns, String fn, List<BlockNode> nodes, List<String> lines,
                                 Map<String, String> out, int[] counter) {
        for (BlockNode n : nodes) {
            emitOne(ns, fn, n, lines, out, counter);
        }
    }

    private static void emitOne(String ns, String fn, BlockNode n, List<String> lines,
                                Map<String, String> out, int[] counter) {
        String c = commandOf(n);
        switch (n.type) {
            case "ctrl_if" -> {
                String cond = conditionsOf(n);
                String thenFn = branchFn(fn, counter);
                List<String> thenLines = new ArrayList<>();
                emitBody(ns, thenFn, n.children, thenLines, out, counter);
                out.put(ns + "/functions/" + thenFn + ".mcfunction", join(thenLines));
                lines.add("execute if " + cond + " run function " + ns + ":" + thenFn);
                if (!n.elseChildren.isEmpty()) {
                    String elseFn = branchFn(fn, counter);
                    List<String> elseLines = new ArrayList<>();
                    emitBody(ns, elseFn, n.elseChildren, elseLines, out, counter);
                    out.put(ns + "/functions/" + elseFn + ".mcfunction", join(elseLines));
                    lines.add("execute unless " + cond + " run function " + ns + ":" + elseFn);
                }
            }
            case "ctrl_foreach" -> {
                String sel = n.p("filter").equals("带标签玩家") ? "@a[tag=" + n.p("tag", "pw_all") + "]" : "@a";
                String eachFn = branchFn(fn, counter);
                List<String> eachLines = new ArrayList<>();
                emitBody(ns, eachFn, n.children, eachLines, out, counter);
                out.put(ns + "/functions/" + eachFn + ".mcfunction", join(eachLines));
                lines.add("execute as " + sel + " at @s run function " + ns + ":" + eachFn);
            }
            case "act_objective" -> {
                lines.add("scoreboard objectives add " + n.p("obj") + " dummy "
                        + jsonText(n.p("name", n.p("obj")), "白色"));
                String slot = n.p("slot");
                if (!slot.equals("无")) {
                    lines.add("scoreboard objectives setdisplay " + slot + " " + n.p("obj"));
                }
            }
            default -> {
                if (!c.isBlank()) {
                    lines.add(c);
                }
            }
        }
    }

    /** 积木 → 单条命令。 */
    public static String commandOf(BlockNode n) {
        return switch (n.type) {
            case "act_send" -> {
                String text = jsonText(n.p("text"), n.p("color", "白色"));
                yield switch (n.p("pos", "聊天栏")) {
                    case "标题" -> "title " + n.p("target") + " title " + text;
                    case "动作栏" -> "title " + n.p("target") + " actionbar " + text;
                    default -> "tellraw " + n.p("target") + " " + text;
                };
            }
            case "act_give" -> "give " + n.p("target") + " " + n.p("item") + " " + n.p("count", "1");
            case "act_tp" -> "tp " + n.p("target") + " " + n.p("x") + " " + n.p("y") + " " + n.p("z");
            case "act_effect" -> "effect give " + n.p("target") + " " + n.p("effect") + " "
                    + n.p("seconds", "10") + " " + n.p("amp", "0");
            case "act_clear_effect" -> "effect clear " + n.p("target");
            case "act_gamemode" -> "gamemode " + n.p("mode") + " " + n.p("target");
            case "act_playsound" -> "playsound " + n.p("sound") + " master " + n.p("target") + " ~ ~ ~ "
                    + n.p("volume", "1.0") + " " + n.p("pitch", "1.0");
            case "act_particle" -> "particle " + n.p("particle") + " " + n.p("x", "~") + " " + n.p("y", "~") + " "
                    + n.p("z", "~") + " 0.5 0.5 0.5 0.05 " + n.p("count", "20");
            case "act_setblock" -> "setblock " + n.p("x", "~") + " " + n.p("y", "~") + " " + n.p("z", "~") + " " + n.p("block");
            case "act_time" -> "time set " + n.p("value");
            case "act_weather" -> "weather " + n.p("weather");
            case "act_score_set" -> {
                String op = switch (n.p("op", "设置")) {
                    case "增加" -> "add";
                    case "减少" -> "remove";
                    default -> "set";
                };
                yield "scoreboard players " + op + " " + n.p("target") + " " + n.p("obj") + " " + n.p("value", "1");
            }
            case "act_tag" -> {
                String op = n.p("op").equals("移除") ? "remove" : "add";
                yield "tag " + n.p("target") + " " + op + " " + n.p("tag");
            }
            case "act_call" -> "function " + n.p("fn");
            case "act_note" -> "# " + n.p("text");
            case "act_cmd", "custom" -> n.p("cmd");
            default -> "";
        };
    }

    /** if 积木的所有条件子积木合成 execute 条件串。 */
    public static String conditionsOf(BlockNode ifNode) {
        List<String> parts = new ArrayList<>();
        for (BlockNode c : ifNode.children) {
            if (isCondition(c)) {
                parts.add(conditionOf(c));
            }
        }
        if (parts.isEmpty()) {
            return "entity @s";
        }
        return String.join(" if ", parts).replaceFirst("^", "");
    }

    public static boolean isCondition(BlockNode n) {
        BlockNode test = n;
        return test.type.startsWith("cond_");
    }

    public static String conditionOf(BlockNode c) {
        return switch (c.type) {
            case "cond_area" -> "entity @s[x=" + c.p("x") + ",y=" + c.p("y") + ",z=" + c.p("z")
                    + ",dx=" + c.p("dx", "2") + ",dy=" + c.p("dy", "2") + ",dz=" + c.p("dz", "2") + "]";
            case "cond_item" -> "entity @s[nbt={SelectedItem:{id:\"" + id(c.p("item")) + "\"}}]";
            case "cond_tag" -> "entity @s[tag=" + c.p("tag") + "]";
            case "cond_score" -> "score @s " + c.p("obj") + " matches " + scoreRange(c.p("op", "≥"), c.p("value", "10"));
            case "cond_block" -> "block " + c.p("x", "~") + " " + c.p("y", "~-1") + " " + c.p("z", "~") + " " + id(c.p("block"));
            default -> "entity @s";
        };
    }

    private static String scoreRange(String op, String value) {
        return switch (op) {
            case "≥" -> value + "..";
            case "≤" -> ".." + value;
            case ">" -> (Integer.parseInt(value) + 1) + "..";
            case "<" -> ".." + (Integer.parseInt(value) - 1);
            default -> value;
        };
    }

    private static String id(String raw) {
        return raw.contains(":") ? raw : "minecraft:" + raw;
    }

    private static final Map<String, String> COLORS = Map.of(
            "白色", "white", "金色", "gold", "红色", "red", "绿色", "green", "水色", "aqua", "紫色", "light_purple");

    public static String jsonText(String s, String colorZh) {
        String color = COLORS.getOrDefault(colorZh, "white");
        return "{\"text\":\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\",\"color\":\"" + color + "\"}";
    }

    private static String branchFn(String parent, int[] counter) {
        counter[0]++;
        return parent + "/b" + counter[0];
    }

    private static String join(List<String> lines) {
        // 过滤空行并保留注释
        List<String> out = new ArrayList<>();
        for (String l : lines) {
            if (!l.isBlank()) {
                out.add(l);
            }
        }
        return out.isEmpty() ? "# (空函数)" : String.join("\n", out) + "\n";
    }

    // ---------------- mcfunction → 积木（可解析子集，其余转自定义代码块） ----------------

    private static final Pattern SIMPLE = Pattern.compile(
            "^(tellraw|give|tp|effect give|effect clear|gamemode|playsound|particle|setblock|time set|weather|"
                    + "scoreboard players (?:set|add|remove)|tag \\S+ (?:add|remove)|function|say|title) (.*)$");

    /** 把函数文件解析为积木列表；无法识别的行成为「自定义代码块」。 */
    public static List<BlockNode> parse(String mcfunction) {
        List<BlockNode> out = new ArrayList<>();
        for (String raw : mcfunction.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            BlockNode n = parseLine(line);
            out.add(n != null ? n : new BlockNode("custom", "cmd", raw));
        }
        return out;
    }

    private static BlockNode parseLine(String line) {
        Matcher m = SIMPLE.matcher(line);
        if (!m.matches()) {
            return null;
        }
        String head = m.group(1);
        String rest = m.group(2);
        if (line.startsWith("tellraw ")) {
            String[] sp = rest.split(" ", 2);
            if (sp.length == 2 && sp[1].startsWith("{\"text\"")) {
                String text = sp[1].replaceAll("^\\{\"text\":\"", "").replaceAll("\"}$", "");
                return new BlockNode("act_send", "target", sp[0], "text", text);
            }
        }
        if (line.startsWith("title ")) {
            String[] sp = rest.split(" ", 3);
            if (sp.length == 3 && sp[2].startsWith("{\"text\"")) {
                String text = sp[2].replaceAll("^\\{\"text\":\"", "").replaceAll("\"}$", "");
                return new BlockNode("act_send", "target", sp[0], "pos",
                        sp[1].equals("actionbar") ? "动作栏" : "标题", "text", text);
            }
        }
        if (line.startsWith("give ")) {
            String[] sp = rest.split(" ");
            if (sp.length >= 3) {
                return new BlockNode("act_give", "target", sp[0], "item", sp[1], "count", sp[2]);
            }
        }
        if (line.startsWith("tp ")) {
            String[] sp = rest.split(" ");
            if (sp.length >= 4) {
                return new BlockNode("act_tp", "target", sp[0], "x", sp[1], "y", sp[2], "z", sp[3]);
            }
        }
        if (line.startsWith("effect give ")) {
            String[] sp = rest.split(" ");
            if (sp.length >= 4) {
                return new BlockNode("act_effect", "target", sp[0], "effect", sp[1],
                        "seconds", sp[2], "amp", sp[3]);
            }
        }
        if (line.startsWith("function ")) {
            return new BlockNode("act_call", "fn", rest);
        }
        if (line.startsWith("tag ")) {
            String[] sp = rest.split(" ");
            if (sp.length == 3) {
                return new BlockNode("act_tag", "target", head.split(" ")[1], "tag", sp[2],
                        "op", sp[1].equals("remove") ? "移除" : "添加");
            }
        }
        if (line.startsWith("scoreboard players ")) {
            String[] sp = line.split(" ");
            if (sp.length >= 6) {
                return new BlockNode("act_score_set", "target", sp[3], "obj", sp[4], "value", sp[5],
                        "op", sp[2].equals("add") ? "增加" : sp[2].equals("remove") ? "减少" : "设置");
            }
        }
        return null;
    }

    private CodeGen() {
    }
}
