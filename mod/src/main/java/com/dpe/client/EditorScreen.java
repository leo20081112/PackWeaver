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
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Scratch 风格积木编辑器主屏幕：
 * 左侧可滚动调色板（分类折叠）/ 画布（拖拽+缩放）/ 字段编辑 / 编译预览 / 导出 / 重载。
 * 顶部按钮：编译 / 切到 IDE(M) / 重载(R) / 打开文件夹 / 新窗口 / 手册 / 设置 / 关闭。
 * 支持游戏内小窗化（{@link EditorWindow}）：非全屏时半透明遮罩 + 裁剪 + 标题栏拖拽/缩放，F11 切换全屏。
 */
public class EditorScreen extends Screen {

    public static class EditorAction {
        public enum ActionType {
            ADD_BLOCK,
            REMOVE_BLOCK,
            MOVE_BLOCK,
            CONNECT_BLOCK,
            DISCONNECT_BLOCK,
            UPDATE_FIELD,
            SET_CUSTOM_NAME,
            COLLAPSE_BLOCK
        }
        public ActionType type;
        public String blockId;
        public Object before;
        public Object after;
        public long timestamp;
    }

    private final ArrayDeque<EditorAction> undoStack = new ArrayDeque<>();
    private final ArrayDeque<EditorAction> redoStack = new ArrayDeque<>();

    private static final int PALETTE_W = 140;
    private static final int BLOCK_TREE_W = 180;
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
    private final Set<String> selectedIds = new HashSet<>();
    private final List<EditorBlock> clipboard = new ArrayList<>();
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
    /** 蛇形网格布局参数（Task 8）：列宽 180，行高 90，从 (40,40) 起。 */
    private static final double GRID_W = 180;
    private static final double GRID_H = 90;
    private static final double GRID_START_X = 40;
    private static final double GRID_START_Y = 40;
    /** 蛇形布局当前列（纵向延伸，列满换列）。 */
    private int gridCol = 0;
    private double gridNextY = GRID_START_Y;
    /** 重叠命中穿透：右键/Tab 在重叠积木间循环选中的偏移（Task 8）。 */
    private int hitOffset = 0;

    /** 积木所属文件分组（Task 7）：blockId -> 组键（事件根 schemaId 或 "ungrouped"）。 */
    private final Map<String, String> blockFile = new HashMap<>();
    /** 移动操作前的位置记录（blockId -> [x, y]）。 */
    private final Map<String, double[]> moveStartPositions = new HashMap<>();
    /** 折叠的分组（仅显示标题栏）。 */
    private final Set<String> collapsedFiles = new HashSet<>();
    /** 隐藏的分组（标题栏与积木均不绘制）。 */
    private final Set<String> hiddenFiles = new HashSet<>();
    /** 分组标题栏渲染缓存（render 构建，mouseClicked 命中复用）。 */
    private final List<GroupHeader> groupHeaders = new ArrayList<>();
    private static final int HEADER_H = 14;
    /** 分组标题栏：组键 + 屏幕矩形 + 关闭小按钮区。 */
    private record GroupHeader(String groupKey, int x, int y, int w, int h, int closeX) {
    }

    private final Map<String, TextFieldWidget> fieldTextFields = new HashMap<>();

    /** 调色板滚动偏移。 */
    private int paletteScroll = 0;
    /** 折叠的分类集合。 */
    private final Set<BlockCategory> collapsedCategories = new HashSet<>();
    /** 调色板是否可见（P 切换）。 */
    private boolean paletteVisible = true;

    /** 积木树侧边栏是否可见（B 切换）。 */
    private boolean blockTreeVisible = false;
    /** 积木树根节点列表（按文件/事件分组）。 */
    private final List<BlockTreeNode> blockTreeRoots = new ArrayList<>();
    /** 积木树节点缓存（渲染时填充，鼠标检测时复用）。 */
    private final List<TreeNodeRect> treeNodeRects = new ArrayList<>();
    /** 积木树滚动偏移。 */
    private int treeScroll = 0;
    /** 积木树搜索关键词。 */
    private String treeSearchText = "";
    /** 当前悬停的树节点。 */
    private BlockTreeNode hoveredTreeNode = null;
    /** 当前正在重命名的树节点。 */
    private BlockTreeNode renamingTreeNode = null;
    /** 重命名输入框。 */
    private TextFieldWidget renameTextField = null;
    /** 积木树搜索框。 */
    private TextFieldWidget treeSearchField = null;
    /** 双击检测：上次点击时间。 */
    private long lastTreeClickTime = 0;
    /** 双击检测：上次点击的节点。 */
    private String lastTreeClickNodeId = null;
    /** 双击归位：上次点击画布空白处的时间。 */
    private long lastCanvasClickTime = 0;
    /** 双击归位：上次点击画布空白处的坐标。 */
    private double lastCanvasClickX = 0, lastCanvasClickY = 0;
    /** 积木树节点：id / schemaId / 自定义名称 / 父节点 / 子节点列表 / 是否展开。 */
    private static class BlockTreeNode {
        String id;
        String schemaId;
        String customName;
        BlockTreeNode parent;
        List<BlockTreeNode> children = new ArrayList<>();
        boolean expanded = true;
        BlockTreeNode(String id, String schemaId, String customName) {
            this.id = id;
            this.schemaId = schemaId;
            this.customName = customName;
        }
    }
    /** 树节点屏幕矩形（用于鼠标命中检测）。 */
    private record TreeNodeRect(BlockTreeNode node, int x, int y, int w, int h, int indent, boolean isGroup) {
    }

    /** 调色板布局缓存（在 init/render 间复用）。 */
    private final List<PaletteRow> paletteRows = new ArrayList<>();
    private static final int PALETTE_ROW_H = 13;
    private static final int PALETTE_HEADER_H = 16;

    /** 新手引导。 */
    private OnboardingOverlay onboarding;
    /** 字体缩放倍率（来自 UserConfig.fontSize）。 */
    private float fontScale = 1.0f;

    /** 游戏内小窗状态。 */
    private EditorWindow window;
    /** 积木模式无法脱离游戏的提示文案（带过期）。 */
    private String detachedNotice = null;
    private long detachedNoticeTime = 0;

    public EditorScreen(EditorState state) {
        super(Text.literal("Datapack Editor"));
        this.state = state == null ? new EditorState() : state;
        this.reg = BlockSchemaRegistry.DEFAULT;
        this.canvas = new Canvas();
        // 从 state 恢复画布视图（Canvas 无 setter，初始 zoom=1.0/pan=0,0，用 zoomBy/panBy 等效还原）
        if (state != null) {
            double targetZoom = state.getZoom();
            if (targetZoom > 0 && Math.abs(targetZoom - 1.0) > 1e-9) {
                this.canvas.zoomBy(targetZoom);
            }
            this.canvas.panBy(state.getPanX(), state.getPanY());
        }
    }

    // ---------- 窗口几何辅助 ----------

    /** 窗口内容区左上角 X（屏幕绝对）。 */
    private int winX() {
        return window == null || window.fullscreen ? 0 : window.x;
    }

    /** 窗口内容区左上角 Y（屏幕绝对，含标题栏偏移）。 */
    private int winYContent() {
        return window == null || window.fullscreen ? 0 : window.y + EditorWindow.TITLE_H;
    }

    /** 窗口内容区宽度。 */
    private int winW() {
        return window == null || window.fullscreen ? this.width : window.width;
    }

    /** 窗口内容区高度（扣除标题栏）。 */
    private int winHContent() {
        int titleH = (window == null || window.fullscreen) ? 0 : EditorWindow.TITLE_H;
        return (window == null || window.fullscreen ? this.height : window.height) - titleH;
    }

    private boolean isFullscreen() {
        return window == null || window.fullscreen;
    }

    /**
     * 获取画布可见区域的世界坐标矩形
     * @return [minX, minY, maxX, maxY]
     */
    private double[] getVisibleWorldRect() {
        int canvasX0 = paletteVisible ? PALETTE_W : 0;
        int canvasW = winW() - FIELD_PANEL_W - canvasX0;
        int canvasH = winHContent() - BOTTOM_BAR_H - TOP_BAR_H;

        double z = canvas.getZoom();
        double panX = canvas.getPanX();
        double panY = canvas.getPanY();

        double minX = (canvasX0 - panX) / z;
        double minY = (TOP_BAR_H - panY) / z;
        double maxX = (canvasX0 + canvasW - panX) / z;
        double maxY = (TOP_BAR_H + canvasH - panY) / z;

        return new double[]{minX, minY, maxX, maxY};
    }

    /**
     * 确保积木在可见区域内，如果完全在可见区域外则重新定位到中心
     */
    private void ensureBlockInView(EditorBlock block) {
        double[] rect = getVisibleWorldRect();
        double minX = rect[0], minY = rect[1], maxX = rect[2], maxY = rect[3];

        double newX = block.x();
        double newY = block.y();
        int bh = blockHeight(reg.get(block.schemaId()));

        if (newX + BLOCK_W < minX || newX > maxX || newY + bh < minY || newY > maxY) {
            newX = (minX + maxX) / 2 - BLOCK_W / 2;
            newY = (minY + maxY) / 2 - bh / 2;
            state.moveBlock(block.id(), newX, newY);
        }
    }

    /**
     * 绘制边界指示器：当积木超出画布边界时显示指示器
     */
    private void drawBoundaryIndicators(DrawContext context) {
        int canvasX0 = paletteVisible ? PALETTE_W : 0;
        int canvasW = winW() - FIELD_PANEL_W - canvasX0;
        int canvasH = winHContent() - BOTTOM_BAR_H - TOP_BAR_H;

        double[] worldRect = getVisibleWorldRect();
        double minX = worldRect[0], minY = worldRect[1],
               maxX = worldRect[2], maxY = worldRect[3];

        int indicatorSize = 8;
        int arrowColor = 0xFFFFAA00;

        for (EditorBlock block : state.getBlocks()) {
            String gk = blockFile.getOrDefault(block.id(), "ungrouped");
            if (hiddenFiles.contains(gk) || collapsedFiles.contains(gk)) {
                continue;
            }

            BlockSchema schema = reg.get(block.schemaId());
            int bh = blockHeight(schema);

            boolean outLeft = block.x() < minX;
            boolean outRight = block.x() + BLOCK_W > maxX;
            boolean outTop = block.y() < minY;
            boolean outBottom = block.y() + bh > maxY;

            if (!outLeft && !outRight && !outTop && !outBottom) {
                continue;
            }

            double z = canvas.getZoom();
            double panX = canvas.getPanX();
            double panY = canvas.getPanY();

            if (outLeft) {
                int sx = canvasX0;
                int sy = (int) (TOP_BAR_H + block.y() * z + panY);
                context.fill(sx, sy - indicatorSize, sx + indicatorSize, sy + indicatorSize, arrowColor);
                context.drawTextWithShadow(this.textRenderer, Text.literal("◀"), sx + 1, sy - 4, 0xFF000000);
            }
            if (outRight) {
                int sx = canvasX0 + canvasW - indicatorSize;
                int sy = (int) (TOP_BAR_H + block.y() * z + panY);
                context.fill(sx, sy - indicatorSize, sx + indicatorSize, sy + indicatorSize, arrowColor);
                context.drawTextWithShadow(this.textRenderer, Text.literal("▶"), sx + 1, sy - 4, 0xFF000000);
            }
            if (outTop) {
                int sx = canvasX0 + (int) (block.x() * z + panX);
                int sy = TOP_BAR_H;
                context.fill(sx - indicatorSize, sy, sx + indicatorSize, sy + indicatorSize, arrowColor);
                context.drawTextWithShadow(this.textRenderer, Text.literal("▲"), sx - 3, sy + 1, 0xFF000000);
            }
            if (outBottom) {
                int sx = canvasX0 + (int) (block.x() * z + panX);
                int sy = TOP_BAR_H + canvasH - indicatorSize;
                context.fill(sx - indicatorSize, sy, sx + indicatorSize, sy + indicatorSize, arrowColor);
                context.drawTextWithShadow(this.textRenderer, Text.literal("▼"), sx - 3, sy + 1, 0xFF000000);
            }
        }
    }

    /**
     * 一键归位：调整canvas的zoom和pan使所有积木可见
     * 最小zoom为0.5，最大为2.0
     */
    private void fitAllBlocksInView() {
        if (state.getBlocks().isEmpty()) {
            return;
        }

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = Double.MIN_VALUE, maxY = Double.MIN_VALUE;

        for (EditorBlock b : state.getBlocks()) {
            BlockSchema bs = reg.get(b.schemaId());
            int bh = blockHeight(bs);
            minX = Math.min(minX, b.x());
            minY = Math.min(minY, b.y());
            maxX = Math.max(maxX, b.x() + BLOCK_W);
            maxY = Math.max(maxY, b.y() + bh);
        }

        int canvasX0 = paletteVisible ? PALETTE_W : 0;
        int canvasW = winW() - FIELD_PANEL_W - canvasX0;
        int canvasH = winHContent() - BOTTOM_BAR_H - TOP_BAR_H;

        double contentW = maxX - minX;
        double contentH = maxY - minY;
        double padding = 40;

        double zoomX = (canvasW - padding * 2) / contentW;
        double zoomY = (canvasH - padding * 2) / contentH;
        double newZoom = Math.min(Math.min(zoomX, zoomY), 2.0);
        newZoom = Math.max(newZoom, 0.5);

        double targetPanX = canvasX0 + (canvasW - contentW * newZoom) / 2 - minX * newZoom;
        double targetPanY = TOP_BAR_H + (canvasH - contentH * newZoom) / 2 - minY * newZoom;

        double currentZoom = canvas.getZoom();
        canvas.zoomBy(newZoom / currentZoom);
        canvas.panBy((int)(targetPanX - canvas.getPanX()), (int)(targetPanY - canvas.getPanY()));

        setStatus("已归位视图");
    }

    @Override
    protected void init() {
        fieldTextFields.clear();
        if (window == null) {
            window = EditorWindow.fromConfig(DatapackEditorClient.config(), this.width, this.height);
        }

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

        int ww = winW();
        int wh = winHContent();

        // 顶部按钮（相对窗口内容坐标）
        int topBtnX = PALETTE_W + 4;
        int bx = topBtnX;
        addDrawableChild(ButtonWidget.builder(Text.literal("编译"), b -> showCompilePreview())
                .dimensions(bx, 2, 44, 16).build());
        bx += 46;
        addDrawableChild(ButtonWidget.builder(Text.literal("切到IDE (M)"), b -> switchToIde())
                .dimensions(bx, 2, 84, 16).build());
        bx += 86;
        addDrawableChild(ButtonWidget.builder(Text.literal("重载 (R)"), b -> doReload())
                .dimensions(bx, 2, 60, 16).build());
        bx += 62;
        addDrawableChild(ButtonWidget.builder(Text.literal("导出Zip"), b -> exportZip())
                .dimensions(bx, 2, 56, 16).build());
        bx += 58;
        addDrawableChild(ButtonWidget.builder(Text.literal("保存应用"), b -> saveAndApply())
                .dimensions(bx, 2, 60, 16).build());
        bx += 62;
        addDrawableChild(ButtonWidget.builder(Text.literal("📂文件夹"), b -> openFolder())
                .dimensions(bx, 2, 64, 16).build());
        bx += 66;
        addDrawableChild(ButtonWidget.builder(Text.literal("🪟新窗口"), b -> openDetached())
                .dimensions(bx, 2, 64, 16).build());
        bx += 66;
        addDrawableChild(ButtonWidget.builder(Text.literal("手册"), b -> openManual())
                .dimensions(bx, 2, 40, 16).build());
        bx += 42;
        addDrawableChild(ButtonWidget.builder(Text.literal("设置"), b -> openSettings())
                .dimensions(bx, 2, 40, 16).build());
        bx += 42;
        addDrawableChild(ButtonWidget.builder(Text.literal("积木树 (B)"), b -> toggleBlockTree())
                .dimensions(bx, 2, 72, 16).build());
        bx += 74;
        addDrawableChild(ButtonWidget.builder(Text.literal("归位"), b -> fitAllBlocksInView())
                .dimensions(bx, 2, 40, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("关闭"), b -> close())
                .dimensions(ww - 50, 2, 46, 16).build());

        // 字段编辑区
        int fx = ww - FIELD_PANEL_W + 4;
        int fw = FIELD_PANEL_W - 8;
        int fy = TOP_BAR_H + 4;
        if (selectedId == null) {
            fy += 14;
        } else {
            EditorBlock selected = state.getById(selectedId);
            BlockSchema schema = selected == null ? null : reg.get(selected.schemaId());
            if (schema != null) {
                fy = drawFieldHeader(fx, fy, fw, schema, selected);
                if (schema.category() == BlockCategory.EVENT) {
                    fy = drawCustomNameWidget(fx, fy, fw, selected);
                }
                
                List<BlockField> commonFields = new ArrayList<>();
                List<BlockField> advancedFields = new ArrayList<>();
                for (BlockField field : schema.fields()) {
                    if (isCommonField(field.name()) || isAdvancedField(field)) {
                        commonFields.add(field);
                    } else {
                        advancedFields.add(field);
                    }
                }
                
                for (BlockField field : commonFields) {
                    fy = drawFieldWidget(fx, fy, fw, field, selected);
                }
                
                if (!advancedFields.isEmpty()) {
                    String expandText = advancedFieldsExpanded ? "收起高级选项 ▲" : "显示更多选项 ▼";
                    int finalFy = fy;
                    addDrawableChild(ButtonWidget.builder(Text.literal(expandText), b -> {
                        advancedFieldsExpanded = !advancedFieldsExpanded;
                        clearAndInit();
                    }).dimensions(fx, finalFy, fw, 14).build());
                    fy += 16;
                    
                    if (advancedFieldsExpanded) {
                        for (BlockField field : advancedFields) {
                            fy = drawFieldWidget(fx, fy, fw, field, selected);
                        }
                    }
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
                // NBT 复制按钮（Task 9）：坐标/物品/准星目标
                addDrawableChild(ButtonWidget.builder(Text.literal("复制坐标 (C)"), b -> copyCoordinatesAction())
                        .dimensions(fx, fy, fw, 14).build());
                fy += 15;
                addDrawableChild(ButtonWidget.builder(Text.literal("复制物品 (I)"), b -> copyHeldItemAction())
                        .dimensions(fx, fy, fw, 14).build());
                fy += 15;
                addDrawableChild(ButtonWidget.builder(Text.literal("复制目标 (T)"), b -> copyTargetNbtAction())
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
                    .dimensions(ww - 30, wh - 30, 20, 20).build());
        }

        // 全屏退出按钮（仅全屏模式显示）
        if (window != null && window.fullscreen) {
            addDrawableChild(ButtonWidget.builder(Text.literal("退出全屏"), b -> {
                        toggleFullscreen();
                        setStatus("已退出全屏模式");
                    })
                    .dimensions(4, 2, 60, 16).build());
        }

        // 积木树搜索框
        if (blockTreeVisible) {
            int treeX = paletteVisible ? PALETTE_W : 0;
            int treeY = TOP_BAR_H;
            int searchX = treeX + 4;
            int searchY = treeY + 2;
            treeSearchField = new TextFieldWidget(this.textRenderer, searchX, searchY, BLOCK_TREE_W - 8, 14, Text.literal("搜索"));
            treeSearchField.setMaxLength(64);
            treeSearchField.setText(treeSearchText);
            treeSearchField.setChangedListener(s -> {
                treeSearchText = s;
                treeScroll = 0;
            });
            addDrawableChild(treeSearchField);
        }

        rebuildPaletteRows();
    }

    /** 计算调色板行布局。 */
    private void rebuildPaletteRows() {
        paletteRows.clear();
        
        BlockCategory[] sortedCategories = getSortedCategories();
        
        for (BlockCategory cat : sortedCategories) {
            List<BlockSchema> schemas = reg.byCategory(cat);
            int count = schemas.size();
            paletteRows.add(new PaletteRow(cat, null, true, count));
            
            if (!collapsedCategories.contains(cat)) {
                if (isAdvancedCategory(cat) && !userHasUsedAdvanced()) {
                    continue;
                }
                for (BlockSchema s : schemas) {
                    paletteRows.add(new PaletteRow(cat, s, false, 0));
                }
            }
        }
    }
    
    /** 按使用频率排序分类：事件 > 动作 > 条件。 */
    private BlockCategory[] getSortedCategories() {
        return new BlockCategory[]{BlockCategory.EVENT, BlockCategory.ACTION, BlockCategory.CONDITION};
    }
    
    /** 判断是否为高级分类。 */
    private boolean isAdvancedCategory(BlockCategory cat) {
        return cat == BlockCategory.CONDITION || cat == BlockCategory.ACTION;
    }
    
    /** 检查用户是否使用过高级功能。 */
    private boolean userHasUsedAdvanced() {
        UserConfig cfg = DatapackEditorClient.config();
        if (cfg == null) {
            return false;
        }
        if (cfg.showAdvancedBlocks) {
            return true;
        }
        for (EditorBlock b : state.getBlocks()) {
            BlockSchema schema = reg.get(b.schemaId());
            if (schema != null && isAdvancedCategory(schema.category())) {
                return true;
            }
        }
        return false;
    }

    /** 调色板行：分类头或 schema 项。 */
    private record PaletteRow(BlockCategory category, BlockSchema schema, boolean header, int blockCount) {
    }
    
    private boolean advancedFieldsExpanded = false;
    
    private boolean isCommonField(String fieldName) {
        if (fieldName == null) return false;
        String lower = fieldName.toLowerCase();
        return lower.equals("target") || 
               lower.equals("command") || 
               lower.equals("message") ||
               lower.equals("text") ||
               lower.equals("function") ||
               lower.equals("item") ||
               lower.equals("entity") ||
               lower.equals("block") ||
               lower.equals("pos");
    }
    
    private boolean isAdvancedField(BlockField field) {
        if (field == null) return false;
        return field.name().toLowerCase().contains("nbt") ||
               field.name().toLowerCase().contains("score") ||
               field.name().toLowerCase().contains("tag") ||
               field.name().toLowerCase().contains("namespace") ||
               field.name().toLowerCase().contains("count") ||
               field.name().toLowerCase().contains("advanced");
    }
    
    private String getBlockChineseDescription(String schemaId) {
        if (schemaId == null) return null;
        return switch (schemaId) {
            case "event.tick" -> "每刻触发：游戏每个刻都会执行一次";
            case "event.load" -> "数据包加载：数据包加载时执行一次";
            case "event.player_join" -> "玩家加入：玩家进入服务器时执行";
            case "event.entity_death" -> "实体死亡：实体死亡时执行";
            case "condition.score_compare" -> "记分板比较：检查玩家的记分板数值";
            case "condition.entity_exists" -> "实体存在：检查指定类型实体是否存在";
            case "condition.random_chance" -> "随机概率：按指定概率随机通过";
            case "action.run_function" -> "运行函数：执行指定的数据包函数";
            case "action.say_text" -> "说话文本：向所有玩家发送聊天消息";
            case "action.set_block" -> "放置方块：在指定位置放置方块";
            case "action.give_item" -> "给予物品：给予玩家指定物品";
            case "action.summon" -> "召唤实体：在指定位置召唤实体";
            case "action.tag_add" -> "添加标签：为实体添加标签";
            case "action.tellraw" -> "原始消息：发送带样式的JSON消息";
            case "raw_text" -> "原始文本：直接输出命令或注释";
            default -> null;
        };
    }
    
    private String getFieldChineseDescription(String fieldName, String schemaId) {
        if (fieldName == null) return null;
        String lower = fieldName.toLowerCase();
        String desc = switch (lower) {
            case "target" -> "目标玩家/实体";
            case "command" -> "要执行的命令";
            case "message", "text" -> "要显示的文本内容";
            case "function" -> "数据包函数路径";
            case "item" -> "物品ID（如 minecraft:diamond_sword）";
            case "entity", "entity_type" -> "实体类型ID";
            case "block" -> "方块ID（如 minecraft:stone）";
            case "pos" -> "坐标位置（~ ~ ~）";
            case "objective" -> "记分板目标名称";
            case "op" -> "比较操作符（> < = ≥ ≤）";
            case "value", "count" -> "数值或数量";
            case "nbt" -> "NBT数据标签";
            case "score" -> "记分板分数";
            case "tag" -> "自定义标签";
            case "namespace" -> "命名空间";
            case "entry" -> "标签条目";
            default -> null;
        };
        if (desc != null && schemaId != null && schemaId.contains("tellraw")) {
            if (lower.equals("text")) {
                desc = "JSON格式的文本组件";
            }
        }
        return desc;
    }

    private int drawFieldHeader(int x, int y, int w, BlockSchema schema, EditorBlock block) {
        addDrawableChild(ButtonWidget.builder(Text.literal(schema.label()), b -> {})
                .dimensions(x, y, w, 14).build());
        return y + 16;
    }

    private int drawCustomNameWidget(int x, int y, int w, EditorBlock block) {
        String curStr = block.customName() != null ? block.customName() : "";
        TextFieldWidget tf = new TextFieldWidget(this.textRenderer, x, y, w, 14, Text.literal("函数名"));
        tf.setMaxLength(64);
        tf.setText(curStr);
        tf.setPlaceholder(Text.literal("函数名（可选）"));
        tf.setChangedListener(s -> {
            state.setCustomName(block.id(), s.isBlank() ? null : s.trim());
        });
        fieldTextFields.put("customName", tf);
        addDrawableChild(tf);
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
        int bh = blockHeight(schema);

        double[] pos;
        if (selectedId != null) {
            EditorBlock selected = state.getById(selectedId);
            if (selected != null) {
                double targetX = selected.x() + BLOCK_W + 10;
                double targetY = selected.y();
                if (!intersectsExisting(targetX, targetY, BLOCK_W, bh)) {
                    pos = new double[]{targetX, targetY};
                } else {
                    pos = findFreeGridCellInArea(schema, targetX - GRID_W * 2, targetY - GRID_H * 2, targetX + GRID_W * 2, targetY + GRID_H * 2);
                }
            } else {
                pos = findFreeGridCell(schema);
            }
        } else {
            double[] rect = getVisibleWorldRect();
            double centerX = (rect[0] + rect[2]) / 2 - BLOCK_W / 2;
            double centerY = (rect[1] + rect[3]) / 2 - bh / 2;
            if (!intersectsExisting(centerX, centerY, BLOCK_W, bh)) {
                pos = new double[]{centerX, centerY};
            } else {
                pos = findFreeGridCellInArea(schema, centerX - GRID_W * 2, centerY - GRID_H * 2, centerX + GRID_W * 2, centerY + GRID_H * 2);
            }
        }

        EditorBlock block = new EditorBlock(id, schemaId, pos[0], pos[1]);
        for (BlockField f : schema.fields()) {
            if (f.defaultValue() != null) {
                block.fieldValues().put(f.name(), f.defaultValue());
            }
        }
        state.addBlock(block);
        selectedId = id;
        linkMode = false;
        ensureBlockInView(block);

        EditorAction action = new EditorAction();
        action.type = EditorAction.ActionType.ADD_BLOCK;
        action.blockId = id;
        action.after = block;
        recordAction(action);

        setStatus("已添加: " + schema.label());
        clearAndInit();
    }

    /**
     * 在指定区域内查找空闲网格格
     * @return {x, y} 世界坐标
     */
    private double[] findFreeGridCellInArea(BlockSchema schema, double minX, double minY, double maxX, double maxY) {
        int bh = blockHeight(schema);
        int maxRows = 8;
        double startCol = (int) ((minX - GRID_START_X) / GRID_W);
        double startRow = (int) ((minY - GRID_START_Y) / GRID_H);
        if (startCol < 0) startCol = 0;
        if (startRow < 0) startRow = 0;
        int endCol = (int) ((maxX - GRID_START_X) / GRID_W) + 1;
        int endRow = (int) ((maxY - GRID_START_Y) / GRID_H) + 1;

        for (int row = (int) startRow; row < endRow + maxRows; row++) {
            int col = (int) startCol;
            while (col < endCol) {
                double x = GRID_START_X + col * GRID_W;
                double y = GRID_START_Y + row * GRID_H;
                if (x >= minX && x <= maxX && y >= minY && y <= maxY && !intersectsExisting(x, y, BLOCK_W, bh)) {
                    return new double[]{x, y};
                }
                col++;
            }
            if (row >= endRow) {
                break;
            }
        }
        return findFreeGridCell(schema);
    }

    /**
     * 蛇形网格查找空闲格子（Task 8）：从 (GRID_START_X, GRID_START_Y) 起纵向递增 y，
     * 每列满（y 超过 8 格）换下一列；跳过与已有积木矩形相交的格子。
     * @return {x, y} 世界坐标
     */
    private double[] findFreeGridCell(BlockSchema schema) {
        int bh = blockHeight(schema);
        int maxRows = 8;
        for (int attempts = 0; attempts < 256; attempts++) {
            double x = GRID_START_X + gridCol * GRID_W;
            double y = gridNextY;
            if (!intersectsExisting(x, y, BLOCK_W, bh)) {
                // 占用此格，推进 y
                gridNextY += GRID_H;
                if ((gridNextY - GRID_START_Y) / GRID_H >= maxRows) {
                    gridCol++;
                    gridNextY = GRID_START_Y;
                }
                return new double[]{x, y};
            }
            gridNextY += GRID_H;
            if ((gridNextY - GRID_START_Y) / GRID_H >= maxRows) {
                gridCol++;
                gridNextY = GRID_START_Y;
            }
        }
        // 回退：返回当前指针位置
        return new double[]{GRID_START_X + gridCol * GRID_W, gridNextY};
    }

    /** 判断世界坐标矩形是否与已有积木相交（排除拖拽中的积木）。 */
    private boolean intersectsExisting(double x, double y, int w, int h) {
        return intersectsExisting(x, y, w, h, null);
    }

    private boolean intersectsExisting(double x, double y, int w, int h, String excludeId) {
        for (EditorBlock b : state.getBlocks()) {
            if (excludeId != null && excludeId.equals(b.id())) {
                continue;
            }
            BlockSchema bs = reg.get(b.schemaId());
            int bh = blockHeight(bs);
            if (x < b.x() + BLOCK_W && x + w > b.x()
                    && y < b.y() + bh && y + h > b.y()) {
                return true;
            }
        }
        return false;
    }

    /** 拖拽落点若与其它块相交，snap 到最近空闲网格位（Task 8）。 */
    private double[] snapToFreeGrid(EditorBlock block) {
        BlockSchema schema = reg.get(block.schemaId());
        int bh = blockHeight(schema);
        // 在 block 当前位置附近搜索最近的空闲网格格
        double baseCol = Math.round((block.x() - GRID_START_X) / GRID_W);
        double baseRow = Math.round((block.y() - GRID_START_Y) / GRID_H);
        if (baseCol < 0) baseCol = 0;
        if (baseRow < 0) baseRow = 0;
        for (int radius = 0; radius < 32; radius++) {
            for (int dr = -radius; dr <= radius; dr++) {
                for (int dc = -radius; dc <= radius; dc++) {
                    if (Math.abs(dr) != radius && Math.abs(dc) != radius) {
                        continue; // 只检查外圈
                    }
                    int col = (int) (baseCol + dc);
                    int row = (int) (baseRow + dr);
                    if (col < 0 || row < 0) {
                        continue;
                    }
                    double x = GRID_START_X + col * GRID_W;
                    double y = GRID_START_Y + row * GRID_H;
                    if (!intersectsExisting(x, y, BLOCK_W, bh, block.id())) {
                        return new double[]{x, y};
                    }
                }
            }
        }
        return new double[]{block.x(), block.y()};
    }

    // ---------- 分组（Task 7）----------

    /**
     * 重建积木-分组关联：按事件根 schemaId 分组（向上查找父链到事件类根块），
     * 无事件根的归入 "ungrouped"。结果写入 {@link #blockFile}。
     */
    private void rebuildGroups() {
        blockFile.clear();
        // child -> parent 映射
        Map<String, String> parentOf = new HashMap<>();
        for (EditorBlock b : state.getBlocks()) {
            for (String childId : b.childIds()) {
                parentOf.put(childId, b.id());
            }
        }
        for (EditorBlock b : state.getBlocks()) {
            String cur = b.id();
            String rootId = cur;
            Set<String> visited = new HashSet<>();
            while (parentOf.containsKey(cur) && visited.add(cur)) {
                cur = parentOf.get(cur);
                rootId = cur;
            }
            EditorBlock root = state.getById(rootId);
            if (root != null) {
                BlockSchema rootSchema = reg.get(root.schemaId());
                if (rootSchema != null && rootSchema.category() == BlockCategory.EVENT) {
                    blockFile.put(b.id(), root.schemaId());
                    continue;
                }
            }
            blockFile.put(b.id(), "ungrouped");
        }
    }

    /** 分组显示名：事件根 schema 的 label，否则 "未分组"。 */
    private String groupLabel(String groupKey) {
        if (groupKey == null || "ungrouped".equals(groupKey)) {
            return "未分组";
        }
        BlockSchema schema = reg.get(groupKey);
        return schema != null ? schema.label() : groupKey;
    }

    /**
     * 重建积木树：按事件根分组构建树形结构。
     * 根节点为事件块，其子节点为该事件下的所有积木。
     */
    private void rebuildBlockTree() {
        blockTreeRoots.clear();
        if (state.getBlocks().isEmpty()) {
            return;
        }
        Map<String, String> parentOf = new HashMap<>();
        for (EditorBlock b : state.getBlocks()) {
            for (String childId : b.childIds()) {
                parentOf.put(childId, b.id());
            }
        }
        Map<String, BlockTreeNode> nodeMap = new HashMap<>();
        for (EditorBlock b : state.getBlocks()) {
            String customName = b.customName();
            nodeMap.put(b.id(), new BlockTreeNode(b.id(), b.schemaId(), customName));
        }
        for (EditorBlock b : state.getBlocks()) {
            BlockTreeNode node = nodeMap.get(b.id());
            if (node == null) continue;
            String cur = b.id();
            String rootId = cur;
            Set<String> visited = new HashSet<>();
            while (parentOf.containsKey(cur) && visited.add(cur)) {
                cur = parentOf.get(cur);
                rootId = cur;
            }
            EditorBlock root = state.getById(rootId);
            if (root != null) {
                BlockSchema rootSchema = reg.get(root.schemaId());
                if (rootSchema != null && rootSchema.category() == BlockCategory.EVENT) {
                    if (!b.id().equals(rootId)) {
                        BlockTreeNode parentNode = nodeMap.get(rootId);
                        if (parentNode != null) {
                            node.parent = parentNode;
                            parentNode.children.add(node);
                        }
                    }
                }
            }
        }
        Set<String> rootIds = new HashSet<>();
        for (EditorBlock b : state.getBlocks()) {
            String cur = b.id();
            String rootId = cur;
            Set<String> visited = new HashSet<>();
            while (parentOf.containsKey(cur) && visited.add(cur)) {
                cur = parentOf.get(cur);
                rootId = cur;
            }
            EditorBlock root = state.getById(rootId);
            if (root != null) {
                BlockSchema rootSchema = reg.get(root.schemaId());
                if (rootSchema != null && rootSchema.category() == BlockCategory.EVENT) {
                    rootIds.add(rootId);
                } else if (!rootIds.contains(b.id())) {
                    rootIds.add(b.id());
                }
            } else if (!rootIds.contains(b.id())) {
                rootIds.add(b.id());
            }
        }
        for (String rid : rootIds) {
            BlockTreeNode node = nodeMap.get(rid);
            if (node != null) {
                blockTreeRoots.add(node);
            }
        }
    }

    /** 根据搜索关键词过滤树节点。 */
    private boolean matchesSearch(BlockTreeNode node) {
        if (treeSearchText == null || treeSearchText.isEmpty()) {
            return true;
        }
        BlockSchema schema = reg.get(node.schemaId);
        String label = schema == null ? node.schemaId : schema.label();
        String name = node.customName != null && !node.customName.isEmpty() ? node.customName : label;
        String searchText = treeSearchText;
        // 检查是否是正则表达式（包含特殊正则字符）
        boolean isRegex = searchText.matches(".*[\\\\\\.\\+\\*\\?\\[\\]\\{\\}\\(\\)\\^\\$\\|].*");
        if (isRegex) {
            try {
                Pattern pattern = Pattern.compile(searchText, Pattern.CASE_INSENSITIVE);
                return pattern.matcher(name).find() || pattern.matcher(node.schemaId).find();
            } catch (PatternSyntaxException e) {
                // 无效正则，回退到普通搜索
                String lower = searchText.toLowerCase();
                return name.toLowerCase().contains(lower) || node.schemaId.toLowerCase().contains(lower);
            }
        } else {
            String lower = searchText.toLowerCase();
            return name.toLowerCase().contains(lower) || node.schemaId.toLowerCase().contains(lower);
        }
    }

    /** 统计可见节点数量（包括符合条件的子节点）。 */
    private int countVisibleNodes(BlockTreeNode node, int depth) {
        if (!matchesSearch(node)) {
            if (node.children.isEmpty()) {
                return 0;
            }
            int count = 0;
            for (BlockTreeNode child : node.children) {
                count += countVisibleNodes(child, depth + 1);
            }
            return count;
        }
        int count = 1;
        if (node.expanded && !node.children.isEmpty()) {
            for (BlockTreeNode child : node.children) {
                count += countVisibleNodes(child, depth + 1);
            }
        }
        return count;
    }

    /** 绘制每个分组的标题栏（折叠/隐藏按钮），并填充 {@link #groupHeaders} 供命中检测。 */
    private void drawGroupHeaders(DrawContext context) {
        groupHeaders.clear();
        if (state.getBlocks().isEmpty()) {
            return;
        }
        Map<String, List<EditorBlock>> groups = new LinkedHashMap<>();
        for (EditorBlock b : state.getBlocks()) {
            String gk = blockFile.getOrDefault(b.id(), "ungrouped");
            groups.computeIfAbsent(gk, k -> new ArrayList<>()).add(b);
        }
        int headerW = 130;
        for (Map.Entry<String, List<EditorBlock>> e : groups.entrySet()) {
            String gk = e.getKey();
            if (hiddenFiles.contains(gk)) {
                continue;
            }
            List<EditorBlock> blocks = e.getValue();
            double minX = Double.MAX_VALUE;
            double minY = Double.MAX_VALUE;
            for (EditorBlock b : blocks) {
                if (b.x() < minX) {
                    minX = b.x();
                }
                if (b.y() < minY) {
                    minY = b.y();
                }
            }
            int sx = toScreenX(minX);
            int sy = toScreenY(minY) - HEADER_H - 2;
            context.fill(sx, sy, sx + headerW, sy + HEADER_H, 0xFF3A3A5A);
            context.drawBorder(sx, sy, headerW, HEADER_H, 0xFF7777BB);
            String mark = collapsedFiles.contains(gk) ? "[+] " : "[-] ";
            String label = truncate(mark + groupLabel(gk), headerW - 22);
            context.drawTextWithShadow(this.textRenderer, Text.literal(label), sx + 3, sy + 2, 0xFFCCCCFF);
            int closeX = sx + headerW - 14;
            context.drawTextWithShadow(this.textRenderer, Text.literal("×"), closeX + 2, sy + 1, 0xFFFFAAAA);
            groupHeaders.add(new GroupHeader(gk, sx, sy, headerW, HEADER_H, closeX));
        }
    }

    /** 平移画布使指定分组的首块居中。 */
    private void centerOnGroup(String groupKey) {
        if (groupKey == null) {
            return;
        }
        EditorBlock first = null;
        for (EditorBlock b : state.getBlocks()) {
            if (groupKey.equals(blockFile.getOrDefault(b.id(), "ungrouped"))) {
                first = b;
                break;
            }
        }
        if (first == null) {
            return;
        }
        int canvasX0 = paletteVisible ? PALETTE_W : 0;
        int canvasW = winW() - FIELD_PANEL_W - canvasX0;
        int canvasH = winHContent() - BOTTOM_BAR_H - TOP_BAR_H;
        double z = canvas.getZoom();
        double targetPanX = (canvasX0 + canvasW / 2.0) - (first.x() + BLOCK_W / 2.0) * z;
        double targetPanY = (TOP_BAR_H + canvasH / 2.0) - first.y() * z;
        canvas.panBy(targetPanX - canvas.getPanX(), targetPanY - canvas.getPanY());
        setStatus("已定位到分组: " + groupLabel(groupKey));
    }

    private void deleteSelected() {
        if (selectedId == null) {
            return;
        }

        EditorAction action = new EditorAction();
        action.type = EditorAction.ActionType.REMOVE_BLOCK;
        action.blockId = selectedId;
        action.before = state.getById(selectedId);
        recordAction(action);

        state.removeBlock(selectedId);
        selectedId = null;
        linkMode = false;
        setStatus("积木已删除");
        clearAndInit();
    }

    private void deleteSelectedBlocks() {
        if (selectedIds.isEmpty() && selectedId == null) {
            setStatus("未选中任何积木");
            return;
        }
        int count = selectedIds.size();
        if (selectedId != null && !selectedIds.contains(selectedId)) {
            count++;
        }

        for (String id : new ArrayList<>(selectedIds)) {
            EditorAction action = new EditorAction();
            action.type = EditorAction.ActionType.REMOVE_BLOCK;
            action.blockId = id;
            action.before = state.getById(id);
            recordAction(action);
            state.removeBlock(id);
        }
        if (selectedId != null && selectedIds.contains(selectedId)) {
            EditorAction action = new EditorAction();
            action.type = EditorAction.ActionType.REMOVE_BLOCK;
            action.blockId = selectedId;
            action.before = state.getById(selectedId);
            recordAction(action);
            state.removeBlock(selectedId);
        }
        selectedIds.clear();
        selectedId = null;
        linkMode = false;
        setStatus("已删除 " + count + " 个积木");
        clearAndInit();
    }

    private void copySelectedBlocks() {
        if (selectedIds.isEmpty() && selectedId == null) {
            setStatus("未选中任何积木");
            return;
        }
        clipboard.clear();
        Set<String> toCopy = new HashSet<>(selectedIds);
        if (selectedId != null && !toCopy.contains(selectedId)) {
            toCopy.add(selectedId);
        }
        for (String id : toCopy) {
            EditorBlock block = state.getById(id);
            if (block != null) {
                clipboard.add(block.copy());
            }
        }
        setStatus("已复制 " + clipboard.size() + " 个积木");
    }

    private void pasteBlocks() {
        if (clipboard.isEmpty()) {
            setStatus("剪贴板为空");
            return;
        }
        double offsetX = 20;
        double offsetY = 20;
        for (EditorBlock block : clipboard) {
            String newId = "b" + (++blockCounter);
            EditorBlock newBlock = new EditorBlock(newId, block.schemaId(), 
                block.x() + offsetX, block.y() + offsetY, 
                new HashMap<>(block.fieldValues()), new ArrayList<>(block.childIds()),
                block.customName(), block.collapsed());
            state.addBlock(newBlock);
            offsetX += 20;
            offsetY += 20;
        }
        setStatus("已粘贴 " + clipboard.size() + " 个积木");
        clearAndInit();
    }

    private void selectAllBlocks() {
        selectedIds.clear();
        for (EditorBlock block : state.getBlocks()) {
            selectedIds.add(block.id());
        }
        selectedId = selectedIds.isEmpty() ? null : selectedIds.iterator().next();
        setStatus("已全选 " + selectedIds.size() + " 个积木");
        clearAndInit();
    }

    private void toggleBlockCollapsed(String blockId) {
        EditorBlock block = state.getById(blockId);
        if (block != null) {
            boolean newCollapsed = !block.collapsed();
            state.setCollapsed(blockId, newCollapsed);
            setStatus(newCollapsed ? "已折叠积木" : "已展开积木");
            clearAndInit();
        }
    }

    /** 复制玩家/准星坐标到选中积木的 pos 字段（Task 9）。 */
    private void copyCoordinatesAction() {
        String msg = NbtCopyService.copyCoordinatesInto(state, selectedId, reg, MinecraftClient.getInstance());
        setStatus(msg);
        clearAndInit();
    }

    /** 复制主手物品 id 到选中积木（Task 9）；物品 NBT 写入剪贴板。 */
    private void copyHeldItemAction() {
        String msg = NbtCopyService.copyHeldItemInto(state, selectedId, reg, MinecraftClient.getInstance());
        setStatus(msg);
        clearAndInit();
    }

    /** 复制准星方块/实体到选中积木（Task 9）；目标 NBT 写入剪贴板。 */
    private void copyTargetNbtAction() {
        String msg = NbtCopyService.copyTargetNbtInto(state, selectedId, reg, MinecraftClient.getInstance());
        setStatus(msg);
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

    /** 切到 IDE 模式：先持久化画布视图与窗口几何到 state/config，再切换。 */
    private void switchToIde() {
        // 画布视图写入 state（zoom/pan），避免跳位
        syncViewToState();
        // 窗口几何写回 config 并保存
        if (window != null) {
            window.applyToConfig(DatapackEditorClient.config());
            DatapackEditorClient.saveConfig();
        }
        // 单机：确保真实数据包骨架存在，IDE 直接编辑真实文件树（Task 10）
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && DatapackEditorClient.worldDatapacksDir(mc) != null) {
            Path skeleton = DatapackEditorClient.generateSkeleton(state.getActiveDatapackNamespace(), mc);
            if (skeleton != null) {
                setStatus("已生成/确认数据包骨架: " + skeleton.getFileName());
            }
        }
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

    /** 打开当前命名空间的数据包文件夹（Task 3）。 */
    private void openFolder() {
        MinecraftClient mc = MinecraftClient.getInstance();
        String ns = state.getActiveDatapackNamespace();
        if (ns == null || ns.isBlank()) {
            ns = "dpe";
        }
        Path dpDir = DatapackEditorClient.worldDatapacksDir(mc);
        if (dpDir == null) {
            setStatus("非单机世界，无法定位数据包目录");
            return;
        }
        try {
            Path folder = dpDir.resolve(ns);
            Files.createDirectories(folder);
            boolean ok = DatapackFolderOpener.open(folder);
            setStatus(ok ? "已打开: " + folder : "打开失败: " + folder);
        } catch (Exception e) {
            setStatus("打开失败: " + e.getMessage());
        }
    }

    /** 积木模式无法脱离游戏渲染，仅提示（Task 5）。 */
    private void openDetached() {
        detachedNotice = "积木模式依赖游戏渲染，无法脱离游戏窗口；请切换到 IDE 文本模式（M）后使用独立窗口";
        detachedNoticeTime = System.currentTimeMillis();
        setStatus("积木模式不支持独立窗口，请切到 IDE");
    }

    private void toggleFullscreen() {
        if (window == null) {
            return;
        }
        boolean wasFullscreen = window.fullscreen;
        window.toggleFullscreen(this.width, this.height);
        DatapackEditorClient.saveConfig();
        for (EditorBlock block : state.getBlocks()) {
            ensureBlockInView(block);
        }
        // 全屏进入时显示提示
        if (!wasFullscreen && window.fullscreen) {
            fullscreenHintTime = System.currentTimeMillis();
        }
        clearAndInit();
    }

    /** 全屏提示显示时间戳。 */
    private long fullscreenHintTime = 0;
    private static final long FULLSCREEN_HINT_DURATION = 3000; // 3秒

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
        MinecraftClient mc = MinecraftClient.getInstance();
        Path worldDatapacks = DatapackEditorClient.worldDatapacksDir(mc);
        try {
            if (worldDatapacks != null) {
                // 单机：直接落盘到世界 datapacks 目录（解压目录）
                Path target = OfflineDatapackIo.exportToDatapacksDir(state, reg, mc);
                setStatus("已导出到 datapacks: " + target.getFileName());
            } else {
                // 非单机回退：写游戏目录 zip
                Path target = OfflineDatapackIo.export(state, reg);
                setStatus("已导出(游戏目录): " + target.getFileName());
            }
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
        // 统一通过 ReloadService 路由：单机一步落盘 + 数据包重载；远程发服务端
        ReloadResult r = ReloadService.reload(state, MinecraftClient.getInstance());
        setStatus((r.success() ? "成功: " : "失败: ") + r.message());
        if (!r.success()) {
            compilePreview = "§c保存失败§r\n" + r.message();
            clearAndInit();
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

    private void recordAction(EditorAction action) {
        action.timestamp = System.currentTimeMillis();
        undoStack.push(action);
        redoStack.clear();
    }

    private void undo() {
        if (undoStack.isEmpty()) {
            setStatus("没有可撤销的操作");
            return;
        }
        EditorAction action = undoStack.pop();
        redoStack.push(action);

        switch (action.type) {
            case ADD_BLOCK:
                state.removeBlock(action.blockId);
                if (selectedId != null && selectedId.equals(action.blockId)) {
                    selectedId = null;
                }
                selectedIds.remove(action.blockId);
                setStatus("已撤销：添加积木");
                break;
            case REMOVE_BLOCK:
                state.addBlock((EditorBlock) action.before);
                setStatus("已撤销：删除积木");
                break;
            case MOVE_BLOCK:
                EditorBlock block = state.getById(action.blockId);
                if (block != null) {
                    double oldX = (double) ((Object[]) action.before)[0];
                    double oldY = (double) ((Object[]) action.before)[1];
                    state.moveBlock(action.blockId, oldX, oldY);
                }
                setStatus("已撤销：移动积木");
                break;
            case CONNECT_BLOCK:
                String parentId = (String) ((Object[]) action.before)[0];
                String childId = (String) ((Object[]) action.before)[1];
                state.disconnect(parentId, childId);
                setStatus("已撤销：连接积木");
                break;
            case DISCONNECT_BLOCK:
                String pId = (String) ((Object[]) action.after)[0];
                String cId = (String) ((Object[]) action.after)[1];
                state.connect(pId, cId);
                setStatus("已撤销：断开连接");
                break;
            case UPDATE_FIELD:
                EditorBlock fb = state.getById(action.blockId);
                if (fb != null) {
                    String fieldName = (String) ((Object[]) action.before)[0];
                    Object oldValue = ((Object[]) action.before)[1];
                    fb.fieldValues().put(fieldName, oldValue);
                }
                setStatus("已撤销：修改字段");
                break;
            case SET_CUSTOM_NAME:
                EditorBlock cb = state.getById(action.blockId);
                if (cb != null) {
                    String oldName = (String) action.before;
                    state.setCustomName(action.blockId, oldName);
                }
                setStatus("已撤销：设置名称");
                break;
            case COLLAPSE_BLOCK:
                EditorBlock clb = state.getById(action.blockId);
                if (clb != null) {
                    boolean wasCollapsed = (Boolean) action.before;
                    state.setCollapsed(action.blockId, wasCollapsed);
                }
                setStatus("已撤销：折叠状态");
                break;
        }
        clearAndInit();
    }

    private void redo() {
        if (redoStack.isEmpty()) {
            setStatus("没有可重做的操作");
            return;
        }
        EditorAction action = redoStack.pop();
        undoStack.push(action);

        switch (action.type) {
            case ADD_BLOCK:
                state.addBlock((EditorBlock) action.after);
                setStatus("已重做：添加积木");
                break;
            case REMOVE_BLOCK:
                state.removeBlock(action.blockId);
                if (selectedId != null && selectedId.equals(action.blockId)) {
                    selectedId = null;
                }
                selectedIds.remove(action.blockId);
                setStatus("已重做：删除积木");
                break;
            case MOVE_BLOCK:
                double newX = (double) ((Object[]) action.after)[0];
                double newY = (double) ((Object[]) action.after)[1];
                state.moveBlock(action.blockId, newX, newY);
                setStatus("已重做：移动积木");
                break;
            case CONNECT_BLOCK:
                String parentId = (String) ((Object[]) action.after)[0];
                String childId = (String) ((Object[]) action.after)[1];
                state.connect(parentId, childId);
                setStatus("已重做：连接积木");
                break;
            case DISCONNECT_BLOCK:
                String pId = (String) ((Object[]) action.before)[0];
                String cId = (String) ((Object[]) action.before)[1];
                state.disconnect(pId, cId);
                setStatus("已重做：断开连接");
                break;
            case UPDATE_FIELD:
                EditorBlock fb = state.getById(action.blockId);
                if (fb != null) {
                    String fieldName = (String) ((Object[]) action.after)[0];
                    Object newValue = ((Object[]) action.after)[1];
                    fb.fieldValues().put(fieldName, newValue);
                }
                setStatus("已重做：修改字段");
                break;
            case SET_CUSTOM_NAME:
                EditorBlock cb = state.getById(action.blockId);
                if (cb != null) {
                    String newName = (String) action.after;
                    state.setCustomName(action.blockId, newName);
                }
                setStatus("已重做：设置名称");
                break;
            case COLLAPSE_BLOCK:
                EditorBlock clb = state.getById(action.blockId);
                if (clb != null) {
                    boolean isCollapsed = (Boolean) action.after;
                    state.setCollapsed(action.blockId, isCollapsed);
                }
                setStatus("已重做：折叠状态");
                break;
        }
        clearAndInit();
    }

    private void toggleBlockTree() {
        blockTreeVisible = !blockTreeVisible;
        setStatus(blockTreeVisible ? "已显示积木树" : "已隐藏积木树");
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

    /** 禁用默认半透明背景叠加（Task 1）：super.render 会调用此方法，置空避免覆盖积木。 */
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // 不绘制默认背景（屏幕已有自己的不透明背景填充）
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int ww = winW();
        int wh = winHContent();
        int wx = winX();
        int wyc = winYContent();
        int lmx = mouseX - wx;
        int lmy = mouseY - wyc;

        if (!isFullscreen()) {
            // 全屏半透明遮罩（小窗外背景）
            context.fill(0, 0, this.width, this.height, 0x80000000);
            context.enableScissor(wx, wyc, wx + ww, wyc + wh);
        }

        context.getMatrices().push();
        context.getMatrices().translate(wx, wyc, 0);

        // 背景
        context.fill(0, 0, ww, wh, 0xFF1A1A1A);
        // 调色板背景
        if (paletteVisible) {
            context.fill(0, TOP_BAR_H, PALETTE_W, wh - BOTTOM_BAR_H, 0xFF252526);
        }
        // 字段面板背景
        context.fill(ww - FIELD_PANEL_W, TOP_BAR_H, ww, wh - BOTTOM_BAR_H, 0xFF252526);
        // 画布背景
        int canvasX0 = paletteVisible ? PALETTE_W : 0;
        context.fill(canvasX0, TOP_BAR_H, ww - FIELD_PANEL_W, wh - BOTTOM_BAR_H, 0xFF1E1E1E);

        // 标题
        context.drawTextWithShadow(this.textRenderer, this.title, 4, 4, 0xFFFFFF);

        // 调色板（自绘）
        if (paletteVisible) {
            drawPalette(context, lmx, lmy);
        }
        // 积木树侧边栏（自绘）
        if (blockTreeVisible) {
            drawBlockTree(context, lmx, lmy);
        }

        // 画布网格
        drawCanvasGrid(context);
        // 连线
        drawConnections(context);
        // 边界指示器
        drawBoundaryIndicators(context);
        // 重建分组关联（Task 7）
        // 积木块（跳过隐藏/折叠分组的块，但折叠时仍画标题栏）
        for (EditorBlock block : state.getBlocks()) {
            String gk = blockFile.getOrDefault(block.id(), "ungrouped");
            if (hiddenFiles.contains(gk) || collapsedFiles.contains(gk)) {
                continue;
            }
            drawBlock(context, block);
        }
        // 分组标题栏（在积木之上，可点击：左键居中/右键折叠/×隐藏）
        drawGroupHeaders(context);

        // 字段面板
        drawFieldPanel(context);

        // 子组件（按钮/文本框，已平移到窗口内）
        super.render(context, lmx, lmy, delta);

        // 编译预览
        if (compilePreview != null) {
            drawCompilePreview(context);
        }

        // 积木 tooltip（悬停）
        drawBlockTooltip(context, lmx, lmy);

        // 状态消息
        drawStatus(context);

        // 全屏模式提示（3秒后自动消失）
        if (window != null && window.fullscreen) {
            drawFullscreenHint(context);
        }

        // 画布边界
        context.drawBorder(canvasX0 - 1, TOP_BAR_H - 1, ww - FIELD_PANEL_W - canvasX0 + 2,
                wh - BOTTOM_BAR_H - TOP_BAR_H + 2, 0xFF444444);

        // 新手引导覆盖
        if (onboarding != null) {
            onboarding.render(context, lmx, lmy, ww, wh);
        }

        // 积木模式无法脱离游戏的提示
        if (detachedNotice != null) {
            drawDetachedNotice(context, ww, wh);
        }

        context.getMatrices().pop();

        if (!isFullscreen()) {
            context.disableScissor();
            drawWindowTitleBar(context, mouseX, mouseY);
        }
    }

    /** 绘制小窗标题栏：背景 + 标题 + 全屏按钮 + 关闭按钮（屏幕绝对坐标）。 */
    private void drawWindowTitleBar(DrawContext context, int mouseX, int mouseY) {
        int x = window.x;
        int y = window.y;
        int w = window.width;
        int h = window.height;
        // 标题栏
        context.fill(x, y, x + w, y + EditorWindow.TITLE_H, 0xFF2D2D2D);
        context.drawTextWithShadow(this.textRenderer, this.title, x + 6, y + 4, 0xFFFFFF);
        // 全屏按钮 / 关闭按钮
        int fsX = x + w - 28;
        int clX = x + w - 14;
        boolean fsHover = mouseX >= fsX && mouseX < fsX + 13 && mouseY >= y + 1 && mouseY < y + 15;
        boolean clHover = mouseX >= clX && mouseX < clX + 12 && mouseY >= y + 1 && mouseY < y + 15;
        context.fill(fsX, y + 1, fsX + 13, y + 15, fsHover ? 0xFF555555 : 0xFF3A3A3A);
        context.drawTextWithShadow(this.textRenderer, Text.literal("▢"), fsX + 3, y + 3, 0xFFCCCCCC);
        context.fill(clX, y + 1, clX + 12, y + 15, clHover ? 0xFF884444 : 0xFF3A3A3A);
        context.drawTextWithShadow(this.textRenderer, Text.literal("x"), clX + 3, y + 3, 0xFFCCCCCC);
        // 窗口边框
        context.drawBorder(x, y, w, h, 0xFF555555);
    }

    /** 积木模式脱离提示（屏幕中央）。 */
    private void drawDetachedNotice(DrawContext context, int ww, int wh) {
        long age = System.currentTimeMillis() - detachedNoticeTime;
        if (age > 5000) {
            detachedNotice = null;
            return;
        }
        int w = Math.min(ww - 20, 360);
        int lines = 2;
        int h = 36;
        int bx = (ww - w) / 2;
        int by = (wh - h) / 2;
        context.fill(bx, by, bx + w, by + h, 0xE8000000);
        context.drawBorder(bx, by, w, h, 0xFFFFAA00);
        context.drawTextWithShadow(this.textRenderer,
                Text.literal("无法脱离游戏窗口").formatted(Formatting.RED), bx + 6, by + 4, 0xFFFFAA00);
        // 第二行截断
        String msg = truncate(detachedNotice, w - 12);
        context.drawTextWithShadow(this.textRenderer, Text.literal(msg), bx + 6, by + 18, 0xFFEEEEEE);
    }

    /** 渲染调色板（自绘可滚动+折叠）。 */
    private void drawPalette(DrawContext context, int mouseX, int mouseY) {
        int top = TOP_BAR_H + 2;
        int bottom = winHContent() - BOTTOM_BAR_H;
        int y = top - paletteScroll;
        for (PaletteRow row : paletteRows) {
            int h = row.header ? PALETTE_HEADER_H : PALETTE_ROW_H;
            int ry = y;
            if (ry + h >= top && ry <= bottom) {
                if (row.header) {
                    boolean collapsed = collapsedCategories.contains(row.category);
                    String prefix = collapsed ? "[+] " : "[-] ";
                    String countStr = row.blockCount() > 0 ? " (" + row.blockCount() + ")" : "";
                    String label = prefix + categoryLabel(row.category) + countStr;
                    boolean isAdvanced = isAdvancedCategory(row.category());
                    int textColor = isAdvanced && !userHasUsedAdvanced() ? 0xFF888888 : 0xFFCCCCCC;
                    context.fill(2, ry, PALETTE_W - 2, ry + h, 0xFF333333);
                    context.drawTextWithShadow(this.textRenderer, Text.literal(label),
                            4, ry + 3, textColor);
                    if (isAdvanced && !userHasUsedAdvanced()) {
                        context.drawTextWithShadow(this.textRenderer, Text.literal(" [高级]"),
                                4 + this.textRenderer.getWidth(prefix + categoryLabel(row.category) + countStr), 
                                ry + 3, 0xFFFFAA00);
                    }
                } else if (row.schema != null) {
                    boolean hover = mouseX >= 2 && mouseX < PALETTE_W - 2
                            && mouseY >= ry && mouseY < ry + h;
                    int bg = hover ? 0xFF094771 : 0xFF2D2D2D;
                    context.fill(4, ry, PALETTE_W - 4, ry + h, bg);
                    int dot = parseColor(row.schema.color(), 0xFF888888);
                    context.fill(6, ry + 3, 10, ry + 9, dot);
                    String label = truncate(row.schema.label(), PALETTE_W - 18);
                    context.drawTextWithShadow(this.textRenderer, Text.literal(label),
                            12, ry + 2, 0xFFEEEEEE);
                }
            }
            y += h;
        }
        int totalH = totalPaletteHeight();
        int visibleH = bottom - top;
        if (totalH > visibleH) {
            int barH = Math.max(20, visibleH * visibleH / totalH);
            int barY = top + (int) ((long) paletteScroll * (visibleH - barH) / Math.max(1, totalH - visibleH));
            context.fill(PALETTE_W - 4, barY, PALETTE_W - 2, barY + barH, 0xFF666666);
        }
    }

    private void drawBlockTree(DrawContext context, int mouseX, int mouseY) {
        if (!blockTreeVisible) {
            return;
        }
        int treeX = paletteVisible ? PALETTE_W : 0;
        int treeY = TOP_BAR_H;
        int treeH = winHContent() - BOTTOM_BAR_H - TOP_BAR_H;
        rebuildBlockTree();
        context.fill(treeX, treeY, treeX + BLOCK_TREE_W, treeY + treeH, 0xFF252526);
        context.drawBorder(treeX, treeY, BLOCK_TREE_W, treeH, 0xFF444444);
        int searchBoxH = 14;
        treeNodeRects.clear();
        int nodeStartY = treeY + searchBoxH + 6 - treeScroll;
        hoveredTreeNode = null;
        for (BlockTreeNode root : blockTreeRoots) {
            int rendered = drawTreeNode(context, root, treeX, nodeStartY, 0, mouseX, mouseY, treeSearchText);
            nodeStartY += rendered;
        }
        int totalNodes = 0;
        for (BlockTreeNode root : blockTreeRoots) {
            totalNodes += countVisibleNodes(root, 0);
        }
        int totalTreeH = totalNodes * 14 + 4;
        if (totalTreeH > treeH - searchBoxH - 8) {
            int barH = Math.max(20, (treeH - searchBoxH - 8) * (treeH - searchBoxH - 8) / totalTreeH);
            int maxScroll = totalTreeH - (treeH - searchBoxH - 8);
            int barY = treeY + searchBoxH + 6 + (int) ((long) treeScroll * ((treeH - searchBoxH - 8) - barH) / Math.max(1, maxScroll));
            context.fill(treeX + BLOCK_TREE_W - 4, barY, treeX + BLOCK_TREE_W - 2, barY + barH, 0xFF666666);
        }
        // 显示搜索匹配数量
        if (treeSearchText != null && !treeSearchText.isEmpty()) {
            int matchCount = countSearchMatches();
            String countText = "匹配: " + matchCount;
            context.drawTextWithShadow(this.textRenderer, Text.literal(countText), treeX + 4, treeY + searchBoxH - 10, 0xFF888888);
        }
    }
    
    /** 统计搜索匹配的节点总数。 */
    private int countSearchMatches() {
        int count = 0;
        for (BlockTreeNode root : blockTreeRoots) {
            count += countMatchesInSubtree(root);
        }
        return count;
    }
    
    private int countMatchesInSubtree(BlockTreeNode node) {
        int c = matchesSearch(node) ? 1 : 0;
        for (BlockTreeNode child : node.children) {
            c += countMatchesInSubtree(child);
        }
        return c;
    }

    private int drawTreeNode(DrawContext context, BlockTreeNode node, int treeX, int y, int depth, int mouseX, int mouseY, String search) {
        if (!matchesSearch(node)) {
            return 0;
        }
        int nodeH = 14;
        int indent = depth * 12;
        int textX = treeX + 4 + indent;
        boolean isGroup = node.parent == null || node.children.isEmpty();
        boolean hover = mouseX >= textX && mouseX < treeX + BLOCK_TREE_W && mouseY >= y && mouseY < y + nodeH;
        if (hover) {
            hoveredTreeNode = node;
        }
        int bgColor = hover ? 0xFF3A5A8A : (selectedId != null && selectedId.equals(node.id) ? 0xFF4A4A6A : 0x00000000);
        if (bgColor != 0x00000000) {
            context.fill(treeX + 2, y, treeX + BLOCK_TREE_W - 2, y + nodeH, bgColor);
        }
        String expandIcon = "";
        if (!node.children.isEmpty()) {
            expandIcon = node.expanded ? "▼ " : "▶ ";
        }
        String label = getNodeLabel(node);
        context.drawTextWithShadow(this.textRenderer, Text.literal(expandIcon), textX + 4, y + 2, 0xFF888888);
        if (!search.isEmpty() && label.toLowerCase().contains(search.toLowerCase())) {
            drawHighlightedText(context, label, search, textX + 16, y + 2, 0xFFFFFFFF);
        } else {
            context.drawTextWithShadow(this.textRenderer, Text.literal(label), textX + 16, y + 2, isGroup ? 0xFFAAAAAA : 0xFFFFFFFF);
        }
        treeNodeRects.add(new TreeNodeRect(node, textX, y, BLOCK_TREE_W - 4 - indent, nodeH, indent, isGroup));
        int totalH = nodeH;
        if (node.expanded && !node.children.isEmpty()) {
            int childY = y + nodeH;
            for (BlockTreeNode child : node.children) {
                int rendered = drawTreeNode(context, child, treeX, childY, depth + 1, mouseX, mouseY, search);
                childY += rendered;
                totalH += rendered;
            }
        }
        return totalH;
    }

    private String getNodeLabel(BlockTreeNode node) {
        if (node.customName != null && !node.customName.isEmpty()) {
            return node.customName;
        }
        BlockSchema schema = reg.get(node.schemaId);
        return schema == null ? node.schemaId : schema.label();
    }

    private void drawHighlightedText(DrawContext context, String text, String highlight, int x, int y, int baseColor) {
        int hl = highlight.length();
        int idx = text.toLowerCase().indexOf(highlight.toLowerCase());
        if (idx < 0) {
            context.drawTextWithShadow(this.textRenderer, Text.literal(text), x, y, baseColor);
            return;
        }
        String before = text.substring(0, idx);
        String match = text.substring(idx, idx + hl);
        String after = text.substring(idx + hl);
        int curX = x;
        if (!before.isEmpty()) {
            context.drawTextWithShadow(this.textRenderer, Text.literal(before), curX, y, 0xFF888888);
            curX += this.textRenderer.getWidth(before);
        }
        context.drawTextWithShadow(this.textRenderer, Text.literal(match), curX, y, 0xFFFFFF00);
        curX += this.textRenderer.getWidth(match);
        if (!after.isEmpty()) {
            context.drawTextWithShadow(this.textRenderer, Text.literal(after), curX, y, baseColor);
        }
    }

    private void centerOnBlock(String blockId) {
        EditorBlock block = state.getById(blockId);
        if (block == null) {
            return;
        }
        int canvasX0 = paletteVisible ? PALETTE_W : 0;
        int canvasW = winW() - FIELD_PANEL_W - canvasX0;
        int canvasH = winHContent() - BOTTOM_BAR_H - TOP_BAR_H;
        double z = canvas.getZoom();
        double targetPanX = (canvasX0 + canvasW / 2.0) - (block.x() + BLOCK_W / 2.0) * z;
        double targetPanY = (TOP_BAR_H + canvasH / 2.0) - (block.y() + blockHeight(reg.get(block.schemaId())) / 2.0) * z;
        canvas.panBy(targetPanX - canvas.getPanX(), targetPanY - canvas.getPanY());
        selectedId = blockId;
        setStatus("已定位到: " + getNodeLabelById(blockId));
        clearAndInit();
    }

    private String getNodeLabelById(String blockId) {
        EditorBlock block = state.getById(blockId);
        if (block == null) {
            return blockId;
        }
        if (block.customName() != null && !block.customName().isEmpty()) {
            return block.customName();
        }
        BlockSchema schema = reg.get(block.schemaId());
        return schema == null ? block.schemaId() : schema.label();
    }

    private void startRename(BlockTreeNode node) {
        renamingTreeNode = node;
        EditorBlock block = state.getById(node.id);
        String initialName = block != null && block.customName() != null ? block.customName() : "";
        int treeX = paletteVisible ? PALETTE_W : 0;
        int rectX = treeX + 4;
        int rectY = TOP_BAR_H + 16;
        for (TreeNodeRect rect : treeNodeRects) {
            if (rect.node != null && rect.node.id.equals(node.id)) {
                rectX = rect.x + 12;
                rectY = rect.y;
                break;
            }
        }
        renameTextField = new TextFieldWidget(this.textRenderer, rectX, rectY, BLOCK_TREE_W - 16, 14, Text.literal(""));
        renameTextField.setMaxLength(64);
        renameTextField.setText(initialName);
        renameTextField.setFocused(true);
        addDrawableChild(renameTextField);
    }

    private void finishRename(boolean save) {
        if (renamingTreeNode != null && save && renameTextField != null) {
            String newName = renameTextField.getText().trim();
            state.setCustomName(renamingTreeNode.id, newName.isEmpty() ? null : newName);
            setStatus("已重命名: " + (newName.isEmpty() ? "(清除)" : newName));
        }
        if (renameTextField != null) {
            children().remove(renameTextField);
            renameTextField = null;
        }
        renamingTreeNode = null;
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
        int x1 = winW() - FIELD_PANEL_W;
        int y0 = TOP_BAR_H;
        int y1 = winHContent() - BOTTOM_BAR_H;
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
        boolean collapsed = block.collapsed();
        int fullH = blockHeight(schema);
        int sh = collapsed ? (int) (BLOCK_H_BASE * canvas.getZoom()) : (int) (fullH * canvas.getZoom());
        int fill = parseColor(schema == null ? "#888888" : schema.color(), 0xFF000000);
        context.fill(sx, sy, sx + sw, sy + sh, fill);
        
        // 选中边框：主选中白色，多选中蓝色
        if (block.id().equals(selectedId)) {
            context.drawBorder(sx - 1, sy - 1, sw + 2, sh + 2, 0xFFFFFFFF);
        } else if (selectedIds.contains(block.id())) {
            context.drawBorder(sx - 1, sy - 1, sw + 2, sh + 2, 0xFF00AAFF);
        } else {
            context.drawBorder(sx, sy, sw, sh, 0xFF000000);
        }
        
        String displayName = block.customName() != null && !block.customName().isBlank()
                ? block.customName() : (schema == null ? block.schemaId() : schema.label());
        
        // 折叠按钮（头部右侧）
        int collapseBtnX = sx + sw - 18;
        int collapseBtnY = sy + 2;
        context.fill(collapseBtnX, collapseBtnY, collapseBtnX + 14, collapseBtnY + 10, 0x60000000);
        String collapseIcon = collapsed ? "[+]" : "[-]";
        drawScaledText(context, Text.literal(collapseIcon), collapseBtnX + 1, collapseBtnY + 1, 0xFFFFFF, fontScale * 0.8f);
        
        drawScaledText(context, Text.literal(truncate(displayName, sw - 24)),
                sx + 3, sy + 2, 0xFFFFFF, fontScale);
        
        // 如果折叠则只显示头部
        if (collapsed) {
            return;
        }
        
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

    /** 积木悬停 tooltip：显示 schema label + 中文描述 + 字段说明。 */
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
        
        String chineseDesc = getBlockChineseDescription(schema.id());
        if (chineseDesc != null) {
            lines.add(schema.label());
            lines.add(chineseDesc);
        } else {
            lines.add(schema.label() + "  [" + schema.id() + "]");
        }
        
        for (BlockField f : schema.fields()) {
            Object v = b.fieldValues().get(f.name());
            if (v == null) {
                v = f.defaultValue();
            }
            String vs = v == null ? "" : v.toString();
            String fieldDesc = getFieldChineseDescription(f.name(), schema.id());
            if (fieldDesc != null) {
                lines.add("  " + f.name() + " = " + truncate(vs, 140));
                lines.add("    " + fieldDesc);
            } else {
                lines.add("  " + f.name() + " = " + truncate(vs, 160));
            }
        }
        if (!schema.acceptsChildrenCategories().isEmpty()) {
            lines.add("可接子块: " + String.join(", ", schema.acceptsChildrenCategories()));
        }
        
        lines.add("---");
        lines.add("右键切换 | Tab 循环 | 快捷键提示");
        
        int w = 260;
        int h = lines.size() * 11 + 8;
        int tx = mouseX + 12;
        int ty = mouseY + 12;
        if (tx + w > winW()) {
            tx = mouseX - w - 8;
        }
        if (ty + h > winHContent()) {
            ty = mouseY - h - 8;
        }
        context.fill(tx, ty, tx + w, ty + h, 0xF8000000);
        context.drawBorder(tx, ty, w, h, 0xFFCCCCCC);
        int ly = ty + 4;
        for (String line : lines) {
            int color = 0xFFDDDDDD;
            if (line.startsWith("---")) {
                color = 0xFF666666;
            } else if (line.startsWith("右键") || line.startsWith("Tab") || line.startsWith("快捷")) {
                color = 0xFF888888;
            }
            context.drawTextWithShadow(this.textRenderer,
                    Text.literal(truncate(line, w - 8)), tx + 4, ly, color);
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
        int x = winW() - FIELD_PANEL_W + 4;
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
            if (block.customName() != null && !block.customName().isBlank()) {
                context.drawTextWithShadow(this.textRenderer,
                        Text.literal("函数名: " + block.customName()), x, y + 24, 0x55FF55);
            }
        }
        if (linkMode) {
            context.drawTextWithShadow(this.textRenderer,
                    Text.literal("> 点击积木以连接").formatted(Formatting.YELLOW),
                    x, winHContent() - BOTTOM_BAR_H - 14, 0xFFFF00);
        }
    }

    private void drawCompilePreview(DrawContext context) {
        int ww = winW();
        int wh = winHContent();
        int pw = Math.min(440, ww - 40);
        int ph = Math.min(300, wh - 80);
        int px = (ww - pw) / 2;
        int py = (wh - ph) / 2;
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
                x, winHContent() - 14, 0x55FF55);
    }

    /** 绘制全屏模式提示（3秒后自动消失）。 */
    private void drawFullscreenHint(DrawContext context) {
        if (fullscreenHintTime == 0) {
            return;
        }
        long age = System.currentTimeMillis() - fullscreenHintTime;
        if (age > FULLSCREEN_HINT_DURATION) {
            fullscreenHintTime = 0;
            return;
        }
        int ww = winW();
        String hint = "全屏模式 - 按ESC或F11退出";
        int textWidth = this.textRenderer.getWidth(hint);
        int padding = 6;
        int boxW = textWidth + padding * 2;
        int boxH = 20;
        int bx = (ww - boxW) / 2;
        int by = 30;
        int alpha = 220;
        if (age > FULLSCREEN_HINT_DURATION - 1000) {
            alpha = (int) (alpha * (FULLSCREEN_HINT_DURATION - age) / 1000.0);
        }
        int bgColor = (alpha << 24) | 0x1A1A1A;
        context.fill(bx, by, bx + boxW, by + boxH, bgColor);
        context.drawBorder(bx, by, boxW, boxH, 0xFF555555);
        context.drawTextWithShadow(this.textRenderer, Text.literal(hint),
                bx + padding, by + 5, 0xFFAAAAAA);
    }

    // ---------- 鼠标交互 ----------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int ww = winW();
        int wh = winHContent();
        int wx = winX();
        int wyc = winYContent();
        double lmx = mouseX - wx;
        double lmy = mouseY - wyc;

        // 新手引导优先
        if (onboarding != null && onboarding.mouseClicked(lmx, lmy, ww, wh)) {
            return true;
        }
        // 标题栏按钮与小窗手势（绝对坐标）
        if (button == 0 && !isFullscreen() && window != null) {
            if (mouseY >= window.y && mouseY < window.y + EditorWindow.TITLE_H
                    && mouseX >= window.x && mouseX < window.x + window.width) {
                int fsX = window.x + window.width - 28;
                int clX = window.x + window.width - 14;
                if (mouseX >= fsX && mouseX < fsX + 13) {
                    toggleFullscreen();
                    return true;
                }
                if (mouseX >= clX && mouseX < clX + 12) {
                    close();
                    return true;
                }
                // 其余标题栏区域交给窗口拖动
            }
            if (window.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        if (super.mouseClicked(lmx, lmy, button)) {
            return true;
        }
        // 重命名输入框处理
        if (renamingTreeNode != null && renameTextField != null) {
            finishRename(true);
            return true;
        }
        // 积木树搜索框点击
        if (blockTreeVisible) {
            int treeX = paletteVisible ? PALETTE_W : 0;
            int treeY = TOP_BAR_H;
            int treeH = winHContent() - BOTTOM_BAR_H - TOP_BAR_H;
            if (lmx >= treeX && lmx < treeX + BLOCK_TREE_W && lmy >= treeY && lmy < treeY + treeH) {
                int searchY = treeY + 2;
                int searchBoxX = treeX + 4;
                int searchBoxW = BLOCK_TREE_W - 8;
                int searchBoxH = 14;
                if (lmx >= searchBoxX && lmx < searchBoxX + searchBoxW && lmy >= searchY && lmy < searchY + searchBoxH) {
                    if (treeSearchField != null) {
                        treeSearchField.setFocused(true);
                    }
                } else {
                    if (treeSearchField != null) {
                        treeSearchField.setFocused(false);
                    }
                    int mouseYTree = (int) lmy;
                    for (TreeNodeRect rect : treeNodeRects) {
                        if (mouseYTree >= rect.y && mouseYTree < rect.y + rect.h && lmx >= rect.x && lmx < rect.x + rect.w) {
                            if (button == 0) {
                                int iconX = rect.x + 10;
                                int iconW = 10;
                                if (lmx >= iconX - iconW && lmx < iconX + iconW && rect.node != null && !rect.node.children.isEmpty()) {
                                    rect.node.expanded = !rect.node.expanded;
                                    return true;
                                }
                                if (rect.node != null) {
                                    long now = System.currentTimeMillis();
                                    if (lastTreeClickNodeId != null && lastTreeClickNodeId.equals(rect.node.id) && now - lastTreeClickTime < 300) {
                                        startRename(rect.node);
                                        lastTreeClickNodeId = null;
                                    } else {
                                        centerOnBlock(rect.node.id);
                                        lastTreeClickNodeId = rect.node.id;
                                        lastTreeClickTime = now;
                                    }
                                    return true;
                                }
                            }
                            return true;
                        }
                    }
                }
                return false;
            }
        }
        // 调色板点击
        if (paletteVisible && lmx >= 0 && lmx < PALETTE_W
                && lmy >= TOP_BAR_H && lmy < wh - BOTTOM_BAR_H) {
            int top = TOP_BAR_H + 2;
            int y = top - paletteScroll;
            for (PaletteRow row : paletteRows) {
                int h = row.header ? PALETTE_HEADER_H : PALETTE_ROW_H;
                if (lmy >= y && lmy < y + h) {
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
        if (lmx < canvasX0 || lmx >= ww - FIELD_PANEL_W
                || lmy < TOP_BAR_H || lmy >= wh - BOTTOM_BAR_H) {
            return false;
        }
        // 分组标题栏命中（Task 7）：左键居中，右键折叠，"[×]"隐藏
        if (button == 0 || button == 1) {
            for (GroupHeader gh : groupHeaders) {
                if (lmx >= gh.x() && lmx < gh.x() + gh.w()
                        && lmy >= gh.y() && lmy < gh.y() + gh.h()) {
                    if (button == 0 && lmx >= gh.closeX() && lmx < gh.closeX() + 12) {
                        // 隐藏分组
                        hiddenFiles.add(gh.groupKey());
                        setStatus("已隐藏分组: " + groupLabel(gh.groupKey()));
                        clearAndInit();
                        return true;
                    }
                    if (button == 1) {
                        // 右键：切换折叠
                        if (collapsedFiles.contains(gh.groupKey())) {
                            collapsedFiles.remove(gh.groupKey());
                        } else {
                            collapsedFiles.add(gh.groupKey());
                        }
                        setStatus(collapsedFiles.contains(gh.groupKey())
                                ? "已折叠: " + groupLabel(gh.groupKey())
                                : "已展开: " + groupLabel(gh.groupKey()));
                        clearAndInit();
                        return true;
                    }
                    // 左键：平移居中到该组首个积木
                    centerOnGroup(gh.groupKey());
                    return true;
                }
            }
            // 顶部"显示隐藏分组"恢复条
            if (button == 0 && !hiddenFiles.isEmpty()
                    && lmx >= canvasX0 + 4 && lmx < canvasX0 + 200
                    && lmy >= TOP_BAR_H + 2 && lmy < TOP_BAR_H + 14) {
                hiddenFiles.clear();
                setStatus("已恢复所有隐藏分组");
                clearAndInit();
                return true;
            }
        }
        // 右键：命中积木则循环穿透，否则平移
        if (button == 1) {
            List<String> hits = hitBlocks(lmx, lmy);
            if (!hits.isEmpty()) {
                hitOffset++;
                selectedId = hitBlock(lmx, lmy);
                setStatus("切换层级: " + selectedId);
                clearAndInit();
                return true;
            }
            panning = true;
            lastPanX = (int) mouseX;
            lastPanY = (int) mouseY;
            return true;
        }
        if (button != 0) {
            return false;
        }
        // 左键命中积木：重置穿透偏移并选中
        hitOffset = 0;
        String hit = hitBlock(lmx, lmy);
        
        // Shift多选功能：检测Shift键
        boolean shiftHeld = GLFW.glfwGetKey(MinecraftClient.getInstance().getWindow().getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS ||
                           GLFW.glfwGetKey(MinecraftClient.getInstance().getWindow().getHandle(), GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
        
        if (linkMode && hit != null && selectedId != null && !hit.equals(selectedId)) {
            state.connect(selectedId, hit);
            linkMode = false;
            setStatus("已连接 " + selectedId + " -> " + hit);
            clearAndInit();
            return true;
        }
        if (hit != null) {
            // 检查是否点击了折叠按钮
            EditorBlock hitBlock = state.getById(hit);
            if (hitBlock != null) {
                BlockSchema hitSchema = reg.get(hitBlock.schemaId());
                int sx = toScreenX(hitBlock.x());
                int sy = toScreenY(hitBlock.y());
                int sw = Math.max(BLOCK_W, (int) (BLOCK_W * canvas.getZoom()));
                int collapseBtnX = sx + sw - 18;
                int collapseBtnY = sy + 2;
                // 检查鼠标位置是否在折叠按钮区域内
                if (lmx >= collapseBtnX && lmx < collapseBtnX + 14 && lmy >= collapseBtnY && lmy < collapseBtnY + 10) {
                    // 切换折叠状态
                    state.setCollapsed(hit, !hitBlock.collapsed());
                    setStatus(hitBlock.collapsed() ? "已展开积木" : "已折叠积木");
                    clearAndInit();
                    return true;
                }
            }
            
            if (shiftHeld) {
                // Shift多选：切换选中状态
                if (selectedIds.contains(hit)) {
                    selectedIds.remove(hit);
                    setStatus("已取消选中: " + hit);
                } else {
                    selectedIds.add(hit);
                    setStatus("已添加选中 (" + selectedIds.size() + "): " + hit);
                }
            } else {
                // 单选：清空其他选中，只选当前
                selectedIds.clear();
                selectedIds.add(hit);
                selectedId = hit;
            }
            EditorBlock b = state.getById(hit);
            double wx2 = toWorldX(lmx);
            double wy2 = toWorldY(lmy);
            dragOffsetX = wx2 - (b == null ? 0 : b.x());
            dragOffsetY = wy2 - (b == null ? 0 : b.y());
            draggingId = hit;
            if (b != null) {
                moveStartPositions.put(hit, new double[]{b.x(), b.y()});
            }
            linkMode = false;
            clearAndInit();
            return true;
        }
        // 双击画布空白处归位
        if (hit == null) {
            long now = System.currentTimeMillis();
            if (now - lastCanvasClickTime < 300 &&
                Math.abs(lmx - lastCanvasClickX) < 5 &&
                Math.abs(lmy - lastCanvasClickY) < 5) {
                fitAllBlocksInView();
                lastCanvasClickTime = 0;
                return true;
            }
            lastCanvasClickTime = now;
            lastCanvasClickX = lmx;
            lastCanvasClickY = lmy;
        }
        // 点击空白处
        if (!shiftHeld) {
            selectedIds.clear();
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
        // 小窗拖拽/缩放优先（绝对坐标）
        if (!isFullscreen() && window != null && window.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            if (window.resized) {
                window.resized = false;
                clearAndInit();
            }
            return true;
        }
        double lmx = mouseX - winX();
        double lmy = mouseY - winYContent();
        if (panning) {
            canvas.panBy((int) deltaX, (int) deltaY);
            return true;
        }
        if (draggingId != null) {
            double wx = toWorldX(lmx) - dragOffsetX;
            double wy = toWorldY(lmy) - dragOffsetY;
            state.moveBlock(draggingId, wx, wy);
            return true;
        }
        return super.mouseDragged(lmx, lmy, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!isFullscreen() && window != null && window.mouseReleased(mouseX, mouseY, button)) {
            // 缩放结束后刷新布局，使按钮/面板跟随新尺寸
            clearAndInit();
            return true;
        }
        double lmx = mouseX - winX();
        double lmy = mouseY - winYContent();
        if (panning) {
            panning = false;
            return true;
        }
        if (draggingId != null) {
            EditorBlock dragged = state.getById(draggingId);
            double[] startPos = moveStartPositions.remove(draggingId);

            if (dragged != null && startPos != null) {
                double finalX = dragged.x();
                double finalY = dragged.y();
                boolean moved = Math.abs(startPos[0] - finalX) > 0.5 || Math.abs(startPos[1] - finalY) > 0.5;

                if (moved) {
                    BlockSchema ds = reg.get(dragged.schemaId());
                    int dh = blockHeight(ds);
                    if (intersectsExisting(finalX, finalY, BLOCK_W, dh, draggingId)) {
                        double[] snapped = snapToFreeGrid(dragged);
                        state.moveBlock(draggingId, snapped[0], snapped[1]);
                        finalX = snapped[0];
                        finalY = snapped[1];
                    }

                    EditorAction action = new EditorAction();
                    action.type = EditorAction.ActionType.MOVE_BLOCK;
                    action.blockId = draggingId;
                    action.before = new double[]{startPos[0], startPos[1]};
                    action.after = new double[]{finalX, finalY};
                    recordAction(action);
                }

                ensureBlockInView(dragged);
            }
            draggingId = null;
            return true;
        }
        return super.mouseReleased(lmx, lmy, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        double lmx = mouseX - winX();
        double lmy = mouseY - winYContent();
        int wh = winHContent();
        // 积木树滚动
        if (blockTreeVisible) {
            int treeX = paletteVisible ? PALETTE_W : 0;
            int treeY = TOP_BAR_H;
            int treeH = wh - BOTTOM_BAR_H - TOP_BAR_H;
            if (lmx >= treeX && lmx < treeX + BLOCK_TREE_W && lmy >= treeY && lmy < treeY + treeH) {
                treeScroll -= (int) (verticalAmount * 14 * 2);
                int searchBoxH = 14;
                rebuildBlockTree();
                int totalNodes = 0;
                for (BlockTreeNode root : blockTreeRoots) {
                    totalNodes += countVisibleNodes(root, 0);
                }
                int totalTreeH = totalNodes * 14 + 4;
                int maxScroll = Math.max(0, totalTreeH - (treeH - searchBoxH - 8));
                if (treeScroll < 0) {
                    treeScroll = 0;
                }
                if (treeScroll > maxScroll) {
                    treeScroll = maxScroll;
                }
                return true;
            }
        }
        // 调色板滚动
        if (paletteVisible && lmx >= 0 && lmx < PALETTE_W
                && lmy >= TOP_BAR_H && lmy < wh - BOTTOM_BAR_H) {
            paletteScroll -= (int) (verticalAmount * PALETTE_ROW_H * 2);
            int top = TOP_BAR_H + 2;
            int visibleH = wh - BOTTOM_BAR_H - top;
            int totalH = totalPaletteHeight();
            if (paletteScroll < 0) {
                paletteScroll = 0;
            }
            if (paletteScroll > Math.max(0, totalH - visibleH)) {
                paletteScroll = Math.max(0, totalH - visibleH);
            }
            return true;
        }
        if (inCanvas(lmx, lmy)) {
            double factor = verticalAmount > 0 ? 1.1 : (verticalAmount < 0 ? 1.0 / 1.1 : 1.0);
            canvas.zoomBy(factor);
            return true;
        }
        return super.mouseScrolled(lmx, lmy, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // ESC键退出全屏
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (window != null && window.fullscreen) {
                toggleFullscreen();
                setStatus("已退出全屏模式");
                return true;
            }
        }
        // F11键切换全屏
        if (keyCode == GLFW.GLFW_KEY_F11) {
            toggleFullscreen();
            if (window != null && window.fullscreen) {
                setStatus("已进入全屏模式 - 按ESC或F11退出");
            } else {
                setStatus("已退出全屏模式");
            }
            return true;
        }
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
        // NBT 复制快捷键（Task 9）：Ctrl+Shift+C 坐标 / Ctrl+Shift+I 物品 / Ctrl+Shift+T 准星目标
        if (!anyFieldFocused() && selectedId != null) {
            boolean ctrlShift = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0 && (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
            if (ctrlShift && keyCode == GLFW.GLFW_KEY_C) {
                copyCoordinatesAction();
                return true;
            }
            if (ctrlShift && keyCode == GLFW.GLFW_KEY_I) {
                copyHeldItemAction();
                return true;
            }
            if (ctrlShift && keyCode == GLFW.GLFW_KEY_T) {
                copyTargetNbtAction();
                return true;
            }
        }
        // 批量操作快捷键（文本框聚焦时不拦截）
        if (!anyFieldFocused()) {
            boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
            // Ctrl+C：复制选中积木
            if (ctrl && keyCode == GLFW.GLFW_KEY_C) {
                copySelectedBlocks();
                return true;
            }
            // Ctrl+V：粘贴积木
            if (ctrl && keyCode == GLFW.GLFW_KEY_V) {
                pasteBlocks();
                return true;
            }
            // Ctrl+D 或 Delete：删除选中积木
            if ((ctrl && keyCode == GLFW.GLFW_KEY_D) || keyCode == GLFW.GLFW_KEY_DELETE) {
                deleteSelectedBlocks();
                return true;
            }
            // Ctrl+A：全选
            if (ctrl && keyCode == GLFW.GLFW_KEY_A) {
                selectAllBlocks();
                return true;
            }
            // Ctrl+Z：撤销
            if (ctrl && keyCode == GLFW.GLFW_KEY_Z) {
                undo();
                return true;
            }
            // Ctrl+Y：重做
            if (ctrl && keyCode == GLFW.GLFW_KEY_Y) {
                redo();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_B) {
                toggleBlockTree();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** 是否有字段文本框正在聚焦（避免快捷键吞掉文本输入）。 */
    private boolean anyFieldFocused() {
        for (TextFieldWidget tf : fieldTextFields.values()) {
            if (tf != null && tf.isFocused()) {
                return true;
            }
        }
        if (treeSearchField != null && treeSearchField.isFocused()) {
            return true;
        }
        if (renameTextField != null && renameTextField.isFocused()) {
            return true;
        }
        return false;
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        if (window != null && !window.fullscreen) {
            window.clampToScreen(width, height);
        }
        super.resize(client, width, height);
    }

    private boolean inCanvas(double x, double y) {
        int canvasX0 = paletteVisible ? PALETTE_W : 0;
        return x >= canvasX0 && x < winW() - FIELD_PANEL_W
                && y >= TOP_BAR_H && y < winHContent() - BOTTOM_BAR_H;
    }

    /** 命中检测：返回鼠标下最上层积木 id（重叠时按 hitOffset 偏移循环，Task 8）。 */
    private String hitBlock(double mouseX, double mouseY) {
        double wx = toWorldX(mouseX);
        double wy = toWorldY(mouseY);
        List<EditorBlock> hits = new ArrayList<>();
        List<EditorBlock> list = new ArrayList<>(state.getBlocks());
        for (int i = list.size() - 1; i >= 0; i--) {
            EditorBlock b = list.get(i);
            BlockSchema schema = reg.get(b.schemaId());
            int h = blockHeight(schema);
            if (wx >= b.x() && wx <= b.x() + BLOCK_W && wy >= b.y() && wy <= b.y() + h) {
                hits.add(b);
            }
        }
        if (hits.isEmpty()) {
            return null;
        }
        // 重叠时按 hitOffset 取第 N 个命中的积木（穿透切换层级）
        int idx = Math.floorMod(hitOffset, hits.size());
        return hits.get(idx).id();
    }

    /** 命中所有积木（按从上到下顺序），用于循环切换。 */
    private List<String> hitBlocks(double mouseX, double mouseY) {
        double wx = toWorldX(mouseX);
        double wy = toWorldY(mouseY);
        List<String> hits = new ArrayList<>();
        List<EditorBlock> list = new ArrayList<>(state.getBlocks());
        for (int i = list.size() - 1; i >= 0; i--) {
            EditorBlock b = list.get(i);
            BlockSchema schema = reg.get(b.schemaId());
            int h = blockHeight(schema);
            if (wx >= b.x() && wx <= b.x() + BLOCK_W && wy >= b.y() && wy <= b.y() + h) {
                hits.add(b.id());
            }
        }
        return hits;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (window != null) {
            window.applyToConfig(DatapackEditorClient.config());
            DatapackEditorClient.saveConfig();
        }
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
