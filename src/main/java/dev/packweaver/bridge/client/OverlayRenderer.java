package dev.packweaver.bridge.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * 叠加层 HUD 渲染器：半透明窗口 + 标题栏 + 内容行（规划书扩展 D）。
 */
public final class OverlayRenderer {

    private static final int BG_COLOR = 0xB00E0E12;      // 半透明深色背景（约 70% 不透明）
    private static final int TITLE_COLOR = 0xFF1E88E5;   // 标题栏
    private static final int TEXT_COLOR = 0xFFE0E0E0;

    private OverlayRenderer() {
    }

    public static void render(DrawContext context, float tickDelta) {
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
            drawWindow(context, client, window);
        }
    }

    public static void drawWindow(DrawContext context, MinecraftClient client, OverlayWindow window) {
        context.fill(window.x, window.y, window.x + window.width, window.y + window.height, BG_COLOR);
        context.fill(window.x, window.y, window.x + window.width, window.y + 12, TITLE_COLOR);
        context.drawText(client.textRenderer, window.title, window.x + 4, window.y + 2, 0xFFFFFFFF, true);
        int lineY = window.y + 18;
        for (String line : OverlayManager.contentOf(window)) {
            if (lineY > window.y + window.height - 6) {
                break;
            }
            String clipped = client.textRenderer.trimToWidth(line, window.width - 8);
            context.drawText(client.textRenderer, clipped, window.x + 4, lineY, TEXT_COLOR, true);
            lineY += 11;
        }
    }
}
