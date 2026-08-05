package com.dpe.common.editor;

import com.dpe.common.block.EditorBlock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 编辑器画布，可变。维护缩放、平移、块集合与分组名。
 */
public final class Canvas {

    private double zoom = 1.0;
    private double panX = 0.0;
    private double panY = 0.0;
    private final Map<String, EditorBlock> blocks = new LinkedHashMap<>();
    private final List<String> groups = new ArrayList<>();

    public Canvas() {
    }

    public double getZoom() {
        return zoom;
    }

    public double getPanX() {
        return panX;
    }

    public double getPanY() {
        return panY;
    }

    public Map<String, EditorBlock> getBlocks() {
        return blocks;
    }

    public List<String> getGroups() {
        return groups;
    }

    /** 缩放（乘以因子，限制在 0.1~10）。 */
    public void zoomBy(double factor) {
        this.zoom *= factor;
        if (this.zoom < 0.1) {
            this.zoom = 0.1;
        }
        if (this.zoom > 10.0) {
            this.zoom = 10.0;
        }
    }

    /** 平移增量。 */
    public void panBy(double dx, double dy) {
        this.panX += dx;
        this.panY += dy;
    }

    /** 添加分组名（去重）。 */
    public void addToGroup(String groupName, String blockId) {
        if (groupName == null || groupName.isBlank()) {
            return;
        }
        if (!groups.contains(groupName)) {
            groups.add(groupName);
        }
        // blockId 关联由调用方在 EditorBlock 上扩展；此处仅维护分组名集合
    }

    /**
     * 屏幕坐标 -> 世界坐标。
     * world = (screen - pan) / zoom
     */
    public double[] screenToWorld(double screenX, double screenY) {
        return new double[]{(screenX - panX) / zoom, (screenY - panY) / zoom};
    }

    /**
     * 世界坐标 -> 屏幕坐标。
     * screen = world * zoom + pan
     */
    public double[] worldToScreen(double worldX, double worldY) {
        return new double[]{worldX * zoom + panX, worldY * zoom + panY};
    }
}
