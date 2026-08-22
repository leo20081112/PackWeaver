package dev.packweaver.bridge.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import dev.packweaver.bridge.PackWeaverBridge;
import dev.packweaver.bridge.pack.BlockNode;
import dev.packweaver.bridge.pack.PackProject;

import java.util.ArrayList;
import java.util.List;

/**
 * 实时预览（规划书第 18.4 章 3D 可视化）：
 * 世界中用橙色线框标出所有项目积木里的「玩家在区域内」检测范围。
 * /pw preview 切换开关。
 */
public final class AreaPreview {
    private static volatile boolean enabled;
    private static final List<Box> BOXES = new ArrayList<>();
    private static volatile long lastRefresh;

    static {
        WorldRenderEvents.AFTER_ENTITIES.register(wrc -> {
            if (!enabled) {
                return;
            }
            try {
                renderBoxes(wrc.matrixStack(), wrc.camera().getPos());
            } catch (Exception e) {
                PackWeaverBridge.LOGGER.debug("[Preview] 渲染失败: {}", e.getMessage());
            }
        });
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void toggle() {
        enabled = !enabled;
    }

    private static void renderBoxes(MatrixStack matrices, Vec3d cameraPos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }
        long now = System.currentTimeMillis();
        synchronized (BOXES) {
            if (now - lastRefresh > 1000) {
                lastRefresh = now;
                BOXES.clear();
                for (String ns : PackProject.listProjects()) {
                    try {
                        PackProject p = PackProject.load(ns);
                        for (BlockNode ev : p.events) {
                            collectAreas(ev.children);
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            if (BOXES.isEmpty()) {
                return;
            }
            matrices.push();
            matrices.translate(-cameraPos.x, -cameraPos.y, -cameraPos.z);
            RenderSystem.setShader(GameRenderer::getPositionColorProgram);
            VertexConsumerProvider.Immediate immediate = client.getBufferBuilders().getEntityVertexConsumers();
            VertexConsumer lines = immediate.getBuffer(RenderLayer.getLines());
            for (Box box : BOXES) {
                // 橙色线框（WorldRenderer.drawBox：1.20.1 的标准盒描边）
                WorldRenderer.drawBox(matrices, lines,
                        box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
                        1.0f, 0.55f, 0.1f, 0.85f, 1.0f, 1.0f, 1.0f);
            }
            immediate.draw();
            matrices.pop();
        }
    }

    private static void collectAreas(List<BlockNode> stack) {
        for (BlockNode n : stack) {
            if (n.type.equals("cond_area")) {
                try {
                    int x = Integer.parseInt(n.p("x", "0"));
                    int y = Integer.parseInt(n.p("y", "0"));
                    int z = Integer.parseInt(n.p("z", "0"));
                    int dx = Math.max(1, Integer.parseInt(n.p("dx", "2")));
                    int dy = Math.max(1, Integer.parseInt(n.p("dy", "2")));
                    int dz = Math.max(1, Integer.parseInt(n.p("dz", "2")));
                    BOXES.add(new Box(x, y, z, x + dx, y + dy, z + dz));
                } catch (NumberFormatException ignored) {
                }
            }
            collectAreas(n.children);
            collectAreas(n.elseChildren);
        }
    }

    private AreaPreview() {
    }
}
