package dev.packweaver.bridge.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import dev.packweaver.bridge.pack.Diag;
import dev.packweaver.bridge.pack.PackProject;

import java.util.List;

/**
 * 诊断报告界面（规划书第 17 章）：
 * 概览（错误/警告/提示）→ 分级列表（代码/位置/说明/修复按钮）→ 全部修复 / 重新诊断。
 */
public class DiagScreen extends Screen {
    private final PackProject project;
    private final Screen back;
    private List<Diag.Issue> issues = List.of();
    private String notice = "";
    private int scroll;

    public DiagScreen(PackProject project, Screen back) {
        super(Text.literal("诊断报告"));
        this.project = project;
        this.back = back;
        rescan();
    }

    private void rescan() {
        issues = Diag.run(project);
        scroll = 0;
    }

    private int count(String severity) {
        int n = 0;
        for (Diag.Issue i : issues) {
            if (i.severity.equals(severity)) {
                n++;
            }
        }
        return n;
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.literal("重新诊断"), b -> rescan())
                .dimensions(this.width - 200, 6, 80, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("全部修复"), b -> fixAll())
                .dimensions(this.width - 116, 6, 66, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.packweaver.done"), b -> {
                    assert this.client != null;
                    this.client.setScreen(back);
                })
                .dimensions(this.width - 46, 6, 40, 18).build());
    }

    private void fixAll() {
        int fixed = 0;
        for (Diag.Issue i : issues) {
            if (i.fixId != null && Diag.applyFix(project, i.fixId)) {
                fixed++;
            }
        }
        try {
            project.save();
            notice = "已修复 " + fixed + " 项并保存";
        } catch (Exception e) {
            notice = "保存失败: " + e.getMessage();
        }
        rescan();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        pendingFix = null;
        renderBackground(context);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer,
                "✖ 错误 " + count("error") + "   ⚠ 警告 " + count("warn") + "   ℹ 提示 " + count("info"),
                8, 28, 0xB0BEC5);
        if (!notice.isEmpty()) {
            context.drawTextWithShadow(this.textRenderer, notice, 8, 40, 0xFF66BB6A);
        }

        int y = 52 - scroll;
        int maxScroll = Math.max(0, issues.size() * 26 - (this.height - 60));
        scroll = Math.min(scroll, maxScroll);
        for (Diag.Issue issue : issues) {
            if (y > this.height - 10) {
                break;
            }
            if (y >= 46) {
                int color = issue.severity.equals("error") ? 0xFFEF5350
                        : issue.severity.equals("warn") ? 0xFFFFB74D : 0xFF4FC3F7;
                context.fill(6, y, this.width - 6, y + 24, 0x40000000);
                context.drawTextWithShadow(this.textRenderer, issue.code, 10, y + 2, color);
                context.drawTextWithShadow(this.textRenderer,
                        shortPath(issue.file) + (issue.line > 0 ? ":" + issue.line : ""), 60, y + 2, 0x90A4AE);
                String msg = issue.message.length() > 64 ? issue.message.substring(0, 64) + "…" : issue.message;
                context.drawTextWithShadow(this.textRenderer, msg, 10, y + 13, 0xFFE0E0E0);
                if (issue.fixId != null) {
                    int fx = this.width - 58;
                    if (mouseX >= fx && mouseX <= fx + 50 && mouseY >= y + 4 && mouseY <= y + 20) {
                        context.fill(fx, y + 4, fx + 50, y + 20, 0xFF2E7D32);
                    } else {
                        context.fill(fx, y + 4, fx + 50, y + 20, 0x802E7D32);
                    }
                    context.drawCenteredTextWithShadow(this.textRenderer, "修复", fx + 25, y + 9, 0xFFFFFFFF);
                    if (mouseX >= fx && mouseX <= fx + 50 && mouseY >= y + 4 && mouseY <= y + 20) {
                        pendingFix = issue;
                    }
                }
            }
            y += 26;
        }
        if (issues.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, "✔ 未发现问题，代码很棒！",
                    this.width / 2, this.height / 2, 0xFF66BB6A);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    private Diag.Issue pendingFix;

    private String shortPath(String path) {
        return path.length() > 38 ? "…" + path.substring(path.length() - 37) : path;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (pendingFix != null) {
            Diag.Issue issue = pendingFix;
            pendingFix = null;
            if (Diag.applyFix(project, issue.fixId)) {
                try {
                    project.save();
                    notice = "已修复 " + issue.code;
                } catch (Exception e) {
                    notice = "保存失败: " + e.getMessage();
                }
                rescan();
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        scroll = Math.max(0, scroll - (int) amount * 26);
        return true;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
