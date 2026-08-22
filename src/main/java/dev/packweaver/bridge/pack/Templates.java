package dev.packweaver.bridge.pack;

import java.util.List;
import java.util.Map;

/**
 * 内置模板（规划书第 12.1 章，难度 ⭐~⭐⭐⭐⭐⭐）。
 * 每个模板返回事件积木 + 可选手写函数，创建项目时一键生成。
 */
public final class Templates {

    public record Tpl(String id, String name, int stars, String learns) {
    }

    public static final List<Tpl> ALL = List.of(
            new Tpl("hello", "你好世界", 1, "消息发送、函数调用"),
            new Tpl("portal", "传送门系统", 2, "区域检测、传送、条件"),
            new Tpl("kills", "计分板小游戏", 2, "计分板、循环、事件"),
            new Tpl("shop", "商店系统", 3, "物品检测、经济、交互"),
            new Tpl("classes", "职业系统", 3, "标签、效果、装备"),
            new Tpl("dungeon", "副本系统", 4, "多阶段、Boss战、奖励"),
            new Tpl("battle", "大逃杀", 5, "区域收缩、随机掉落、排名"));

    public static String describe() {
        StringBuilder sb = new StringBuilder();
        for (Tpl t : ALL) {
            sb.append(t.id).append(" - ").append(t.name).append(" ").append("*".repeat(t.stars))
                    .append("（").append(t.learns).append("）\n");
        }
        return sb.toString();
    }

    /** 应用模板到项目（覆盖事件积木与手写文件）。 */
    public static void apply(PackProject p, String id) {
        p.events.clear();
        p.files.clear();
        String ns = p.namespace;
        switch (id) {
            case "portal" -> {
                // 规划书第 22 章：区域检测 → 传送 + 音效 + 粒子
                BlockNode tick = new BlockNode("event_tick");
                BlockNode ifArea = new BlockNode("ctrl_if", "note", "走进传送门区域");
                ifArea.children.add(new BlockNode("cond_area",
                        "x", "100", "y", "64", "z", "100", "dx", "2", "dy", "3", "dz", "2"));
                ifArea.elseChildren = new java.util.ArrayList<>();
                BlockNode body = new BlockNode("ctrl_if", "note", "传送+特效");
                body.children.add(new BlockNode("cond_tag", "tag", "__never__")); // 占位恒假，仅结构示例
                tick.children.add(ifArea);
                p.events.add(tick);
                p.files.put(ns + "/functions/teleport.mcfunction",
                        "tp @s 200 64 200\n"
                                + "playsound minecraft:entity.enderman.teleport master @s ~ ~ ~ 1.0 1.0\n"
                                + "particle minecraft:portal ~ ~1 ~ 0.5 0.5 0.5 0.1 50\n"
                                + "tellraw @s {\"text\":\"传送成功！\",\"color\":\"aqua\"}\n");
                p.files.put(ns + "/functions/tick.mcfunction",
                        "execute as @a at @s if entity @s[x=100,y=64,z=100,dx=2,dy=3,dz=2] run function " + ns + ":teleport\n");
            }
            case "kills" -> {
                // 规划书第 23 章：击杀僵尸计分，先到 10 分获胜
                BlockNode load = new BlockNode("event_load");
                load.children.add(new BlockNode("act_objective",
                        "obj", "kills", "name", "击杀数", "slot", "sidebar"));
                p.events.add(load);
                BlockNode tick = new BlockNode("event_tick");
                BlockNode win = new BlockNode("ctrl_if", "note", "分数达到 10 获胜");
                win.children.add(new BlockNode("cond_score", "obj", "kills", "op", "≥", "value", "10"));
                win.children.add(new BlockNode("act_send",
                        "target", "@a", "text", "游戏结束！获胜者产生了！", "pos", "标题"));
                win.children.add(new BlockNode("act_playsound",
                        "target", "@a", "sound", "minecraft:ui.toast.challenge_complete",
                        "volume", "1.0", "pitch", "1.0"));
                win.children.add(new BlockNode("act_score_set",
                        "target", "@a", "obj", "kills", "op", "设置", "value", "0"));
                tick.children.add(win);
                p.events.add(tick);
                p.files.put(ns + "/functions/tick.mcfunction",
                        "execute as @a if score @s kills matches 10.. run function " + ns + ":win\n");
                p.files.put(ns + "/functions/win.mcfunction",
                        "tellraw @a [{\"text\":\"游戏结束！\",\"color\":\"gold\"},{\"text\":\"获胜者：\",\"color\":\"yellow\"},{\"selector\":\"@s\"}]\n"
                                + "playsound minecraft:ui.toast.challenge_complete master @a ~ ~ ~ 1.0 1.0\n"
                                + "scoreboard players set @a kills 0\n");
                p.files.put(ns + "/functions/load.mcfunction",
                        "scoreboard objectives add kills minecraft.killed:minecraft.zombie 击杀数\n"
                                + "scoreboard objectives setdisplay sidebar kills\n");
            }
            case "shop" -> {
                BlockNode load = new BlockNode("event_load");
                load.children.add(new BlockNode("act_objective", "obj", "coins", "name", "金币", "slot", "sidebar"));
                p.events.add(load);
                p.files.put(ns + "/functions/load.mcfunction",
                        "scoreboard objectives add coins dummy 金币\n"
                                + "scoreboard objectives setdisplay sidebar coins\n"
                                + "tellraw @a {\"text\":\"[商店] 手持钻石执行 /trigger pw_buy 购买装备\",\"color\":\"yellow\"}\n");
                p.files.put(ns + "/functions/buy.mcfunction",
                        "# 手持钻石 + trigger 触发 → 扣除金币给装备\n"
                                + "execute as @a[scores={pw_buy=1..},nbt={SelectedItem:{id:\"minecraft:diamond\"}}] run function "
                                + ns + ":buy_do\n"
                                + "scoreboard players set @a[scores={pw_buy=1..}] pw_buy 0\n");
                p.files.put(ns + "/functions/buy_do.mcfunction",
                        "give @s minecraft:diamond_sword 1\n"
                                + "tellraw @s {\"text\":\"购买成功：钻石剑\",\"color\":\"green\"}\n");
                p.files.put(ns + "/functions/tick.mcfunction",
                        "function " + ns + ":buy\n");
                p.files.put(ns + "/advancements/pw_buy.json",
                        "{\"criteria\":{\"t\":{\"trigger\":\"minecraft:tick\"}},\"rewards\":{\"function\":\"" + ns + ":buy\"}}\n");
                p.files.put(ns + "/functions/init_trigger.mcfunction",
                        "scoreboard objectives add pw_buy trigger\n");
                p.files.put(ns + "/functions/load2.mcfunction", "");
            }
            case "classes" -> {
                // 规划书第 24 章：标签驱动的职业
                BlockNode join = new BlockNode("event_join");
                join.children.add(new BlockNode("act_send",
                        "target", "@s", "text", "欢迎！站在职业台选择职业", "pos", "标题"));
                p.events.add(join);
                p.files.put(ns + "/functions/load.mcfunction",
                        "tellraw @a {\"text\":\"[职业系统] 踩在金块=战士 钻石块=法师 附魔台=射手\",\"color\":\"yellow\"}\n");
                p.files.put(ns + "/functions/tick.mcfunction",
                        "execute as @a at @s if block ~ ~-1 ~ minecraft:gold_block run function " + ns + ":warrior\n"
                                + "execute as @a at @s if block ~ ~-1 ~ minecraft:diamond_block run function " + ns + ":mage\n"
                                + "execute as @a at @s if block ~ ~-1 ~ minecraft:enchanting_table run function " + ns + ":archer\n");
                p.files.put(ns + "/functions/warrior.mcfunction",
                        "tag @s add class.warrior\n"
                                + "effect give @s minecraft:resistance 999999 0 true\n"
                                + "effect give @s minecraft:strength 999999 0 true\n"
                                + "give @s minecraft:iron_sword 1\n"
                                + "tellraw @s {\"text\":\"你已成为【战士】\",\"color\":\"gold\"}\n");
                p.files.put(ns + "/functions/mage.mcfunction",
                        "tag @s add class.mage\n"
                                + "effect give @s minecraft:speed 999999 1 true\n"
                                + "give @s minecraft:blaze_rod 1\n"
                                + "tellraw @s {\"text\":\"你已成为【法师】\",\"color\":\"aqua\"}\n");
                p.files.put(ns + "/functions/archer.mcfunction",
                        "tag @s add class.archer\n"
                                + "effect give @s minecraft:night_vision 999999 0 true\n"
                                + "give @s minecraft:bow 1\n"
                                + "tellraw @s {\"text\":\"你已成为【射手】\",\"color\":\"green\"}\n");
            }
            case "dungeon" -> {
                BlockNode load = new BlockNode("event_load");
                load.children.add(new BlockNode("act_objective", "obj", "stage", "name", "副本阶段", "slot", "无"));
                load.children.add(new BlockNode("act_objective", "obj", "boss_hp", "name", "Boss血量", "slot", "sidebar"));
                p.events.add(load);
                p.files.put(ns + "/functions/load.mcfunction",
                        "scoreboard objectives add stage dummy\n"
                                + "scoreboard objectives add boss_hp dummy Boss血量\n"
                                + "scoreboard objectives setdisplay sidebar boss_hp\n");
                p.files.put(ns + "/functions/start.mcfunction",
                        "scoreboard players set #global stage 1\n"
                                + "scoreboard players set #boss stage 0\n"
                                + "scoreboard players set #global boss_hp 100\n"
                                + "summon minecraft:wither 100 70 100 {CustomName:'{\"text\":\"副本Boss\"}',CustomNameVisible:1b}\n"
                                + "tellraw @a {\"text\":\"副本开始！Boss 出现了\",\"color\":\"red\"}\n");
                p.files.put(ns + "/functions/tick.mcfunction",
                        "execute if score #global stage matches 1 if entity @e[type=wither] run function " + ns + ":stage1\n"
                                + "execute if score #global stage matches 1 unless entity @e[type=wither] run function " + ns + ":stage_clear\n");
                p.files.put(ns + "/functions/stage1.mcfunction",
                        "scoreboard players operation #global boss_hp = #global boss_hp\n");
                p.files.put(ns + "/functions/stage_clear.mcfunction",
                        "scoreboard players set #global stage 2\n"
                                + "tellraw @a {\"text\":\"Boss 被击败！获得奖励\",\"color\":\"gold\"}\n"
                                + "give @a minecraft:diamond 3\n"
                                + "give @a minecraft:golden_apple 2\n");
            }
            case "battle" -> {
                BlockNode load = new BlockNode("event_load");
                load.children.add(new BlockNode("act_objective", "obj", "alive", "name", "存活", "slot", "list"));
                p.events.add(load);
                p.files.put(ns + "/functions/load.mcfunction",
                        "scoreboard objectives add alive dummy 存活\n"
                                + "scoreboard objectives setdisplay list alive\n");
                p.files.put(ns + "/functions/start.mcfunction",
                        "worldborder center 0 0\n"
                                + "worldborder set 500\n"
                                + "spreadplayers 0 0 100 200 false @a\n"
                                + "tellraw @a {\"text\":\"大逃杀开始！战场将在 5 分钟后收缩\",\"color\":\"red\"}\n"
                                + "gamemode survival @a\n");
                p.files.put(ns + "/functions/shrink.mcfunction",
                        "worldborder set 50\n"
                                + "tellraw @a {\"text\":\"⚠ 战场已收缩！\",\"color\":\"red\",\"bold\":true}\n"
                                + "playsound minecraft:entity.ender_dragon.growl master @a ~ ~ ~ 1.0 1.0\n");
                p.files.put(ns + "/functions/tick.mcfunction",
                        "execute as @a[gamemode=survival] run scoreboard players set @s alive 1\n"
                                + "execute as @a[gamemode=spectator] run scoreboard players set @s alive 0\n"
                                + "execute if entity @a[scores={alive=1},limit=1] unless entity @a[scores={alive=1},limit=2] run function "
                                + ns + ":win\n");
                p.files.put(ns + "/functions/win.mcfunction",
                        "execute as @a[scores={alive=1}] run tellraw @a [{\"text\":\"最后的赢家：\",\"color\":\"gold\"},{\"selector\":\"@s\"}]\n"
                                + "gamemode spectator @a\n");
            }
            default -> {
                // hello：你好世界（规划书 2.4）
                BlockNode load = new BlockNode("event_load");
                load.children.add(new BlockNode("act_send",
                        "target", "@a", "text", "你好，PackWeaver！", "pos", "聊天栏"));
                p.events.add(load);
                p.files.put(ns + "/functions/hello.mcfunction",
                        "tellraw @a {\"text\":\"你好，PackWeaver！\",\"color\":\"aqua\"}\n"
                                + "playsound minecraft:entity.player.levelup master @a ~ ~ ~ 1.0 1.0\n");
                p.files.put(ns + "/functions/load.mcfunction",
                        "function " + ns + ":hello\n");
            }
        }
        // 模板的 tick/load 手写实现优先于积木生成，避免重复：清除同名事件积木中的生成文件冲突
        if (p.files.containsKey(ns + "/functions/tick.mcfunction")) {
            p.events.removeIf(e -> e.type.equals("event_tick"));
        }
        if (p.files.containsKey(ns + "/functions/load.mcfunction")) {
            p.events.removeIf(e -> e.type.equals("event_load"));
        }
    }

    private Templates() {
    }
}
