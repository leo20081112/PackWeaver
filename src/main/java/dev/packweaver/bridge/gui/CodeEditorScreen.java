package dev.packweaver.bridge.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import dev.packweaver.bridge.pack.PackProject;

import java.util.ArrayList;
import java.util.List;

/**
 * IDE 模式：mcfunction 代码编辑器（规划书第 4 章）。
 * 行号 + 语法高亮（命令蓝 / 选择器橙 / 字符串绿 / 数字紫 / 注释灰）
 * + 文件切换 + 保存(Ctrl+S) + 诊断。
 */
public class CodeEditorScreen extends Screen {
    private final PackProject project;
    private String fn;
    private List<String> lines = new ArrayList<>();
    private int caretLine;
    private int caretCol;
    private int scroll;
    private String message = "";
    private static final int LINE_H = 10;
    private static final int X0 = 40;
    private static final int Y0 = 30;

    public CodeEditorScreen(PackProject project, String fn) {
        super(Text.literal("PackWeaver IDE - " + project.namespace));
        this.project = project;
        this.fn = fn;
        loadFile();
    }

    private void loadFile() {
        try {
            String content = project.readFunction(fn);
            lines.clear();
            for (String l : content.replace("\r", "").split("\n", -1)) {
                lines.add(l);
            }
            if (lines.isEmpty()) {
                lines.add("");
            }
        } catch (Exception e) {
            lines = new ArrayList<>(List.of("# 读取失败: " + e.getMessage()));
        }
        caretLine = 0;
        caretCol = 0;
        scroll = 0;
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.literal("保存 Ctrl+S"), b -> save())
                .dimensions(this.width - 210, 6, 90, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("诊断"), b -> {
                    assert this.client != null;
                    this.client.setScreen(new DiagScreen(project, this));
                })
                .dimensions(this.width - 115, 6, 50, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("积木模式"), b -> {
                    assert this.client != null;
                    this.client.setScreen(new BlockEditorScreen(project));
                })
                .dimensions(this.width - 60, 6, 55, 18).build());
        // 文件切换标签
        int x = 8;
        for (String name : functionNames()) {
            final String f = name;
            addDrawableChild(ButtonWidget.builder(Text.literal(f), b -> {
                        fn = f;
                        loadFile();
                    })
                    .dimensions(x, 6, Math.max(40, this.textRenderer.getWidth(name) + 8), 16).build());
            x += Math.max(40, this.textRenderer.getWidth(name) + 12);
            if (x > this.width - 260) {
                break;
            }
        }
    }

    private List<String> functionNames() {
        List<String> names = new ArrayList<>();
        for (String path : project.files.keySet()) {
            if (path.endsWith(".mcfunction")) {
                names.add(path.substring((project.namespace + "/functions/").length())
                        .replace(".mcfunction", ""));
            }
        }
        for (dev.packweaver.bridge.pack.BlockNode ev : project.events) {
            String f = dev.packweaver.bridge.pack.CodeGen.eventFunction(ev.type);
            if (f != null) {
                names.add(f);
            }
        }
        names.add("tick");
        names.add("load");
        return names.stream().distinct().toList();
    }

    private void save() {
        try {
            project.writeFunction(fn, String.join("\n", lines));
            message = "§a已保存并重载（" + fn + "）";
        } catch (Exception e) {
            message = "§c保存失败: " + e.getMessage();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, -40, 0xFFFFFF);

        int visible = (this.height - Y0 - 24) / LINE_H;
        if (caretLine - scroll >= visible) {
            scroll = caretLine - visible + 1;
        }
        if (caretLine < scroll) {
            scroll = caretLine;
        }

        for (int i = scroll; i < Math.min(lines.size(), scroll + visible); i++) {
            int y = Y0 + (i - scroll) * LINE_H;
            String num = String.valueOf(i + 1);
            context.drawTextWithShadow(this.textRenderer, num, 8 + Math.max(0, 24 - this.textRenderer.getWidth(num)), y, 0x607D8B);
            if (i == caretLine) {
                context.fill(X0 - 2, y - 1, this.width - 8, y + LINE_H - 1, 0x30FFFFFF);
            }
            drawHighlighted(context, lines.get(i), X0, y);
        }
        // 光标
        int caretY = Y0 + (caretLine - scroll) * LINE_H;
        String cur = lines.get(caretLine);
        String before = cur.substring(0, Math.min(caretCol, cur.length()));
        int caretX = X0 + this.textRenderer.getWidth(before);
        context.fill(caretX, caretY, caretX + 1, caretY + LINE_H - 1, 0xFFFFFFFF);

        if (!message.isEmpty()) {
            context.drawTextWithShadow(this.textRenderer, message, 8, this.height - 22,
                    message.startsWith("§a") ? 0xFF66BB6A : 0xFFEF5350);
        }
        context.drawTextWithShadow(this.textRenderer, "行 " + (caretLine + 1) + " 列 " + (caretCol + 1)
                + "  |  " + project.namespace + ":" + fn, 8, this.height - 10, 0x90A4AE);
        super.render(context, mouseX, mouseY, delta);
    }

    /** 简易语法高亮：注释灰 / 命令首词蓝 / 选择器橙 / 字符串绿 / 数字紫 / 命名空间青。 */
    private void drawHighlighted(DrawContext context, String line, int x, int y) {
        if (line.startsWith("#")) {
            context.drawTextWithShadow(this.textRenderer, line, x, y, 0xFF78909C);
            return;
        }
        int cx = x;
        String remaining = line;
        int sp = remaining.indexOf(' ');
        if (sp > 0 && remaining.substring(0, sp).matches("[a-z]+")) {
            context.drawTextWithShadow(this.textRenderer, remaining.substring(0, sp), cx, y, 0xFF64B5F6);
            cx += this.textRenderer.getWidth(remaining.substring(0, sp));
            remaining = remaining.substring(sp);
        }
        int i = 0;
        while (i < remaining.length()) {
            char c = remaining.charAt(i);
            if (c == ' ') {
                cx += this.textRenderer.getWidth(" ");
                i++;
                continue;
            }
            int start = i;
            while (i < remaining.length() && remaining.charAt(i) != ' ') {
                i++;
            }
            String tok = remaining.substring(start, i);
            context.drawTextWithShadow(this.textRenderer, tok, cx, y, tokenColor(tok));
            cx += this.textRenderer.getWidth(tok);
        }
    }

    private int tokenColor(String tok) {
        if (tok.startsWith("@") || tok.startsWith("[")) {
            return 0xFFFFB74D;
        }
        if (tok.startsWith("\"") || tok.startsWith("{") || tok.endsWith("}")) {
            return 0xFF81C784;
        }
        if (tok.matches("-?\\d+(\\.\\d+)?[bslfdL]?")) {
            return 0xFFBA68C8;
        }
        if (tok.contains(":")) {
            return 0xFF4DD0E1;
        }
        return 0xFFE0E0E0;
    }

    // ---------------- 键盘编辑 ----------------

    @Override
    public boolean charTyped(char chr, int modifiers) {
        String cur = lines.get(caretLine);
        lines.set(caretLine, cur.substring(0, caretCol) + chr + cur.substring(caretCol));
        caretCol++;
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (hasControlDown() && keyCode == 83) { // Ctrl+S
            save();
            return true;
        }
        switch (keyCode) {
            case 259 -> { // Backspace
                if (caretCol > 0) {
                    String cur = lines.get(caretLine);
                    lines.set(caretLine, cur.substring(0, caretCol - 1) + cur.substring(caretCol));
                    caretCol--;
                } else if (caretLine > 0) {
                    String prev = lines.get(caretLine - 1);
                    lines.set(caretLine - 1, prev + lines.get(caretLine));
                    lines.remove(caretLine);
                    caretLine--;
                    caretCol = prev.length();
                }
                return true;
            }
            case 257, 335 -> { // Enter
                String cur = lines.get(caretLine);
                String indent = cur.matches("\\s*(execute|if).*") ? "    " : "";
                lines.set(caretLine, cur.substring(0, caretCol));
                lines.add(caretLine + 1, indent + cur.substring(caretCol));
                caretLine++;
                caretCol = indent.length();
                return true;
            }
            case 262 -> { // Right
                if (caretCol < lines.get(caretLine).length()) {
                    caretCol++;
                } else if (caretLine < lines.size() - 1) {
                    caretLine++;
                    caretCol = 0;
                }
                return true;
            }
            case 263 -> { // Left
                if (caretCol > 0) {
                    caretCol--;
                } else if (caretLine > 0) {
                    caretLine--;
                    caretCol = lines.get(caretLine).length();
                }
                return true;
            }
            case 264 -> { // Down
                if (caretLine < lines.size() - 1) {
                    caretLine++;
                    caretCol = Math.min(caretCol, lines.get(caretLine).length());
                }
                return true;
            }
            case 265 -> { // Up
                if (caretLine > 0) {
                    caretLine--;
                    caretCol = Math.min(caretCol, lines.get(caretLine).length());
                }
                return true;
            }
            case 268 -> { // Home
                caretCol = 0;
                return true;
            }
            case 269 -> { // End
                caretCol = lines.get(caretLine).length();
                return true;
            }
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        scroll = Math.max(0, Math.min(Math.max(0, lines.size() - 1), scroll - (int) amount));
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 点击定位光标
        if (mouseX > X0 - 6 && mouseY > Y0 - 4) {
            int line = scroll + (int) ((mouseY - Y0) / LINE_H);
            if (line >= 0 && line < lines.size()) {
                caretLine = line;
                String text = lines.get(line);
                int col = 0;
                while (col < text.length()
                        && X0 + this.textRenderer.getWidth(text.substring(0, col + 1)) <= mouseX) {
                    col++;
                }
                caretCol = col;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
