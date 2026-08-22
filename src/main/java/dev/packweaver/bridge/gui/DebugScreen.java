package dev.packweaver.bridge.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ScoreboardPlayerScore;
import net.minecraft.text.Text;
import dev.packweaver.bridge.bridge.BridgeServer;
import dev.packweaver.bridge.perf.PerfTracker;

import java.util.ArrayList;
import java.util.List;

/**
 * 调试监视界面（规划书第 16.4 章「变量监视」）：
 * 计分板目标与在线玩家分数、性能数据、桥接状态。每 20 tick 自动刷新。
 */
public class DebugScreen extends Screen {
    private List<String> lines = new ArrayList<>();
    private int tick;

    public DebugScreen() {
        super(Text.literal("调试监视"));
    }

    @Override
    protected void init() {
        refresh();
        addDrawableChild(ButtonWidget.builder(Text.literal("刷新"), b -> refresh())
                .dimensions(this.width - 110, 6, 50, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.packweaver.done"), b -> close())
                .dimensions(this.width - 56, 6, 50, 18).build());
    }

    private void refresh() {
        lines = new ArrayList<>();
        MinecraftClient client = MinecraftClient.getInstance();
        lines.add("§l性能");
        lines.add("  MSPT " + String.format("%.1f", PerfTracker.averageMspt())
                + "ms（峰值 " + String.format("%.1f", PerfTracker.maxMspt()) + "ms）  TPS "
                + String.format("%.1f", PerfTracker.tps()) + "  状态 " + PerfTracker.status());
        BridgeServer bridge = BridgeServer.getInstance();
        lines.add("§l桥接");
        lines.add("  TCP 127.0.0.1:" + bridge.getPort() + (bridge.isRunning() ? " 运行中" : " 停止")
                + "  |  HTTP 127.0.0.1:32006");
        lines.add("§l计分板（变量监视）");
        var server = client.getServer();
        if (server == null) {
            lines.add("  （未进入世界）");
            return;
        }
        Scoreboard sb = server.getScoreboard();
        int count = 0;
        for (ScoreboardObjective obj : sb.getObjectives()) {
            if (count++ > 8) {
                lines.add("  …（更多目标略）");
                break;
            }
            lines.add("  ▸ " + obj.getName() + " [" + obj.getCriterion().getName() + "] "
                    + (obj.getDisplayName() != null ? obj.getDisplayName().getString() : ""));
            int shown = 0;
            for (ScoreboardPlayerScore score : sb.getAllPlayerScores(obj)) {
                if (shown++ >= 6) {
                    lines.add("      …");
                    break;
                }
                lines.add("      " + score.getPlayerName() + " = " + score.getScore());
            }
        }
        if (count == 0) {
            lines.add("  （暂无计分板目标）");
        }
    }

    @Override
    public void tick() {
        if (++tick % 20 == 0) {
            refresh();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFF);
        int y = 30;
        for (String line : lines) {
            if (y > this.height - 16) {
                break;
            }
            boolean heading = line.startsWith("§l");
            String text = heading ? line.substring(2) : line;
            context.drawTextWithShadow(this.textRenderer, text, 12, y,
                    heading ? 0xFF4FC3F7 : 0xFFE0E0E0);
            y += heading ? 13 : 11;
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
