package com.dpe.client;

import com.dpe.common.block.BlockSchemaRegistry;
import com.dpe.common.block.EditorState;
import com.dpe.common.complete.CompletionCandidate;
import com.dpe.common.complete.CompletionContext;
import com.dpe.common.complete.CompletionService;
import com.dpe.common.compile.BlockCompiler;
import com.dpe.common.compile.CompileResult;
import com.dpe.common.compile.ValidationError;
import com.dpe.common.filetree.DatapackFileIo;
import com.dpe.common.filetree.DatapackScanner;
import com.dpe.common.filetree.FileNode;
import com.dpe.common.filetree.FileTree;
import com.dpe.common.model.Datapack;
import com.dpe.common.model.ResourceLocation;
import com.dpe.common.parse.TextToBlocksParser;
import com.dpe.common.reload.ReloadResult;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * VSCode 风格代码编辑器：
 * <ul>
 *   <li>左侧文件树 + 顶部多标签页头 + 中心编辑区（行号/语法高亮/光标）。</li>
 *   <li>顶部按钮：切到积木(M)/重载(R)/保存/打开文件夹/新窗口/关闭。</li>
 *   <li>键入时调用 {@link CompletionService} 浮层补全，Tab/Enter 插入 insertText。</li>
 *   <li>支持游戏内小窗化（{@link EditorWindow}）与脱离游戏的独立窗口（{@link DetachedEditorWindow}）。</li>
 * </ul>
 * 多行编辑自实现：每文件以 {@code List<StringBuilder>} 行表保存，charTyped/keyPressed 维护。
 */
public class IdeEditorScreen extends Screen {

    private static final int FILE_TREE_W = 160;
    private static final int TAB_BAR_H = 18;
    private static final int TOP_BAR_H = 22;
    private static final int BOTTOM_BAR_H = 20;
    private static final int LINE_NUMBER_W = 36;
    private static final int LINE_H = 11;
    private static final int CHAR_W = 6; // 近似字符宽

    private static final int COMPLETION_PANEL_W = 280;
    private static final int COMPLETION_PANEL_H = 12;
    private static final int COMPLETION_DETAIL_W = 220;
    private static final int COMPLETION_DETAIL_H = 100;
    private static final int COMPLETION_BG = 0xF0000000;
    private static final int COMPLETION_BORDER = 0xFF4488FF;
    private static final int COMPLETION_SELECTED_BG = 0xFF094771;
    private static final int COMPLETION_DETAIL_BG = 0xF0101010;

    private static final Map<String, WikiCommandInfo> WIKI_CACHE = new LinkedHashMap<>();
    private static final int WIKI_CACHE_MAX_SIZE = 100;
    private static long lastWikiFetchTime = 0;
    private static final long WIKI_FETCH_COOLDOWN = 60000;

    private final EditorState state;
    private final BlockSchemaRegistry reg = BlockSchemaRegistry.DEFAULT;
    private final CompletionService completionService = new CompletionService();

    /** 文件路径 -> 行列表（可编辑）。 */
    private final Map<String, List<StringBuilder>> buffers = new LinkedHashMap<>();
    /** 原始文件路径集合（用于标记为 mcfunction/json）。 */
    private final Map<String, String> fileKinds = new LinkedHashMap<>();
    /** 打开的标签页路径列表（顺序）。 */
    private final List<String> openTabs = new ArrayList<>();
    /** 当前激活标签路径。 */
    private String activeTab = null;
    /** 每标签光标 {line, col}。 */
    private final Map<String, int[]> cursors = new LinkedHashMap<>();
    /** 每标签滚动 {x, y}。 */
    private final Map<String, int[]> scrolls = new LinkedHashMap<>();
    /** 标签路径 -> 对应的磁盘 FileNode（虚拟缓冲无节点，保存时跳过磁盘写入）。 */
    private final Map<String, FileNode> tabNodes = new LinkedHashMap<>();

    private int scrollX = 0;
    private int scrollY = 0;
    private int cursorLine = 0;
    private int cursorCol = 0;

    private int completionPanelX = 0;
    private int completionPanelY = 0;
    private int completionScrollOffset = 0;

    /** 当前编译错误列表。 */
    private List<ValidationError> errors = List.of();
    /** 错误面板是否展开。 */
    private boolean showErrors = true;

    /** 补全候选（当前行编辑后刷新）。 */
    private List<CompletionCandidate> completionCandidates = List.of();
    /** 补全浮层选中索引。 */
    private int completionIndex = 0;
    private boolean completionVisible = false;

    private String statusMessage = null;
    private long statusTime = 0;

    /** 游戏内小窗状态。 */
    private EditorWindow window;

    /** 真实文件树（扫描 worldDatapacksDir）；非单机为 null。 */
    private FileTree fileTree = null;
    /** 文件树渲染行缓存（drawFileTree 构建，mouseClicked 复用）。 */
    private final List<TreeDisplayRow> treeRows = new ArrayList<>();
    /** 折叠的目录路径集合。 */
    private final java.util.Set<String> collapsedDirs = new java.util.HashSet<>();
    /** 文件树选中的节点（用于新建/重命名/删除操作的目标）。 */
    private FileNode selectedTreeNode = null;
    /** 新建/重命名名称输入框。 */
    private TextFieldWidget nameField = null;
    /** 文件树垂直滚动偏移。 */
    private int treeScroll = 0;
    /** 文件树单行高度。 */
    private static final int TREE_ROW_H = 12;

    /** 文件树渲染行：节点 + 缩进深度 + 屏幕行号。 */
    private record TreeDisplayRow(FileNode node, int depth, int y) {
    }

    private record WikiCommandInfo(String commandName, String description, String example, long fetchTime) {
    }

    public IdeEditorScreen(EditorState state) {
        super(Text.literal("IDE 代码编辑器"));
        this.state = state == null ? new EditorState() : state;
        // 编译当前 state 得到初始（虚拟）文件集，作为无真实文件树时的回退
        rebuildBuffersFromState();
    }

    // ---------- 窗口几何辅助 ----------

    private int winX() {
        return window == null || window.fullscreen ? 0 : window.x;
    }

    private int winYContent() {
        return window == null || window.fullscreen ? 0 : window.y + EditorWindow.TITLE_H;
    }

    private int winW() {
        return window == null || window.fullscreen ? this.width : window.width;
    }

    private int winHContent() {
        int titleH = (window == null || window.fullscreen) ? 0 : EditorWindow.TITLE_H;
        return (window == null || window.fullscreen ? this.height : window.height) - titleH;
    }

    private boolean isFullscreen() {
        return window == null || window.fullscreen;
    }

    /** 编译当前 state 得到 mcfunctions/jsonFiles，写入 buffers。 */
    private void rebuildBuffersFromState() {
        buffers.clear();
        fileKinds.clear();
        CompileResult result = new BlockCompiler().compile(state, reg);
        if (!result.success()) {
            errors = result.errors();
            // 即使失败，也提供空 mcfunction 以便编辑
            buffers.put("data/<ns>/functions/internal/tick.mcfunction", new ArrayList<>(List.of(new StringBuilder())));
            fileKinds.put("data/<ns>/functions/internal/tick.mcfunction", "mcfunction");
        } else {
            errors = List.of();
            String ns = state.getActiveDatapackNamespace();
            for (Map.Entry<ResourceLocation, String> e : result.mcfunctions().entrySet()) {
                String path = "data/" + ns + "/functions/" + e.getKey().path() + ".mcfunction";
                buffers.put(path, toLines(e.getValue()));
                fileKinds.put(path, "mcfunction");
            }
            for (Map.Entry<ResourceLocation, String> e : result.jsonFiles().entrySet()) {
                String path = "data/" + ns + "/tags/" + e.getKey().path() + ".json";
                buffers.put(path, toLines(e.getValue()));
                fileKinds.put(path, "json");
            }
            if (buffers.isEmpty()) {
                buffers.put("data/" + ns + "/functions/internal/tick.mcfunction",
                        new ArrayList<>(List.of(new StringBuilder("# 空文件，请添加命令"))));
                fileKinds.put("data/" + ns + "/functions/internal/tick.mcfunction", "mcfunction");
            }
        }
        // 默认打开第一个文件
        if (activeTab == null || !buffers.containsKey(activeTab)) {
            activeTab = buffers.isEmpty() ? null : buffers.keySet().iterator().next();
            openTabs.clear();
            if (activeTab != null) {
                openTabs.add(activeTab);
            }
        }
        restoreCursor();
    }

    private static List<StringBuilder> toLines(String content) {
        List<StringBuilder> lines = new ArrayList<>();
        if (content == null || content.isEmpty()) {
            lines.add(new StringBuilder());
            return lines;
        }
        for (String l : content.split("\n", -1)) {
            lines.add(new StringBuilder(l));
        }
        if (lines.isEmpty()) {
            lines.add(new StringBuilder());
        }
        return lines;
    }

    private void restoreCursor() {
        if (activeTab == null) {
            return;
        }
        int[] c = cursors.get(activeTab);
        if (c == null) {
            cursorLine = 0;
            cursorCol = 0;
        } else {
            cursorLine = c[0];
            cursorCol = c[1];
        }
        int[] s = scrolls.get(activeTab);
        if (s == null) {
            scrollX = 0;
            scrollY = 0;
        } else {
            scrollX = s[0];
            scrollY = s[1];
        }
        clampCursor();
    }

    private int[] calculateCompletionPosition(int mouseX, int mouseY, int panelWidth, int panelHeight) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int screenHeight = mc.getWindow().getScaledHeight();
        int screenWidth = mc.getWindow().getScaledWidth();

        if (mouseY + panelHeight + 20 < screenHeight) {
            return new int[]{mouseX, mouseY + 20};
        } else if (mouseY - panelHeight - 20 > 0) {
            return new int[]{mouseX, mouseY - panelHeight - 20};
        }
        return new int[]{(screenWidth - panelWidth) / 2, (screenHeight - panelHeight) / 2};
    }

    private void saveCursor() {
        if (activeTab == null) {
            return;
        }
        cursors.put(activeTab, new int[]{cursorLine, cursorCol});
        scrolls.put(activeTab, new int[]{scrollX, scrollY});
    }

    @Override
    protected void init() {
        if (window == null) {
            window = EditorWindow.fromConfig(DatapackEditorClient.config(), this.width, this.height);
        }
        int ww = winW();
        int wh = winHContent();
        // 扫描真实文件树（Task 6）
        refreshFileTree();
        // 顶部按钮（相对窗口内容坐标）
        int bx = FILE_TREE_W + 4;
        addDrawableChild(ButtonWidget.builder(Text.literal("切到积木 (Ctrl+M)"), b -> switchToBlocks())
                .dimensions(bx, 2, 116, 16).build());
        bx += 120;
        addDrawableChild(ButtonWidget.builder(Text.literal("重载 (Ctrl+R)"), b -> doReload())
                .dimensions(bx, 2, 78, 16).build());
        bx += 82;
        addDrawableChild(ButtonWidget.builder(Text.literal("保存 (Ctrl+S)"), b -> doSave())
                .dimensions(bx, 2, 78, 16).build());
        bx += 82;
        addDrawableChild(ButtonWidget.builder(Text.literal("显示错误"), b -> {
                    showErrors = !showErrors;
                    clearAndInit();
                })
                .dimensions(bx, 2, 64, 16).build());
        bx += 68;
        addDrawableChild(ButtonWidget.builder(Text.literal("📂文件夹"), b -> openFolder())
                .dimensions(bx, 2, 64, 16).build());
        bx += 68;
        addDrawableChild(ButtonWidget.builder(Text.literal("🪟新窗口"), b -> openDetached())
                .dimensions(bx, 2, 64, 16).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("关闭"), b -> close())
                .dimensions(ww - 60, 2, 50, 16).build());

        // 文件树底部操作栏：名称输入 + 新建文件/目录/重命名/删除（Task 6）
        int opsY = wh - BOTTOM_BAR_H - 16;
        nameField = new TextFieldWidget(this.textRenderer, 2, opsY, FILE_TREE_W - 4, 12, Text.literal("名称"));
        nameField.setMaxLength(128);
        nameField.setPlaceholder(Text.literal("名称..."));
        addDrawableChild(nameField);
        int obx = 2;
        int oby = opsY + 13;
        int halfW = (FILE_TREE_W - 6) / 2;
        addDrawableChild(ButtonWidget.builder(Text.literal("+文件"), b -> createFileFromField())
                .dimensions(obx, oby, halfW, 12).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("+目录"), b -> createDirFromField())
                .dimensions(obx + halfW + 2, oby, halfW, 12).build());
        int oby2 = oby + 13;
        addDrawableChild(ButtonWidget.builder(Text.literal("重命名"), b -> renameFromField())
                .dimensions(obx, oby2, halfW, 12).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("删除"), b -> deleteSelectedNode())
                .dimensions(obx + halfW + 2, oby2, halfW, 12).build());
    }

    /** 重新扫描 worldDatapacksDir 刷新文件树缓存（Task 6）。 */
    private void refreshFileTree() {
        MinecraftClient mc = MinecraftClient.getInstance();
        Path dpDir = DatapackEditorClient.worldDatapacksDir(mc);
        if (dpDir != null) {
            try {
                fileTree = DatapackScanner.scan(dpDir);
            } catch (Exception e) {
                fileTree = null;
            }
        } else {
            fileTree = null;
        }
    }

    /** 把当前激活缓冲写回磁盘（若有真实 FileNode）。 */
    private void saveActiveBufferToDisk() {
        if (activeTab == null) {
            return;
        }
        FileNode node = tabNodes.get(activeTab);
        if (node == null) {
            return; // 虚拟缓冲，无磁盘节点
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        Path dpDir = DatapackEditorClient.worldDatapacksDir(mc);
        if (dpDir == null) {
            return;
        }
        String content = joinBuffer(buffers.get(activeTab));
        try {
            DatapackFileIo.write(dpDir, node, content);
        } catch (Exception e) {
            setStatus("写盘失败: " + e.getMessage());
        }
    }

    /** 拼接缓冲为字符串。 */
    private static String joinBuffer(List<StringBuilder> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(lines.get(i));
        }
        return sb.toString();
    }

    /** 打开一个真实文件节点：读入缓冲、加标签、记录节点（Task 6）。 */
    private void openFileNode(FileNode node) {
        if (node == null || node.isDirectory()) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        Path dpDir = DatapackEditorClient.worldDatapacksDir(mc);
        if (dpDir == null) {
            setStatus("非单机世界，无法读取文件");
            return;
        }
        String content;
        try {
            content = DatapackFileIo.read(dpDir, node);
        } catch (Exception e) {
            setStatus("读取失败: " + e.getMessage());
            return;
        }
        String path = node.path();
        buffers.put(path, toLines(content));
        fileKinds.put(path, path.endsWith(".json") ? "json" : "mcfunction");
        tabNodes.put(path, node);
        saveCursor();
        activeTab = path;
        if (!openTabs.contains(path)) {
            openTabs.add(path);
        }
        restoreCursor();
        setStatus("已打开: " + node.name());
    }

    /** 在选中节点（或其所属数据包根）下新建文件（Task 6）。 */
    private void createFileFromField() {
        createNodeFromField(false);
    }

    /** 在选中节点下新建目录（Task 6）。 */
    private void createDirFromField() {
        createNodeFromField(true);
    }

    private void createNodeFromField(boolean directory) {
        String name = nameField == null ? "" : nameField.getText();
        if (name == null || name.isBlank()) {
            setStatus("请先在名称框输入名称");
            return;
        }
        FileNode parent = resolveParentDir();
        if (parent == null) {
            setStatus("无可用父目录（非单机或无数据包）");
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        Path dpDir = DatapackEditorClient.worldDatapacksDir(mc);
        try {
            FileNode created = directory
                    ? DatapackFileIo.createDirectory(dpDir, parent, name)
                    : DatapackFileIo.createFile(dpDir, parent, name);
            refreshFileTree();
            setStatus("已创建: " + created.name());
            clearAndInit();
        } catch (Exception e) {
            setStatus("创建失败: " + e.getMessage());
        }
    }

    /** 重命名选中节点（Task 6）。 */
    private void renameFromField() {
        String name = nameField == null ? "" : nameField.getText();
        if (name == null || name.isBlank()) {
            setStatus("请先在名称框输入新名称");
            return;
        }
        FileNode node = selectedTreeNode;
        if (node == null) {
            setStatus("请先在文件树选中一个节点");
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        Path dpDir = DatapackEditorClient.worldDatapacksDir(mc);
        try {
            DatapackFileIo.rename(dpDir, node, name);
            // 更新缓冲键与节点引用
            String oldPath = node.path();
            refreshFileTree();
            // 旧缓冲路径已失效，移除标签
            if (openTabs.remove(oldPath) || activeTab != null && activeTab.equals(oldPath)) {
                tabNodes.remove(oldPath);
                buffers.remove(oldPath);
                fileKinds.remove(oldPath);
                cursors.remove(oldPath);
                scrolls.remove(oldPath);
                if (activeTab != null && activeTab.equals(oldPath)) {
                    activeTab = openTabs.isEmpty() ? null : openTabs.get(openTabs.size() - 1);
                    restoreCursor();
                }
            }
            setStatus("已重命名为: " + name);
            clearAndInit();
        } catch (Exception e) {
            setStatus("重命名失败: " + e.getMessage());
        }
    }

    /** 删除选中节点（Task 6）。 */
    private void deleteSelectedNode() {
        FileNode node = selectedTreeNode;
        if (node == null) {
            setStatus("请先在文件树选中一个节点");
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        Path dpDir = DatapackEditorClient.worldDatapacksDir(mc);
        try {
            DatapackFileIo.delete(dpDir, node);
            String oldPath = node.path();
            refreshFileTree();
            // 关闭对应标签
            if (openTabs.remove(oldPath) || activeTab != null && activeTab.equals(oldPath)) {
                tabNodes.remove(oldPath);
                buffers.remove(oldPath);
                fileKinds.remove(oldPath);
                cursors.remove(oldPath);
                scrolls.remove(oldPath);
                if (activeTab != null && activeTab.equals(oldPath)) {
                    activeTab = openTabs.isEmpty() ? null : openTabs.get(openTabs.size() - 1);
                    restoreCursor();
                }
            }
            selectedTreeNode = null;
            setStatus("已删除: " + node.name());
            clearAndInit();
        } catch (Exception e) {
            setStatus("删除失败: " + e.getMessage());
        }
    }

    /** 解析新建操作的父目录：选中目录用本身，选中文件用其父目录，未选则用首个数据包根。 */
    private FileNode resolveParentDir() {
        if (fileTree == null || fileTree.datapacks().isEmpty()) {
            return null;
        }
        if (selectedTreeNode != null) {
            if (selectedTreeNode.isDirectory()) {
                return selectedTreeNode;
            }
            // 文件：找其父目录节点
            return findParentNode(selectedTreeNode.path());
        }
        // 默认：当前命名空间数据包的 data 目录
        String ns = state.getActiveDatapackNamespace();
        String dpName = "dpe-" + ns;
        for (FileNode dp : fileTree.datapacks()) {
            if (dp.name().equals(dpName)) {
                FileNode data = dp.childByName("data");
                if (data != null) {
                    FileNode nsDir = data.childByName(ns);
                    return nsDir != null ? nsDir : data;
                }
                return dp;
            }
        }
        return fileTree.datapacks().get(0);
    }

    /** 在文件树中查找某路径的父目录节点。 */
    private FileNode findParentNode(String childPath) {
        if (childPath == null || fileTree == null) {
            return null;
        }
        int idx = childPath.lastIndexOf('/');
        if (idx <= 0) {
            // 顶层，父为数据包根
            for (FileNode dp : fileTree.datapacks()) {
                if (dp.childByName(childPath) != null) {
                    return dp;
                }
            }
            return null;
        }
        String parentPath = childPath.substring(0, idx);
        for (FileNode dp : fileTree.datapacks()) {
            FileNode found = dp.findByPath(parentPath);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** 切回积木模式：用合并式解析（保留原 state 引用与坐标），持久化窗口几何后切换。 */
    private void switchToBlocks() {
        // 合并式重载：传 this.state，按签名匹配保留坐标，不丢弃原 state
        Map<String, String> files = collectFiles();
        String ns = state.getActiveDatapackNamespace();
        new TextToBlocksParser().parse(ns, files, this.state);
        // 持久化窗口几何到 config（IDE 无画布，画布视图已存于 state）
        if (window != null) {
            window.applyToConfig(DatapackEditorClient.config());
            DatapackEditorClient.saveConfig();
        }
        if (this.client != null) {
            this.client.setScreen(new EditorScreen(this.state));
        }
    }

    /** 把当前 buffers 解析回新 EditorState（仅用于无 state 的回退场景）。 */
    private EditorState parseFilesToState() {
        Map<String, String> files = collectFiles();
        String ns = state.getActiveDatapackNamespace();
        return new TextToBlocksParser().parse(ns, files);
    }

    /** 收集当前 buffers 为 路径->内容 map。 */
    private Map<String, String> collectFiles() {
        Map<String, String> files = new LinkedHashMap<>();
        for (Map.Entry<String, List<StringBuilder>> e : buffers.entrySet()) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < e.getValue().size(); i++) {
                if (i > 0) {
                    sb.append('\n');
                }
                sb.append(e.getValue().get(i));
            }
            files.put(e.getKey(), sb.toString());
        }
        return files;
    }

    /** 用独立窗口回传的文件覆盖当前 buffers。 */
    private void applyDetachedFiles(Map<String, String> newFiles) {
        if (newFiles == null) {
            return;
        }
        // 保留 fileKinds，按路径更新内容
        for (Map.Entry<String, String> e : newFiles.entrySet()) {
            List<StringBuilder> lines = toLines(e.getValue());
            buffers.put(e.getKey(), lines);
            if (!fileKinds.containsKey(e.getKey())) {
                fileKinds.put(e.getKey(), e.getKey().endsWith(".json") ? "json" : "mcfunction");
            }
        }
        clearAndInit();
    }

    /** 保存：合并式解析更新 this.state（保留坐标），写回磁盘，再编译验证。 */
    private void doSave() {
        Map<String, String> files = collectFiles();
        String ns = state.getActiveDatapackNamespace();
        // 合并式：在 this.state 上更新，保留坐标与引用
        new TextToBlocksParser().parse(ns, files, this.state);
        // 写回磁盘（若有真实文件节点）
        saveActiveBufferToDisk();
        // 重新编译验证
        CompileResult result = new BlockCompiler().compile(state, reg);
        errors = result.errors();
        setStatus(result.success() ? "保存成功（编译通过）" : "已保存，但有 " + errors.size() + " 处编译错误");
        clearAndInit();
    }

    /** 一键重载：合并式保存后通过 ReloadService 路由。 */
    private void doReload() {
        // 合并式保存到 this.state
        Map<String, String> files = collectFiles();
        String ns = state.getActiveDatapackNamespace();
        new TextToBlocksParser().parse(ns, files, this.state);
        saveActiveBufferToDisk();
        MinecraftClient mc = MinecraftClient.getInstance();
        ReloadResult r = ReloadService.reload(state, mc);
        setStatus((r.success() ? "重载成功: " : "重载失败: ") + r.message());
        if (r.success()) {
            // 重载后刷新文件树（磁盘可能已变化）
            refreshFileTree();
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

    /** 在独立 Swing 窗口打开当前文件集（Task 5）。 */
    private void openDetached() {
        String ns = state.getActiveDatapackNamespace();
        if (ns == null || ns.isBlank()) {
            ns = "dpe";
        }
        Map<String, String> files = collectFiles();
        DetachedEditorWindow.create(ns, files, this::applyDetachedFiles);
        DatapackEditorClient.config().detachedWindowOpen = true;
        DatapackEditorClient.saveConfig();
        setStatus("已在独立窗口打开（保存即同步回游戏）");
    }

    private void toggleFullscreen() {
        if (window == null) {
            return;
        }
        window.toggleFullscreen(this.width, this.height);
        DatapackEditorClient.saveConfig();
        clearAndInit();
    }

    private void setStatus(String s) {
        statusMessage = s;
        statusTime = System.currentTimeMillis();
    }

    // ---------- 渲染 ----------

    /** 禁用默认半透明背景叠加（Task 1）。 */
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // 不绘制默认背景
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
            context.fill(0, 0, this.width, this.height, 0x80000000);
            context.enableScissor(wx, wyc, wx + ww, wyc + wh);
        }

        context.getMatrices().push();
        context.getMatrices().translate(wx, wyc, 0);

        // 背景
        context.fill(0, 0, ww, wh, 0xFF1E1E1E);
        // 文件树背景
        context.fill(0, TOP_BAR_H, FILE_TREE_W, wh - BOTTOM_BAR_H, 0xFF252526);
        // 编辑区背景
        context.fill(FILE_TREE_W, TOP_BAR_H + TAB_BAR_H, ww, wh - BOTTOM_BAR_H, 0xFF1E1E1E);

        super.render(context, lmx, lmy, delta);

        // 标题
        context.drawTextWithShadow(this.textRenderer, this.title, 4, 4, 0xFFFFFF);

        // 标签页头
        drawTabBar(context, lmx, lmy);
        // 文件树
        drawFileTree(context, lmx, lmy);
        // 编辑器
        drawEditor(context, lmx, lmy);

        // 错误面板
        if (showErrors && !errors.isEmpty()) {
            drawErrorsPanel(context, lmx, lmy);
        }

        // 补全浮层
        if (completionVisible && !completionCandidates.isEmpty()) {
            drawCompletion(context, lmx, lmy);
        }

        // 状态栏
        if (statusMessage != null) {
            long age = System.currentTimeMillis() - statusTime;
            if (age > 5000) {
                statusMessage = null;
            } else {
                context.drawTextWithShadow(this.textRenderer, Text.literal(statusMessage),
                        FILE_TREE_W + 4, wh - 14, 0x55FF55);
            }
        }
        // 行/列指示
        if (activeTab != null) {
            String pos = "行 " + (cursorLine + 1) + ", 列 " + (cursorCol + 1);
            context.drawTextWithShadow(this.textRenderer, Text.literal(pos),
                    ww - 100, wh - 14, 0xAAAAAA);
        }

        context.getMatrices().pop();

        if (!isFullscreen()) {
            context.disableScissor();
            drawWindowTitleBar(context, mouseX, mouseY);
        }
    }

    /** 绘制小窗标题栏（屏幕绝对坐标）。 */
    private void drawWindowTitleBar(DrawContext context, int mouseX, int mouseY) {
        int x = window.x;
        int y = window.y;
        int w = window.width;
        int h = window.height;
        context.fill(x, y, x + w, y + EditorWindow.TITLE_H, 0xFF2D2D2D);
        context.drawTextWithShadow(this.textRenderer, this.title, x + 6, y + 4, 0xFFFFFF);
        int fsX = x + w - 28;
        int clX = x + w - 14;
        boolean fsHover = mouseX >= fsX && mouseX < fsX + 13 && mouseY >= y + 1 && mouseY < y + 15;
        boolean clHover = mouseX >= clX && mouseX < clX + 12 && mouseY >= y + 1 && mouseY < y + 15;
        context.fill(fsX, y + 1, fsX + 13, y + 15, fsHover ? 0xFF555555 : 0xFF3A3A3A);
        context.drawTextWithShadow(this.textRenderer, Text.literal("▢"), fsX + 3, y + 3, 0xFFCCCCCC);
        context.fill(clX, y + 1, clX + 12, y + 15, clHover ? 0xFF884444 : 0xFF3A3A3A);
        context.drawTextWithShadow(this.textRenderer, Text.literal("x"), clX + 3, y + 3, 0xFFCCCCCC);
        context.drawBorder(x, y, w, h, 0xFF555555);
    }

    private void drawTabBar(DrawContext context, int mouseX, int mouseY) {
        int y = TOP_BAR_H;
        int x = FILE_TREE_W;
        for (String tab : openTabs) {
            String label = shortName(tab);
            int w = this.textRenderer.getWidth(label) + 16;
            boolean active = tab.equals(activeTab);
            int bg = active ? 0xFF1E1E1E : 0xFF2D2D2D;
            context.fill(x, y, x + w, y + TAB_BAR_H, bg);
            context.drawTextWithShadow(this.textRenderer, Text.literal(label),
                    x + 6, y + 4, active ? 0xFFFFFF : 0xAAAAAA);
            // 关闭标记
            context.drawTextWithShadow(this.textRenderer, Text.literal("x"),
                    x + w - 10, y + 4, 0xFF7777);
            x += w;
        }
    }

    private void drawFileTree(DrawContext context, int mouseX, int mouseY) {
        treeRows.clear();
        int wh = winHContent();
        int top = TOP_BAR_H + 4;
        // 底部操作栏占 ~52px，文件树可视区到此为止
        int bottom = wh - BOTTOM_BAR_H - 52;
        context.drawTextWithShadow(this.textRenderer, Text.literal("文件"), 4, top, 0xAAAAAA);
        int y = top + 12 - treeScroll;
        if (fileTree != null && !fileTree.datapacks().isEmpty()) {
            // 真实文件树：递归遍历数据包节点
            for (FileNode dp : fileTree.datapacks()) {
                y = drawTreeNode(context, mouseX, mouseY, dp, 0, y, top, bottom);
            }
        } else {
            // 回退：虚拟缓冲路径
            for (String path : buffers.keySet()) {
                if (y + TREE_ROW_H >= top && y < bottom) {
                    drawTreeRowBg(context, mouseX, mouseY, 0, y, path.equals(activeTab),
                            shortName(path), "json".equals(fileKinds.get(path)));
                }
                y += TREE_ROW_H;
            }
        }
    }

    /** 递归绘制一个文件树节点（含折叠展开）。返回下一行的 y。 */
    private int drawTreeNode(DrawContext context, int mouseX, int mouseY, FileNode node,
                             int depth, int y, int top, int bottom) {
        if (y + TREE_ROW_H >= top && y < bottom) {
            int indent = depth * 8;
            int x0 = 2 + indent;
            boolean active = node.path().equals(activeTab);
            boolean selected = node == selectedTreeNode;
            boolean hover = mouseX >= x0 && mouseX < FILE_TREE_W - 2
                    && mouseY >= y && mouseY < y + TREE_ROW_H;
            int bg = active ? 0xFF094771 : (selected ? 0xFF264F78 : (hover ? 0xFF37373D : 0xFF252526));
            context.fill(x0, y, FILE_TREE_W - 2, y + TREE_ROW_H, bg);
            // 折叠/展开标记（目录）
            String prefix = "";
            if (node.isDirectory()) {
                prefix = collapsedDirs.contains(node.path()) ? "[+] " : "[-] ";
            }
            String label = prefix + node.name();
            int color = node.isDirectory() ? 0xFF9CDCFE
                    : (node.name().endsWith(".json") ? 0xFFCC88FF : 0xFFD4D4D4);
            context.drawTextWithShadow(this.textRenderer, Text.literal(truncate(label, FILE_TREE_W - indent - 6)),
                    x0 + 2, y + 1, color);
        }
        treeRows.add(new TreeDisplayRow(node, depth, y));
        int ny = y + TREE_ROW_H;
        if (node.isDirectory() && !collapsedDirs.contains(node.path())) {
            for (FileNode child : node.children()) {
                if (ny >= bottom) {
                    break;
                }
                ny = drawTreeNode(context, mouseX, mouseY, child, depth + 1, ny, top, bottom);
            }
        }
        return ny;
    }

    /** 绘制回退虚拟缓冲行背景与文字。 */
    private void drawTreeRowBg(DrawContext context, int mouseX, int mouseY, int depth,
                               int y, boolean active, String label, boolean isJson) {
        int indent = depth * 8;
        int x0 = 2 + indent;
        boolean hover = mouseX >= x0 && mouseX < FILE_TREE_W - 2
                && mouseY >= y && mouseY < y + TREE_ROW_H;
        int bg = active ? 0xFF094771 : (hover ? 0xFF37373D : 0xFF252526);
        context.fill(x0, y, FILE_TREE_W - 2, y + TREE_ROW_H, bg);
        int color = isJson ? 0xFFCC88FF : 0xFFD4D4D4;
        context.drawTextWithShadow(this.textRenderer, Text.literal(truncate(label, FILE_TREE_W - indent - 6)),
                x0 + 2, y + 1, color);
    }

    private void drawEditor(DrawContext context, int mouseX, int mouseY) {
        int ww = winW();
        int wh = winHContent();
        if (activeTab == null) {
            context.drawTextWithShadow(this.textRenderer,
                    Text.literal("请从左侧选择一个文件").formatted(Formatting.ITALIC),
                    FILE_TREE_W + 8, TOP_BAR_H + TAB_BAR_H + 4, 0x888888);
            return;
        }
        List<StringBuilder> lines = buffers.get(activeTab);
        if (lines == null) {
            return;
        }
        String kind = fileKinds.getOrDefault(activeTab, "mcfunction");
        int editorX = FILE_TREE_W;
        int editorY = TOP_BAR_H + TAB_BAR_H;
        int editorW = ww - editorX;
        int editorH = wh - BOTTOM_BAR_H - editorY;
        int codeX = editorX + LINE_NUMBER_W + 4 - scrollX;
        int codeY = editorY - scrollY;

        // 行号背景
        context.fill(editorX, editorY, editorX + LINE_NUMBER_W, editorY + editorH, 0xFF1E1E1E);
        context.fill(editorX + LINE_NUMBER_W, editorY, editorX + LINE_NUMBER_W + 1, editorY + editorH, 0xFF333333);

        // 错误行集合
        java.util.Set<Integer> errorLines = new java.util.HashSet<>();
        for (ValidationError e : errors) {
            if (e.blockId() != null) {
                try {
                    int ln = Integer.parseInt(e.blockId());
                    errorLines.add(ln);
                } catch (NumberFormatException ignored) {
                    // 忽略
                }
            }
        }

        // 渲染每行
        int firstVisibleLine = Math.max(0, scrollY / LINE_H);
        int lastVisibleLine = Math.min(lines.size() - 1, (scrollY + editorH) / LINE_H + 1);
        for (int i = firstVisibleLine; i <= lastVisibleLine; i++) {
            int ly = codeY + i * LINE_H;
            if (ly + LINE_H < editorY || ly > editorY + editorH) {
                continue;
            }
            // 行号
            String numStr = String.valueOf(i + 1);
            int numColor = errorLines.contains(i + 1) ? 0xFFFF5555 : 0xFF858585;
            context.drawTextWithShadow(this.textRenderer, Text.literal(numStr),
                    editorX + LINE_NUMBER_W - 4 - this.textRenderer.getWidth(numStr), ly + 1, numColor);
            // 行背景（错误行）
            if (errorLines.contains(i + 1)) {
                context.fill(editorX + LINE_NUMBER_W + 1, ly, ww, ly + LINE_H, 0x55FF0000);
            }
            // 文本（按语法高亮）
            String line = lines.get(i).toString();
            drawHighlightedLine(context, line, codeX, ly, kind, editorX + LINE_NUMBER_W + 4,
                    editorX + editorW);
        }

        // 光标
        if (cursorLine >= 0 && cursorLine < lines.size()) {
            int cl = Math.min(cursorCol, lines.get(cursorLine).length());
            int cy = codeY + cursorLine * LINE_H;
            int cx = codeX + this.textRenderer.getWidth(lines.get(cursorLine).substring(0, cl));
            // 闪烁（按时间）
            if ((System.currentTimeMillis() / 500) % 2 == 0) {
                context.fill(cx, cy + 1, cx + 1, cy + LINE_H - 1, 0xFFCCCCCC);
            }
        }

        // 边框
        context.drawBorder(editorX, editorY, editorW, editorH, 0xFF333333);
    }

    /** 简单按 token 着色一行。 */
    private void drawHighlightedLine(DrawContext context, String line, int x, int y, String kind,
                                     int clipX, int clipRight) {
        if (line == null || line.isEmpty()) {
            return;
        }
        if ("json".equals(kind)) {
            drawJsonLine(context, line, x, y, clipX, clipRight);
        } else {
            drawMcFunctionLine(context, line, x, y, clipX, clipRight);
        }
    }

    /** mcfunction：注释灰、命令名蓝、参数白。 */
    private void drawMcFunctionLine(DrawContext context, String line, int x, int y,
                                    int clipX, int clipRight) {
        if (line.startsWith("#")) {
            drawClipped(context, line, x, y, 0xFF6A9955, clipX, clipRight);
            return;
        }
        String[] parts = line.split(" ", 2);
        String cmd = parts[0];
        String rest = parts.length > 1 ? parts[1] : "";
        int cx = x;
        cx = drawClipped(context, cmd, cx, y, 0xFF569CD6, clipX, clipRight);
        if (!rest.isEmpty()) {
            cx = drawClipped(context, " ", cx, y, 0xFFD4D4D4, clipX, clipRight);
            // 参数中 minecraft:xxx 着色为浅绿
            String[] tokens = rest.split(" ");
            for (int i = 0; i < tokens.length; i++) {
                int tColor = tokens[i].startsWith("minecraft:") || tokens[i].startsWith("#")
                        ? 0xFF6A9955 : 0xFFD4D4D4;
                cx = drawClipped(context, tokens[i], cx, y, tColor, clipX, clipRight);
                if (i < tokens.length - 1) {
                    cx = drawClipped(context, " ", cx, y, 0xFFD4D4D4, clipX, clipRight);
                }
            }
        }
    }

    /** JSON：键名金、字符串绿、数字青、标点灰。 */
    private void drawJsonLine(DrawContext context, String line, int x, int y,
                              int clipX, int clipRight) {
        int cx = x;
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (Character.isWhitespace(c)) {
                cx = drawClipped(context, String.valueOf(c), cx, y, 0xFFD4D4D4, clipX, clipRight);
                i++;
                continue;
            }
            if (c == '"') {
                int start = i;
                i++;
                while (i < line.length()) {
                    if (line.charAt(i) == '\\') {
                        i += 2;
                        continue;
                    }
                    if (line.charAt(i) == '"') {
                        i++;
                        break;
                    }
                    i++;
                }
                String str = line.substring(start, Math.min(i, line.length()));
                int color = 0xFFCE9178;
                // 看后面紧跟的是否为 ':' -> 键名
                int j = i;
                while (j < line.length() && Character.isWhitespace(line.charAt(j))) {
                    j++;
                }
                if (j < line.length() && line.charAt(j) == ':') {
                    color = 0xFFFFD700;
                }
                cx = drawClipped(context, str, cx, y, color, clipX, clipRight);
                continue;
            }
            if (Character.isDigit(c) || (c == '-' && i + 1 < line.length() && Character.isDigit(line.charAt(i + 1)))) {
                int start = i;
                while (i < line.length() && (Character.isDigit(line.charAt(i)) || line.charAt(i) == '.'
                        || line.charAt(i) == '-' || line.charAt(i) == 'e' || line.charAt(i) == 'E')) {
                    i++;
                }
                String num = line.substring(start, i);
                cx = drawClipped(context, num, cx, y, 0xFFB5CEA8, clipX, clipRight);
                continue;
            }
            if (c == '{' || c == '}' || c == '[' || c == ']' || c == ':' || c == ',') {
                cx = drawClipped(context, String.valueOf(c), cx, y, 0xFF808080, clipX, clipRight);
                i++;
                continue;
            }
            // 标识符（true/false/null）
            if (Character.isLetter(c)) {
                int start = i;
                while (i < line.length() && Character.isLetter(line.charAt(i))) {
                    i++;
                }
                String id = line.substring(start, i);
                int color = (id.equals("true") || id.equals("false") || id.equals("null"))
                        ? 0xFF569CD6 : 0xFF9CDCFE;
                cx = drawClipped(context, id, cx, y, color, clipX, clipRight);
                continue;
            }
            cx = drawClipped(context, String.valueOf(c), cx, y, 0xFFD4D4D4, clipX, clipRight);
            i++;
        }
    }

    /** 渲染文本（不裁剪，简单按 x 起始；超出右边界的字符仍绘制但可被边框遮挡）。 */
    private int drawClipped(DrawContext context, String s, int x, int y, int color,
                            int clipX, int clipRight) {
        if (s == null || s.isEmpty()) {
            return x;
        }
        // 仅当 x 在可视范围才绘制（避免太长行性能问题）
        if (x > clipRight || x + this.textRenderer.getWidth(s) < clipX) {
            return x + this.textRenderer.getWidth(s);
        }
        context.drawTextWithShadow(this.textRenderer, Text.literal(s), x, y, color);
        return x + this.textRenderer.getWidth(s);
    }

    /** 错误面板（右下角悬浮）。 */
    private void drawErrorsPanel(DrawContext context, int mouseX, int mouseY) {
        int ww = winW();
        int wh = winHContent();
        int pw = Math.min(420, ww - FILE_TREE_W - 20);
        int ph = Math.min(160, wh / 3);
        int px = ww - pw - 8;
        int py = wh - BOTTOM_BAR_H - ph - 4;
        context.fill(px, py, px + pw, py + ph, 0xE8000000);
        context.drawBorder(px, py, pw, ph, 0xFFFF5555);
        context.drawTextWithShadow(this.textRenderer,
                Text.literal("编译错误 (" + errors.size() + ")").formatted(Formatting.RED),
                px + 4, py + 4, 0xFFFF5555);
        int lineY = py + 16;
        for (ValidationError e : errors) {
            if (lineY > py + ph - 8) {
                break;
            }
            String msg = e.friendlyMessage();
            if (msg == null || msg.isBlank()) {
                msg = e.message();
            }
            String line = "[" + (e.blockId() == null ? "?" : e.blockId()) + "] " + msg;
            context.drawTextWithShadow(this.textRenderer, Text.literal(truncate(line, pw - 8)),
                    px + 4, lineY, 0xFFAAAAAA);
            lineY += 10;
        }
    }

    /** 补全浮层：独立渲染、位置智能调整、高亮选择、详情面板。 */
    private void drawCompletion(DrawContext context, int mouseX, int mouseY) {
        if (activeTab == null || cursorLine < 0 || completionCandidates.isEmpty()) {
            return;
        }
        List<StringBuilder> lines = buffers.get(activeTab);
        if (lines == null || cursorLine >= lines.size()) {
            return;
        }

        int editorX = FILE_TREE_W + LINE_NUMBER_W + 4;
        int editorY = TOP_BAR_H + TAB_BAR_H;

        String curLine = lines.get(cursorLine).toString();
        int cursorPixelX = editorX + this.textRenderer.getWidth(curLine.substring(0, Math.min(cursorCol, curLine.length()))) - scrollX;
        int cursorPixelY = editorY + (cursorLine + 1) * LINE_H - scrollY;

        int panelWidth = COMPLETION_PANEL_W;
        int itemCount = Math.min(completionCandidates.size(), 8);
        int panelHeight = itemCount * COMPLETION_PANEL_H + 20;

        int[] pos = calculateCompletionPosition(cursorPixelX, cursorPixelY, panelWidth, panelHeight);
        int panelX = pos[0];
        int panelY = pos[1];

        completionPanelX = panelX;
        completionPanelY = panelY;

        int visibleStart = Math.max(0, completionScrollOffset);
        int visibleEnd = Math.min(completionCandidates.size(), visibleStart + 8);

        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 400);

        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, COMPLETION_BG);
        context.drawBorder(panelX, panelY, panelWidth, panelHeight, COMPLETION_BORDER);

        int indexDisplayY = panelY + 2;
        String indexStr = (completionIndex + 1) + "/" + completionCandidates.size();
        context.drawTextWithShadow(this.textRenderer, Text.literal(indexStr),
                panelX + panelWidth - this.textRenderer.getWidth(indexStr) - 4, indexDisplayY, 0xFF888888);

        for (int i = visibleStart; i < visibleEnd; i++) {
            CompletionCandidate c = completionCandidates.get(i);
            int ry = panelY + 14 + (i - visibleStart) * COMPLETION_PANEL_H;
            boolean isSelected = (i == completionIndex);

            if (isSelected) {
                context.fill(panelX + 1, ry - 1, panelX + panelWidth - 1, ry + COMPLETION_PANEL_H - 1, COMPLETION_SELECTED_BG);
            }

            String label = c.label();
            int labelColor = isSelected ? 0xFFFFFFFF : 0xFFE0E0E0;
            context.drawTextWithShadow(this.textRenderer, Text.literal(truncate(label, 130)),
                    panelX + 6, ry + 2, labelColor);

            if (c.kind() != null) {
                String kindStr = c.kind();
                int kindColor = kindStr.contains("Function") ? 0xFF569CD6 :
                               kindStr.contains("Keyword") ? 0xFFC586C0 :
                               kindStr.contains("Snippet") ? 0xFFCE9178 : 0xFF6A9955;
                context.drawTextWithShadow(this.textRenderer, Text.literal(kindStr),
                        panelX + panelWidth - this.textRenderer.getWidth(kindStr) - 6, ry + 2, kindColor);
            }
        }

        if (completionIndex >= 0 && completionIndex < completionCandidates.size()) {
            CompletionCandidate selected = completionCandidates.get(completionIndex);
            drawCompletionDetail(context, panelX, panelY, panelWidth, panelHeight, selected);
        }

        context.getMatrices().pop();
    }

    private void drawCompletionDetail(DrawContext context, int panelX, int panelY, int panelWidth, int panelHeight, CompletionCandidate candidate) {
        int detailX = panelX + panelWidth + 4;
        int detailY = panelY;

        int screenWidth = MinecraftClient.getInstance().getWindow().getScaledWidth();
        if (detailX + COMPLETION_DETAIL_W > screenWidth) {
            detailX = panelX - COMPLETION_DETAIL_W - 4;
        }

        if (detailX < 0) {
            detailX = panelX;
            detailY = panelY + panelHeight + 4;
        }

        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 401);

        context.fill(detailX, detailY, detailX + COMPLETION_DETAIL_W, detailY + COMPLETION_DETAIL_H, COMPLETION_DETAIL_BG);
        context.drawBorder(detailX, detailY, COMPLETION_DETAIL_W, COMPLETION_DETAIL_H, COMPLETION_BORDER);

        int textX = detailX + 8;
        int textY = detailY + 8;
        int maxTextWidth = COMPLETION_DETAIL_W - 16;

        String name = candidate.label();
        if (name != null && !name.isEmpty()) {
            context.drawTextWithShadow(this.textRenderer, Text.literal(truncate(name, maxTextWidth / 6)),
                    textX, textY, 0xFF4488FF);
            textY += 14;
        }

        if (candidate.detail() != null && !candidate.detail().isEmpty()) {
            String detail = candidate.detail();
            String[] detailLines = wrapText(detail, maxTextWidth);
            for (String line : detailLines) {
                if (textY > detailY + COMPLETION_DETAIL_H - 12) break;
                context.drawTextWithShadow(this.textRenderer, Text.literal(line),
                        textX, textY, 0xFFAAAAAA);
                textY += 11;
            }
            textY += 4;
        }

        if (candidate.insertText() != null && !candidate.insertText().isEmpty()) {
            context.drawTextWithShadow(this.textRenderer, Text.literal("示例:"),
                    textX, textY, 0xFF6A9955);
            textY += 12;

            String example = candidate.insertText();
            String[] exampleLines = wrapText(example, maxTextWidth - 10);
            for (String line : exampleLines) {
                if (textY > detailY + COMPLETION_DETAIL_H - 12) break;
                context.drawTextWithShadow(this.textRenderer, Text.literal("  " + line),
                        textX, textY, 0xFFCE9178);
                textY += 11;
            }
        }

        context.getMatrices().pop();
    }

    private String[] wrapText(String text, int maxPixelWidth) {
        if (text == null || text.isEmpty()) {
            return new String[0];
        }
        List<String> lines = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();

        for (char c : text.toCharArray()) {
            String testLine = currentLine.toString() + c;
            if (this.textRenderer.getWidth(testLine) > maxPixelWidth && currentLine.length() > 0) {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder();
            }
            currentLine.append(c);
        }
        if (currentLine.length() > 0) {
            lines.add(currentLine.toString());
        }

        return lines.toArray(new String[0]);
    }

    private String truncate(String s, int maxPixelWidth) {
        if (s == null) {
            return "";
        }
        if (maxPixelWidth <= 4) {
            return "";
        }
        if (this.textRenderer.getWidth(s) <= maxPixelWidth) {
            return s;
        }
        int i = s.length() - 1;
        while (i > 0 && this.textRenderer.getWidth(s.substring(0, i)) + this.textRenderer.getWidth("...") > maxPixelWidth) {
            i--;
        }
        return s.substring(0, Math.max(0, i)) + "...";
    }

    private static String shortName(String path) {
        if (path == null) {
            return "";
        }
        int idx = path.lastIndexOf('/');
        return idx < 0 ? path : path.substring(idx + 1);
    }

    // ---------- 编辑交互 ----------

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (activeTab == null) {
            return false;
        }
        List<StringBuilder> lines = buffers.get(activeTab);
        if (lines == null || cursorLine < 0 || cursorLine >= lines.size()) {
            return false;
        }
        StringBuilder sb = lines.get(cursorLine);
        int cl = Math.min(cursorCol, sb.length());
        sb.insert(cl, chr);
        cursorCol = cl + 1;
        refreshCompletion();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_F11) {
            toggleFullscreen();
            return true;
        }
        // 补全优先：Tab / Enter 插入选中候选
        if (completionVisible && !completionCandidates.isEmpty()) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB
                    || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) {
                applyCompletion();
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN) {
                if ((modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL) != 0) {
                    completionScrollOffset = Math.min(completionScrollOffset + 3, Math.max(0, completionCandidates.size() - 8));
                    completionIndex = Math.min(completionIndex + 3, completionCandidates.size() - 1);
                } else {
                    completionIndex = (completionIndex + 1) % completionCandidates.size();
                    if (completionIndex >= completionScrollOffset + 8) {
                        completionScrollOffset = completionIndex - 7;
                    }
                }
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_UP) {
                if ((modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL) != 0) {
                    completionScrollOffset = Math.max(0, completionScrollOffset - 3);
                    completionIndex = Math.max(0, completionIndex - 3);
                } else {
                    completionIndex = (completionIndex - 1 + completionCandidates.size()) % completionCandidates.size();
                    if (completionIndex < completionScrollOffset) {
                        completionScrollOffset = Math.max(0, completionIndex);
                    }
                }
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN) {
                completionIndex = Math.min(completionIndex + 5, completionCandidates.size() - 1);
                if (completionIndex >= completionScrollOffset + 8) {
                    completionScrollOffset = Math.min(completionScrollOffset + 5, Math.max(0, completionCandidates.size() - 8));
                }
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP) {
                completionIndex = Math.max(0, completionIndex - 5);
                if (completionIndex < completionScrollOffset) {
                    completionScrollOffset = Math.max(0, completionScrollOffset - 5);
                }
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_HOME) {
                completionIndex = 0;
                completionScrollOffset = 0;
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_END) {
                completionIndex = completionCandidates.size() - 1;
                completionScrollOffset = Math.max(0, completionCandidates.size() - 8);
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                completionVisible = false;
                return true;
            }
        }

        // 快捷键：Ctrl+M 切到积木，Ctrl+R 重载，Ctrl+S 保存
        // （在文本编辑器中要求 Ctrl 修饰，避免与字符输入冲突）
        if ((modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL) != 0) {
            int switchKey = DatapackEditorClient.config().keyBindings.switchMode;
            int reloadKey = DatapackEditorClient.config().keyBindings.reload;
            int saveKey = DatapackEditorClient.config().keyBindings.save;
            if (keyCode == switchKey) {
                switchToBlocks();
                return true;
            }
            if (keyCode == reloadKey) {
                doReload();
                return true;
            }
            if (keyCode == saveKey) {
                doSave();
                return true;
            }
        }

        if (activeTab == null) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
        List<StringBuilder> lines = buffers.get(activeTab);
        if (lines == null) {
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        switch (keyCode) {
            case org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT -> {
                if (cursorCol > 0) {
                    cursorCol--;
                } else if (cursorLine > 0) {
                    cursorLine--;
                    cursorCol = lines.get(cursorLine).length();
                }
                completionVisible = false;
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT -> {
                if (cursorCol < lines.get(cursorLine).length()) {
                    cursorCol++;
                } else if (cursorLine < lines.size() - 1) {
                    cursorLine++;
                    cursorCol = 0;
                }
                completionVisible = false;
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_UP -> {
                if (cursorLine > 0) {
                    cursorLine--;
                    cursorCol = Math.min(cursorCol, lines.get(cursorLine).length());
                }
                completionVisible = false;
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN -> {
                if (cursorLine < lines.size() - 1) {
                    cursorLine++;
                    cursorCol = Math.min(cursorCol, lines.get(cursorLine).length());
                }
                completionVisible = false;
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_HOME -> {
                cursorCol = 0;
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_END -> {
                cursorCol = lines.get(cursorLine).length();
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE -> {
                if (cursorCol > 0) {
                    lines.get(cursorLine).deleteCharAt(cursorCol - 1);
                    cursorCol--;
                } else if (cursorLine > 0) {
                    String cur = lines.get(cursorLine).toString();
                    lines.remove(cursorLine);
                    cursorLine--;
                    cursorCol = lines.get(cursorLine).length();
                    lines.get(cursorLine).append(cur);
                }
                refreshCompletion();
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_DELETE -> {
                if (cursorCol < lines.get(cursorLine).length()) {
                    lines.get(cursorLine).deleteCharAt(cursorCol);
                } else if (cursorLine < lines.size() - 1) {
                    String next = lines.get(cursorLine + 1).toString();
                    lines.remove(cursorLine + 1);
                    lines.get(cursorLine).append(next);
                }
                refreshCompletion();
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER -> {
                String cur = lines.get(cursorLine).toString();
                String after = cursorCol < cur.length() ? cur.substring(cursorCol) : "";
                if (cursorCol < cur.length()) {
                    lines.get(cursorLine).delete(cursorCol, cur.length());
                }
                lines.add(cursorLine + 1, new StringBuilder(after));
                cursorLine++;
                cursorCol = 0;
                completionVisible = false;
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP -> {
                cursorLine = Math.max(0, cursorLine - 10);
                cursorCol = Math.min(cursorCol, lines.get(cursorLine).length());
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN -> {
                cursorLine = Math.min(lines.size() - 1, cursorLine + 10);
                cursorCol = Math.min(cursorCol, lines.get(cursorLine).length());
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** 应用当前选中的补全候选。 */
    private void applyCompletion() {
        if (completionCandidates.isEmpty() || completionIndex < 0
                || completionIndex >= completionCandidates.size()) {
            completionVisible = false;
            return;
        }
        CompletionCandidate c = completionCandidates.get(completionIndex);
        String insert = c.insertText();
        if (insert == null) {
            insert = c.label();
        }
        List<StringBuilder> lines = buffers.get(activeTab);
        if (lines == null || cursorLine >= lines.size()) {
            completionVisible = false;
            return;
        }
        StringBuilder sb = lines.get(cursorLine);
        // 替换当前光标前的"单词"
        int wordStart = findWordStart(sb, cursorCol);
        sb.delete(wordStart, Math.min(cursorCol, sb.length()));
        sb.insert(wordStart, insert);
        cursorCol = wordStart + insert.length();
        completionVisible = false;
    }

    /** 找光标左侧的单词起点（用于补全替换）。 */
    private static int findWordStart(StringBuilder sb, int col) {
        int i = Math.min(col, sb.length()) - 1;
        while (i >= 0) {
            char c = sb.charAt(i);
            if (Character.isWhitespace(c)) {
                break;
            }
            i--;
        }
        return i + 1;
    }

    /** 刷新当前行的补全候选。 */
    private void refreshCompletion() {
        if (activeTab == null) {
            completionVisible = false;
            return;
        }
        List<StringBuilder> lines = buffers.get(activeTab);
        if (lines == null || cursorLine < 0 || cursorLine >= lines.size()) {
            completionVisible = false;
            return;
        }
        String line = lines.get(cursorLine).toString();
        int cursor = Math.min(cursorCol, line.length());
        String ns = state.getActiveDatapackNamespace();
        String kind = fileKinds.getOrDefault(activeTab, "mcfunction");
        String commandContext = "json".equals(kind) ? "text_component" : "function";
        CompletionContext ctx = new CompletionContext(line, cursor, ns, (Datapack) null, commandContext);
        try {
            completionCandidates = completionService.complete(ctx);
        } catch (Exception e) {
            completionCandidates = List.of();
        }
        completionIndex = 0;
        completionScrollOffset = 0;
        completionVisible = !completionCandidates.isEmpty();
    }

    private void clampCursor() {
        List<StringBuilder> lines = activeTab == null ? null : buffers.get(activeTab);
        if (lines == null) {
            return;
        }
        if (cursorLine < 0) {
            cursorLine = 0;
        }
        if (cursorLine >= lines.size()) {
            cursorLine = lines.size() - 1;
        }
        if (cursorCol < 0) {
            cursorCol = 0;
        }
        int len = lines.get(cursorLine).length();
        if (cursorCol > len) {
            cursorCol = len;
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int ww = winW();
        int wh = winHContent();
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
            }
            if (window.mouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        double lmx = mouseX - winX();
        double lmy = mouseY - winYContent();
        if (super.mouseClicked(lmx, lmy, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        // 标签页头点击
        if (lmy >= TOP_BAR_H && lmy < TOP_BAR_H + TAB_BAR_H && lmx >= FILE_TREE_W) {
            int x = FILE_TREE_W;
            for (String tab : openTabs) {
                int w = this.textRenderer.getWidth(shortName(tab)) + 16;
                if (lmx >= x && lmx < x + w) {
                    // 关闭按钮 x 区
                    if (lmx >= x + w - 12) {
                        closeTab(tab);
                    } else {
                        saveCursor();
                        activeTab = tab;
                        restoreCursor();
                    }
                    return true;
                }
                x += w;
            }
        }
        // 文件树点击（真实 FileNode 或虚拟缓冲）
        if (lmx >= 0 && lmx < FILE_TREE_W && lmy >= TOP_BAR_H && lmy < wh - BOTTOM_BAR_H) {
            // 真实文件树：用渲染行缓存命中
            for (TreeDisplayRow row : treeRows) {
                if (lmy >= row.y() && lmy < row.y() + TREE_ROW_H) {
                    FileNode node = row.node();
                    selectedTreeNode = node;
                    if (node.isDirectory()) {
                        // 点击目录：切换折叠
                        if (collapsedDirs.contains(node.path())) {
                            collapsedDirs.remove(node.path());
                        } else {
                            collapsedDirs.add(node.path());
                        }
                    } else {
                        // 点击文件：读入缓冲并打开标签
                        openFileNode(node);
                    }
                    return true;
                }
            }
            // 回退虚拟缓冲：按 buffers 顺序命中
            if (fileTree == null || fileTree.datapacks().isEmpty()) {
                int y = TOP_BAR_H + 4 + 12;
                for (String path : buffers.keySet()) {
                    if (lmy >= y && lmy < y + TREE_ROW_H) {
                        saveCursor();
                        activeTab = path;
                        if (!openTabs.contains(path)) {
                            openTabs.add(path);
                        }
                        restoreCursor();
                        return true;
                    }
                    y += TREE_ROW_H;
                }
            }
        }
        // 编辑器点击：定位光标
        if (completionVisible && completionPanelX > 0 && completionPanelY > 0) {
            int panelWidth = COMPLETION_PANEL_W;
            int itemCount = Math.min(completionCandidates.size(), 8);
            int panelHeight = itemCount * COMPLETION_PANEL_H + 20;
            if (lmx >= completionPanelX && lmx < completionPanelX + panelWidth &&
                lmy >= completionPanelY && lmy < completionPanelY + panelHeight) {
                int clickedIndex = completionScrollOffset + (int)((lmy - completionPanelY - 14) / COMPLETION_PANEL_H);
                if (clickedIndex >= 0 && clickedIndex < completionCandidates.size()) {
                    completionIndex = clickedIndex;
                    applyCompletion();
                    return true;
                }
            }
        }

        int editorX = FILE_TREE_W + LINE_NUMBER_W + 4;
        int editorY = TOP_BAR_H + TAB_BAR_H;
        if (lmx >= editorX && lmx < ww && lmy >= editorY && lmy < wh - BOTTOM_BAR_H) {
            int line = (int) ((lmy - editorY + scrollY) / LINE_H);
            List<StringBuilder> lines = activeTab == null ? null : buffers.get(activeTab);
            if (lines != null && line >= 0 && line < lines.size()) {
                cursorLine = line;
                String l = lines.get(line).toString();
                // 用字符宽度估算列
                int rel = (int) (lmx - editorX + scrollX);
                int col = 0;
                int acc = 0;
                while (col < l.length()) {
                    int cw = this.textRenderer.getWidth(String.valueOf(l.charAt(col)));
                    if (acc + cw / 2 >= rel) {
                        break;
                    }
                    acc += cw;
                    col++;
                }
                cursorCol = col;
                completionVisible = false;
                return true;
            }
        }
        return false;
    }

    /** 关闭一个标签。 */
    private void closeTab(String tab) {
        int idx = openTabs.indexOf(tab);
        if (idx < 0) {
            return;
        }
        openTabs.remove(idx);
        cursors.remove(tab);
        scrolls.remove(tab);
        if (tab.equals(activeTab)) {
            if (openTabs.isEmpty()) {
                activeTab = null;
            } else {
                activeTab = openTabs.get(Math.max(0, idx - 1));
                restoreCursor();
            }
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (!isFullscreen() && window != null && window.mouseDragged(mouseX, mouseY, button, deltaX, deltaY)) {
            if (window.resized) {
                window.resized = false;
                clearAndInit();
            }
            return true;
        }
        double lmx = mouseX - winX();
        double lmy = mouseY - winYContent();
        return super.mouseDragged(lmx, lmy, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (!isFullscreen() && window != null && window.mouseReleased(mouseX, mouseY, button)) {
            clearAndInit();
            return true;
        }
        double lmx = mouseX - winX();
        double lmy = mouseY - winYContent();
        return super.mouseReleased(lmx, lmy, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        double lmx = mouseX - winX();
        double lmy = mouseY - winYContent();
        int ww = winW();
        int wh = winHContent();
        int editorX = FILE_TREE_W;
        int editorY = TOP_BAR_H + TAB_BAR_H;
        // 文件树滚动（Task 6）
        if (lmx >= 0 && lmx < FILE_TREE_W && lmy >= TOP_BAR_H && lmy < wh - BOTTOM_BAR_H) {
            treeScroll -= (int) (verticalAmount * TREE_ROW_H * 2);
            if (treeScroll < 0) {
                treeScroll = 0;
            }
            return true;
        }
        if (lmx >= editorX && lmx < ww && lmy >= editorY && lmy < wh - BOTTOM_BAR_H) {
            scrollY -= (int) (verticalAmount * LINE_H * 3);
            if (scrollY < 0) {
                scrollY = 0;
            }
            return true;
        }
        return super.mouseScrolled(lmx, lmy, horizontalAmount, verticalAmount);
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        if (window != null && !window.fullscreen) {
            window.clampToScreen(width, height);
        }
        super.resize(client, width, height);
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
}
