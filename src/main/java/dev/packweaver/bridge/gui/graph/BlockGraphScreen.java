package dev.packweaver.bridge.gui.graph;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import dev.packweaver.bridge.pack.BlockDefs;
import dev.packweaver.bridge.pack.BlockNode;
import dev.packweaver.bridge.pack.PackProject;

import java.util.ArrayList;
import java.util.List;

/**
 * 蓝图模式（Blender 节点编辑器式交互）：
 *
 * - 每个积木 = 节点卡片，左缘输入锚点 / 右缘输出锚点；「如果」节点还有
 *   条件输入锚点与 是/否 两个输出锚点
 * - 从输出锚点按下拖拽 → 贝塞尔导线跟随鼠标 → 落到目标输入锚点即重挂接
 *   （执行流连线）；条件锚点接受条件积木；是/否锚点把目标设为分支首块
 * - 中键（或左键拖空白）平移画布，滚轮以光标为中心缩放
 * - 左键点节点体 = 参数编辑；右键节点 = 菜单（参数/上移/下移/删除/加否分支）
 * - 左侧积木面板点击添加到当前事件末尾；底部实时显示生成代码
 */
public class BlockGraphScreen extends Screen {
    private final PackProject project;
    private String category = "事件";
    private int eventIndex;
    private String message = "";

    // 视图状态
    private float zoom = 1.0f;
    private int panX = 40, panY = 0;

    private static final int NW = 150;   // 节点宽
    private static final int NH = 18;    // 节点高
    private static final int GAP = 5;    // 纵向间距
    private static final int IND = 26;   // 每层缩进
    private static final int BASE_X = 150;
    private static final int LEFT_W = 126; // 左侧面板宽

    // 布局产物（世界坐标）
    private static final class NodeView {
        BlockNode node;
        BlockDefs.BlockDef def;
        int x, y;
        boolean cond, event, iff;

        float inY() { return y + 9; }
        float outY() { return y + 9; }
        float condInY() { return y + NH - 4; }
        float falseOutY() { return y + NH - 4; }
    }

    private record Wire(float x1, float y1, float x2, float y2, int color) {
    }

    private record Label(int x, int y, String text, int color) {
    }

    private final List<NodeView> views = new ArrayList<>();
    private final List<Wire> wires = new ArrayList<>();
    private final List<Label> labels = new ArrayList<>();

    // 交互状态
    private BlockNode dragNode;
    private String dragSocket; // out | true | false
    private int dragMX, dragMY;
    private boolean panning;
    private int panStartX, panStartY, panOrigX, panOrigY;
    private BlockNode menuNode;
    private int menuX, menuY;
    private final List<String> menuItems = new ArrayList<>();

    private static final java.util.Map<String, Integer> CATEGORY_COLORS = java.util.Map.of(
            "事件", 0xFFFF7043, "玩家操作", 0xFF26A69A, "世界操作", 0xFF8D6E63,
            "逻辑控制", 0xFF5C6BC0, "数据", 0xFFFFA726, "高级", 0xFF78909C, "自定义", 0xFFAB47BC);

    public BlockGraphScreen(PackProject project) {
        super(Text.literal("PackWeaver 蓝图 - " + project.namespace));
        this.project = project;
        if (project.events.isEmpty()) {
            project.events.add(new BlockNode("event_tick"));
        }
    }

    private BlockNode currentEvent() {
        return project.events.get(Math.min(eventIndex, project.events.size() - 1));
    }

    @Override
    protected void init() {
        String[] names = {"每tick", "开始时", "玩家加入", "玩家死亡"};
        int x = 6;
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            addDrawableChild(ButtonWidget.builder(Text.literal(names[idx]), b -> {
                        ensureEvent(idx);
                        eventIndex = idx;
                    })
                    .dimensions(x, 4, 44, 16).build());
            x += 47;
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("保存运行"), b -> save(true))
                .dimensions(this.width - 226, 4, 62, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("积木模式"), b -> {
                    assert this.client != null;
                    this.client.setScreen(new dev.packweaver.bridge.gui.BlockEditorScreen(project));
                })
                .dimensions(this.width - 160, 4, 60, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("IDE模式"), b -> {
                    assert this.client != null;
                    this.client.setScreen(new dev.packweaver.bridge.gui.CodeEditorScreen(project, "tick"));
                })
                .dimensions(this.width - 96, 4, 60, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("诊断"), b -> {
                    assert this.client != null;
                    this.client.setScreen(new dev.packweaver.bridge.gui.DiagScreen(project, this));
                })
                .dimensions(this.width - 32, 4, 28, 16).build());
    }

    private void ensureEvent(int slot) {
        String[] types = {"event_tick", "event_load", "event_join", "event_death"};
        for (BlockNode ev : project.events) {
            if (ev.type.equals(types[slot])) {
                return;
            }
        }
        project.events.add(new BlockNode(types[slot]));
    }

    // ---------------- 布局 ----------------

    private void layout() {
        views.clear();
        wires.clear();
        labels.clear();
        nextY = 20;
        BlockNode ev = currentEvent();
        NodeView evView = addView(ev, 0);
        int[] cursor = {evView.y + NH + GAP};
        layoutList(ev.children, 1, evView, "out", cursor);
    }

    private NodeView addView(BlockNode n, int depth) {
        NodeView v = new NodeView();
        v.node = n;
        v.def = BlockDefs.get(n.type);
        v.x = BASE_X + depth * IND;
        v.y = nextY;
        nextY += NH + GAP;
        v.cond = n.type.startsWith("cond_");
        v.event = n.type.startsWith("event_");
        v.iff = n.type.equals("ctrl_if");
        views.add(v);
        return v;
    }

    private int nextY;

    private void layoutList(List<BlockNode> stack, int depth, NodeView prev, String prevSocket, int[] cursor) {
        NodeView prevExec = prev;
        String sock = prevSocket;
        for (BlockNode n : stack) {
            if (n.type.startsWith("cond_")) {
                continue; // 条件由所属 if 布局
            }
            nextY = cursor[0];
            NodeView v = addView(n, depth);
            cursor[0] = nextY;
            if (prevExec != null) {
                wires.add(wireFrom(prevExec, sock, v, "in"));
            }
            if (n.type.equals("ctrl_if")) {
                layoutIf(v, depth, cursor);
            } else if (n.type.equals("ctrl_foreach")) {
                layoutList(n.children, depth + 1, v, "out", cursor);
            }
            prevExec = v;
            sock = "out";
        }
    }

    private void layoutIf(NodeView ifView, int depth, int[] cursor) {
        BlockNode n = ifView.node;
        List<BlockNode> conds = new ArrayList<>();
        List<BlockNode> actions = new ArrayList<>();
        for (BlockNode c : n.children) {
            if (c.type.startsWith("cond_")) {
                conds.add(c);
            } else {
                actions.add(c);
            }
        }
        nextY = cursor[0];
        for (BlockNode c : conds) {
            NodeView cv = addView(c, depth + 1);
            wires.add(new Wire(cv.x + NW, cv.outY(), ifView.x, ifView.condInY(), 0xFFFFB74D));
        }
        cursor[0] = nextY + 2;
        labels.add(new Label(BASE_X + depth * IND + 8, cursor[0] - 9, "是 ↓", 0xFFA5D6A7));
        layoutList(actions, depth + 1, ifView, "true", cursor);
        if (!n.elseChildren.isEmpty()) {
            labels.add(new Label(BASE_X + depth * IND + 8, cursor[0] - 9, "否 ↓", 0xFFEF9A9A));
            layoutList(n.elseChildren, depth + 1, ifView, "false", cursor);
        }
    }

    private Wire wireFrom(NodeView from, String socket, NodeView to, String in) {
        float x1, y1;
        int color;
        switch (socket) {
            case "true" -> {
                x1 = from.x + NW;
                y1 = from.outY();
                color = 0xFFA5D6A7;
            }
            case "false" -> {
                x1 = from.x + NW;
                y1 = from.falseOutY();
                color = 0xFFEF9A9A;
            }
            default -> {
                x1 = from.x + NW;
                y1 = from.outY();
                color = 0xFF64B5F6;
            }
        }
        float x2 = to.x;
        float y2 = in.equals("cond") ? to.condInY() : to.inY();
        return new Wire(x1, y1, x2, y2, color);
    }

    // ---------------- 坐标变换 ----------------

    private int toScreenX(float wx) {
        return (int) (wx * zoom) + panX;
    }

    private int toScreenY(float wy) {
        return (int) (wy * zoom) + panY;
    }

    private float toWorldX(int sx) {
        return (sx - panX) / zoom;
    }

    private float toWorldY(int sy) {
        return (sy - panY) / zoom;
    }

    // ---------------- 渲染 ----------------

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        layout();

        // 导线（贝塞尔近似）
        for (Wire w : wires) {
            drawBezier(context, w.x1, w.y1, w.x2, w.y2, w.color);
        }
        // 拖拽中的临时导线
        if (dragNode != null) {
            NodeView src = viewOf(dragNode);
            if (src != null) {
                Wire temp = wireFrom(src, dragSocket,
                        ghostTarget(toWorldX(dragMX), toWorldY(dragMY)), "in");
                drawBezier(context, temp.x1, temp.y1,
                        toWorldX(dragMX), toWorldY(dragMY), temp.color);
            }
        }
        // 分支标签
        for (Label l : labels) {
            context.drawTextWithShadow(this.textRenderer, l.text(),
                    toScreenX(l.x()), toScreenY(l.y()), l.color());
        }
        // 节点卡片
        for (NodeView v : views) {
            drawNode(context, v);
        }
        drawPalette(context);
        drawCodePreview(context);
        if (menuNode != null) {
            drawMenu(context);
        }
        if (!message.isEmpty()) {
            context.drawTextWithShadow(this.textRenderer, message, LEFT_W + 6, this.height - 12,
                    message.startsWith("§a") ? 0xFF66BB6A : message.startsWith("§7") ? 0xFF90A4AE : 0xFFEF5350);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    /** 拖拽导线的假目标（只提供坐标）。 */
    private NodeView ghostTarget(float wx, float wy) {
        NodeView g = new NodeView();
        g.x = (int) wx;
        g.y = (int) wy;
        return g;
    }

    private void drawNode(DrawContext context, NodeView v) {
        int sx = toScreenX(v.x);
        int sy = toScreenY(v.y);
        int sw = (int) (NW * zoom);
        int sh = (int) (NH * zoom);
        if (sx > this.width || sy > this.height || sx + sw < LEFT_W || sy + sh < 22) {
            return;
        }
        int color = v.def != null ? CATEGORY_COLORS.getOrDefault(v.def.category, 0xFF78909C) : 0xFF78909C;
        context.fill(sx, sy, sx + sw, sy + sh, 0xE0000000 | color);
        context.fill(sx, sy, sx + sw, sy + 1, 0xFFFFFFFF);
        String title = v.def != null ? v.def.label : v.node.type;
        String param = v.def != null && !v.def.params.isEmpty()
                ? firstParam(v.node, v.def) : "";
        context.drawTextWithShadow(this.textRenderer,
                trunc(title, (int) (24 * zoom)) + (param.isEmpty() ? "" : " " + param),
                sx + 6, sy + (sh / 2 - 4), 0xFFFFFFFF);
        // 锚点（右缘输出 / 左缘输入）
        drawSocket(context, sx + sw, sy + (int) (9 * zoom), 0xFF4FC3F7);
        if (!v.event && !v.cond) {
            drawSocket(context, sx, sy + (int) (9 * zoom), 0xFFCFD8DC);
        }
        if (v.iff) {
            drawSocket(context, sx, sy + (int) ((v.condInY() - v.y) * zoom), 0xFFFFB74D);
            context.drawTextWithShadow(this.textRenderer, "是▸",
                    sx + sw - 18, sy + 1, 0xFFA5D6A7);
            context.drawTextWithShadow(this.textRenderer, "否▸",
                    sx + sw - 18, sy + sh - 9, 0xFFEF9A9A);
        }
    }

    private String firstParam(BlockNode n, BlockDefs.BlockDef def) {
        for (BlockDefs.Param p : def.params) {
            if (p.name.equals("__template")) {
                continue;
            }
            String val = n.p(p.name, p.def);
            if (!val.isEmpty()) {
                return trunc(val, (int) (10 * zoom));
            }
        }
        return "";
    }

    private void drawSocket(DrawContext context, int cx, int cy, int color) {
        int s = Math.max(3, (int) (5 * zoom));
        context.fill(cx - s / 2 - 1, cy - s / 2 - 1, cx + s / 2 + 1, cy + s / 2 + 1, 0xFF000000);
        context.fill(cx - s / 2, cy - s / 2, cx + s / 2, cy + s / 2, color);
    }

    private void drawBezier(DrawContext context, float x1, float y1, float x2, float y2, int color) {
        float dx = Math.max(30, Math.abs(x2 - x1) * 0.5f);
        float c1x = x1 + dx, c1y = y1, c2x = x2 - dx, c2y = y2;
        int steps = 24;
        float px = 0, py = 0;
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            float mt = 1 - t;
            float bx = mt * mt * mt * x1 + 3 * mt * mt * t * c1x + 3 * mt * t * t * c2x + t * t * t * x2;
            float by = mt * mt * mt * y1 + 3 * mt * mt * t * c1y + 3 * mt * t * t * c2y + t * t * t * y2;
            if (i > 0) {
                line(context, px, py, bx, by, color);
            }
            px = bx;
            py = by;
        }
    }

    /** 世界坐标粗线段（2px）。 */
    private void line(DrawContext context, float x1, float y1, float x2, float y2, int color) {
        int steps = (int) Math.max(1, Math.hypot(x2 - x1, y2 - y1));
        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            int sx = toScreenX(x1 + (x2 - x1) * t);
            int sy = toScreenY(y1 + (y2 - y1) * t);
            context.fill(sx, sy, sx + Math.max(1, (int) zoom), sy + Math.max(1, (int) zoom), color);
        }
    }

    private void drawPalette(DrawContext context) {
        context.fill(0, 22, LEFT_W, this.height, 0xB0101014);
        int y = 30;
        context.drawTextWithShadow(this.textRenderer, "分类", 8, y, 0xB0BEC5);
        y += 12;
        for (String cat : BlockDefs.CATEGORIES) {
            boolean active = cat.equals(category);
            boolean locked = (cat.equals("高级") || cat.equals("自定义"))
                    && !dev.packweaver.bridge.client.PWLevel.canUseAdvanced();
            context.fill(4, y - 2, LEFT_W - 4, y + 10, active ? 0x604FC3F7 : 0x20000000);
            context.drawTextWithShadow(this.textRenderer, locked ? cat + "🔒" : cat, 8, y,
                    active ? 0xFFFFFFFF : locked ? 0xFF78909C : 0xB0BEC5);
            y += 13;
        }
        y += 4;
        context.drawTextWithShadow(this.textRenderer, "积木（点击添加）", 8, y, 0xB0BEC5);
        y += 12;
        for (BlockDefs.BlockDef d : BlockDefs.byCategory(category)) {
            if (y > this.height - 60) {
                break;
            }
            int color = CATEGORY_COLORS.getOrDefault(d.category, 0xFF78909C);
            context.fill(4, y, LEFT_W - 4, y + 12, 0x90000000 | color);
            context.drawTextWithShadow(this.textRenderer, d.label, 8, y + 2, 0xFFFFFFFF);
            y += 14;
        }
        context.drawTextWithShadow(this.textRenderer, "拖锚点连线·中键平移·滚轮缩放",
                4, this.height - 24, 0x607D8B);
        context.drawTextWithShadow(this.textRenderer, "右键节点弹出菜单",
                4, this.height - 14, 0x607D8B);
    }

    private void drawCodePreview(DrawContext context) {
        int x0 = LEFT_W + 4;
        int y0 = this.height - 66;
        if (dragNode != null || menuNode != null) {
            return;
        }
        context.fill(x0, y0, this.width - 4, this.height - 30, 0xC0101014);
        context.drawTextWithShadow(this.textRenderer, "▼ 生成的代码", x0 + 4, y0 + 2, 0x4FC3F7);
        int cy = y0 + 14;
        List<String> lines = new ArrayList<>();
        collectCode(currentEvent().children, lines);
        for (String line : lines) {
            if (cy > this.height - 34) {
                break;
            }
            context.drawTextWithShadow(this.textRenderer,
                    line.length() > 84 ? line.substring(0, 84) : line, x0 + 6, cy, 0x81C784);
            cy += 10;
        }
    }

    private void collectCode(List<BlockNode> stack, List<String> out) {
        for (BlockNode n : stack) {
            if (n.type.startsWith("cond_")) {
                continue;
            }
            String c = dev.packweaver.bridge.pack.CodeGen.commandOf(n);
            if (n.type.equals("ctrl_if")) {
                out.add("if [" + dev.packweaver.bridge.pack.CodeGen.conditionsOf(n) + "]:");
                collectCode(n.children, out);
                if (!n.elseChildren.isEmpty()) {
                    out.add("else:");
                    collectCode(n.elseChildren, out);
                }
            } else if (n.type.equals("ctrl_foreach")) {
                out.add("for each @a:");
                collectCode(n.children, out);
            } else if (!c.isBlank()) {
                out.add(c);
            }
        }
    }

    private void drawMenu(DrawContext context) {
        int w = 130;
        int h = menuItems.size() * 14 + 4;
        context.fill(menuX, menuY, menuX + w, menuY + h, 0xF0202028);
        context.fill(menuX, menuY, menuX + w, menuY + 1, 0xFF4FC3F7);
        for (int i = 0; i < menuItems.size(); i++) {
            context.drawTextWithShadow(this.textRenderer, menuItems.get(i), menuX + 6, menuY + 4 + i * 14, 0xFFE0E0E0);
        }
    }

    private String trunc(String s, int chars) {
        if (s == null) {
            return "";
        }
        return s.length() > chars ? s.substring(0, Math.max(1, chars)) + "…" : s;
    }

    // ---------------- 交互 ----------------

    private NodeView viewOf(BlockNode n) {
        for (NodeView v : views) {
            if (v.node == n) {
                return v;
            }
        }
        return null;
    }

    private NodeView hitNode(float wx, float wy) {
        for (int i = views.size() - 1; i >= 0; i--) {
            NodeView v = views.get(i);
            if (wx >= v.x && wx <= v.x + NW && wy >= v.y && wy <= v.y + NH) {
                return v;
            }
        }
        return null;
    }

    /** 命中某节点锚点，返回 socket 名。 */
    private String hitSocket(NodeView v, float wx, float wy) {
        float r = 6;
        if (!v.event && !v.cond && Math.abs(wx - v.x) <= r && Math.abs(wy - v.inY()) <= r) {
            return "in";
        }
        if (v.iff && Math.abs(wx - v.x) <= r && Math.abs(wy - v.condInY()) <= r) {
            return "cond";
        }
        if (!v.event && Math.abs(wx - (v.x + NW)) <= r) {
            if (v.iff) {
                if (Math.abs(wy - v.outY()) <= r) {
                    return "true";
                }
                if (Math.abs(wy - v.falseOutY()) <= r) {
                    return "false";
                }
            } else if (Math.abs(wy - v.outY()) <= r) {
                return "out";
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX;
        int my = (int) mouseY;
        float wx = toWorldX(mx);
        float wy = toWorldY(my);

        if (menuNode != null) {
            handleMenuClick(mx, my);
            return true;
        }

        // 左侧面板
        if (mx < LEFT_W && my > 22) {
            return paletteClick(mx, my);
        }

        NodeView hit = hitNode(wx, wy);

        // 右键：节点菜单
        if (button == 1 && hit != null) {
            menuNode = hit.node;
            menuX = Math.min(mx, this.width - 140);
            menuY = Math.min(my, this.height - 90);
            buildMenu(menuNode);
            return true;
        }
        // 中键：平移
        if (button == 2) {
            panning = true;
            panStartX = mx;
            panStartY = my;
            panOrigX = panX;
            panOrigY = panY;
            return true;
        }
        // 左键
        if (button == 0) {
            // 锚点优先
            if (hit != null) {
                String socket = hitSocket(hit, wx, wy);
                if (socket != null && !socket.equals("in") && !socket.equals("cond")) {
                    dragNode = hit.node;
                    dragSocket = socket;
                    dragMX = mx;
                    dragMY = my;
                    return true;
                }
            }
            if (hit != null) {
                openParams(hit.node);
                return true;
            }
            // 空白左键拖动 = 平移
            panning = true;
            panStartX = mx;
            panStartY = my;
            panOrigX = panX;
            panOrigY = panY;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (dragNode != null) {
            dragMX = (int) mouseX;
            dragMY = (int) mouseY;
            return true;
        }
        if (panning) {
            panX = panOrigX + ((int) mouseX - panStartX);
            panY = panOrigY + ((int) mouseY - panStartY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (dragNode != null) {
            finishDrag(toWorldX((int) mouseX), toWorldY((int) mouseY));
            dragNode = null;
            return true;
        }
        panning = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        float oldZoom = zoom;
        zoom = Math.max(0.4f, Math.min(2.5f, zoom * (amount > 0 ? 1.12f : 0.89f)));
        if (oldZoom != zoom) {
            // 以光标为中心缩放
            float wx = toWorldX((int) mouseX);
            float wy = toWorldY((int) mouseY);
            panX = (int) (mouseX - wx * zoom);
            panY = (int) (mouseY - wy * zoom);
        }
        return true;
    }

    /** 落锚：根据拖拽源与目标锚点重挂接（Blender 连线语义）。 */
    private void finishDrag(float wx, float wy) {
        NodeView targetView = hitNode(wx, wy);
        if (targetView == null || targetView.node == dragNode) {
            message = "§7已取消连线";
            return;
        }
        BlockNode target = targetView.node;
        String targetSocket = hitSocket(targetView, wx, wy);
        List<BlockNode> roots = currentEvent().children;

        if (GraphOps.isAncestor(dragNode, target)) {
            message = "§c不能把节点连进它自己的子树";
            return;
        }
        // 源 = 条件输出 → 目标 if 的条件锚点
        if (dragNode.type.startsWith("cond_") && "cond".equals(targetSocket)) {
            GraphOps.detach(roots, dragNode);
            target.children.add(dragNode);
            message = "§a条件已接入「如果」节点";
            return;
        }
        // 其余连线必须落在目标「输入」锚点上
        if (!"in".equals(targetSocket)) {
            message = "§7请连到目标节点的左侧输入锚点";
            return;
        }
        // 源 = 是/否 分支 → 目标成为该分支的第一个块
        if (dragSocket.equals("true") || dragSocket.equals("false")) {
            List<BlockNode> oldList = GraphOps.findList(roots, dragNode);
            int oldIdx = oldList != null ? oldList.indexOf(dragNode) : -1;
            GraphOps.detach(roots, dragNode);
            GraphOps.detach(roots, target);
            if (dragSocket.equals("true")) {
                dragNode.children.add(0, target);
            } else {
                dragNode.elseChildren.add(0, target);
            }
            if (oldList != null) {
                oldList.add(Math.min(oldIdx < 0 ? 0 : oldIdx, oldList.size()), dragNode);
            }
            message = "§a已连到「" + (dragSocket.equals("true") ? "是" : "否") + "」分支";
            return;
        }
        // 源 = 执行输出 → 目标输入：目标接到源之后
        if ("out".equals(dragSocket)) {
            GraphOps.detach(roots, dragNode);
            GraphOps.insertAfter(roots, target, dragNode);
            message = "§a执行流已连接";
            return;
        }
        message = "§7该锚点组合无效（输出→输入 / 条件→如果）";
    }

    private void openParams(BlockNode node) {
        assert this.client != null;
        this.client.setScreen(new dev.packweaver.bridge.gui.BlockParamScreen(this, project, node));
    }

    // ---------------- 菜单 ----------------

    private void buildMenu(BlockNode node) {
        menuItems.clear();
        menuItems.add("✎ 编辑参数");
        menuItems.add("▲ 上移");
        menuItems.add("▼ 下移");
        if (node.type.equals("ctrl_if") && node.elseChildren.isEmpty()) {
            menuItems.add("＋ 添加「否」分支");
        }
        menuItems.add("✕ 删除");
    }

    private void handleMenuClick(int mx, int my) {
        BlockNode node = menuNode;
        menuNode = null;
        if (mx < menuX || mx > menuX + 130 || my < menuY || my > menuY + menuItems.size() * 14 + 4) {
            return;
        }
        int idx = (my - menuY - 4) / 14;
        String item = idx >= 0 && idx < menuItems.size() ? menuItems.get(idx) : "";
        List<BlockNode> roots = currentEvent().children;
        switch (item) {
            case "✎ 编辑参数" -> openParams(node);
            case "▲ 上移" -> {
                List<BlockNode> list = GraphOps.findList(roots, node);
                if (list != null) {
                    int i = list.indexOf(node);
                    if (i > 0) {
                        list.remove(i);
                        list.add(i - 1, node);
                    }
                }
            }
            case "▼ 下移" -> {
                List<BlockNode> list = GraphOps.findList(roots, node);
                if (list != null) {
                    int i = list.indexOf(node);
                    if (i >= 0 && i < list.size() - 1) {
                        list.remove(i);
                        list.add(i + 1, node);
                    }
                }
            }
            case "＋ 添加「否」分支" -> node.elseChildren.add(
                    new BlockNode("act_send", "target", "@s", "text", "（否分支）", "pos", "聊天栏", "color", "红色"));
            case "✕ 删除" -> GraphOps.delete(roots, node);
            default -> {
            }
        }
    }

    // ---------------- 面板添加 ----------------

    private boolean paletteClick(int mx, int my) {
        int y = 30 + 12;
        for (String cat : BlockDefs.CATEGORIES) {
            if (mx >= 4 && mx <= LEFT_W - 4 && my >= y - 2 && my <= y + 10) {
                if ((cat.equals("高级") || cat.equals("自定义"))
                        && !dev.packweaver.bridge.client.PWLevel.canUseAdvanced()) {
                    message = "§7需要 Lv.2（创建 3 个项目）或 /pw level unlock";
                } else {
                    category = cat;
                    message = "";
                }
                return true;
            }
            y += 13;
        }
        y += 4 + 12;
        for (BlockDefs.BlockDef d : BlockDefs.byCategory(category)) {
            if (y > this.height - 60) {
                break;
            }
            if (mx >= 4 && mx <= LEFT_W - 4 && my >= y && my <= y + 12) {
                addBlock(d);
                return true;
            }
            y += 14;
        }
        return false;
    }

    private void addBlock(BlockDefs.BlockDef d) {
        if (d.event) {
            message = "§c事件请用顶部按钮切换";
            return;
        }
        BlockNode n = new BlockNode(d.type);
        for (BlockDefs.Param p : d.params) {
            if (p.def != null) {
                n.params.put(p.name, p.def);
            }
        }
        BlockNode ev = currentEvent();
        if (n.type.startsWith("cond_")) {
            BlockNode targetIf = lastIf(ev.children);
            if (targetIf == null) {
                message = "§c先添加「如果」节点，再把条件块加进去";
                return;
            }
            targetIf.children.add(n);
        } else {
            ev.children.add(n);
        }
        message = "";
    }

    private BlockNode lastIf(List<BlockNode> stack) {
        BlockNode found = null;
        for (BlockNode n : stack) {
            if (n.type.equals("ctrl_if")) {
                found = n;
            }
            BlockNode deeper = lastIf(n.children);
            if (deeper != null) {
                found = deeper;
            }
            deeper = lastIf(n.elseChildren);
            if (deeper != null) {
                found = deeper;
            }
        }
        return found;
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

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (hasControlDown() && keyCode == 83) {
            save(true);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
