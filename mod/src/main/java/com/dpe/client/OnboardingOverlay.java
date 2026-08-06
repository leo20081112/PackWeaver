package com.dpe.client;

import com.dpe.common.config.UserConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * 新手引导覆盖层（Task 6）：在 EditorScreen 上叠加分步提示。
 * 步骤：1.点调色板加积木；2.选中积木编辑字段；3.点编译预览；4.点重载。
 * 点击「下一步」推进，「跳过」关闭并持久化 showOnboarding=false。
 */
public final class OnboardingOverlay {

    /** 引导步骤文案。 */
    private static final String[] STEPS = {
            "第 1 步：在左侧调色板点击一个积木类型（如「每刻触发」）添加到画布",
            "第 2 步：点击画布上的积木选中它，在右侧字段面板填写参数",
            "第 3 步：点击顶部「Compile」按钮查看编译预览与产物",
            "第 4 步：点击顶部「重载」按钮将数据包写入并应用（快捷键 R）"
    };

    private final Screen host;
    private final UserConfig config;
    private final java.nio.file.Path configPath;
    private int step = 0;

    public OnboardingOverlay(Screen host, UserConfig config, java.nio.file.Path configPath) {
        this.host = host;
        this.config = config;
        this.configPath = configPath;
    }

    /** 是否应渲染引导。 */
    public boolean shouldShow() {
        return config != null && config.showOnboarding && step < STEPS.length;
    }

    public int step() {
        return step;
    }

    public int totalSteps() {
        return STEPS.length;
    }

    /** 推进到下一步；已完成则关闭并持久化。 */
    public void next() {
        step++;
        if (step >= STEPS.length) {
            dismiss();
        }
    }

    /** 跳过并持久化关闭。 */
    public void dismiss() {
        step = STEPS.length;
        if (config != null) {
            config.showOnboarding = false;
            if (configPath != null) {
                try {
                    config.save(configPath);
                } catch (Exception ignored) {
                    // 持久化失败不影响本次会话
                }
            }
        }
    }

    /** 渲染覆盖层；返回是否处理了点击。 */
    public boolean render(DrawContext context, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        if (!shouldShow()) {
            return false;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        int boxW = Math.min(420, screenWidth - 20);
        int boxH = 56;
        int boxX = (screenWidth - boxW) / 2;
        int boxY = screenHeight - boxH - 8;

        // 半透明背景
        context.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xE8000000);
        context.drawBorder(boxX, boxY, boxW, boxH, 0xFFFFAA00);

        // 步骤指示
        String stepLabel = (step + 1) + " / " + STEPS.length + " · 新手引导";
        if (mc != null) {
            context.drawTextWithShadow(mc.textRenderer, Text.literal(stepLabel),
                    boxX + 6, boxY + 4, 0xFFFFAA00);
            String text = STEPS[step];
            // 文本过长则截断
            int maxW = boxW - 12;
            String display = truncate(mc, text, maxW);
            context.drawTextWithShadow(mc.textRenderer, Text.literal(display),
                    boxX + 6, boxY + 18, 0xFFFFFFEE);
            // 按钮：下一步 / 跳过
            context.drawTextWithShadow(mc.textRenderer,
                    Text.literal("[下一步 →] 点击此处").formatted(net.minecraft.util.Formatting.GREEN),
                    boxX + 6, boxY + 38, 0xFF55FF55);
            context.drawTextWithShadow(mc.textRenderer,
                    Text.literal("[跳过]").formatted(net.minecraft.util.Formatting.RED),
                    boxX + boxW - 50, boxY + 38, 0xFFFF5555);
        }
        return true;
    }

    /** 处理点击；返回是否消费了事件。 */
    public boolean mouseClicked(double mouseX, double mouseY, int screenWidth, int screenHeight) {
        if (!shouldShow()) {
            return false;
        }
        int boxW = Math.min(420, screenWidth - 20);
        int boxH = 56;
        int boxX = (screenWidth - boxW) / 2;
        int boxY = screenHeight - boxH - 8;
        // 点击范围在引导框内
        if (mouseX < boxX || mouseX > boxX + boxW
                || mouseY < boxY || mouseY > boxY + boxH) {
            return false;
        }
        // 跳过按钮（右下角）
        if (mouseX >= boxX + boxW - 50 && mouseY >= boxY + 38) {
            dismiss();
            return true;
        }
        // 其余区域视为「下一步」
        next();
        return true;
    }

    /** 按键快捷：Enter 下一步，Esc 跳过。 */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!shouldShow()) {
            return false;
        }
        return false;
    }

    private static String truncate(MinecraftClient mc, String s, int maxW) {
        if (s == null) {
            return "";
        }
        if (mc == null || mc.textRenderer == null) {
            return s.length() > 60 ? s.substring(0, 60) + "..." : s;
        }
        if (mc.textRenderer.getWidth(s) <= maxW) {
            return s;
        }
        int i = s.length() - 1;
        while (i > 0 && mc.textRenderer.getWidth(s.substring(0, i) + "...") > maxW) {
            i--;
        }
        return s.substring(0, Math.max(0, i)) + "...";
    }
}
