package dev.packweaver.bridge.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wiki 联动 + 命令拆解教学（规划书第 6 章 / 扩展 C）。
 * 左侧命令列表，右侧说明与示例；输入任意命令 → 逐段中文拆解 + 人话总结。
 */
public class WikiScreen extends Screen {
    private static final Map<String, String[]> WIKI = new LinkedHashMap<>();

    static {
        WIKI.put("execute", new String[]{
                "改变命令的执行上下文（执行者/位置/条件），数据包最强大的命令。",
                "execute [子命令]... run <命令>",
                "常用子命令：as @a（以所有玩家执行）| at @s（在玩家位置）| if block（检查方块）|",
                "if entity（检查实体）| if score（比较分数）| unless（条件不满足）| run（执行）",
                "示例：execute as @a at @s if block ~ ~-1 ~ stone_pressure_plate run tp @s 100 64 100"});
        WIKI.put("scoreboard", new String[]{
                "计分板：存储与比较数值（击杀数/金币/状态机）。",
                "scoreboard objectives add <名> <准则> [显示名]",
                "scoreboard players set/add/remove <目标> <计分板> <值>",
                "scoreboard players operation <A> <板A> <op> <B> <板B>（op: = += -= *= /= %= < > <>）",
                "常用准则：dummy | deathCount | playerKillCount | totalKillCount | health | level | food |",
                "minecraft.killed:minecraft:zombie（击杀僵尸数）| trigger（玩家可触发）"});
        WIKI.put("data", new String[]{
                "读写 NBT 数据（实体/方块/存储）。",
                "data get entity @p Health",
                "data modify storage my_pack:data winner set value \"Steve\"",
                "操作：set | merge | append | prepend | insert <i> | remove",
                "来源：value <值> | from <目标> <路径> | string <目标> <路径>"});
        WIKI.put("tellraw", new String[]{
                "发送富文本消息（颜色/点击/悬停）。",
                "tellraw @a {\"text\":\"你好\",\"color\":\"aqua\",\"bold\":true}",
                "点击执行：\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/function ns:start\"}",
                "显示选择器结果：{\"selector\":\"@s\"}"});
        WIKI.put("give", new String[]{
                "给予物品（可带 NBT：名称/附魔/Lore）。",
                "give @s minecraft:diamond_sword 1",
                "带 NBT：give @s diamond_sword{Enchantments:[{id:\"sharpness\",lvl:5}],Unbreakable:1b} 1"});
        WIKI.put("tp", new String[]{
                "传送实体。支持绝对坐标 / 相对 ~ / 局部 ^ 坐标。",
                "tp @s 100 64 100 | tp @s ~ ~ ~5（向前5格）| tp @s @p[name=Steve]"});
        WIKI.put("effect", new String[]{
                "给予/清除状态效果。等级参数 0 = I 级。",
                "effect give @a minecraft:speed 10 1 | effect clear @s"});
        WIKI.put("function", new String[]{
                "调用命名空间函数。函数 = 一组命令的集合。",
                "function my_pack:start | execute as @a run function my_pack:per_player"});
        WIKI.put("title", new String[]{
                "屏幕大字标题 / 动作栏消息。",
                "title @a title {\"text\":\"游戏开始\"} | title @s actionbar {\"text\":\"金币+1\"}",
                "title @a times 10 70 20（淡入/持续/淡出 tick）"});
        WIKI.put("particle", new String[]{
                "生成粒子效果。",
                "particle minecraft:portal ~ ~1 ~ 0.5 0.5 0.5 0.1 50"});
        WIKI.put("playsound", new String[]{
                "播放音效。音调 >1 高快，<1 低慢。",
                "playsound minecraft:entity.enderman.teleport master @s ~ ~ ~ 1.0 1.0"});
        WIKI.put("tag", new String[]{
                "给实体打标签（职业/状态/分组利器）。",
                "tag @s add class.warrior | tag @s remove joined | 选择器: @a[tag=class.warrior]"});
        WIKI.put("summon", new String[]{
                "生成实体（可带 NBT 名称/装备）。",
                "summon minecraft:zombie 100 64 100 {CustomName:'{\"text\":\"Boss\"}',CustomNameVisible:1b}"});
        WIKI.put("advancement", new String[]{
                "进度系统（也可当事件触发器用）。",
                "advancement grant @s only my_pack:join | 撤销: revoke"});
        WIKI.put("NBT 类型", new String[]{
                "字节 1b | 短整型 100s | 整型 100 | 长整型 100L",
                "浮点 1.5f | 双精度 1.5d | 字符串 \"hello\" | 列表 [\"a\",\"b\"]",
                "字节数组 [B;1b,2b] | 整型数组 [I;1,2] | 复合 {name:\"test\"}"});
    }

    private String selected = "execute";
    private TextFieldWidget dissectField;
    private List<String[]> dissectResult = new ArrayList<>();

    public WikiScreen() {
        super(Text.literal("PackWeaver Wiki"));
    }

    @Override
    protected void init() {
        dissectField = new TextFieldWidget(this.textRenderer, 150, this.height - 52,
                this.width - 170, 16, Text.literal("命令拆解"));
        dissectField.setMaxLength(200);
        addSelectableChild(dissectField);
        setInitialFocus(dissectField);
        addDrawableChild(ButtonWidget.builder(Text.literal("拆解"), b -> dissect())
                .dimensions(this.width - 58, this.height - 54, 50, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.packweaver.done"), b -> close())
                .dimensions(this.width - 58, 6, 50, 18).build());
    }

    private void dissect() {
        dissectResult = dev.packweaver.bridge.pack.CommandExplainer.explain(dissectField.getText());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawTextWithShadow(this.textRenderer, "命令文档", 8, 8, 0x4FC3F7);
        int y = 22;
        for (String key : WIKI.keySet()) {
            boolean active = key.equals(selected);
            context.fill(6, y - 2, 142, y + 10, active ? 0x604FC3F7 : 0x20000000);
            context.drawTextWithShadow(this.textRenderer, key, 10, y, active ? 0xFFFFFFFF : 0xB0BEC5);
            y += 13;
        }
        String[] doc = WIKI.get(selected);
        int dy = 22;
        for (int i = 0; i < doc.length; i++) {
            context.drawTextWithShadow(this.textRenderer,
                    doc[i].length() > 80 ? doc[i].substring(0, 80) : doc[i],
                    150, dy, i == 1 ? 0xFFCC80 : 0xFFE0E0E0);
            dy += 12;
        }

        // 拆解结果
        context.drawTextWithShadow(this.textRenderer, "命令拆解（边用边学）", 150, this.height - 64, 0x4FC3F7);
        dissectField.render(context, mouseX, mouseY, delta);
        int ry = this.height - 34;
        for (String[] seg : dissectResult) {
            if (ry > this.height - 12) {
                break;
            }
            context.drawTextWithShadow(this.textRenderer, seg[0], 150, ry, 0xFFFFB74D);
            String explain = seg[1].length() > 76 ? seg[1].substring(0, 76) + "…" : seg[1];
            context.drawTextWithShadow(this.textRenderer, explain, 230, ry, 0xFFE0E0E0);
            ry += 11;
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int y = 22;
        for (String key : WIKI.keySet()) {
            if (mouseX >= 6 && mouseX <= 142 && mouseY >= y - 2 && mouseY <= y + 10) {
                selected = key;
                return true;
            }
            y += 13;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
