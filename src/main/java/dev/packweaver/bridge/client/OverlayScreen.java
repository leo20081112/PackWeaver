package dev.packweaver.bridge.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * 叠加层布局编辑界面：拖拽窗口标题栏移动位置，按钮切换各窗口显示/隐藏。
 * 对应规划书扩展 D.3「窗口操作」。
 */
public class OverlayScreen extends Screen {
    private static final int TITLE_BAR = 12;

    private OverlayWindow dragging;
    private int dragOffsetX;
    private int dragOffsetY;

    public OverlayScreen() {
        super(Text.translatable("screen.packweaver.overlay_editor"));
    }

    @Override
    protected void init() {
        int i = 0;
        for (OverlayWindow window : OverlayManager.getInstance().getWindows().values()) {
            int x = this.width - 130;
            int y = 40 + i * 24;
            addDrawableChild(ButtonWidget.builder(
                            Text.literal(window.title + ": " + (window.enabled ? "开" : "关")),
                            b -> {
                                window.enabled = !window.enabled;
                                b.setMessage(Text.literal(window.title + ": " + (window.enabled ? "开" : "关")));
                            })
                    .dimensions(x, y, 120, 20)
                    .build());
            i++;
        }
        addDrawableChild(ButtonWidget.builder(
                        Text.translatable("screen.packweaver.done"),
                        b -> close())
                .dimensions(this.width / 2 - 50, this.height - 28, 100, 20)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title,
                this.width / 2, 15, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer,
                Text.translatable("screen.packweaver.overlay_hint"),
                this.width / 2, 28, 0xA0A0A0);
        for (OverlayWindow window : OverlayManager.getInstance().getWindows().values()) {
            OverlayRenderer.drawWindow(context, this.client, window);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        OverlayWindow hit = hitTitle((int) mouseX, (int) mouseY);
        if (hit != null) {
            dragging = hit;
            dragOffsetX = (int) mouseX - hit.x;
            dragOffsetY = (int) mouseY - hit.y;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging != null) {
            dragging.x = Math.max(0, Math.min(this.width - dragging.width, (int) mouseX - dragOffsetX));
            dragging.y = Math.max(0, Math.min(this.height - dragging.height, (int) mouseY - dragOffsetY));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragging = null;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void close() {
        OverlayManager.getInstance().save();
        super.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private OverlayWindow hitTitle(int mx, int my) {
        for (OverlayWindow window : OverlayManager.getInstance().getWindows().values()) {
            if (mx >= window.x && mx <= window.x + window.width
                    && my >= window.y && my <= window.y + TITLE_BAR) {
                return window;
            }
        }
        return null;
    }
}
