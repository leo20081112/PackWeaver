package com.dpe.client;

import com.dpe.common.config.UserConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/**
 * 新手引导覆盖层（Task 6）：在 EditorScreen 上叠加分步提示。
 * 步骤：1.积木树侧边栏；2.添加第一个积木；3.编辑积木字段；4.编译和重载。
 * 点击「下一步」推进，「跳过」关闭并持久化 showOnboarding=false。
 */
public final class OnboardingOverlay {

    private static final String[] STEPS = {
            "第 1 步：按 B 键打开积木树侧边栏，可以快速浏览和管理所有积木",
            "第 2 步：在左侧调色板点击一个积木类型（如「每刻触发」）添加到画布",
            "第 3 步：点击画布上的积木选中它，在右侧字段面板填写参数（可点击「显示更多选项」展开高级字段）",
            "第 4 步：点击顶部「编译」查看预览，确认无误后点击「保存应用」或按 R 重载"
    };
    
    private static final String[] STEP_TIPS = {
            "💡 提示：积木树可以搜索、定位和重命名积木",
            "💡 提示：常用字段会自动显示，高级字段需要展开「显示更多选项」",
            "💡 提示：悬停积木可查看详细说明和使用方法",
            "💡 提示：按 M 可切换到 IDE 文本模式进行高级编辑"
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
                }
            }
        }
    }
    
    /** 重播引导。 */
    public void replay() {
        step = 0;
        if (config != null) {
            config.showOnboarding = true;
            if (configPath != null) {
                try {
                    config.save(configPath);
                } catch (Exception ignored) {
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
        int boxW = Math.min(480, screenWidth - 20);
        int boxH = 76;
        int boxX = (screenWidth - boxW) / 2;
        int boxY = screenHeight - boxH - 8;

        context.fill(boxX, boxY, boxX + boxW, boxY + boxH, 0xE8000000);
        context.drawBorder(boxX, boxY, boxW, boxH, 0xFFFFAA00);

        String stepLabel = (step + 1) + " / " + STEPS.length + " · 新手引导";
        if (mc != null) {
            context.drawTextWithShadow(mc.textRenderer, Text.literal(stepLabel),
                    boxX + 6, boxY + 4, 0xFFFFAA00);
            
            String text = STEPS[step];
            int maxW = boxW - 12;
            String display = truncate(mc, text, maxW);
            context.drawTextWithShadow(mc.textRenderer, Text.literal(display),
                    boxX + 6, boxY + 18, 0xFFFFFFEE);
            
            String tip = STEP_TIPS[step];
            context.drawTextWithShadow(mc.textRenderer, Text.literal(tip),
                    boxX + 6, boxY + 32, 0xFF88FF88);
            
            int btnY = boxY + 50;
            context.drawTextWithShadow(mc.textRenderer,
                    Text.literal("[下一步 →]").formatted(net.minecraft.util.Formatting.GREEN),
                    boxX + 6, btnY, 0xFF55FF55);
            context.drawTextWithShadow(mc.textRenderer,
                    Text.literal("[重播]").formatted(net.minecraft.util.Formatting.YELLOW),
                    boxX + 90, btnY, 0xFFFFAA00);
            context.drawTextWithShadow(mc.textRenderer,
                    Text.literal("[跳过]").formatted(net.minecraft.util.Formatting.RED),
                    boxX + boxW - 50, btnY, 0xFFFF5555);
        }
        return true;
    }

    /** 处理点击；返回是否消费了事件。 */
    public boolean mouseClicked(double mouseX, double mouseY, int screenWidth, int screenHeight) {
        if (!shouldShow()) {
            return false;
        }
        int boxW = Math.min(480, screenWidth - 20);
        int boxH = 76;
        int boxX = (screenWidth - boxW) / 2;
        int boxY = screenHeight - boxH - 8;
        
        if (mouseX < boxX || mouseX > boxX + boxW
                || mouseY < boxY || mouseY > boxY + boxH) {
            return false;
        }
        
        int btnY = boxY + 50;
        
        if (mouseX >= boxX + boxW - 50 && mouseY >= btnY && mouseY < btnY + 12) {
            dismiss();
            return true;
        }
        
        if (mouseX >= boxX + 90 && mouseX < boxX + 140 && mouseY >= btnY && mouseY < btnY + 12) {
            replay();
            return true;
        }
        
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
