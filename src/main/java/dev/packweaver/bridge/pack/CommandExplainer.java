package dev.packweaver.bridge.pack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 命令逐段拆解（规划书第 6.2 章 / 扩展 C.1）：
 * 把 execute 链与常用命令切成片段，逐段中文解释 + 人话总结。
 */
public final class CommandExplainer {

    private static final Map<String, String> SUB = Map.ofEntries(
            Map.entry("execute", "改变命令的执行方式/执行者/执行位置"),
            Map.entry("as", "以指定实体为执行者（后面的命令就像它自己执行的）"),
            Map.entry("at", "在指定实体的位置执行"),
            Map.entry("positioned", "在指定坐标执行"),
            Map.entry("align", "坐标对齐到方块边界"),
            Map.entry("facing", "朝向指定方向"),
            Map.entry("rotated", "设置执行朝向"),
            Map.entry("in", "在指定维度执行"),
            Map.entry("anchored", "锚点设为眼睛/脚"),
            Map.entry("if", "条件满足才继续"),
            Map.entry("unless", "条件不满足才继续"),
            Map.entry("run", "执行真正的命令"));

    private static final Map<String, String> CMDS = Map.ofEntries(
            Map.entry("say", "发送聊天消息"),
            Map.entry("tellraw", "发送富文本消息"),
            Map.entry("give", "给予物品"),
            Map.entry("tp", "传送"),
            Map.entry("teleport", "传送"),
            Map.entry("effect", "给予/清除状态效果"),
            Map.entry("gamemode", "设置游戏模式"),
            Map.entry("playsound", "播放音效"),
            Map.entry("particle", "生成粒子"),
            Map.entry("setblock", "放置方块"),
            Map.entry("fill", "填充区域"),
            Map.entry("title", "显示标题/动作栏"),
            Map.entry("scoreboard", "计分板操作"),
            Map.entry("tag", "添加/移除标签"),
            Map.entry("function", "调用函数"),
            Map.entry("summon", "生成实体"),
            Map.entry("kill", "杀死实体"),
            Map.entry("time", "设置时间"),
            Map.entry("weather", "设置天气"),
            Map.entry("worldborder", "世界边界"),
            Map.entry("data", "读写 NBT 数据"),
            Map.entry("clear", "清除物品"),
            Map.entry("spreadplayers", "随机分散玩家"));

    /** 返回 [片段, 解释] 列表，最后一项是人话总结。 */
    public static List<String[]> explain(String rawCommand) {
        List<String[]> out = new ArrayList<>();
        String cmd = rawCommand.trim().replaceFirst("^/", "");
        if (cmd.isEmpty()) {
            out.add(new String[]{"（空）", "在上方输入框粘贴一条命令"});
            return out;
        }
        StringBuilder human = new StringBuilder("人话：");
        String[] tokens = cmd.split(" ");
        int i = 0;
        boolean inExecute = tokens[0].equals("execute");
        if (inExecute) {
            out.add(new String[]{"execute", SUB.get("execute")});
            i = 1;
            while (i < tokens.length) {
                String t = tokens[i];
                if (t.equals("run")) {
                    out.add(new String[]{"run", "执行后面的命令："});
                    i++;
                    human.append("然后");
                    break;
                }
                if (SUB.containsKey(t)) {
                    StringBuilder seg = new StringBuilder(t);
                    StringBuilder args = new StringBuilder();
                    // 吃掉该子命令的参数（到下一个已知子命令或 run）
                    int j = i + 1;
                    while (j < tokens.length && !SUB.containsKey(tokens[j]) && !tokens[j].equals("run")) {
                        args.append(tokens[j]).append(" ");
                        j++;
                    }
                    out.add(new String[]{seg + " " + args.toString().trim(), explainSub(t, args.toString().trim())});
                    i = j;
                } else {
                    i++;
                }
            }
        }
        // 剩余部分作为主命令
        if (i < tokens.length) {
            StringBuilder rest = new StringBuilder();
            for (int j = i; j < tokens.length; j++) {
                rest.append(tokens[j]).append(" ");
            }
            String main = tokens[i];
            out.add(new String[]{rest.toString().trim(), CMDS.getOrDefault(main,
                    main.matches("[\\w:]+") && main.contains(":") ? "调用 " + main + "（命名空间函数）" : "执行 " + main)});
            human.append(humanMain(main, tokens, i));
        }
        out.add(new String[]{"💡", human.toString()});
        return out;
    }

    private static String explainSub(String sub, String arg) {
        return switch (sub) {
            case "as" -> "以 " + arg + " 为执行者" + (arg.equals("@a") ? "（对每个玩家）" : "");
            case "at" -> "在 " + arg + " 的位置执行";
            case "if" -> "只有当 " + explainCond(arg) + " 才继续";
            case "unless" -> "只有当 " + explainCond(arg) + " 不成立才继续";
            case "run" -> "执行";
            default -> SUB.getOrDefault(sub, sub) + (arg.isEmpty() ? "" : " " + arg);
        };
    }

    private static String explainCond(String arg) {
        if (arg.startsWith("block")) {
            return "位置 " + arg.replaceFirst("^block\\s*", "") + " 是该方块";
        }
        if (arg.startsWith("entity")) {
            return "存在实体 " + arg.replaceFirst("^entity\\s*", "");
        }
        if (arg.startsWith("score")) {
            return "分数满足 " + arg.replaceFirst("^score\\s*", "");
        }
        return arg;
    }

    private static String humanMain(String main, String[] tokens, int i) {
        StringBuilder detail = new StringBuilder(CMDS.getOrDefault(main, "执行 " + main));
        for (int j = i + 1; j < tokens.length && j < i + 3; j++) {
            detail.append(" ").append(tokens[j]);
        }
        return detail.toString();
    }

    private CommandExplainer() {
    }
}
