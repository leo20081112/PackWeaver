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
import com.dpe.common.compile.ValidationError;
import com.dpe.common.config.UserConfig;
import com.dpe.common.editor.Canvas;
import com.dpe.common.reload.ReloadResult;
import net.minecraft.client.MinecraftClient;
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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scratch 风格积木编辑器主屏幕：
 * 左侧可滚动调色板（分类折叠）/ 画布（拖拽+缩放）/ 字段编辑 / 编译预览 / 导出 / 重载。
 * 顶部按钮：编译 / 切到 IDE(M) / 重载(R) / 手册 / 设置 / 帮助(F1) / 关闭。
 */
public class EditorScreen extends Screen {

    private static final int PALETTE_W = 140;
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

    /** 调色板滚动偏移。 */
    private int paletteScroll = 0;
    /** 折叠的分类集合。 */
    private final Set<BlockCategory> collapsedCategories = new HashSet<>();
    /** 调色板是否可见（P 切换）。 */
    private boolean paletteVisible = true;

    /** 调色板布局缓存（在 init/render 间复用）。 */
    private final List<PaletteRow> paletteRows = new ArrayList<>();
    private static final int PALETTE_ROW_H = 13;
    private static final int PALETTE_HEADER_H = 16;

    /** 新手引导。 */
    private OnboardingOverlay onboarding;
    /** 字体缩放倍率（来自 UserConfig.fontSize）。 */
    private float fontScale = 1.0f;

    public EditorScreen(EditorState state) {
        super(Text.literal("Datapack Editor"));
        this.state = state == null ? new EditorState() : state;
        this.reg = BlockSchemaRegistry.DEFAULT;
        this.canvas = new Canvas();
    }

    @Override
    protected void init() {
        fieldTextFields.clear();

        UserConfig cfg = DatapackEditorClient.config();
        // 应用字体缩放
        fontScale = (float) (cfg != null && cfg.fontSize > 0 ? cfg.fontSize : 1.0);
        Path configPath = DatapackEditorClient.configPath(this.client);
        if (onboarding == null) {
            onboarding = new OnboardingOverlay(this, cfg, configPath);
        } else {
            // 切屏后路径不变，沿用
            onboarding = new OnboardingOverlay(this, cfg, configPath);
        }

        // 顶部按钮
        int topBtnX = PALETTE_W + 4;
        int bx = topBtnX;
        addDrawableChild(ButtonWidget.builder(Text.literal("编译"), b -> showCompilePreview())
                .dimensions(bx, 2, 50, 16).build());
        bx += 52;
        addDrawableChild(ButtonWidget.builder(Text.literal("切到IDE (M)"), b -> switchToIde())
                .dimensions(bx, 2, 90, 16).build());
        bx += 92;
        addDrawableChild(ButtonWidget.builder(Text.literal("重载 (R)"), b -> doReload())
                .dimensions(bx, 2, 70, 16).build());
        bx += 72;
        addDrawableChild(ButtonWidget.builder(Text.literal("导出Zip"), b -> exportZip())
                .dimensions(bx, 2, 70, 16).build());
        bx += 72;
        addDrawableChild(ButtonWidget.builder(Text.literal("保存应用"), b -> saveAndApply())
                .dimensions(bx, 2, 70, 16).build());
        bx += 72;
        addDrawableChild(ButtonWidget.builder(Text.literal("手册"), b -> openManual())
                .dimensions(bx, 2, 50, 16).build());
        bx += 52;
        addDrawableChild(ButtonWidget.builder(Text.literal("设置"), b -> openSettings())
                .dimensions(bx, 2, 50, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("关闭"), b -> close())
                .dimensions(this.width - 50, 2, 46, 16).build());

        // 字段编辑区
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
                addDrawableChild(ButtonWidget.builder(
                                Text.literal(linkMode ? "点子块..." : "连接子块"),
                                b -> toggleLinkMode())
                        .dimensions(fx, fy, fw, 14).build());
                fy += 15;
                addDrawableChild(ButtonWidget.builder(Text.literal("删除积木"), b -> deleteSelected())
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

        rebuildPaletteRows();
    }

    /** 计算调色板行布局。 */
    private void rebuildPaletteRows() {
        paletteRows.clear();
        for (BlockCategory cat : BlockCategory.values()) {
            paletteRows.add(new PaletteRow(cat, null, true));
            if (!collapsedCategories.contains(cat)) {
                for (BlockSchema s : reg.byCategory(cat)) {
                    paletteRows.add(new PaletteRow(cat, s, false));
                }
            }
        }
    }

    /** 调色板行：分类头或 schema 项。 */
    private record PaletteRow(BlockCategory category, BlockSchema schema, boolean header) {
    }

    private int drawFieldHeader(int x, int y, int w, BlockSchema schema, EditorBlock block) {
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
                                Text.literal(fname + ": [编辑]"),
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
        for (BlockField f : schema.fields()) {
            if (f.defaultValue() != null) {
                block.fieldValues().put(f.name(), f.defaultValue());
            }
        }
        state.addBlock(block);
        nextPlaceX += 24;
        nextPlaceY += 24;
        if (nextPlaceX > 400) {
            nextPlaceX = 40;
            nextPlaceY = 40;
        }
        selectedId = id;
        linkMode = false;
        setStatus("已添加: " + schema.label());
        clearAndInit();
    }

    private void deleteSelected() {
        if (selectedId == null) {
            return;
        }
        state.removeBlock(selectedId);
        selectedId = null;
        linkMode = false;
        setStatus("积木已删除");
        clearAndInit();
    }

    private void toggleLinkMode() {
        if (selectedId == null) {
            return;
        }
        linkMode = !linkMode;
        setStatus(linkMode ? "点击子块以连接" : "已取消连接");
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
                setStatus("文本组件已更新");
            }, this));
        }
    }

    /** 切到 IDE 模式。 */
    private void switchToIde() {
        syncViewToState();
        if (this.client != null) {
            this.client.setScreen(new IdeEditorScreen(state));
        }
    }

    /** 重载：调用 ReloadService。 */
    private void doReload() {
        syncViewToState();
        ReloadResult r = ReloadService.reload(state, MinecraftClient.getInstance());
        setStatus((r.success() ? "重载成功: " : "重载失败: ") + r.message());
        if (!r.success()) {
            // 显示编译错误
            compilePreview = "§c重载失败§r\n" + r.message();
            clearAndInit();
        }
    }

    private void openManual() {
        if (this.client != null) {
            this.client.setScreen(new ManualScreen(this));
        }
    }

    private void openSettings() {
        if (this.client != null) {
            this.client.setScreen(new KeyBindingsSettingsScreen(this,
                    DatapackEditorClient.config(),
                    DatapackEditorClient.configPath(this.client)));
        }
    }

    private void showCompilePreview() {
        syncViewToState();
        CompileResult result = new BlockCompiler().compile(state, reg);
        StringBuilder sb = new StringBuilder();
        if (result.success()) {
            sb.append("§a编译成功§r\n");
            sb.append("函数: ").append(result.mcfunctions().size()).append('\n');
            for (var e : result.mcfunctions().entrySet()) {
                sb.append("  ").append(e.getKey()).append('\n');
                for (String line : e.getValue().split("\n")) {
                    if (!line.isEmpty()) {
                        sb.append("    ").append(line).append('\n');
                    }
                }
            }
            if (!result.jsonFiles().isEmpty()) {
                sb.append("JSON 文件: ").append(result.jsonFiles().size()).append('\n');
                for (var e : result.jsonFiles().entrySet()) {
                    sb.append("  ").append(e.getKey()).append('\n');
                }
            }
        } else {
            sb.append("§c编译失败§r\n");
            sb.append(formatErrors(result.errors()));
        }
        compilePreview = sb.toString();
        setStatus(result.success() ? "编译成功" : "编译失败");
        clearAndInit();
    }

    private void exportZip() {
        syncViewToState();
        try {
            Path target = OfflineDatapackIo.export(state, reg);
            setStatus("已导出: " + target.getFileName());
        } catch (IllegalStateException e) {
            compilePreview = "§c导出失败§r\n" + e.getMessage();
            setStatus("导出失败");
            clearAndInit();
        } catch (IOException e) {
            setStatus("导出 IO 错误: " + e.getMessage());
        }
    }

    private void saveAndApply() {
        syncViewToState();
        if (ClientNetworking.canSend()) {
            // 通过 ReloadService 统一路由
            ReloadResult r = ReloadService.reload(state, MinecraftClient.getInstance());
            setStatus((r.success() ? "成功: " : "失败: ") + r.message());
        } else {
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
            this.state.setActiveDatapackNamespace(synced.getActiveDatapackNamespace());
            for (EditorBlock b : new ArrayList<>(this.state.getBlocks())) {
                this.state.removeBlock(b.id());
            }
            for (EditorBlock b : synced.getBlocks()) {
                this.state.addBlock(b);
            }
            this.lastRevision = revision;
            this.selectedId = null;
            this.linkMode = false;
            setStatus("已同步版本 " + revision);
            clearAndInit();
        } catch (Exception ignored) {
            // 忽略同步错误
        }
    }

    // ---------- 渲染 ----------

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 背景
        context.fill(0, 0, this.width, this.height, 0xFF1A1A1A);
        // 调色板背景
        if (paletteVisible) {
            context.fill(0, TOP_BAR_H, PALETTE_W, this.height - BOTTOM_BAR_H, 0xFF252526);
        }
        // 字段面板背景
        context.fill(this.width - FIELD_PANEL_W, TOP_BAR_H, this.width, this.height - BOTTOM_BAR_H, 0xFF252526);
        // 画布背景
        int canvasX0 = paletteVisible ? PALETTE_W : 0;
        context.fill(canvasX0, TOP_BAR_H, this.width - FIELD_PANEL_W, this.height - BOTTOM_BAR_H, 0xFF1E1E1E);

        // 标题
        context.drawTextWithShadow(this.textRenderer, this.title, 4, 4, 0xFFFFFF);

        // 调色板（自绘）
        if (paletteVisible) {
            drawPalette(context, mouseX, mouseY);
        }

        // 画布网格
        drawCanvasGrid(context);
        // 连线
        drawConnections(context);
        // 积木块
        for (EditorBlock block : state.getBlocks()) {
            drawBlock(context, block);
        }

        // 字段面板
        drawFieldPanel(context);

        // 子组件
        super.render(context, mouseX, mouseY, delta);

        // 编译预览
        if (compilePreview != null) {
            drawCompilePreview(context);
        }

        // 积木 tooltip（悬停）
        drawBlockTooltip(context, mouseX, mouseY);

        // 状态消息
        drawStatus(context);

        // 画布边界
        context.drawBorder(canvasX0 - 1, TOP_BAR_H - 1, this.width - FIELD_PANEL_W - canvasX0 + 2,
                this.height - BOTTOM_BAR_H - TOP_BAR_H + 2, 0xFF444444);

        // 新手引导覆盖
        if (onboarding != null) {
            onboarding.render(context, mouseX, mouseY, this.width, this.height);
        }
    }

    /** 渲染调色板（自绘可滚动+折叠）。 */
    private void drawPalette(DrawContext context, int mouseX, int mouseY) {
        int top = TOP_BAR_H + 2;
        int bottom = this.height - BOTTOM_BAR_H;
        int y = top - paletteScroll;
        // 裁剪范围（简单：超出 bottom 不绘制）
        for (PaletteRow row : paletteRows) {
            int h = row.header ? PALETTE_HEADER_H : PALETTE_ROW_H;
            int ry = y;
            if (ry + h >= top && ry <= bottom) {
                if (row.header) {
                    boolean collapsed = collapsedCategories.contains(row.category);
                    String prefix = collapsed ? "[+] " : "[-] ";
                    String label = prefix + categoryLabel(row.category);
                    // 头部背景
                    context.fill(2, ry, PALETTE_W - 2, ry + h, 0xFF333333);
                    context.drawTextWithShadow(this.textRenderer, Text.literal(label),
                            4, ry + 3, 0xFFCCCCCC);
                } else if (row.schema != null) {
                    boolean hover = mouseX >= 2 && mouseX < PALETTE_W - 2
                            && mouseY >= ry && mouseY < ry + h;
                    int bg = hover ? 0xFF094771 : 0xFF2D2D2D;
                    context.fill(4, ry, PALETTE_W - 4, ry + h, bg);
                    // 颜色点
                    int dot = parseColor(row.schema.color(), 0xFF888888);
                    context.fill(6, ry + 3, 10, ry + 9, dot);
                    String label = truncate(row.schema.label(), PALETTE_W - 18);
                    context.drawTextWithShadow(this.textRenderer, Text.literal(label),
                            12, ry + 2, 0xFFEEEEEE);
                }
            }
            y += h;
        }
        // 滚动条
        int totalH = totalPaletteHeight();
        int visibleH = bottom - top;
        if (totalH > visibleH) {
            int barH = Math.max(20, visibleH * visibleH / totalH);
            int barY = top + (int) ((long) paletteScroll * (visibleH - barH) / Math.max(1, totalH - visibleH));
            context.fill(PALETTE_W - 4, barY, PALETTE_W - 2, barY + barH, 0xFF666666);
        }
    }

    private int totalPaletteHeight() {
        int h = 0;
        for (PaletteRow row : paletteRows) {
            h += row.header ? PALETTE_HEADER_H : PALETTE_ROW_H;
        }
        return h;
    }

    private static String categoryLabel(BlockCategory cat) {
        return switch (cat) {
            case EVENT -> "事件";
            case CONDITION -> "条件";
            case ACTION -> "动作";
        };
    }

    private void drawCanvasGrid(DrawContext context) {
        int x0 = paletteVisible ? PALETTE_W : 0;
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
        int origin = paletteVisible ? PALETTE_W : 0;
        return (int) (wx * canvas.getZoom() + canvas.getPanX() + origin);
    }

    private int toScreenY(double wy) {
        return (int) (wy * canvas.getZoom() + canvas.getPanY());
    }

    private double toWorldX(double sx) {
        int origin = paletteVisible ? PALETTE_W : 0;
        return (sx - canvas.getPanX() - origin) / canvas.getZoom();
    }

    private double toWorldY(double sy) {
        return (sy - canvas.getPanY()) / canvas.getZoom();
    }

    private void drawBlock(DrawContext context, EditorBlock block) {
        BlockSchema schema = reg.get(block.schemaId());
        int sx = toScreenX(block.x());
        int sy = toScreenY(block.y());
        int sw = Math.max(BLOCK_W, (int) (BLOCK_W * canvas.getZoom()));
        int sh = (int) (blockHeight(schema) * canvas.getZoom());
        int fill = parseColor(schema == null ? "#888888" : schema.color(), 0xFF000000);
        context.fill(sx, sy, sx + sw, sy + sh, fill);
        if (block.id().equals(selectedId)) {
            context.drawBorder(sx - 1, sy - 1, sw + 2, sh + 2, 0xFFFFFFFF);
        } else {
            context.drawBorder(sx, sy, sw, sh, 0xFF000000);
        }
        String label = schema == null ? block.schemaId() : schema.label();
        drawScaledText(context, Text.literal(truncate(label, sw - 4)),
                sx + 3, sy + 2, 0xFFFFFF, fontScale);
        if (schema != null) {
            int lineY = sy + 14;
            for (BlockField f : schema.fields()) {
                Object v = block.fieldValues().get(f.name());
                if (v == null) {
                    v = f.defaultValue();
                }
                String vs = v == null ? "" : v.toString();
                drawScaledText(context,
                        Text.literal(truncate(f.name() + "=" + vs, sw - 4)),
                        sx + 3, lineY, 0xDDDDDD, fontScale);
                lineY += (int) (BLOCK_H_PER_FIELD * canvas.getZoom());
            }
        }
    }

    /** 应用 fontScale 缩放绘制文本（基于矩阵）。 */
    private void drawScaledText(DrawContext context, Text text, int x, int y, int color, float scale) {
        if (text == null) {
            return;
        }
        if (scale <= 0.0f) {
            scale = 1.0f;
        }
        if (Math.abs(scale - 1.0f) < 0.001f) {
            context.drawTextWithShadow(this.textRenderer, text, x, y, color);
            return;
        }
        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0);
        context.getMatrices().scale(scale, scale, 1.0f);
        context.drawTextWithShadow(this.textRenderer, text, 0, 0, color);
        context.getMatrices().pop();
    }

    /** 积木悬停 tooltip：显示 schema label + 字段中文说明。 */
    private void drawBlockTooltip(DrawContext context, int mouseX, int mouseY) {
        String hitId = hitBlock(mouseX, mouseY);
        if (hitId == null) {
            return;
        }
        EditorBlock b = state.getById(hitId);
        if (b == null) {
            return;
        }
        BlockSchema schema = reg.get(b.schemaId());
        if (schema == null) {
            return;
        }
        List<String> lines = new ArrayList<>();
        lines.add(schema.label() + "  [" + schema.id() + "]");
        for (BlockField f : schema.fields()) {
            Object v = b.fieldValues().get(f.name());
            if (v == null) {
                v = f.defaultValue();
            }
            String vs = v == null ? "" : v.toString();
            lines.add("  " + f.name() + " = " + truncate(vs, 160));
            lines.add("    类型: " + f.type().name().toLowerCase());
        }
        if (!schema.acceptsChildrenCategories().isEmpty()) {
            lines.add("可接子块: " + String.join(", ", schema.acceptsChildrenCategories()));
        }
        int w = 240;
        int h = lines.size() * 11 + 8;
        int tx = mouseX + 12;
        int ty = mouseY + 12;
        if (tx + w > this.width) {
            tx = mouseX - w - 8;
        }
        if (ty + h > this.height) {
            ty = mouseY - h - 8;
        }
        context.fill(tx, ty, tx + w, ty + h, 0xF8000000);
        context.drawBorder(tx, ty, w, h, 0xFFCCCCCC);
        int ly = ty + 4;
        for (String line : lines) {
            context.drawTextWithShadow(this.textRenderer,
                    Text.literal(truncate(line, w - 8)), tx + 4, ly, 0xFFDDDDDD);
            ly += 11;
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
                drawLine(context, px, py, cx, cy, 0xFFCCCCCC);
            }
        }
    }

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
            context.drawTextWithShadow(this.textRenderer, Text.literal("未选中积木"),
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
                    Text.literal("类型: " + schema.id()), x, y + 12, 0xAAAAFF);
        }
        if (linkMode) {
            context.drawTextWithShadow(this.textRenderer,
                    Text.literal("> 点击积木以连接").formatted(Formatting.YELLOW),
                    x, this.height - BOTTOM_BAR_H - 14, 0xFFFF00);
        }
    }

    private void drawCompilePreview(DrawContext context) {
        int pw = Math.min(440, this.width - 40);
        int ph = Math.min(300, this.height - 80);
        int px = (this.width - pw) / 2;
        int py = (this.height - ph) / 2;
        context.fill(px, py, px + pw, py + ph, 0xE8000000);
        context.drawBorder(px, py, pw, ph, 0xFF888888);
        context.drawTextWithShadow(this.textRenderer, Text.literal("编译预览 / 错误"),
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
        int x = paletteVisible ? PALETTE_W + 4 : 4;
        context.drawTextWithShadow(this.textRenderer, Text.literal(statusMessage),
                x, this.height - 14, 0x55FF55);
    }

    // ---------- 鼠标交互 ----------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 新手引导优先
        if (onboarding != null && onboarding.mouseClicked(mouseX, mouseY, this.width, this.height)) {
            return true;
        }
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        // 调色板点击
        if (paletteVisible && mouseX >= 0 && mouseX < PALETTE_W
                && mouseY >= TOP_BAR_H && mouseY < this.height - BOTTOM_BAR_H) {
            int top = TOP_BAR_H + 2;
            int y = top - paletteScroll;
            for (PaletteRow row : paletteRows) {
                int h = row.header ? PALETTE_HEADER_H : PALETTE_ROW_H;
                if (mouseY >= y && mouseY < y + h) {
                    if (row.header) {
                        if (collapsedCategories.contains(row.category)) {
                            collapsedCategories.remove(row.category);
                        } else {
                            collapsedCategories.add(row.category);
                        }
                        rebuildPaletteRows();
                    } else if (row.schema != null) {
                        addBlock(row.schema.id());
                    }
                    return true;
                }
                y += h;
            }
            return false;
        }
        // 仅画布区域
        int canvasX0 = paletteVisible ? PALETTE_W : 0;
        if (mouseX < canvasX0 || mouseX >= this.width - FIELD_PANEL_W
                || mouseY < TOP_BAR_H || mouseY >= this.height - BOTTOM_BAR_H) {
            return false;
        }
        if (button == 1) {
            panning = true;
            lastPanX = (int) mouseX;
            lastPanY = (int) mouseY;
            return true;
        }
        if (button != 0) {
            return false;
        }
        String hit = hitBlock(mouseX, mouseY);
        if (linkMode && hit != null && selectedId != null && !hit.equals(selectedId)) {
            state.connect(selectedId, hit);
            linkMode = false;
            setStatus("已连接 " + selectedId + " -> " + hit);
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
        // 调色板滚动
        if (paletteVisible && mouseX >= 0 && mouseX < PALETTE_W
                && mouseY >= TOP_BAR_H && mouseY < this.height - BOTTOM_BAR_H) {
            paletteScroll -= (int) (verticalAmount * PALETTE_ROW_H * 2);
            int top = TOP_BAR_H + 2;
            int visibleH = this.height - BOTTOM_BAR_H - top;
            int totalH = totalPaletteHeight();
            if (paletteScroll < 0) {
                paletteScroll = 0;
            }
            if (paletteScroll > Math.max(0, totalH - visibleH)) {
                paletteScroll = Math.max(0, totalH - visibleH);
            }
            return true;
        }
        if (inCanvas(mouseX, mouseY)) {
            double factor = verticalAmount > 0 ? 1.1 : (verticalAmount < 0 ? 1.0 / 1.1 : 1.0);
            canvas.zoomBy(factor);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        UserConfig cfg = DatapackEditorClient.config();
        if (cfg != null && cfg.keyBindings != null) {
            if (keyCode == cfg.keyBindings.switchMode) {
                switchToIde();
                return true;
            }
            if (keyCode == cfg.keyBindings.reload) {
                doReload();
                return true;
            }
            if (keyCode == cfg.keyBindings.togglePalette) {
                paletteVisible = !paletteVisible;
                return true;
            }
            if (keyCode == cfg.keyBindings.help) {
                openManual();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private boolean inCanvas(double x, double y) {
        int canvasX0 = paletteVisible ? PALETTE_W : 0;
        return x >= canvasX0 && x < this.width - FIELD_PANEL_W
                && y >= TOP_BAR_H && y < this.height - BOTTOM_BAR_H;
    }

    private String hitBlock(double mouseX, double mouseY) {
        double wx = toWorldX(mouseX);
        double wy = toWorldY(mouseY);
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

    /** 格式化校验错误列表（含 friendlyMessage 与修复建议）。 */
    private static String formatErrors(List<ValidationError> errors) {
        if (errors == null || errors.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ValidationError e : errors) {
            sb.append('[').append(e.blockId() == null ? "?" : e.blockId()).append(']');
            if (e.field() != null && !e.field().isBlank()) {
                sb.append(' ').append(e.field());
            }
            String msg = e.friendlyMessage();
            if (msg == null || msg.isBlank()) {
                msg = e.message();
            }
            sb.append(": ").append(msg);
            if (e.fixSuggestion() != null && !e.fixSuggestion().isBlank()) {
                sb.append("（建议: ").append(e.fixSuggestion()).append('）');
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }
}
