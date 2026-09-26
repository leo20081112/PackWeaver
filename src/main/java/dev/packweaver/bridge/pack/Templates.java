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
            new Tpl("battle", "大逃杀", 5, "区域收缩、随机掉落、排名"),
            new Tpl("parkour", "跑酷计时", 2, "压力板、计时、tag 状态"),
            new Tpl("waypoints", "传送点网络", 2, "trigger 交互、score 分支"),
            new Tpl("mobarena", "怪物竞技场", 3, "波次刷怪、schedule、实体 tag"),
            new Tpl("mining", "挖矿经济", 2, "统计型计分板、商店兑换"),
            new Tpl("levelup", "升级烟花", 1, "custom 统计、召唤 NBT"),
            new Tpl("daynight", "昼夜播报", 1, "predicate 谓词、状态检测"),
            new Tpl("welcome", "新手礼包", 1, "进服事件、进度触发"),
            new Tpl("autodoor", "自动门", 1, "区域检测、方块状态"));

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
            case "parkour" -> {
                // 跑酷计时：金压板=开始 石压板=检查点 铁压板=终点
                p.files.put(ns + "/functions/load.mcfunction",
                        "scoreboard objectives add pk_time dummy \"用时(tick)\"\n"
                                + "tellraw @a {\"text\":\"[跑酷] 金压板=开始 · 石压板=检查点 · 铁压板=终点\",\"color\":\"yellow\"}\n");
                p.files.put(ns + "/functions/tick.mcfunction",
                        "execute as @a[tag=pk_run] run scoreboard players add @s pk_time 1\n"
                                + "execute as @a at @s if block ~ ~-1 ~ minecraft:light_weighted_pressure_plate unless entity @s[tag=pk_run] run function "
                                + ns + ":pk_start\n"
                                + "execute as @a[tag=pk_run] at @s if block ~ ~-1 ~ minecraft:stone_pressure_plate run function " + ns + ":pk_cp\n"
                                + "execute as @a[tag=pk_run] at @s if block ~ ~-1 ~ minecraft:heavy_weighted_pressure_plate run function " + ns + ":pk_finish\n");
                p.files.put(ns + "/functions/pk_start.mcfunction",
                        "tag @s add pk_run\n"
                                + "scoreboard players set @s pk_time 0\n"
                                + "tag @s remove pk_cp\n"
                                + "title @s times 5 20 10\n"
                                + "title @s actionbar {\"text\":\"计时开始！\",\"color\":\"gold\"}\n"
                                + "playsound minecraft:block.note_block.pling master @s ~ ~ ~ 1 1.5\n");
                p.files.put(ns + "/functions/pk_cp.mcfunction",
                        "title @s actionbar {\"text\":\"检查点！\",\"color\":\"aqua\"}\n"
                                + "playsound minecraft:block.note_block.pling master @s ~ ~ ~ 1 2\n");
                p.files.put(ns + "/functions/pk_finish.mcfunction",
                        "tag @s remove pk_run\n"
                                + "title @s times 10 40 10\n"
                                + "title @s title {\"text\":\"跑酷完成！\",\"color\":\"gold\"}\n"
                                + "tellraw @a [{\"text\":\"[跑酷] \",\"color\":\"gray\"},{\"selector\":\"@s\"},{\"text\":\" 用时 \",\"color\":\"gray\"},{\"score\":{\"name\":\"@s\",\"objective\":\"pk_time\"},\"color\":\"gold\"},{\"text\":\" tick\",\"color\":\"gray\"}]\n"
                                + "playsound minecraft:ui.toast.challenge_complete master @s ~ ~ ~ 1 1\n");
            }
            case "waypoints" -> {
                p.files.put(ns + "/functions/load.mcfunction",
                        "scoreboard objectives add pw_warp trigger 传送点\n"
                                + "scoreboard players enable @a pw_warp\n"
                                + "tellraw @a {\"text\":\"[传送点] /trigger pw_warp set 1~3 前往传送点（坐标在 warp_do 函数里改）\",\"color\":\"yellow\"}\n");
                p.files.put(ns + "/functions/tick.mcfunction",
                        "scoreboard players enable @a pw_warp\n"
                                + "execute as @a[scores={pw_warp=1..}] at @s run function " + ns + ":warp_do\n");
                p.files.put(ns + "/functions/warp_do.mcfunction",
                        "execute if score @s pw_warp matches 1 run tp @s 100 64 100\n"
                                + "execute if score @s pw_warp matches 2 run tp @s 200 64 200\n"
                                + "execute if score @s pw_warp matches 3 run tp @s 300 64 300\n"
                                + "execute if score @s pw_warp matches 1.. run function " + ns + ":warp_fx\n"
                                + "scoreboard players set @s pw_warp 0\n");
                p.files.put(ns + "/functions/warp_fx.mcfunction",
                        "particle minecraft:portal ~ ~1 ~ 0.5 0.5 0.5 0.2 40\n"
                                + "playsound minecraft:entity.enderman.teleport master @s ~ ~ ~ 1 1\n");
            }
            case "mobarena" -> {
                p.files.put(ns + "/functions/load.mcfunction",
                        "scoreboard objectives add wave dummy 波数\n");
                p.files.put(ns + "/functions/start.mcfunction",
                        "scoreboard players set #g wave 0\n"
                                + "kill @e[tag=arena]\n"
                                + "tellraw @a {\"text\":\"[竞技场] 3 秒后开始！\",\"color\":\"red\"}\n"
                                + "schedule function " + ns + ":next_wave 3s\n");
                p.files.put(ns + "/functions/tick.mcfunction",
                        "execute if score #g wave matches 1.. unless entity @e[tag=arena] run function " + ns + ":next_wave\n");
                p.files.put(ns + "/functions/next_wave.mcfunction",
                        "scoreboard players add #g wave 1\n"
                                + "execute if score #g wave matches 4 run function " + ns + ":arena_win\n"
                                + "execute if score #g wave matches 1 run function " + ns + ":wave1\n"
                                + "execute if score #g wave matches 2 run function " + ns + ":wave2\n"
                                + "execute if score #g wave matches 3 run function " + ns + ":wave3\n");
                p.files.put(ns + "/functions/wave1.mcfunction",
                        "# 竞技场坐标 (0 64 0) 请改成你的场地中心\n"
                                + "summon minecraft:zombie 0 64 0 {Tags:[\"arena\"],CustomName:'{\"text\":\"竞技场僵尸\"}',CustomNameVisible:1b}\n"
                                + "summon minecraft:zombie 2 64 0 {Tags:[\"arena\"],CustomName:'{\"text\":\"竞技场僵尸\"}',CustomNameVisible:1b}\n"
                                + "summon minecraft:zombie -2 64 0 {Tags:[\"arena\"],CustomName:'{\"text\":\"竞技场僵尸\"}',CustomNameVisible:1b}\n"
                                + "tellraw @a {\"text\":\"第 1 波：3 只僵尸！\",\"color\":\"red\"}\n");
                p.files.put(ns + "/functions/wave2.mcfunction",
                        "summon minecraft:zombie 0 64 0 {Tags:[\"arena\"]}\n"
                                + "summon minecraft:zombie 2 64 2 {Tags:[\"arena\"]}\n"
                                + "summon minecraft:skeleton -2 64 0 {Tags:[\"arena\"]}\n"
                                + "summon minecraft:skeleton 0 64 -2 {Tags:[\"arena\"]}\n"
                                + "tellraw @a {\"text\":\"第 2 波：僵尸 + 骷髅！\",\"color\":\"red\"}\n");
                p.files.put(ns + "/functions/wave3.mcfunction",
                        "summon minecraft:wither_skeleton 0 64 0 {Tags:[\"arena\"]}\n"
                                + "summon minecraft:wither_skeleton 2 64 0 {Tags:[\"arena\"]}\n"
                                + "summon minecraft:zombie 0 64 2 {Tags:[\"arena\"]}\n"
                                + "summon minecraft:zombie 0 64 -2 {Tags:[\"arena\"]}\n"
                                + "tellraw @a {\"text\":\"最终波：凋灵骷髅！\",\"color\":\"dark_red\"}\n");
                p.files.put(ns + "/functions/arena_win.mcfunction",
                        "tellraw @a {\"text\":\"[竞技场] 通关！奖励已发放\",\"color\":\"gold\"}\n"
                                + "playsound minecraft:ui.toast.challenge_complete master @a ~ ~ ~ 1 1\n"
                                + "give @a minecraft:diamond 3\n"
                                + "scoreboard players set #g wave 0\n");
            }
            case "mining" -> {
                p.files.put(ns + "/functions/load.mcfunction",
                        "scoreboard objectives add coins dummy 金币\n"
                                + "scoreboard objectives add mine_dia minecraft.mined:minecraft.diamond_ore \"挖钻石\"\n"
                                + "scoreboard objectives add pw_sell trigger 商店\n"
                                + "scoreboard objectives setdisplay sidebar coins\n");
                p.files.put(ns + "/functions/tick.mcfunction",
                        "execute as @a[scores={mine_dia=1..}] run function " + ns + ":mine_pay\n"
                                + "scoreboard players enable @a pw_sell\n"
                                + "execute as @a[scores={pw_sell=1..}] run function " + ns + ":shop_do\n");
                p.files.put(ns + "/functions/mine_pay.mcfunction",
                        "scoreboard players operation @s coins += @s mine_dia\n"
                                + "title @s actionbar {\"text\":\"+金币！\",\"color\":\"gold\"}\n"
                                + "playsound minecraft:entity.experience_orb.pickup master @s ~ ~ ~ 1 1\n"
                                + "scoreboard players set @s mine_dia 0\n");
                p.files.put(ns + "/functions/shop_do.mcfunction",
                        "execute if score @s pw_sell matches 1 if score @s coins matches 10.. run function " + ns + ":buy_ok\n"
                                + "execute if score @s pw_sell matches 1 unless score @s coins matches 10.. run tellraw @s {\"text\":\"金币不足（需要 10 金币）\",\"color\":\"red\"}\n"
                                + "scoreboard players set @s pw_sell 0\n");
                p.files.put(ns + "/functions/buy_ok.mcfunction",
                        "scoreboard players remove @s coins 10\n"
                                + "give @s minecraft:diamond_block 1\n"
                                + "tellraw @s {\"text\":\"购买成功：钻石块！\",\"color\":\"green\"}\n"
                                + "playsound minecraft:entity.villager.levelup master @s ~ ~ ~ 1 1\n");
            }
            case "levelup" -> {
                p.files.put(ns + "/functions/load.mcfunction",
                        "scoreboard objectives add lvlup minecraft.custom:minecraft.level_up \"升级次数\"\n");
                p.files.put(ns + "/functions/tick.mcfunction",
                        "execute as @a[scores={lvlup=1..}] at @s run function " + ns + ":party\n");
                p.files.put(ns + "/functions/party.mcfunction",
                        "summon minecraft:firework_rocket ~ ~1 ~ {LifeTime:1,FireworksItem:{id:\"minecraft:firework_rocket\",Count:1b,tag:{Fireworks:{Explosions:[{Type:1,Colors:[I;16711680],FadeColors:[I;16776960]}]}}}}\n"
                                + "playsound minecraft:entity.firework_rocket.launch master @s ~ ~ ~ 1 1\n"
                                + "title @s actionbar {\"text\":\"升级庆祝！\",\"color\":\"gold\"}\n"
                                + "scoreboard players set @s lvlup 0\n");
            }
            case "daynight" -> {
                p.files.put(ns + "/predicates/is_day.json",
                        "{\"condition\":\"minecraft:time_check\",\"value\":{\"min\":1000,\"max\":11000}}\n");
                p.files.put(ns + "/functions/load.mcfunction",
                        "scoreboard objectives add pw_day dummy\n"
                                + "scoreboard players set #g pw_day -1\n");
                p.files.put(ns + "/functions/tick.mcfunction",
                        "execute if predicate " + ns + ":is_day run scoreboard players set #c pw_day 1\n"
                                + "execute unless predicate " + ns + ":is_day run scoreboard players set #c pw_day 0\n"
                                + "execute if score #c pw_day != #g pw_day run function " + ns + ":phase\n"
                                + "execute if score #c pw_day != #g pw_day run scoreboard players operation #g pw_day = #c pw_day\n");
                p.files.put(ns + "/functions/phase.mcfunction",
                        "execute if score #c pw_day matches 1 run tellraw @a {\"text\":\"☀ 天亮了\",\"color\":\"yellow\"}\n"
                                + "execute if score #c pw_day matches 0 run tellraw @a {\"text\":\"夜幕降临\",\"color\":\"aqua\"}\n"
                                + "playsound minecraft:block.bell.use master @a ~ ~ ~ 0.6 1\n");
            }
            case "welcome" -> {
                p.files.put(ns + "/advancements/first_join.json",
                        "{\"criteria\":{\"j\":{\"trigger\":\"minecraft:tick\"}},\"rewards\":{\"function\":\"" + ns + ":wj_guard\"}}\n");
                p.files.put(ns + "/functions/wj_guard.mcfunction",
                        "execute unless entity @s[tag=pw_welcomed] run function " + ns + ":wj_kit\n"
                                + "tag @s add pw_welcomed\n");
                p.files.put(ns + "/functions/wj_kit.mcfunction",
                        "give @s minecraft:bread 16\n"
                                + "give @s minecraft:iron_sword 1\n"
                                + "give @s minecraft:torch 16\n"
                                + "title @s times 10 60 20\n"
                                + "title @s title {\"text\":\"欢迎加入！\",\"color\":\"gold\"}\n"
                                + "playsound minecraft:entity.player.levelup master @s ~ ~ ~ 1 1\n");
            }
            case "autodoor" -> {
                p.files.put(ns + "/functions/tick.mcfunction",
                        "# 把下面 3 处 100 64 100 改成你的门（活板门）位置\n"
                                + "execute as @a at @s if entity @s[x=100,y=64,z=100,dx=2,dy=2,dz=2] unless block 100 64 100 minecraft:oak_trapdoor[open=true] run setblock 100 64 100 minecraft:oak_trapdoor[open=true]\n"
                                + "execute unless entity @a[x=100,y=64,z=100,dx=4,dy=4,dz=4] if block 100 64 100 minecraft:oak_trapdoor[open=true] run setblock 100 64 100 minecraft:oak_trapdoor[open=false]\n");
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
