package com.dpe.client;

import com.dpe.common.manual.BuiltinManual;
import com.dpe.common.manual.ManualCategory;
import com.dpe.common.manual.ManualEntry;
import com.dpe.common.manual.ManualSearcher;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.ArrayList;
import java.util.List;

/**
 * 内置手册浏览屏幕（Task 6）：
 * 搜索框 + 分类筛选 + 条目列表（可滚动）+ 详情面板 + 在线查询 Wiki。
 */
public class ManualScreen extends Screen {

    private static final int LIST_W = 200;
    private static final int TOP_BAR_H = 28;
    private static final int ENTRY_H = 14;
    private static final int PADDING = 6;

    private final ManualSearcher searcher = new ManualSearcher();
    private final Screen parent;

    private TextFieldWidget searchField;
    private ManualCategory currentCategory = null; // null = 全部
    private List<ManualEntry> visibleEntries = new ArrayList<>();
    private ManualEntry selected = null;
    private int scrollOffset = 0;

    private String wikiResult = null;
    private boolean wikiFetching = false;
    private String wikiQuery = null;

    public ManualScreen(Screen parent) {
        super(Text.literal("Datapack 手册"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        // 搜索框
        searchField = new TextFieldWidget(this.textRenderer, 6, 4, 220, 18, Text.literal("搜索..."));
        searchField.setMaxLength(256);
        searchField.setPlaceholder(Text.literal("搜索命令/方块/物品/实体..."));
        searchField.setChangedListener(s -> refreshEntries());
        addDrawableChild(searchField);

        // 分类按钮：全部 + 各类
        int cx = 232;
        addDrawableChild(ButtonWidget.builder(Text.literal("全部"), b -> {
                    currentCategory = null;
                    refreshEntries();
                })
                .dimensions(cx, 4, 50, 18).build());
        cx += 52;
        for (ManualCategory cat : ManualCategory.values()) {
            String label = categoryLabel(cat);
            addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> {
                        currentCategory = cat;
                        refreshEntries();
                    })
                    .dimensions(cx, 4, 50, 18).build());
            cx += 52;
        }

        // 顶部右侧按钮
        int rx = this.width - 156;
        addDrawableChild(ButtonWidget.builder(Text.literal("在线查询 Wiki"), b -> fetchWiki())
                .dimensions(rx, 4, 100, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("返回"), b -> close())
                .dimensions(rx + 104, 4, 50, 18).build());

        refreshEntries();
    }

    /** 刷新当前可见条目列表。 */
    private void refreshEntries() {
        String q = searchField == null ? "" : searchField.getText();
        if (currentCategory == null) {
            visibleEntries = new ArrayList<>(searcher.search(q, 200));
        } else {
            List<ManualEntry> byCat = searcher.byCategory(currentCategory);
            if (q == null || q.isBlank()) {
                visibleEntries = new ArrayList<>(byCat);
            } else {
                visibleEntries = new ArrayList<>();
                String ql = q.toLowerCase(java.util.Locale.ROOT);
                for (ManualEntry e : byCat) {
                    if (matches(e, ql)) {
                        visibleEntries.add(e);
                    }
                }
            }
        }
        // 维持选中
        if (selected != null) {
            boolean found = false;
            for (ManualEntry e : visibleEntries) {
                if (e.id().equals(selected.id())) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                selected = null;
            }
        }
        // 修正滚动偏移
        clampScroll();
    }

    private boolean matches(ManualEntry e, String ql) {
        if (e.title() != null && e.title().toLowerCase(java.util.Locale.ROOT).contains(ql)) {
            return true;
        }
        if (e.description() != null && e.description().toLowerCase(java.util.Locale.ROOT).contains(ql)) {
            return true;
        }
        for (String k : e.keywords()) {
            if (k != null && k.toLowerCase(java.util.Locale.ROOT).contains(ql)) {
                return true;
            }
        }
        return e.id() != null && e.id().toLowerCase(java.util.Locale.ROOT).contains(ql);
    }

    private void clampScroll() {
        int listH = this.height - TOP_BAR_H - 8;
        int totalH = visibleEntries.size() * ENTRY_H;
        int maxScroll = Math.max(0, totalH - listH);
        if (scrollOffset < 0) {
            scrollOffset = 0;
        }
        if (scrollOffset > maxScroll) {
            scrollOffset = maxScroll;
        }
    }

    private static String categoryLabel(ManualCategory cat) {
        return switch (cat) {
            case COMMAND -> "命令";
            case BLOCK -> "方块";
            case ITEM -> "物品";
            case ENTITY -> "实体";
            case TAG -> "标签";
            case NBT -> "NBT";
            case TEXT_COMPONENT -> "文本组件";
        };
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // 背景
        context.fill(0, 0, this.width, this.height, 0xFF1E1E1E);
        // 列表区背景
        context.fill(4, TOP_BAR_H, 4 + LIST_W, this.height - 4, 0xFF262626);
        // 详情区背景
        context.fill(8 + LIST_W, TOP_BAR_H, this.width - 4, this.height - 4, 0xFF222222);

        super.render(context, mouseX, mouseY, delta);

        // 列表头
        context.drawTextWithShadow(this.textRenderer, Text.literal("条目 (" + visibleEntries.size() + ")"),
                8, TOP_BAR_H + 2, 0xFFAAAAAA);

        // 条目列表（带滚动）
        int listTop = TOP_BAR_H + 14;
        int listH = this.height - TOP_BAR_H - 22;
        int listBottom = listTop + listH;
        int listLeft = 6;
        int row = 0;
        for (ManualEntry e : visibleEntries) {
            int ey = listTop + row * ENTRY_H - scrollOffset;
            if (ey + ENTRY_H < listTop || ey > listBottom) {
                row++;
                continue;
            }
            boolean sel = selected != null && e.id().equals(selected.id());
            if (sel) {
                context.fill(listLeft, ey - 1, listLeft + LIST_W - 4, ey + ENTRY_H - 1, 0xFF3A5F8F);
            } else if (mouseX >= listLeft && mouseX < listLeft + LIST_W - 4
                    && mouseY >= ey - 1 && mouseY < ey + ENTRY_H - 1) {
                context.fill(listLeft, ey - 1, listLeft + LIST_W - 4, ey + ENTRY_H - 1, 0xFF333333);
            }
            // 分类色点
            int dotColor = categoryColor(e.category());
            context.fill(listLeft + 2, ey + 2, listLeft + 6, ey + 8, dotColor);
            String title = e.title();
            if (title == null) {
                title = e.id();
            }
            context.drawTextWithShadow(this.textRenderer, Text.literal(truncate(title, LIST_W - 16)),
                    listLeft + 9, ey + 1, 0xFFEEEEEE);
            row++;
        }

        // 滚动条
        int totalH = visibleEntries.size() * ENTRY_H;
        if (totalH > listH) {
            int barH = Math.max(20, listH * listH / totalH);
            int barY = listTop + (int) ((long) scrollOffset * (listH - barH) / Math.max(1, totalH - listH));
            context.fill(4 + LIST_W - 4, barY, 4 + LIST_W - 2, barY + barH, 0xFF666666);
        }

        // 详情面板
        int dx = 8 + LIST_W + 6;
        int dy = TOP_BAR_H + 4;
        if (selected == null) {
            context.drawTextWithShadow(this.textRenderer,
                    Text.literal("请在左侧选择一个条目查看详情").formatted(Formatting.ITALIC),
                    dx, dy, 0xFF999999);
        } else {
            drawDetail(context, dx, dy, selected);
        }

        // Wiki 查询结果（叠加在详情底部）
        if (wikiResult != null || wikiFetching) {
            int wy = this.height - 96;
            context.fill(dx, wy, this.width - 8, this.height - 8, 0xE8000000);
            context.drawBorder(dx, wy, this.width - 8 - dx, this.height - 8 - wy, 0xFF555555);
            if (wikiFetching) {
                context.drawTextWithShadow(this.textRenderer,
                        Text.literal("正在查询 Minecraft Wiki: " + wikiQuery + " ...")
                                .formatted(Formatting.YELLOW),
                        dx + 4, wy + 4, 0xFFFFFFAA);
            } else if (wikiResult == null) {
                context.drawTextWithShadow(this.textRenderer,
                        Text.literal("Wiki 查询失败或离线：\"" + wikiQuery + "\"")
                                .formatted(Formatting.RED),
                        dx + 4, wy + 4, 0xFFFF7777);
            } else {
                context.drawTextWithShadow(this.textRenderer,
                        Text.literal("Wiki 摘要（" + wikiQuery + "）:").formatted(Formatting.AQUA),
                        dx + 4, wy + 4, 0xFF55FFFF);
                int lineY = wy + 16;
                for (String line : wikiResult.split("\n", -1)) {
                    if (lineY < this.height - 12) {
                        context.drawTextWithShadow(this.textRenderer,
                                Text.literal(truncate(line, this.width - 8 - dx - 8)),
                                dx + 4, lineY, 0xFFDDDDDD);
                        lineY += 10;
                    }
                }
            }
        }
    }

    private void drawDetail(DrawContext context, int x, int y, ManualEntry e) {
        int maxW = this.width - x - 8;
        // 标题
        context.drawTextWithShadow(this.textRenderer,
                Text.literal(e.title()).formatted(Formatting.GOLD),
                x, y, 0xFFFFAA00);
        // 分类与 id
        context.drawTextWithShadow(this.textRenderer,
                Text.literal("分类: " + categoryLabel(e.category()) + "    id: " + e.id())
                        .formatted(Formatting.DARK_GRAY),
                x, y + 12, 0xFF999999);
        // 描述
        context.drawTextWithShadow(this.textRenderer, Text.literal("说明"),
                x, y + 28, 0xFFAAAAFF);
        int lineY = y + 40;
        lineY = drawWrapped(context, e.description(), x, lineY, maxW);
        // 示例
        lineY += 6;
        context.drawTextWithShadow(this.textRenderer, Text.literal("示例")
                .formatted(Formatting.GREEN), x, lineY, 0xFF55FF55);
        lineY += 12;
        if (e.example() != null && !e.example().isBlank()) {
            // 用灰色等宽风格
            for (String line : e.example().split("\n", -1)) {
                context.drawTextWithShadow(this.textRenderer,
                        Text.literal(truncate(line, maxW)).formatted(Formatting.GRAY),
                        x, lineY, 0xFFCCCCCC);
                lineY += 10;
            }
        } else {
            context.drawTextWithShadow(this.textRenderer,
                    Text.literal("（无示例）").formatted(Formatting.ITALIC),
                    x, lineY, 0xFF888888);
        }
        // 关键词
        lineY += 6;
        if (!e.keywords().isEmpty()) {
            context.drawTextWithShadow(this.textRenderer,
                    Text.literal("关键词: " + String.join(", ", e.keywords())),
                    x, lineY, 0xFF888888);
        }
    }

    /** 简单按字符宽度换行渲染。 */
    private int drawWrapped(DrawContext context, String text, int x, int y, int maxW) {
        if (text == null || text.isEmpty()) {
            return y;
        }
        int lineY = y;
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n') {
                context.drawTextWithShadow(this.textRenderer, Text.literal(cur.toString()),
                        x, lineY, 0xFFDDDDDD);
                lineY += 10;
                cur.setLength(0);
                continue;
            }
            cur.append(c);
            if (this.textRenderer.getWidth(cur.toString()) > maxW) {
                // 回退一个字符
                cur.deleteCharAt(cur.length() - 1);
                context.drawTextWithShadow(this.textRenderer, Text.literal(cur.toString()),
                        x, lineY, 0xFFDDDDDD);
                lineY += 10;
                cur.setLength(0);
                cur.append(c);
            }
        }
        if (cur.length() > 0) {
            context.drawTextWithShadow(this.textRenderer, Text.literal(cur.toString()),
                    x, lineY, 0xFFDDDDDD);
            lineY += 10;
        }
        return lineY;
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

    private static int categoryColor(ManualCategory cat) {
        return switch (cat) {
            case COMMAND -> 0xFF4C97FF;
            case BLOCK -> 0xFF8B5A2B;
            case ITEM -> 0xFFFF8C1A;
            case ENTITY -> 0xFF59C059;
            case TAG -> 0xFF9966FF;
            case NBT -> 0xFFFF5555;
            case TEXT_COMPONENT -> 0xFFFFAA00;
        };
    }

    private void fetchWiki() {
        String q;
        if (selected != null) {
            q = selected.title();
        } else if (searchField != null && !searchField.getText().isBlank()) {
            q = searchField.getText();
        } else {
            wikiQuery = "（请先选中条目或输入关键词）";
            wikiResult = null;
            wikiFetching = false;
            return;
        }
        wikiQuery = q;
        wikiFetching = true;
        wikiResult = null;
        // 异步抓取，避免阻塞渲染线程
        final String term = q;
        Thread t = new Thread(() -> {
            String r = WikiFetcher.fetch(term);
            wikiResult = r;
            wikiFetching = false;
        }, "dpe-wiki-fetch");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        // 列表项点击
        int listTop = TOP_BAR_H + 14;
        int listH = this.height - TOP_BAR_H - 22;
        int listLeft = 6;
        if (mouseX >= listLeft && mouseX < listLeft + LIST_W - 4
                && mouseY >= listTop && mouseY < listTop + listH) {
            int idx = (int) ((mouseY - listTop + scrollOffset) / ENTRY_H);
            if (idx >= 0 && idx < visibleEntries.size()) {
                selected = visibleEntries.get(idx);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int listTop = TOP_BAR_H + 14;
        int listH = this.height - TOP_BAR_H - 22;
        int listLeft = 6;
        if (mouseX >= listLeft && mouseX < listLeft + LIST_W - 4
                && mouseY >= listTop && mouseY < listTop + listH) {
            scrollOffset -= (int) (verticalAmount * ENTRY_H * 2);
            clampScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }
}
