package com.dpe.common.manual;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置原版数据包手册。硬编码一批常用条目（命令/方块/物品/实体/标签/NBT/文本组件）。
 * title 为条目标识（命令名/资源 id/字段名），description 为中文说明。
 */
public final class BuiltinManual {

    private static final List<ManualEntry> ENTRIES = buildEntries();
    private static final Map<String, ManualEntry> BY_ID = new LinkedHashMap<>();

    static {
        for (ManualEntry e : ENTRIES) {
            BY_ID.put(e.id(), e);
        }
    }

    private BuiltinManual() {
    }

    /** 全部条目（不可变）。 */
    public static List<ManualEntry> all() {
        return ENTRIES;
    }

    /** 按 id 查找；不存在返回 null。 */
    public static ManualEntry byId(String id) {
        if (id == null) {
            return null;
        }
        return BY_ID.get(id);
    }

    private static List<ManualEntry> buildEntries() {
        List<ManualEntry> list = new ArrayList<>();
        // ---------- 命令 ----------
        cmd(list, "say", "说话：向所有玩家发送一条黄色聊天消息。",
                "say Hello, world!", List.of("说话", "聊天", "广播", "消息"));
        cmd(list, "tellraw", "原始消息：向指定目标发送 JSON 文本消息，可含点击/悬停事件与样式。",
                "tellraw @a {\"text\":\"hi\",\"color\":\"gold\"}", List.of("原始消息", "聊天", "json", "文本", "消息"));
        cmd(list, "title", "标题：向玩家显示标题（大字）、副标题或动作栏提示。",
                "title @a title {\"text\":\"欢迎\"}", List.of("标题", "大字", "动作栏"));
        cmd(list, "function", "运行函数：调用指定命名空间下的函数文件。",
                "function dpe:internal/tick", List.of("运行函数", "调用", "函数", "命名空间"));
        cmd(list, "execute", "执行修饰：在改变执行环境（坐标/朝向/执行者）或满足条件时运行子命令。",
                "execute as @e at @s run say hi", List.of("执行修饰", "条件", "修饰", "执行者", "坐标"));
        cmd(list, "if", "条件判断：execute 的子修饰，满足条件才执行 run 后命令。",
                "execute if entity @p run say 有人", List.of("条件判断", "条件", "判断", "execute"));
        cmd(list, "data", "数据操作：读取/修改/合并/移除实体或方块实体的 NBT 数据。",
                "data get entity @e[limit=1] Health", List.of("数据操作", "nbt", "数据", "读写"));
        cmd(list, "get", "读取数据：data 的子命令，读取指定 NBT 路径的值。",
                "data get entity @p Inventory[0].id", List.of("读取数据", "读取", "data", "nbt"));
        cmd(list, "modify", "修改数据：data 的子命令，修改指定 NBT 路径。",
                "data modify entity @e[limit=1] Health set value 20", List.of("修改数据", "修改", "data", "nbt"));
        cmd(list, "scoreboard", "记分板：管理记分项（objectives）与玩家分数（players）。",
                "scoreboard objectives add kills dummy", List.of("记分板", "分数", "计分", "统计"));
        cmd(list, "objectives", "记分项：scoreboard 子命令，创建/删除/列出记分项。",
                "scoreboard objectives add kills dummy", List.of("记分项", "scoreboard", "dummy"));
        cmd(list, "players", "玩家分数：scoreboard 子命令，读取/设置/重置玩家在某记分项上的分数。",
                "scoreboard players set @p kills 3", List.of("玩家分数", "分数", "scoreboard", "玩家"));
        cmd(list, "give", "给予物品：向目标玩家给予指定物品与数量。",
                "give @p minecraft:diamond 64", List.of("给予物品", "给予", "物品", "发放"));
        cmd(list, "summon", "召唤实体：在指定坐标召唤一个实体（可带 NBT）。",
                "summon minecraft:zombie ~ ~ ~", List.of("召唤实体", "召唤", "实体", "生成"));
        cmd(list, "setblock", "放置方块：在指定坐标放置一个方块。",
                "setblock ~ ~ ~ minecraft:stone", List.of("放置方块", "方块", "放置", "setblock"));
        cmd(list, "fill", "填充区域：用一种方块填充立方体区域。",
                "fill ~ ~ ~ ~5 ~5 ~5 minecraft:stone", List.of("填充区域", "区域", "填充", "方块"));
        cmd(list, "clone", "克隆区域：把一个区域的方块复制到另一区域。",
                "clone ~ ~ ~ ~5 ~5 ~5 ~10 ~ ~", List.of("克隆区域", "复制", "区域", "方块"));
        cmd(list, "particle", "粒子效果：在指定位置播放粒子效果。",
                "particle minecraft:flame ~ ~1 ~ 0 0 0 0.05 10", List.of("粒子效果", "粒子", "特效", "效果"));
        cmd(list, "playsound", "播放音效：向玩家播放音效。",
                "playsound minecraft:entity.player.levelup master @p", List.of("播放音效", "声音", "音效", "播放"));

        // ---------- 方块 id ----------
        block(list, "minecraft:stone", "石头：最基础的方块，常用于建筑填充。",
                "setblock ~ ~ ~ minecraft:stone", List.of("石头", "基础", "方块"));
        block(list, "minecraft:dirt", "泥土：地表常见方块，草方块的底层。",
                "setblock ~ ~ ~ minecraft:dirt", List.of("泥土", "土", "方块"));
        block(list, "minecraft:oak_log", "橡木原木：橡树的树干，可用于合成木板。",
                "setblock ~ ~ ~ minecraft:oak_log", List.of("橡木原木", "原木", "木头", "橡木"));
        block(list, "minecraft:chest", "箱子：可储存物品的方块，可组成为大箱子。",
                "setblock ~ ~ ~ minecraft:chest", List.of("箱子", "储存", "容器"));
        block(list, "minecraft:command_block", "命令方块：可执行命令的方块（需管理员权限）。",
                "setblock ~ ~ ~ minecraft:command_block", List.of("命令方块", "指令", "执行"));
        block(list, "minecraft:diamond_block", "钻石块：由 9 个钻石合成的装饰方块。",
                "setblock ~ ~ ~ minecraft:diamond_block", List.of("钻石块", "钻石", "装饰", "方块"));
        block(list, "minecraft:glass", "玻璃：透明的装饰方块。",
                "setblock ~ ~ ~ minecraft:glass", List.of("玻璃", "透明", "装饰"));
        block(list, "minecraft:torch", "火把：提供光照的常见光源方块。",
                "setblock ~ ~ ~ minecraft:torch", List.of("火把", "光源", "照明"));
        block(list, "minecraft:crafting_table", "工作台：右键打开 3x3 合成界面。",
                "setblock ~ ~ ~ minecraft:crafting_table", List.of("工作台", "合成", "制作"));
        block(list, "minecraft:furnace", "熔炉：用于烧炼物品的方块。",
                "setblock ~ ~ ~ minecraft:furnace", List.of("熔炉", "烧炼", "炉子"));

        // ---------- 物品 id ----------
        item(list, "minecraft:diamond_sword", "钻石剑：高伤害近战武器。",
                "give @p minecraft:diamond_sword", List.of("钻石剑", "剑", "武器", "钻石"));
        item(list, "minecraft:bread", "面包：常见食物，恢复饱食度。",
                "give @p minecraft:bread 16", List.of("面包", "食物", "饥饿"));
        item(list, "minecraft:totem_of_undying", "不死图腾：手持时免死一次。",
                "give @p minecraft:totem_of_undying", List.of("不死图腾", "图腾", "不死", "免死"));
        item(list, "minecraft:apple", "苹果：基础食物，恢复少量饱食度。",
                "give @p minecraft:apple 8", List.of("苹果", "食物", "饥饿"));
        item(list, "minecraft:golden_apple", "金苹果：恢复饱食度并附带生命恢复效果。",
                "give @p minecraft:golden_apple", List.of("金苹果", "食物", "恢复"));
        item(list, "minecraft:bow", "弓：远程武器，需搭配箭矢使用。",
                "give @p minecraft:bow", List.of("弓", "远程", "武器"));
        item(list, "minecraft:arrow", "箭：弓的弹药。",
                "give @p minecraft:arrow 64", List.of("箭", "弹药", "弓"));
        item(list, "minecraft:ender_pearl", "末影珍珠：投出后传送至落点。",
                "give @p minecraft:ender_pearl", List.of("末影珍珠", "传送", "投掷"));
        item(list, "minecraft:iron_ingot", "铁锭：基础合成材料。",
                "give @p minecraft:iron_ingot 32", List.of("铁锭", "材料", "铁"));
        item(list, "minecraft:stick", "木棍：工具与武器的合成材料。",
                "give @p minecraft:stick 16", List.of("木棍", "棍", "材料"));

        // ---------- 实体 id ----------
        entity(list, "minecraft:zombie", "僵尸：常见敌对生物，夜间生成。",
                "summon minecraft:zombie ~ ~ ~", List.of("僵尸", "怪物", "亡灵"));
        entity(list, "minecraft:skeleton", "骷髅：远程弓箭手敌对生物。",
                "summon minecraft:skeleton ~ ~ ~", List.of("骷髅", "弓箭", "怪物"));
        entity(list, "minecraft:creeper", "苦力怕：接近玩家自爆的敌对生物。",
                "summon minecraft:creeper ~ ~ ~", List.of("苦力怕", "爬行者", "爆炸"));
        entity(list, "minecraft:villager", "村民：可交易的中立生物。",
                "summon minecraft:villager ~ ~ ~", List.of("村民", "交易", "中立"));
        entity(list, "minecraft:armor_stand", "盔甲架：用于展示装备的实体。",
                "summon minecraft:armor_stand ~ ~ ~", List.of("盔甲架", "展示", "装备"));
        entity(list, "minecraft:enderman", "末影人：高个中立生物，可拾取方块。",
                "summon minecraft:enderman ~ ~ ~", List.of("末影人", "末影", "怪物"));
        entity(list, "minecraft:blaze", "烈焰人：下界敌对生物，发射火球。",
                "summon minecraft:blaze ~ ~ ~", List.of("烈焰人", "下界", "火"));
        entity(list, "minecraft:witch", "女巫：投掷药水攻击的敌对生物。",
                "summon minecraft:witch ~ ~ ~", List.of("女巫", "药水", "怪物"));

        // ---------- 标签 ----------
        tag(list, "#minecraft:logs", "原木标签：包含所有原木类方块，常用于配方/标签。",
                "#minecraft:logs", List.of("原木标签", "原木", "标签", "logs"));
        tag(list, "#minecraft:planks", "木板标签：包含所有木板类方块。",
                "#minecraft:planks", List.of("木板标签", "木板", "标签", "planks"));
        tag(list, "#minecraft:wool", "羊毛标签：包含所有颜色羊毛。",
                "#minecraft:wool", List.of("羊毛标签", "羊毛", "标签", "wool"));
        tag(list, "#minecraft:stone_bricks", "石砖标签：包含各类石砖方块。",
                "#minecraft:stone_bricks", List.of("石砖标签", "石砖", "标签", "stone"));
        tag(list, "#minecraft:saplings", "树苗标签：包含各类树苗。",
                "#minecraft:saplings", List.of("树苗标签", "树苗", "标签", "saplings"));

        // ---------- NBT 路径 ----------
        nbt(list, "Health", "生命值：实体的当前生命值（浮点）。",
                "data get entity @e[limit=1] Health", List.of("生命值", "血量", "生命", "hp"));
        nbt(list, "Pos", "坐标：实体所在世界坐标 [x,y,z]。",
                "data get entity @p Pos", List.of("坐标", "位置", "xyz"));
        nbt(list, "Inventory", "物品栏：玩家或实体的物品栏列表。",
                "data get entity @p Inventory", List.of("物品栏", "背包", "inventory"));
        nbt(list, "ArmorItems", "护甲槽：实体四件护甲物品列表。",
                "data get entity @p ArmorItems", List.of("护甲槽", "护甲", "装备", "armor"));
        nbt(list, "CustomName", "自定义名称：实体显示名（JSON 文本）。",
                "data modify entity @e[limit=1] CustomName set value '{\"text\":\"鲍勃\"}'", List.of("自定义名称", "名称", "名字", "改名"));
        nbt(list, "Motion", "运动向量：实体速度向量 [dx,dy,dz]。",
                "data get entity @e[limit=1] Motion", List.of("运动向量", "速度", "运动", "motion"));
        nbt(list, "Rotation", "朝向：实体旋转 [yaw,pitch]。",
                "data get entity @p Rotation", List.of("朝向", "旋转", "yaw"));
        nbt(list, "UUID", "唯一标识：实体唯一 ID（整数数组）。",
                "data get entity @e[limit=1] UUID", List.of("唯一标识", "uuid", "唯一", "标识"));

        // ---------- 文本组件字段 ----------
        text(list, "text", "文本内容：显示的纯文本字符串。",
                "{\"text\":\"你好\"}", List.of("文本内容", "文本", "内容", "纯文本"));
        text(list, "translate", "翻译键：根据玩家语言文件显示本地化文本。",
                "{\"translate\":\"item.minecraft.apple\"}", List.of("翻译键", "翻译", "本地化", "lang"));
        text(list, "clickEvent", "点击事件：玩家点击文本时触发动作（如 run_command 运行命令）。",
                "{\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/home\"}}", List.of("点击事件", "点击", "run_command"));
        text(list, "hoverEvent", "悬停事件：鼠标悬停时显示提示（如 show_text 显示文本）。",
                "{\"hoverEvent\":{\"action\":\"show_text\",\"contents\":{\"text\":\"提示\"}}}", List.of("悬停事件", "悬停", "show_text"));
        text(list, "color", "颜色：文本颜色，如 red/gold/aqua 或十六进制。",
                "{\"text\":\"红\",\"color\":\"red\"}", List.of("颜色", "color", "red"));
        text(list, "bold", "加粗：true 时文本加粗显示。",
                "{\"text\":\"粗\",\"bold\":true}", List.of("加粗", "粗体", "bold"));
        text(list, "italic", "斜体：true 时文本倾斜显示。",
                "{\"text\":\"斜\",\"italic\":true}", List.of("斜体", "倾斜", "italic"));
        text(list, "extra", "附加组件：在当前文本后追加更多文本组件。",
                "{\"text\":\"a\",\"extra\":[{\"text\":\"b\"}]}", List.of("附加组件", "附加", "追加", "子组件"));

        return List.copyOf(list);
    }

    private static void cmd(List<ManualEntry> list, String id, String desc,
                            String example, List<String> kw) {
        list.add(new ManualEntry("cmd/" + id, ManualCategory.COMMAND, id, desc, example, kw));
    }

    private static void block(List<ManualEntry> list, String id, String desc,
                              String example, List<String> kw) {
        list.add(new ManualEntry("block/" + id, ManualCategory.BLOCK, id, desc, example, kw));
    }

    private static void item(List<ManualEntry> list, String id, String desc,
                             String example, List<String> kw) {
        list.add(new ManualEntry("item/" + id, ManualCategory.ITEM, id, desc, example, kw));
    }

    private static void entity(List<ManualEntry> list, String id, String desc,
                               String example, List<String> kw) {
        list.add(new ManualEntry("entity/" + id, ManualCategory.ENTITY, id, desc, example, kw));
    }

    private static void tag(List<ManualEntry> list, String id, String desc,
                            String example, List<String> kw) {
        list.add(new ManualEntry("tag/" + id, ManualCategory.TAG, id, desc, example, kw));
    }

    private static void nbt(List<ManualEntry> list, String id, String desc,
                            String example, List<String> kw) {
        list.add(new ManualEntry("nbt/" + id, ManualCategory.NBT, id, desc, example, kw));
    }

    private static void text(List<ManualEntry> list, String id, String desc,
                             String example, List<String> kw) {
        list.add(new ManualEntry("text/" + id, ManualCategory.TEXT_COMPONENT, id, desc, example, kw));
    }
}
