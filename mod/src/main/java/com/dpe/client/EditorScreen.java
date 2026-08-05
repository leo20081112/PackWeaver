package com.dpe.client;

import com.dpe.common.block.BlockCategory;
import com.dpe.common.block.BlockField;
import com.dpe.common.block.BlockFieldType;
import com.dpe.common.block.BlockSchema;
import com.dpe.common.block.BlockSchemaRegistry;
import com.dpe.common.block.EditorBlock;
import com.dpe.common.block.EditorState;
import com.dpe.common.compile.BlockCompiler;
import com.dpe.common.compile.CompileResult;
import com.dpe.common.editor.Canvas;
import com.dpe.common.protocol.SaveApplyMessage;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scratch 风格积木编辑器主屏幕：调色板 / 画布（拖拽+缩放）/ 字段编辑 / 编译预览 / 导出。
 */
public class EditorScreen extends Screen {

    private static final int PALETTE_W = 130;
    private static final int FIELD_PANEL_W = 210;
    private static final int TOP_BAR_H = 22;
    private static final int BOTTOM_BAR_H = 24;
    private static final int BLOCK_W = 130;
    private static final int BLOCK_H_BASE = 30;
    private static final int BLOCK_H_PER_FIELD = 12;

    private final EditorState state;
    private final BlockSchemaRegistry reg;
    private final Canvas canvas;

    private String selectedId = null;
    private String draggingId = null;
    private double dragOffsetX = 0;
    private double dragOffsetY = 0;
    private boolean panning = false;
    private int lastPanX = 0;
    private int lastPanY = 0;

    private boolean linkMode = false;
    private String compilePreview = null;
    private String statusMessage = null;
    private long statusMessageTime = 0;
    private long lastRevision = 0;

    private int blockCounter = 0;
    private double nextPlaceX = 40;
    private double nextPlaceY = 40;

    private final Map<String, TextFieldWidget> fieldTextFields = new HashMap<>();

    public EditorScreen(EditorState state) {
        super(Text.literal("Datapack Editor"));
        this.state = state == null ? new EditorState() : state;
        this.reg = BlockSchemaRegistry.DEFAULT;
        this.canvas = new Canvas();
    }

    @Override
    protected void init() {
        fieldTextFields.clear();
        int bottomY = this.height - BOTTOM_BAR_H;

        // 顶部操作按钮（画布区上方）
        int topBtnX = PALETTE_W + 4;
        addDrawableChild(ButtonWidget.builder(Text.literal("Compile"), b -> showCompilePreview())
                .dimensions(topBtnX, 2, 70, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Export Zip"), b -> exportZip())
                .dimensions(topBtnX + 74, 2, 80, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Save & Apply"), b -> saveAndApply())
                .dimensions(topBtnX + 158, 2, 100, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
                .dimensions(this.width - 60, 2, 50, 16).build());

        // 左侧调色板：按 BlockCategory 分组列出 schema
        int py = TOP_BAR_H + 4;
        for (BlockCategory cat : BlockCategory.values()) {
            py += 4;
            addDrawableChild(ButtonWidget.builder(Text.literal("[" + cat.name() + "]"), b -> {})
                    .dimensions(4, py, PALETTE_W - 8, 12).build());
            py += 14;
            for (BlockSchema schema : reg.byCategory(cat)) {
                String schemaId = schema.id();
                addDrawableChild(ButtonWidget.builder(
                                Text.literal(schema.label()),
                                b -> addBlock(schemaId))
                        .dimensions(6, py, PALETTE_W - 12, 14).build());
                py += 15;
            }
        }

        // 右侧字段编辑区
        int fx = this.width - FIELD_PANEL_W + 4;
        int fw = FIELD_PANEL_W - 8;
        int fy = TOP_BAR_H + 4;
        if (selectedId == null) {
            fy += 14;
        } else {
            EditorBlock selected = state.getById(selectedId);
            BlockSchema schema = selected == null ? null : reg.get(selected.schemaId());
            if (schema != null) {
                fy = drawFieldHeader(fx, fy, fw, schema, selected);
                for (BlockField field : schema.fields()) {
                    fy = drawFieldWidget(fx, fy, fw, field, selected);
                }
                fy += 6;
                // 连接 / 删除
                addDrawableChild(ButtonWidget.builder(
                                Text.literal(linkMode ? "Click child..." : "Link Child"),
                                b -> toggleLinkMode())
                        .dimensions(fx, fy, fw, 14).build());
                fy += 15;
                addDrawableChild(ButtonWidget.builder(Text.literal("Delete Block"), b -> deleteSelected())
                        .dimensions(fx, fy, fw, 14).build());
                fy += 15;
            }
        }

        // 编译预览关闭按钮
        if (compilePreview != null) {
            addDrawableChild(ButtonWidget.builder(Text.literal("X"), b -> {
                        compilePreview = null;
                        clearAndInit();
                    })
                    .dimensions(this.width - 30, this.height - 30, 20, 20).build());
        }
    }

    private int drawFieldHeader(int x, int y, int w, BlockSchema schema, EditorBlock block) {
        // 仅占位，实际渲染在 render() 中
        addDrawableChild(ButtonWidget.builder(Text.literal(schema.label()), b -> {})
                .dimensions(x, y, w, 14).build());
        return y + 16;
    }

    private int drawFieldWidget(int x, int y, int w, BlockField field, EditorBlock block) {
        Object current = block.fieldValues().get(field.name());
        if (current == null) {
            current = field.defaultValue();
        }
        String curStr = current == null ? "" : current.toString();

        switch (field.type()) {
            case STRING, NUMBER, RESOURCE_LOCATION, BLOCK_REF -> {
                TextFieldWidget tf = new TextFieldWidget(this.textRenderer, x, y, w, 14, Text.literal(field.name()));
                tf.setMaxLength(4096);
                tf.setText(curStr);
                tf.setPlaceholder(Text.literal(field.name()));
                String fname = field.name();
                tf.setChangedListener(s -> {
                    EditorBlock b = state.getById(selectedId);
                    if (b != null) {
                        b.fieldValues().put(fname, s);
                    }
                });
                fieldTextFields.put(field.name(), tf);
                addDrawableChild(tf);
                return y + 16;
            }
            case BOOLEAN -> {
                boolean val = "true".equalsIgnoreCase(curStr);
                ButtonWidget btn = ButtonWidget.builder(
                                Text.literal(field.name() + ": " + val),
                                b -> {
                                    EditorBlock bb = state.getById(selectedId);
                                    if (bb != null) {
                                        boolean nv = !"true".equalsIgnoreCase(String.valueOf(bb.fieldValues().get(field.name())));
                                        bb.fieldValues().put(field.name(), nv);
                                        b.setMessage(Text.literal(field.name() + ": " + nv));
                                    }
                                })
                        .dimensions(x, y, w, 14).build();
                addDrawableChild(btn);
                return y + 16;
            }
            case ENUM -> {
                List<String> values = field.enumValues();
                ButtonWidget btn = ButtonWidget.builder(
                                Text.literal(field.name() + "=" + curStr),
                                b -> {
                                    EditorBlock bb = state.getById(selectedId);
                                    if (bb == null) {
                                        return;
                                    }
                                    String cur = String.valueOf(bb.fieldValues().get(field.name()));
                                    int idx = values.indexOf(cur);
                                    idx = (idx + 1) % Math.max(1, values.size());
                                    String nv = values.get(idx);
                                    bb.fieldValues().put(field.name(), nv);
                                    b.setMessage(Text.literal(field.name() + "=" + nv));
                                })
                        .dimensions(x, y, w, 14).build();
                addDrawableChild(btn);
                return y + 16;
            }
            case TEXT_COMPONENT -> {
                String fname = field.name();
                ButtonWidget btn = ButtonWidget.builder(
                                Text.literal(fname + ": [edit]"),
                                b -> openTextComponentEditor(fname))
                        .dimensions(x, y, w, 14).build();
                addDrawableChild(btn);
                return y + 16;
            }
            default -> {
                return y + 16;
            }
        }
    }

    private void addBlock(String schemaId) {
        BlockSchema schema = reg.get(schemaId);
        if (schema == null) {
            return;
        }
        String id = "b" + (++blockCounter);
        EditorBlock block = new EditorBlock(id, schemaId, nextPlaceX, nextPlaceY);
        // 应用默认字段值
        for (BlockField f : schema.fields()) {
            if (f.defaultValue() != null) {
                block.fieldValues().put(f.name(), f.defaultValue());
            }
        }
        state.addBlock(block);
        // 错开放置位置避免重叠
        nextPlaceX += 24;
        nextPlaceY += 24;
        if (nextPlaceX > 400) {
            nextPlaceX = 40;
            nextPlaceY = 40;
        }
        selectedId = id;
        linkMode = false;
        setStatus("Added " + schema.label());
        clearAndInit();
    }

    private void deleteSelected() {
        if (selectedId == null) {
            return;
        }
        state.removeBlock(selectedId);
        selectedId = null;
        linkMode = false;
        setStatus("Block deleted");
        clearAndInit();
    }

    private void toggleLinkMode() {
        if (selectedId == null) {
            return;
        }
        linkMode = !linkMode;
        setStatus(linkMode ? "Click a block to link as child" : "Link cancelled");
        clearAndInit();
    }

    private void openTextComponentEditor(String fieldName) {
        if (selectedId == null) {
            return;
        }
        EditorBlock block = state.getById(selectedId);
        if (block == null) {
            return;
        }
        Object cur = block.fieldValues().get(fieldName);
        String initial = cur == null ? "" : cur.toString();
        if (this.client != null) {
            this.client.setScreen(new TextComponentScreen(initial, json -> {
                EditorBlock b = state.getById(selectedId);
                if (b != null) {
                    b.fieldValues().put(fieldName, json);
                }
                setStatus("Text component updated");
            }, this));
        }
    }

    private void showCompilePreview() {
        syncViewToState();
        CompileResult result = new BlockCompiler().compile(state, reg);
        StringBuilder sb = new StringBuilder();
        if (result.success()) {
            sb.append("§aCompile OK§r\n");
            sb.append("Functions: ").append(result.mcfunctions().size()).append('\n');
            for (var e : result.mcfunctions().entrySet()) {
                sb.append("  ").append(e.getKey()).append('\n');
                for (String line : e.getValue().split("\n")) {
                    if (!line.isEmpty()) {
                        sb.append("    ").append(line).append('\n');
                    }
                }
            }
            if (!result.jsonFiles().isEmpty()) {
                sb.append("JSON files: ").append(result.jsonFiles().size()).append('\n');
                for (var e : result.jsonFiles().entrySet()) {
                    sb.append("  ").append(e.getKey()).append('\n');
                }
            }
        } else {
            sb.append("§cCompile FAILED§r\n");
            sb.append(OfflineDatapackIo.formatErrors(result.errors()));
        }
        compilePreview = sb.toString();
        setStatus(result.success() ? "Compile OK" : "Compile failed");
        clearAndInit();
    }

    private void exportZip() {
        syncViewToState();
        try {
            Path target = OfflineDatapackIo.export(state, reg);
            setStatus("Exported to " + target.getFileName());
        } catch (IllegalStateException e) {
            compilePreview = "§cExport failed§r\n" + e.getMessage();
            setStatus("Export failed");
            clearAndInit();
        } catch (IOException e) {
            setStatus("Export IO error: " + e.getMessage());
        }
    }

    private void saveAndApply() {
        syncViewToState();
        if (ClientNetworking.canSend()) {
            ClientNetworking.send(new SaveApplyMessage(state.getActiveDatapackNamespace()));
            setStatus("Sent save & apply to server");
        } else {
            // 离线：导出 zip
            exportZip();
        }
    }

    private void syncViewToState() {
        state.setZoom(canvas.getZoom());
        state.setPan(canvas.getPanX(), canvas.getPanY());
    }

    private void setStatus(String msg) {
        statusMessage = msg;
        statusMessageTime = System.currentTimeMillis();
    }

    /** 接收服务端同步的编辑器状态。 */
    public void applySync(String json, long revision) {
        try {
            EditorState synced = EditorState.fromJson(json);
            // 保留画布视图，仅替换 blocks
            this.state.setActiveDatapackNamespace(synced.getActiveDatapackNamespace());
            // 清空并重建
            for (EditorBlock b : new ArrayList<>(this.state.getBlocks())) {
                this.state.removeBlock(b.id());
            }
            for (EditorBlock b : synced.getBlocks()) {
                this.state.addBlock(b);
            }
            this.lastRevision = revision;
            this.selectedId = null;
            this.linkMode = false;
            setStatus("Synced rev " + revision);
            clearAndInit();
        } catch (Exception ignored) {
            // 忽略同步错误
        }
    }

    // ---------- 渲染 ----------

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 背景
        context.fill(0, 0, this.width, this.height, 0xFF202020);
        // 调色板背景
        context.fill(0, TOP_BAR_H, PALETTE_W, this.height - BOTTOM_BAR_H, 0xFF2B2B2B);
        // 字段面板背景
        context.fill(this.width - FIELD_PANEL_W, TOP_BAR_H, this.width, this.height - BOTTOM_BAR_H, 0xFF2B2B2B);
        // 画布背景
        context.fill(PALETTE_W, TOP_BAR_H, this.width - FIELD_PANEL_W, this.height - BOTTOM_BAR_H, 0xFF1A1A1A);

        // 标题
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 4, 0xFFFFFF);

        // 调色板分组标签
        int py = TOP_BAR_H + 4;
        for (BlockCategory cat : BlockCategory.values()) {
            py += 4 + 14;
            for (BlockSchema s : reg.byCategory(cat)) {
                py += 15;
            }
        }

        // 画布网格点（简单）
        drawCanvasGrid(context);

        // 连线
        drawConnections(context);

        // 积木块
        for (EditorBlock block : state.getBlocks()) {
            drawBlock(context, block);
        }

        // 字段面板内容
        drawFieldPanel(context);

        // 子组件（按钮/输入框）
        super.render(context, mouseX, mouseY, delta);

        // 编译预览覆盖层
        if (compilePreview != null) {
            drawCompilePreview(context);
        }

        // 状态消息
        drawStatus(context);

        // 画布区域边界
        context.drawBorder(PALETTE_W - 1, TOP_BAR_H - 1, this.width - FIELD_PANEL_W - PALETTE_W + 2,
                this.height - BOTTOM_BAR_H - TOP_BAR_H + 2, 0xFF555555);
    }

    private void drawCanvasGrid(DrawContext context) {
        int x0 = PALETTE_W;
        int x1 = this.width - FIELD_PANEL_W;
        int y0 = TOP_BAR_H;
        int y1 = this.height - BOTTOM_BAR_H;
        double z = canvas.getZoom();
        int step = (int) Math.max(20, 40 * z);
        int panX = (int) canvas.getPanX();
        int panY = (int) canvas.getPanY();
        int gridColor = 0xFF2A2A2A;
        for (int x = panX % step; x < x1 - x0; x += step) {
            int sx = x0 + x;
            if (sx >= x0 && sx < x1) {
                context.fill(sx, y0, sx + 1, y1, gridColor);
            }
        }
        for (int y = panY % step; y < y1 - y0; y += step) {
            int sy = y0 + y;
            if (sy >= y0 && sy < y1) {
                context.fill(x0, sy, x1, sy + 1, gridColor);
            }
        }
    }

    private int blockHeight(BlockSchema schema) {
        return BLOCK_H_BASE + BLOCK_H_PER_FIELD * (schema == null ? 0 : schema.fields().size());
    }

    private int toScreenX(double wx) {
        return (int) (wx * canvas.getZoom() + canvas.getPanX());
    }

    private int toScreenY(double wy) {
        return (int) (wy * canvas.getZoom() + canvas.getPanY());
    }

    private double toWorldX(double sx) {
        return (sx - canvas.getPanX()) / canvas.getZoom();
    }

    private double toWorldY(double sy) {
        return (sy - canvas.getPanY()) / canvas.getZoom();
    }

    private void drawBlock(DrawContext context, EditorBlock block) {
        BlockSchema schema = reg.get(block.schemaId());
        int sx = toScreenX(block.x());
        int sy = toScreenY(block.y());
        int sw = (int) (BLOCK_W * canvas.getZoom());
        int sh = (int) (blockHeight(schema) * canvas.getZoom());
        if (sx + sw < PALETTE_W || sx > this.width - FIELD_PANEL_W
                || sy + sh < TOP_BAR_H || sy > this.height - BOTTOM_BAR_H) {
            // 部分裁剪判断（仍渲染可见部分）
        }
        int fill = parseColor(schema == null ? "#888888" : schema.color(), 0xFF000000);
        context.fill(sx, sy, sx + sw, sy + sh, fill);
        // 选中高亮边框
        if (block.id().equals(selectedId)) {
            context.drawBorder(sx - 1, sy - 1, sw + 2, sh + 2, 0xFFFFFFFF);
        } else {
            context.drawBorder(sx, sy, sw, sh, 0xFF000000);
        }
        // 标签
        String label = schema == null ? block.schemaId() : schema.label();
        context.drawTextWithShadow(this.textRenderer, Text.literal(truncate(label, sw - 4)),
                sx + 3, sy + 2, 0xFFFFFF);
        // 字段值
        if (schema != null) {
            int lineY = sy + 14;
            for (BlockField f : schema.fields()) {
                Object v = block.fieldValues().get(f.name());
                if (v == null) {
                    v = f.defaultValue();
                }
                String vs = v == null ? "" : v.toString();
                context.drawTextWithShadow(this.textRenderer,
                        Text.literal(truncate(f.name() + "=" + vs, sw - 4)),
                        sx + 3, lineY, 0xDDDDDD);
                lineY += (int) (BLOCK_H_PER_FIELD * canvas.getZoom());
            }
        }
    }

    private void drawConnections(DrawContext context) {
        for (EditorBlock parent : state.getBlocks()) {
            int px = toScreenX(parent.x()) + (int) (BLOCK_W * canvas.getZoom()) / 2;
            int py = toScreenY(parent.y()) + (int) (blockHeight(reg.get(parent.schemaId())) * canvas.getZoom());
            for (String childId : parent.childIds()) {
                EditorBlock child = state.getById(childId);
                if (child == null) {
                    continue;
                }
                int cx = toScreenX(child.x()) + (int) (BLOCK_W * canvas.getZoom()) / 2;
                int cy = toScreenY(child.y());
                // 简单直线
                drawLine(context, px, py, cx, cy, 0xFFCCCCCC);
            }
        }
    }

    /** 用 fill 画 1px 线段（水平/垂直/对角近似）。 */
    private void drawLine(DrawContext context, int x1, int y1, int x2, int y2, int color) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        if (steps == 0) {
            context.fill(x1, y1, x1 + 1, y1 + 1, color);
            return;
        }
        for (int i = 0; i <= steps; i++) {
            int x = x1 + (x2 - x1) * i / steps;
            int y = y1 + (y2 - y1) * i / steps;
            context.fill(x, y, x + 1, y + 1, color);
        }
    }

    private void drawFieldPanel(DrawContext context) {
        int x = this.width - FIELD_PANEL_W + 4;
        int y = TOP_BAR_H + 2;
        if (selectedId == null) {
            context.drawTextWithShadow(this.textRenderer, Text.literal("No block selected"),
                    x, y + 14, 0xAAAAAA);
            return;
        }
        EditorBlock block = state.getById(selectedId);
        if (block == null) {
            return;
        }
        BlockSchema schema = reg.get(block.schemaId());
        if (schema != null) {
            context.drawTextWithShadow(this.textRenderer, Text.literal("ID: " + block.id()),
                    x, y, 0xFFAA00);
            context.drawTextWithShadow(this.textRenderer,
                    Text.literal("Type: " + schema.id()), x, y + 12, 0xAAAAFF);
        }
        if (linkMode) {
            context.drawTextWithShadow(this.textRenderer,
                    Text.literal("> Click a block to link").formatted(Formatting.YELLOW),
                    x, this.height - BOTTOM_BAR_H - 14, 0xFFFF00);
        }
    }

    private void drawCompilePreview(DrawContext context) {
        int pw = Math.min(420, this.width - 40);
        int ph = Math.min(280, this.height - 80);
        int px = (this.width - pw) / 2;
        int py = (this.height - ph) / 2;
        context.fill(px, py, px + pw, py + ph, 0xE8000000);
        context.drawBorder(px, py, pw, ph, 0xFF888888);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Compile Preview"),
                px + 6, py + 4, 0xFFFFFF);
        int lineY = py + 20;
        for (String line : compilePreview.split("\n")) {
            String clean = line.replace("§a", "").replace("§c", "").replace("§r", "");
            int color = 0xDDDDDD;
            if (line.contains("§a")) {
                color = 0x55FF55;
            } else if (line.contains("§c")) {
                color = 0xFF5555;
            }
            if (lineY < py + ph - 8) {
                context.drawTextWithShadow(this.textRenderer, Text.literal(truncate(clean, pw - 12)),
                        px + 6, lineY, color);
                lineY += 10;
            }
        }
    }

    private void drawStatus(DrawContext context) {
        if (statusMessage == null) {
            return;
        }
        long age = System.currentTimeMillis() - statusMessageTime;
        if (age > 4000) {
            statusMessage = null;
            return;
        }
        context.drawTextWithShadow(this.textRenderer, Text.literal(statusMessage),
                PALETTE_W + 4, this.height - 14, 0x55FF55);
    }

    // ---------- 鼠标交互 ----------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 先让子组件处理（按钮/输入框）
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        // 仅画布区域
        if (!inCanvas(mouseX, mouseY)) {
            return false;
        }
        if (button == 1) {
            // 右键开始平移
            panning = true;
            lastPanX = (int) mouseX;
            lastPanY = (int) mouseY;
            return true;
        }
        if (button != 0) {
            return false;
        }
        // 命中测试
        String hit = hitBlock(mouseX, mouseY);
        if (linkMode && hit != null && selectedId != null && !hit.equals(selectedId)) {
            state.connect(selectedId, hit);
            linkMode = false;
            setStatus("Linked " + selectedId + " -> " + hit);
            clearAndInit();
            return true;
        }
        if (hit != null) {
            selectedId = hit;
            EditorBlock b = state.getById(hit);
            double wx = toWorldX(mouseX);
            double wy = toWorldY(mouseY);
            dragOffsetX = wx - (b == null ? 0 : b.x());
            dragOffsetY = wy - (b == null ? 0 : b.y());
            draggingId = hit;
            linkMode = false;
            clearAndInit();
            return true;
        }
        // 空白：取消选中
        if (selectedId != null || linkMode) {
            selectedId = null;
            linkMode = false;
            clearAndInit();
        }
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (panning) {
            canvas.panBy((int) deltaX, (int) deltaY);
            return true;
        }
        if (draggingId != null) {
            double wx = toWorldX(mouseX) - dragOffsetX;
            double wy = toWorldY(mouseY) - dragOffsetY;
            state.moveBlock(draggingId, wx, wy);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (panning) {
            panning = false;
            return true;
        }
        if (draggingId != null) {
            draggingId = null;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (inCanvas(mouseX, mouseY)) {
            double factor = verticalAmount > 0 ? 1.1 : (verticalAmount < 0 ? 1.0 / 1.1 : 1.0);
            canvas.zoomBy(factor);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    private boolean inCanvas(double x, double y) {
        return x >= PALETTE_W && x < this.width - FIELD_PANEL_W
                && y >= TOP_BAR_H && y < this.height - BOTTOM_BAR_H;
    }

    private String hitBlock(double mouseX, double mouseY) {
        double wx = toWorldX(mouseX);
        double wy = toWorldY(mouseY);
        // 倒序遍历（顶层优先）
        List<EditorBlock> list = new ArrayList<>(state.getBlocks());
        for (int i = list.size() - 1; i >= 0; i--) {
            EditorBlock b = list.get(i);
            BlockSchema schema = reg.get(b.schemaId());
            int h = blockHeight(schema);
            if (wx >= b.x() && wx <= b.x() + BLOCK_W && wy >= b.y() && wy <= b.y() + h) {
                return b.id();
            }
        }
        return null;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(null);
        }
    }

    // ---------- 工具 ----------

    private String truncate(String s, int maxPixelWidth) {
        if (s == null) {
            return "";
        }
        if (maxPixelWidth <= 4) {
            return "";
        }
        int w = this.textRenderer.getWidth(s);
        if (w <= maxPixelWidth) {
            return s;
        }
        String ellipsis = "..";
        int ew = this.textRenderer.getWidth(ellipsis);
        int i = s.length() - 1;
        while (i > 0 && this.textRenderer.getWidth(s.substring(0, i)) + ew > maxPixelWidth) {
            i--;
        }
        return s.substring(0, Math.max(0, i)) + ellipsis;
    }

    /** 解析 "#RRGGBB" 为 ARGB int（含 alpha）。 */
    private static int parseColor(String hex, int defaultArgb) {
        if (hex == null || !hex.startsWith("#") || hex.length() < 7) {
            return defaultArgb;
        }
        try {
            int rgb = Integer.parseInt(hex.substring(1), 16);
            return (defaultArgb & 0xFF000000) | (rgb & 0xFFFFFF);
        } catch (NumberFormatException ignored) {
            return defaultArgb;
        }
    }
}
