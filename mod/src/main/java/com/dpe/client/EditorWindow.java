package com.dpe.client;

import com.dpe.common.config.UserConfig;

/**
 * 游戏内小窗状态（Task 4）：窗口位置/尺寸/全屏，以及拖拽与缩放手势。
 * 坐标为屏幕绝对坐标；非全屏时屏幕渲染据此裁剪与平移。
 */
public class EditorWindow {

    public static final int TITLE_H = 16;
    public static final int EDGE = 6;
    public static final int MIN_W = 320;
    public static final int MIN_H = 200;

    public int x;
    public int y;
    public int width;
    public int height;
    public boolean fullscreen;

    /** 上次 resize 是否改变了尺寸（供屏幕 clearAndInit 用）。 */
    public transient boolean resized = false;

    // 手势状态
    private boolean moving = false;
    private boolean resizeL = false;
    private boolean resizeR = false;
    private boolean resizeT = false;
    private boolean resizeB = false;
    private double startMX = 0;
    private double startMY = 0;
    private int startX = 0;
    private int startY = 0;
    private int startW = 0;
    private int startH = 0;

    // 全屏切换前的窗口尺寸（用于恢复）
    private int savedX = 0;
    private int savedY = 0;
    private int savedW = 0;
    private int savedH = 0;

    public EditorWindow() {
    }

    /** 从配置构造窗口；fullscreen 用屏幕全尺寸，否则用配置或默认 80%。 */
    public static EditorWindow fromConfig(UserConfig cfg, int screenW, int screenH) {
        EditorWindow w = new EditorWindow();
        if (cfg == null) {
            cfg = UserConfig.defaults();
        }
        w.fullscreen = cfg.fullscreen;
        if (w.fullscreen) {
            w.x = 0;
            w.y = 0;
            w.width = screenW;
            w.height = screenH;
            return w;
        }
        int ww = cfg.windowWidth > 0 ? cfg.windowWidth : (int) (screenW * 0.8);
        int wh = cfg.windowHeight > 0 ? cfg.windowHeight : (int) (screenH * 0.8);
        ww = Math.max(MIN_W, Math.min(ww, screenW));
        wh = Math.max(MIN_H, Math.min(wh, screenH));
        int wx = cfg.windowX >= 0 ? cfg.windowX : (screenW - ww) / 2;
        int wy = cfg.windowY >= 0 ? cfg.windowY : (screenH - wh) / 2;
        w.x = Math.max(0, Math.min(wx, screenW - ww));
        w.y = Math.max(0, Math.min(wy, screenH - wh));
        w.width = ww;
        w.height = wh;
        w.savedX = w.x;
        w.savedY = w.y;
        w.savedW = ww;
        w.savedH = wh;
        return w;
    }

    /** 写回配置（仅非全屏时写位置/尺寸）。 */
    public void applyToConfig(UserConfig cfg) {
        if (cfg == null) {
            return;
        }
        cfg.fullscreen = fullscreen;
        if (!fullscreen) {
            cfg.windowX = x;
            cfg.windowY = y;
            cfg.windowWidth = width;
            cfg.windowHeight = height;
        }
    }

    /** 标题栏点击：开始拖动；边角点击：开始缩放。返回是否消费。 */
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || fullscreen) {
            return false;
        }
        boolean insideX = mx >= x && mx < x + width;
        boolean insideY = my >= y && my < y + height;
        if (!insideX || !insideY) {
            return false;
        }
        boolean onL = mx < x + EDGE;
        boolean onR = mx >= x + width - EDGE;
        boolean onT = my < y + EDGE;
        boolean onB = my >= y + height - EDGE;
        if (onL || onR || onT || onB) {
            resizeL = onL;
            resizeR = onR;
            resizeT = onT;
            resizeB = onB;
            startGesture(mx, my);
            return true;
        }
        if (my < y + TITLE_H) {
            moving = true;
            startGesture(mx, my);
            return true;
        }
        return false;
    }

    private void startGesture(double mx, double my) {
        startMX = mx;
        startMY = my;
        startX = x;
        startY = y;
        startW = width;
        startH = height;
        resized = false;
    }

    /** 拖动标题栏移动窗口；拖动边角缩放窗口。返回是否消费。 */
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (moving) {
            x = startX + (int) (mx - startMX);
            y = startY + (int) (my - startMY);
            return true;
        }
        if (resizeL || resizeR || resizeT || resizeB) {
            if (resizeL) {
                int nx = startX + (int) (mx - startMX);
                int nw = startW - (int) (mx - startMX);
                if (nw >= MIN_W) {
                    x = nx;
                    width = nw;
                }
            }
            if (resizeR) {
                width = Math.max(MIN_W, startW + (int) (mx - startMX));
            }
            if (resizeT) {
                int ny = startY + (int) (my - startMY);
                int nh = startH - (int) (my - startMY);
                if (nh >= MIN_H) {
                    y = ny;
                    height = nh;
                }
            }
            if (resizeB) {
                height = Math.max(MIN_H, startH + (int) (my - startMY));
            }
            if (width != startW || height != startH) {
                resized = true;
            }
            return true;
        }
        return false;
    }

    /** 结束手势。返回是否消费。 */
    public boolean mouseReleased(double mx, double my, int button) {
        if (moving || resizeL || resizeR || resizeT || resizeB) {
            moving = false;
            resizeL = false;
            resizeR = false;
            resizeT = false;
            resizeB = false;
            return true;
        }
        return false;
    }

    /** 切换全屏；退出全屏时恢复上次窗口尺寸。 */
    public void toggleFullscreen(int screenW, int screenH) {
        if (fullscreen) {
            fullscreen = false;
            x = savedX;
            y = savedY;
            width = savedW;
            height = savedH;
            clampToScreen(screenW, screenH);
        } else {
            savedX = x;
            savedY = y;
            savedW = width;
            savedH = height;
            fullscreen = true;
        }
    }

    /** 将窗口夹紧到屏幕范围内。 */
    public void clampToScreen(int screenW, int screenH) {
        if (fullscreen) {
            return;
        }
        width = Math.max(MIN_W, Math.min(width, screenW));
        height = Math.max(MIN_H, Math.min(height, screenH));
        if (x < 0) {
            x = 0;
        }
        if (y < 0) {
            y = 0;
        }
        if (x + width > screenW) {
            x = Math.max(0, screenW - width);
        }
        if (y + height > screenH) {
            y = Math.max(0, screenH - height);
        }
    }
}
