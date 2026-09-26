package dev.packweaver.bridge.pack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 积木定义注册表（规划书第 9.3 章积木分类总览 + 第 19 章自定义积木）。
 *
 * 分类（对照规划书）：事件 / 玩家操作 / 实体操作 / 世界操作 / 消息界面 /
 * 物品经验 / 数据 / 进度配方 / 逻辑控制 / 高级 / 自定义
 * kind：text | number | options（options 数组循环切换）
 */
public final class BlockDefs {

    public static class Param {
        public final String name;
        public final String label;
        public final String kind;
        public final String def;
        public final String[] options;

        public Param(String name, String label, String kind, String def, String[] options) {
            this.name = name;
            this.label = label;
            this.kind = kind;
            this.def = def;
            this.options = options;
        }
    }

    public static class BlockDef {
        public final String type;
        public final String category;
        public final String label;
        public final List<Param> params = new ArrayList<>();
        public final boolean event;      // 事件积木（栈顶）
        public final boolean container;  // 有子分支（if / foreach / wait）
        public final boolean condition;  // 可作为 if 的条件

        BlockDef(String type, String category, String label, boolean event, boolean container, boolean condition) {
            this.type = type;
            this.category = category;
            this.label = label;
            this.event = event;
            this.container = container;
            this.condition = condition;
        }

        BlockDef param(String name, String label, String kind, String def) {
            params.add(new Param(name, label, kind, def, null));
            return this;
        }

        BlockDef param(String name, String label, String[] options) {
            params.add(new Param(name, label, "options", options[0], options));
            return this;
        }
    }

    private static final Map<String, BlockDef> DEFS = new LinkedHashMap<>();
    public static final String[] CATEGORIES = {"事件", "玩家操作", "实体操作", "世界操作",
            "消息界面", "物品经验", "数据", "进度配方", "逻辑控制", "高级", "自定义"};

    static {
        // ---- 事件（规划书 A.1）----
        def("event_tick", "事件", "每 tick 执行", true, true, false);
        def("event_load", "事件", "当游戏开始时", true, true, false);
        def("event_join", "事件", "当玩家加入游戏", true, true, false);
        def("event_death", "事件", "当玩家死亡", true, true, false);

        // ---- 玩家操作（规划书 A.3）----
        def("act_send", "玩家操作", "发送消息", false, false, false)
                .param("target", "目标", new String[]{"@s", "@a", "@p", "@r"})
                .param("text", "内容", "text", "你好！")
                .param("pos", "位置", new String[]{"聊天栏", "标题", "动作栏"})
                .param("color", "颜色", new String[]{"白色", "金色", "红色", "绿色", "水色", "紫色"});
        def("act_give", "玩家操作", "给予物品", false, false, false)
                .param("target", "目标", new String[]{"@s", "@a", "@p", "@r"})
                .param("item", "物品ID", "text", "minecraft:diamond")
                .param("count", "数量", "number", "1");
        def("act_tp", "玩家操作", "传送玩家", false, false, false)
                .param("target", "目标", new String[]{"@s", "@a", "@p", "@r"})
                .param("x", "X", "text", "100").param("y", "Y", "text", "64").param("z", "Z", "text", "100");
        def("act_effect", "玩家操作", "给予效果", false, false, false)
                .param("target", "目标", new String[]{"@s", "@a", "@p", "@r"})
                .param("effect", "效果", "text", "minecraft:speed")
                .param("seconds", "秒", "number", "10")
                .param("amp", "等级", "number", "0");
        def("act_clear_effect", "玩家操作", "清除效果", false, false, false)
                .param("target", "目标", new String[]{"@s", "@a", "@p", "@r"});
        def("act_heal", "玩家操作", "恢复生命", false, false, false)
                .param("target", "目标", new String[]{"@s", "@a", "@p", "@r"})
                .param("amount", "强度", "number", "3");
        def("act_feed", "玩家操作", "恢复饥饿", false, false, false)
                .param("target", "目标", new String[]{"@s", "@a", "@p", "@r"})
                .param("amount", "强度", "number", "3");
        def("act_gamemode", "玩家操作", "设置游戏模式", false, false, false)
                .param("target", "目标", new String[]{"@s", "@a", "@p", "@r"})
                .param("mode", "模式", new String[]{"survival", "creative", "adventure", "spectator"});
        def("act_playsound", "玩家操作", "播放音效", false, false, false)
                .param("target", "目标", new String[]{"@s", "@a", "@p", "@r"})
                .param("sound", "音效", "text", "minecraft:entity.player.levelup")
                .param("volume", "音量", "text", "1.0").param("pitch", "音调", "text", "1.0");
        def("act_particle", "玩家操作", "生成粒子", false, false, false)
                .param("particle", "粒子", "text", "minecraft:portal")
                .param("x", "X", "text", "~").param("y", "Y", "text", "~1").param("z", "Z", "text", "~")
                .param("count", "数量", "number", "20");

        // ---- 实体操作（规划书 9.3 实体操作类）----
        def("act_summon", "实体操作", "生成实体", false, false, false)
                .param("entity", "实体", "text", "minecraft:zombie")
                .param("x", "X", "text", "~").param("y", "Y", "text", "~").param("z", "Z", "text", "~")
                .param("name", "名字", "text", "");
        def("act_kill", "实体操作", "清除实体", false, false, false)
                .param("sel", "选择器", "text", "@e[type=minecraft:zombie]");
        def("act_tp_here", "实体操作", "把实体传到身边", false, false, false)
                .param("sel", "选择器", "text", "@e[type=minecraft:cow]");
        def("act_ent_effect", "实体操作", "给实体效果", false, false, false)
                .param("sel", "选择器", "text", "@e[type=minecraft:zombie]")
                .param("effect", "效果", "text", "minecraft:glowing")
                .param("seconds", "秒", "number", "10")
                .param("amp", "等级", "number", "0");

        // ---- 世界操作（规划书 9.3 世界操作类）----
        def("act_setblock", "世界操作", "放置方块", false, false, false)
                .param("x", "X", "text", "~").param("y", "Y", "text", "~").param("z", "Z", "text", "~")
                .param("block", "方块", "text", "minecraft:stone");
        def("act_fill", "世界操作", "填充区域", false, false, false)
                .param("x1", "X1", "text", "~").param("y1", "Y1", "text", "~").param("z1", "Z1", "text", "~")
                .param("x2", "X2", "text", "~3").param("y2", "Y2", "text", "~3").param("z2", "Z2", "text", "~3")
                .param("block", "方块", "text", "minecraft:stone");
        def("act_time", "世界操作", "设置时间", false, false, false)
                .param("value", "时间", new String[]{"day", "noon", "night", "midnight"});
        def("act_weather", "世界操作", "设置天气", false, false, false)
                .param("weather", "天气", new String[]{"clear", "rain", "thunder"});
        def("act_gamerule", "世界操作", "游戏规则", false, false, false)
                .param("rule", "规则", new String[]{"keepInventory", "doDaylightCycle", "doMobSpawning", "mobGriefing", "doFireTick", "announceAdvancements"})
                .param("value", "值", new String[]{"true", "false"});

        // ---- 消息界面（规划书 9.3 消息/UI：Boss 血条）----
        def("bb_create", "消息界面", "创建Boss血条", false, false, false)
                .param("id", "ID", "text", "boss1")
                .param("name", "名称", "text", "Boss")
                .param("color", "颜色", new String[]{"red", "blue", "green", "yellow", "pink", "purple", "white"});
        def("bb_update", "消息界面", "更新Boss血条", false, false, false)
                .param("id", "ID", "text", "boss1")
                .param("value", "数值0-100", "number", "50");
        def("bb_remove", "消息界面", "移除Boss血条", false, false, false)
                .param("id", "ID", "text", "boss1");

        // ---- 物品经验（规划书 9.3 物品/NBT）----
        def("act_enchant", "物品经验", "附魔手中物品", false, false, false)
                .param("id", "附魔", "text", "minecraft:sharpness")
                .param("lvl", "等级", "number", "3");
        def("act_clear", "物品经验", "清空物品", false, false, false)
                .param("target", "目标", new String[]{"@s", "@a", "@p", "@r"})
                .param("item", "物品ID(可空)", "text", "");
        def("act_xp", "物品经验", "给予经验", false, false, false)
                .param("target", "目标", new String[]{"@s", "@a", "@p", "@r"})
                .param("amount", "数量", "number", "10")
                .param("mode", "类型", new String[]{"points", "levels"});

        // ---- 数据（规划书 9.3 数据存储 / 27 章命令参考）----
        def("act_score_set", "数据", "设置计分板", false, false, false)
                .param("target", "目标", new String[]{"@s", "@a", "@p"})
                .param("obj", "计分板", "text", "kills")
                .param("op", "操作", new String[]{"设置", "增加", "减少"})
                .param("value", "数值", "number", "1");
        def("act_score_op", "数据", "分数运算", false, false, false)
                .param("a", "目标A", "text", "@s")
                .param("objA", "计分板A", "text", "coins")
                .param("op", "运算", new String[]{"=", "+=", "-=", "*=", "/=", "%=", "<", ">"})
                .param("b", "目标B", "text", "@s")
                .param("objB", "计分板B", "text", "coins");
        def("act_tag", "数据", "添加/移除标签", false, false, false)
                .param("target", "目标", new String[]{"@s", "@a", "@p"})
                .param("tag", "标签", "text", "joined")
                .param("op", "操作", new String[]{"添加", "移除"});
        def("act_objective", "数据", "创建计分板", false, false, false)
                .param("obj", "名称", "text", "kills")
                .param("name", "显示名", "text", "击杀数")
                .param("criterion", "准则", new String[]{"dummy", "health", "deathCount", "totalKillCount", "playerKillCount", "trigger"})
                .param("slot", "显示位置", new String[]{"sidebar", "list", "belowName", "无"});
        def("act_data_set", "数据", "写存储数据", false, false, false)
                .param("key", "存储key", "text", "mydata")
                .param("path", "路径", "text", "count")
                .param("value", "值", "text", "1");
        def("act_data_show", "数据", "读存储数据显示", false, false, false)
                .param("target", "目标", new String[]{"@s", "@a"})
                .param("key", "存储key", "text", "mydata")
                .param("path", "路径", "text", "count");

        // ---- 进度配方（规划书 9.3 进度/配方）----
        def("act_adv_grant", "进度配方", "授予进度", false, false, false)
                .param("target", "目标", new String[]{"@s", "@a", "@p"})
                .param("id", "进度ID", "text", "story/mine_diamond");
        def("act_adv_revoke", "进度配方", "撤销进度", false, false, false)
                .param("target", "目标", new String[]{"@s", "@a", "@p"})
                .param("id", "进度ID", "text", "story/mine_diamond");
        def("act_recipe_give", "进度配方", "解锁配方", false, false, false)
                .param("target", "目标", new String[]{"@s", "@a", "@p"})
                .param("id", "配方ID", "text", "minecraft:diamond_sword");
        def("act_recipe_take", "进度配方", "锁定配方", false, false, false)
                .param("target", "目标", new String[]{"@s", "@a", "@p"})
                .param("id", "配方ID", "text", "minecraft:diamond_sword");

        // ---- 逻辑控制（规划书 A.4）----
        def("ctrl_if", "逻辑控制", "如果…那么", false, true, false)
                .param("note", "备注", "text", "");
        def("cond_area", "逻辑控制", "玩家在区域内", false, false, true)
                .param("x", "X", "number", "100").param("y", "Y", "number", "64").param("z", "Z", "number", "100")
                .param("dx", "DX", "number", "2").param("dy", "DY", "number", "2").param("dz", "DZ", "number", "2");
        def("cond_item", "逻辑控制", "手持物品", false, false, true)
                .param("item", "物品ID", "text", "minecraft:diamond_sword");
        def("cond_has_item", "逻辑控制", "背包拥有物品", false, false, true)
                .param("item", "物品ID", "text", "minecraft:diamond");
        def("cond_tag", "逻辑控制", "拥有标签", false, false, true)
                .param("tag", "标签", "text", "class.warrior");
        def("cond_score", "逻辑控制", "计分板分数", false, false, true)
                .param("obj", "计分板", "text", "kills")
                .param("op", "比较", new String[]{"≥", "≤", ">", "<", "="})
                .param("value", "数值", "number", "10");
        def("cond_block", "逻辑控制", "位置是方块", false, false, true)
                .param("x", "X", "text", "~").param("y", "Y", "text", "~-1").param("z", "Z", "text", "~")
                .param("block", "方块", "text", "minecraft:oak_door");
        def("cond_gamemode", "逻辑控制", "游戏模式为", false, false, true)
                .param("mode", "模式", new String[]{"survival", "creative", "adventure", "spectator"});
        def("cond_ent", "逻辑控制", "存在实体", false, false, true)
                .param("type", "实体类型", "text", "minecraft:zombie");
        def("ctrl_foreach", "逻辑控制", "对每个玩家", false, true, false)
                .param("filter", "筛选", new String[]{"全部玩家", "带标签玩家"})
                .param("tag", "标签", "text", "pw_all");
        def("ctrl_wait", "逻辑控制", "延迟执行(N tick)", false, true, false)
                .param("ticks", "延迟tick", "number", "20");

        // ---- 高级（规划书第 9.3 高级）----
        def("act_call", "高级", "调用函数", false, false, false)
                .param("fn", "函数名", "text", "ns:main");
        def("act_note", "高级", "注释（说明文字）", false, false, false)
                .param("text", "注释", "text", "这一段做什么");
        def("act_cmd", "高级", "执行原始命令", false, false, false)
                .param("cmd", "命令", "text", "say hello");
        def("custom", "自定义", "自定义代码块", false, false, false)
                .param("cmd", "命令", "text", "");
    }

    private static BlockDef def(String type, String category, String label, boolean event, boolean container, boolean condition) {
        BlockDef d = new BlockDef(type, category, label, event, container, condition);
        DEFS.put(type, d);
        return d;
    }

    /** 注册 JSON 自定义积木（规划书第 19 章插件能力，v1.1 以 JSON 代替脚本）。 */
    public static void registerCustom(String type, String label, String template) {
        if (DEFS.containsKey(type)) {
            return;
        }
        BlockDef d = def(type, "自定义", label, false, false, false);
        // 把模板中的 {占位符} 拆成参数
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\{(\\w+)\\}").matcher(template);
        while (m.find()) {
            d.param(m.group(1), m.group(1), "text", "");
        }
        d.params.add(new Param("__template", "模板", "text", template, null));
    }

    public static BlockDef get(String type) {
        return DEFS.get(type);
    }

    public static Map<String, BlockDef> all() {
        return DEFS;
    }

    public static List<BlockDef> byCategory(String category) {
        List<BlockDef> out = new ArrayList<>();
        for (BlockDef d : DEFS.values()) {
            if (d.category.equals(category)) {
                out.add(d);
            }
        }
        return out;
    }

    public static String[] paramOptions(BlockDef d, String name) {
        for (Param p : d.params) {
            if (p.name.equals(name)) {
                return p.options;
            }
        }
        return null;
    }

    private BlockDefs() {
    }
}
