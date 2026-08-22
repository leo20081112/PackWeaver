package dev.packweaver.bridge.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import dev.packweaver.bridge.pack.BlockDefs;
import dev.packweaver.bridge.pack.BlockNode;
import dev.packweaver.bridge.pack.CodeGen;
import dev.packweaver.bridge.pack.PackProject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 技术模式：游戏内积木编辑器（规划书第 9-11 章）。
 *
 * 左侧积木分类 + 积木列表；中央为当前事件栈（彩色积木块 + 凹槽缩进）；
 * 顶部事件切换；每个积木有 ▲▼ 顺序 / ✕ 删除 / （容器）选择插入点；
 * 参数内联编辑（文本框 / 选项循环按钮）；底部实时显示生成命令（边用边学）。
 */
public class BlockEditorScreen extends Screen {
    private final PackProject project;
    private String category = "事件";
    private int eventIndex;
    private String message = "";

    /** 当前插入点：父容器路径（null = 事件根）。 */
    private List<BlockNode> insertTarget;
    private String insertLabel = "事件根";

    private static final java.util.Map<String, Integer> CATEGORY_COLORS = java.util.Map.of(
            "事件", 0xFFFF7043, "玩家操作", 0xFF26A69A, "世界操作", 0xFF8D6E63,
            "逻辑控制", 0xFF5C6BC0, "数据", 0xFFFFA726, "高级", 0xFF78909C, "自定义", 0xFFAB47BC);

    public BlockEditorScreen(PackProject project) {
        super(Text.literal("PackWeaver 技术模式 - " + project.namespace));
        this.project = project;
        if (project.events.isEmpty()) {
            project.events.add(new BlockNode("event_tick"));
        }
        this.insertTarget = project.events.get(0).children;
    }

    private BlockNode currentEvent() {
        return project.events.get(Math.min(eventIndex, project.events.size() - 1));
    }

    @Override
    protected void init() {
        // 顶部事件切换
        int x = 8;
        String[] names = {"每tick", "开始时", "玩家加入", "玩家死亡"};
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            addDrawableChild(ButtonWidget.builder(Text.literal(names[idx]), b -> {
                        ensureEvent(idx);
                        eventIndex = idx;
                        insertTarget = currentEvent().children;
                        insertLabel = "事件根";
                    })
                    .dimensions(x, 6, 48, 16).build());
            x += 52;
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("保存运行"), b -> save(true))
                .dimensions(this.width - 168, 6, 80, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("IDE模式"), b -> {
                    assert this.client != null;
                    this.client.setScreen(new CodeEditorScreen(project, "tick"));
                })
                .dimensions(this.width - 84, 6, 76, 16).build());
    }

    private void ensureEvent(int slot) {
        String[] types = {"event_tick", "event_load", "event_join", "event_death"};
        // events 列表按 type 查找
        for (BlockNode ev : project.events) {
            if (ev.type.equals(types[slot])) {
                return;
            }
        }
        project.events.add(new BlockNode(types[slot]));
    }

    private int selectedEventSlot() {
        String[] types = {"event_tick", "event_load", "event_join", "event_death"};
        for (int i = 0; i < types.length; i++) {
            if (currentEvent().type.equals(types[i])) {
                return i;
            }
        }
        return 0;
    }

    // ---------------- 渲染 ----------------

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);

        // 左侧分类
        int y = 28;
        context.drawTextWithShadow(this.textRenderer, "积木分类", 8, y, 0xB0BEC5);
        y += 12;
        for (String cat : BlockDefs.CATEGORIES) {
            boolean active = cat.equals(category);
            context.fill(6, y - 2, 96, y + 10, active ? 0x604FC3F7 : 0x30000000);
            context.drawTextWithShadow(this.textRenderer, cat, 9, y, active ? 0xFFFFFFFF : 0xB0BEC5);
            y += 13;
        }

        // 积木列表
        int paletteTop = y + 6;
        context.drawTextWithShadow(this.textRenderer, "积木（点击添加）", 8, paletteTop - 11, 0xB0BEC5);
        y = paletteTop;
        for (BlockDefs.BlockDef d : BlockDefs.byCategory(category)) {
            if (y > this.height - 30) {
                break;
            }
            int color = CATEGORY_COLORS.getOrDefault(d.category, 0xFF78909C);
            context.fill(6, y, 118, y + 12, 0x90000000 | color);
            context.drawTextWithShadow(this.textRenderer, d.label, 10, y + 2, 0xFFFFFFFF);
            y += 14;
        }

        // 中间画布
        int canvasX = 124;
        context.drawTextWithShadow(this.textRenderer, "插入点: " + insertLabel, canvasX, 24, 0xFFAB91);
        drawStack(context, canvasX, 38, currentEvent().children, 0);

        // 底部代码预览（边用边学）
        int codeY = this.height - 72;
        context.fill(canvasX, codeY, this.width - 8, this.height - 24, 0xC0101014);
        context.drawTextWithShadow(this.textRenderer, "▼ 生成的代码（边用边学）", canvasX + 4, codeY + 3, 0x4FC3F7);
        String code = previewCode();
        int cy = codeY + 16;
        for (String line : code.split("\n")) {
            if (cy > this.height - 28) {
                break;
            }
            context.drawTextWithShadow(this.textRenderer, line.length() > 90 ? line.substring(0, 90) : line,
                    canvasX + 6, cy, 0x81C784);
            cy += 10;
        }
        if (!message.isEmpty()) {
            context.drawTextWithShadow(this.textRenderer, message, canvasX, this.height - 14,
                    message.startsWith("§a") ? 0xFF66BB6A : 0xFFEF5350);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private String previewCode() {
        List<String> lines = new ArrayList<>();
        for (BlockNode n : currentEvent().children) {
            String c = CodeGen.commandOf(n);
            if (!c.isBlank()) {
                lines.add(c);
            } else if (n.type.startsWith("ctrl_")) {
                lines.add("# " + BlockDefs.get(n.type).label + " →（保存时生成子函数）");
            }
        }
        return lines.isEmpty() ? "# （空，从左侧添加积木）" : String.join("\n", lines);
    }

    private int drawStack(DrawContext context, int x, int y, List<BlockNode> stack, int depth) {
        int[] counter = {0};
        for (BlockNode n : stack) {
            BlockDefs.BlockDef def = BlockDefs.get(n.type);
            if (def == null) {
                continue;
            }
            int color = CATEGORY_COLORS.getOrDefault(def.category, 0xFF78909C);
            int h = 16;
            context.fill(x, y, x + 240, y + h, 0x90000000 | color);
            context.fill(x, y, x + 3, y + h, color);
            String label = def.label + paramSummary(n, def);
            context.drawTextWithShadow(this.textRenderer, trunc(label, 34), x + 7, y + 4, 0xFFFFFFFF);

            // 行号用于点击命中
            rowHit(x, y, 240, h, n);

            int cy = y + h;
            if (def.container) {
                if (n.type.equals("ctrl_if")) {
                    // 条件区
                    List<BlockNode> conds = new ArrayList<>();
                    for (BlockNode c : n.children) {
                        if (CodeGen.isCondition(c)) {
                            conds.add(c);
                        }
                    }
                    for (BlockNode c : conds) {
                        context.fill(x + 12, cy, x + 240, cy + 14, 0x50333A45);
                        context.drawTextWithShadow(this.textRenderer, "◇ " + BlockDefs.get(c.type).label
                                + paramSummary(c, BlockDefs.get(c.type)), x + 18, cy + 3, 0xFFFFCC80);
                        rowHit(x + 12, cy, 228, 14, c);
                        cy += 15;
                    }
                    context.drawTextWithShadow(this.textRenderer, "┌ 是：", x + 10, cy, 0xFFA5D6A7);
                    cy += 11;
                    cy = drawStack(context, x + 14, cy, actions(n.children), depth + 1);
                    context.drawTextWithShadow(this.textRenderer, "└", x + 10, cy, 0xFFA5D6A7);
                    cy += 6;
                    if (!n.elseChildren.isEmpty()) {
                        context.drawTextWithShadow(this.textRenderer, "┌ 否：", x + 10, cy, 0xFFEF9A9A);
                        cy += 11;
                        cy = drawStack(context, x + 14, cy, n.elseChildren, depth + 1);
                        cy += 6;
                    }
                } else {
                    cy = drawStack(context, x + 14, cy, n.children, depth + 1);
                }
            }
            y = cy + 2;
            counter[0]++;
        }
        return y;
    }

    private List<BlockNode> actions(List<BlockNode> mixed) {
        List<BlockNode> out = new ArrayList<>();
        for (BlockNode n : mixed) {
            if (!CodeGen.isCondition(n)) {
                out.add(n);
            }
        }
        return out;
    }

    private String paramSummary(BlockNode n, BlockDefs.BlockDef def) {
        StringBuilder sb = new StringBuilder(" ");
        for (BlockDefs.Param p : def.params) {
            if (p.name.equals("__template")) {
                continue;
            }
            String v = n.p(p.name, p.def);
            sb.append(p.label).append(":").append(trunc(v, 8)).append(" ");
        }
        return sb.toString();
    }

    private String trunc(String s, int chars) {
        return s != null && s.length() > chars ? s.substring(0, chars) + "…" : s;
    }

    // ---------------- 点击命中：列表驱动 ----------------

    private record Hit(int x, int y, int w, int h, BlockNode node) {
        boolean inside(int mx, int my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }

    private final List<Hit> hits = new ArrayList<>();

    private void rowHit(int x, int y, int w, int h, BlockNode node) {
        hits.add(new Hit(x, y, w, h, node));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX;
        int my = (int) mouseY;

        // 积木面板：添加积木
        int y = 28 + 12 + BlockDefs.CATEGORIES.length * 13 + 6;
        for (BlockDefs.BlockDef d : BlockDefs.byCategory(category)) {
            if (y > this.height - 30) {
                break;
            }
            if (mx >= 6 && mx <= 118 && my >= y && my <= y + 12) {
                addBlock(d);
                return true;
            }
            y += 14;
        }

        // 分类切换
        int cy2 = 28 + 12;
        for (String cat : BlockDefs.CATEGORIES) {
            if (mx >= 6 && mx <= 96 && my >= cy2 - 2 && my <= cy2 + 10) {
                category = cat;
                return true;
            }
            cy2 += 13;
        }

        // 画布：点击积木 → 打开参数编辑；点击容器 → 设为插入点
        for (Hit hit : hits) {
            if (hit.inside(mx, my)) {
                openParamEditor(hit.node());
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void addBlock(BlockDefs.BlockDef d) {
        if (d.event) {
            message = "§c事件积木请用顶部按钮切换";
            return;
        }
        BlockNode n = new BlockNode(d.type);
        for (BlockDefs.Param p : d.params) {
            if (p.def != null) {
                n.params.put(p.name, p.def);
            }
        }
        if (CodeGen.isCondition(n)) {
            // 条件积木 → 加入当前事件最近的 if（或选中容器的 if）
            BlockNode targetIf = findSelectedIf();
            if (targetIf == null) {
                message = "§c请先添加「如果」积木，再把条件加进去";
                return;
            }
            targetIf.children.add(n);
        } else if (d.container) {
            insertTarget.add(n);
            insertTarget = n.children;
            insertLabel = d.label + " 内";
        } else {
            insertTarget.add(n);
        }
        message = "";
    }

    private BlockNode findSelectedIf() {
        // 从插入点向上找：简化为当前事件中最后一个 ctrl_if
        BlockNode found = null;
        for (BlockNode n : currentEvent().children) {
            if (n.type.equals("ctrl_if")) {
                found = n;
            }
        }
        return found;
    }

    private void openParamEditor(BlockNode node) {
        assert this.client != null;
        this.client.setScreen(new BlockParamScreen(this, project, node));
    }

    private void save(boolean reload) {
        try {
            project.save();
            message = "§a已保存";
            if (reload && this.client != null && this.client.getServer() != null) {
                this.client.getServer().execute(() -> this.client.getServer().getCommandManager()
                        .executeWithPrefix(this.client.getServer().getCommandSource(), "reload"));
                message = "§a已保存并热重载";
            }
        } catch (Exception e) {
            message = "§c保存失败: " + e.getMessage();
        }
    }

    // ---------------- 参数编辑屏回调 ----------------

    void setInsertTarget(BlockNode container) {
        this.insertTarget = container.children;
        BlockDefs.BlockDef d = BlockDefs.get(container.type);
        this.insertLabel = (d != null ? d.label : container.type) + " 内";
    }

    void moveSelected(BlockNode node, int dir) {
        List<BlockNode> list = findList(currentEvent().children, node);
        if (list == null) {
            return;
        }
        int i = list.indexOf(node);
        int j = i + dir;
        if (i >= 0 && j >= 0 && j < list.size()) {
            list.remove(i);
            list.add(j, node);
        }
    }

    void deleteSelected(BlockNode node) {
        List<BlockNode> list = findList(currentEvent().children, node);
        if (list != null) {
            list.remove(node);
            if (insertTarget != null && insertTarget.isEmpty()) {
                // 插入点失效时回到事件根
                insertTarget = currentEvent().children;
                insertLabel = "事件根";
            }
        }
    }

    /** 在事件树中递归查找 node 所在的兄弟列表。 */
    private List<BlockNode> findList(List<BlockNode> stack, BlockNode node) {
        if (stack.contains(node)) {
            return stack;
        }
        for (BlockNode n : stack) {
            List<BlockNode> found = findList(n.children, node);
            if (found != null) {
                return found;
            }
            found = findList(n.elseChildren, node);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    @Override
    public void tick() {
        hits.clear();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
