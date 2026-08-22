package dev.packweaver.bridge.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.math.MatrixStack;

/**
 * 叠加层 HUD 渲染器：半透明窗口 + 标题栏 + 内容行（规划书扩展 D）。
 */
public final class OverlayRenderer {

    private static final int BG_COLOR = 0xB00E0E12;      // 半透明深色背景（约 70% 不透明）
    private static final int TITLE_COLOR = 0xFF1E88E5;   // 标题栏
    private static final int TEXT_COLOR = 0xFFE0E0E0;

    private OverlayRenderer() {
    }

    public static void render(MatrixStack matrices, float tickDelta) {
        OverlayManager manager = OverlayManager.getInstance();
        if (!manager.isVisible()) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.options.hudHidden || client.player == null) {
            return;
        }
        for (OverlayWindow window : manager.getWindows().values()) {
            if (!window.enabled) {
                continue;
            }
            drawWindow(matrices, client, window);
        }
    }

    public static void drawWindow(MatrixStack matrices, MinecraftClient client, OverlayWindow window) {
        DrawableHelper.fill(matrices, window.x, window.y,
                window.x + window.width, window.y + window.height, BG_COLOR);
        DrawableHelper.fill(matrices, window.x, window.y,
                window.x + window.width, window.y + 12, TITLE_COLOR);
        client.textRenderer.drawWithShadow(matrices, window.title,
                window.x + 4, window.y + 2, 0xFFFFFFFF);
        int lineY = window.y + 18;
        for (String line : OverlayManager.contentOf(window)) {
            if (lineY > window.y + window.height - 6) {
                break;
            }
            String clipped = client.textRenderer.trimToWidth(line, window.width - 8);
            client.textRenderer.drawWithShadow(matrices, clipped,
                    window.x + 4, lineY, TEXT_COLOR);
            lineY += 11;
        }
    }
}
