package dev.packweaver.bridge.client;

/** 叠加层窗口的数据模型（位置、尺寸、可见性）。 */
public class OverlayWindow {
    public final String id;
    public final String title;
    public int x;
    public int y;
    public int width;
    public int height;
    public boolean enabled;

    public OverlayWindow(String id, String title, int x, int y, int width, int height, boolean enabled) {
        this.id = id;
        this.title = title;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.enabled = enabled;
    }
}
