package dev.packweaver.bridge.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import dev.packweaver.bridge.pack.PackProject;

import java.util.ArrayList;
import java.util.List;

/**
 * 调试控制界面（规划书第 16 章可落地子集）：
 *
 * - 日志断点（16.2）：选择函数 → 输入行号 → 保存后该行前注入 tellraw，
 *   执行到即输出（带 pw_debugger 标签的玩家可见）
 * - 函数执行轨迹（16.3）：开关 traceMode，每个函数入口注入 "→ ns:fn" 轨迹
 * - 单人调试（16.6）：给自己加/移除 pw_debugger 标签，输出只对标签携带者生效
 * - 断点列表与移除
 */
public class DebugControlScreen extends Screen {
    private final PackProject project;
    private final Screen back;
    private final List<String> functions = new ArrayList<>();
    private int fnIndex;
    private net.minecraft.client.gui.widget.TextFieldWidget lineField;
    private String message = "";

    public DebugControlScreen(PackProject project, Screen back) {
        super(Text.literal("调试控制 - " + project.namespace));
        this.project = project;
        this.back = back;
        for (String path : project.allFiles()) {
            if (path.endsWith(".mcfunction")) {
                functions.add(path);
            }
        }
    }

    @Override
    protected void init() {
        int cx = this.width / 2;

        addDrawableChild(ButtonWidget.builder(Text.literal("函数: ◀"), b -> {
                    fnIndex = (fnIndex + functions.size() - 1) % Math.max(1, functions.size());
                    updateFnButton();
                })
                .dimensions(cx - 150, 34, 40, 18).build());
        fnButton = ButtonWidget.builder(Text.literal(fnLabel()), b -> {
                    fnIndex = (fnIndex + 1) % Math.max(1, functions.size());
                    updateFnButton();
                })
                .dimensions(cx - 108, 34, 176, 18).build();
        updateFnButton();
        addDrawableChild(fnButton);
        addDrawableChild(ButtonWidget.builder(Text.literal("▶"), b -> {
                    fnIndex = (fnIndex + 1) % Math.max(1, functions.size());
                    updateFnButton();
                })
                .dimensions(cx + 70, 34, 40, 18).build());

        lineField = new net.minecraft.client.gui.widget.TextFieldWidget(
                this.textRenderer, cx - 108, 58, 100, 16, Text.literal("行号"));
        lineField.setMaxLength(4);
        addSelectableChild(lineField);
        addDrawableChild(ButtonWidget.builder(Text.literal("＋ 添加日志断点"), b -> addBreakpoint())
                .dimensions(cx + 0, 56, 110, 18).build());

        addDrawableChild(ButtonWidget.builder(
                        Text.literal("函数执行轨迹: " + (project.traceMode ? "开" : "关")),
                        b -> {
                            project.traceMode = !project.traceMode;
                            b.setMessage(Text.literal("函数执行轨迹: " + (project.traceMode ? "开" : "关")));
                            saveQuietly();
                        })
                .dimensions(cx - 150, 82, 145, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(
                        dev.packweaver.bridge.client.PwDebuggerTag.has() ? "调试输出: 已接收(移除标签)" : "调试输出: 未接收(给我加标签)"),
                        b -> {
                            dev.packweaver.bridge.client.PwDebuggerTag.toggle();
                            b.setMessage(Text.literal(dev.packweaver.bridge.client.PwDebuggerTag.has()
                                    ? "调试输出: 已接收(移除标签)" : "调试输出: 未接收(给我加标签)"));
                        })
                .dimensions(cx + 5, 82, 145, 18).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("💾 保存并重载（注入生效）"), b -> saveAndReload())
                .dimensions(cx - 150, 112, 145, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("清空全部断点"), b -> {
                    project.debugLines.clear();
                    saveQuietly();
                    message = "§a已清空";
                })
                .dimensions(cx + 5, 112, 145, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.packweaver.done"), b -> {
                    assert this.client != null;
                    this.client.setScreen(back);
                })
                .dimensions(cx - 60, this.height - 30, 120, 18).build());
    }

    private net.minecraft.client.gui.widget.ButtonWidget fnButton;

    private String fnLabel() {
        if (functions.isEmpty()) {
            return "（无函数）";
        }
        String p = functions.get(fnIndex);
        return p.substring(p.lastIndexOf('/') + 1).replace(".mcfunction", "");
    }

    private void updateFnButton() {
        if (fnButton != null) {
            fnButton.setMessage(Text.literal("函数: " + fnLabel()));
        }
    }

    private void addBreakpoint() {
        if (functions.isEmpty() || lineField.getText().isBlank()) {
            message = "§c先选函数并输入行号";
            return;
        }
        int line;
        try {
            line = Integer.parseInt(lineField.getText().trim());
        } catch (NumberFormatException e) {
            message = "§c行号必须是数字";
            return;
        }
        if (line < 1) {
            message = "§c行号从 1 开始";
            return;
        }
        project.debugLines.computeIfAbsent(functions.get(fnIndex), k -> new ArrayList<>());
        List<Integer> marks = project.debugLines.get(functions.get(fnIndex));
        if (!marks.contains(line)) {
            marks.add(line);
        }
        saveQuietly();
        message = "§a断点已添加并保存";
        lineField.setText("");
    }

    private void saveQuietly() {
        try {
            project.save();
        } catch (Exception e) {
            message = "§c保存失败: " + e.getMessage();
        }
    }

    private void saveAndReload() {
        try {
            project.save();
            var server = this.client != null ? this.client.getServer() : null;
            if (server != null) {
                server.execute(() -> server.getCommandManager()
                        .executeWithPrefix(server.getCommandSource(), "reload"));
            }
            message = "§a已保存并重载，断点/轨迹注入生效";
        } catch (Exception e) {
            message = "§c失败: " + e.getMessage();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, "行号", this.width / 2 - 108, 50, 0xB0BEC5);

        // 断点列表
        int y = 140;
        context.drawTextWithShadow(this.textRenderer, "当前断点（行号以保存内容为准）：", 30, y, 0x4FC3F7);
        y += 13;
        for (var e : project.debugLines.entrySet()) {
            String fn = e.getKey().substring(e.getKey().lastIndexOf('/') + 1).replace(".mcfunction", "");
            for (int line : e.getValue()) {
                if (y > this.height - 46) {
                    break;
                }
                context.drawTextWithShadow(this.textRenderer,
                        "● " + fn + " 第 " + line + " 行", 40, y, 0xFFFFB74D);
                final String path = e.getKey();
                final int ln = line;
                addBreakpointRemoveButton(path, ln, this.width - 80, y - 3);
                y += 15;
            }
        }
        if (project.debugLines.isEmpty()) {
            context.drawTextWithShadow(this.textRenderer, "（无）", 40, y, 0x78909C);
        }
        if (!message.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, message, this.width / 2, this.height - 46,
                    message.startsWith("§a") ? 0xFF66BB6A : 0xFFEF5350);
        }
        context.drawTextWithShadow(this.textRenderer,
                "提示：给 `function` 积木调用的子函数加断点即可观察调用顺序；轨迹输出对全服带标签玩家可见",
                20, this.height - 20, 0x607D8B);
        super.render(context, mouseX, mouseY, delta);
    }

    private final List<net.minecraft.client.gui.widget.ButtonWidget> removeButtons = new ArrayList<>();

    private void addBreakpointRemoveButton(String path, int line, int x, int y) {
        removeButtons.add(addDrawableChild(ButtonWidget.builder(Text.literal("✕"), b -> {
                    List<Integer> marks = project.debugLines.get(path);
                    if (marks != null) {
                        marks.remove(Integer.valueOf(line));
                        if (marks.isEmpty()) {
                            project.debugLines.remove(path);
                        }
                    }
                    saveQuietly();
                    this.clearAndInit();
                })
                .dimensions(x, y, 20, 14).build()));
    }

    @Override
    public void tick() {
        removeButtons.clear();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
