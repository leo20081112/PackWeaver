package com.dpe.client;

import com.dpe.common.config.KeyBindings;
import com.dpe.common.config.UserConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 键位设置屏幕（Task 5）：列出 KeyBindings 各动作，
 * 点击进入「等待按键」状态，再次按键绑定；
 * 冲突时红色提示，可覆盖或取消。
 * 保存到 {@code config/packweaver/config.json}，「重置默认」恢复 {@link KeyBindings#defaults()}。
 */
public class KeyBindingsSettingsScreen extends Screen {

    /** 单条动作描述。 */
    private record ActionRow(String id, String label, java.util.function.IntSupplier getter,
                             java.util.function.IntConsumer setter) {
    }

    private static final int ROW_H = 18;
    private static final int TOP_BAR_H = 30;

    private final Screen parent;
    private final UserConfig config;
    private final Path configPath;

    private final List<ActionRow> rows = new ArrayList<>();
    private final Map<String, String> conflictLabels = new LinkedHashMap<>();

    private int waitingRowIndex = -1; // 进入等待按键状态的行索引
    private int conflictPendingKey = -1;
    private int conflictPendingRow = -1;

    public KeyBindingsSettingsScreen(Screen parent, UserConfig config, Path configPath) {
        super(Text.literal("键位设置"));
        this.parent = parent;
        this.config = config == null ? UserConfig.defaults() : config;
        this.configPath = configPath;
    }

    @Override
    protected void init() {
        rows.clear();
        KeyBindings kb0 = this.config.keyBindings;
        if (kb0 == null) {
            kb0 = KeyBindings.defaults();
            this.config.keyBindings = kb0;
        }
        final KeyBindings kb = kb0;
        // 列出各动作（按 KeyBindings 字段顺序）
        rows.add(new ActionRow("openEditor", "打开编辑器", () -> kb.openEditor, v -> kb.openEditor = v));
        rows.add(new ActionRow("switchMode", "切换模式 (积木/IDE)", () -> kb.switchMode, v -> kb.switchMode = v));
        rows.add(new ActionRow("reload", "重载数据包", () -> kb.reload, v -> kb.reload = v));
        rows.add(new ActionRow("save", "保存", () -> kb.save, v -> kb.save = v));
        rows.add(new ActionRow("help", "帮助 (F1)", () -> kb.help, v -> kb.help = v));
        rows.add(new ActionRow("togglePalette", "切换调色板", () -> kb.togglePalette, v -> kb.togglePalette = v));

        // 顶部按钮
        int bx = 6;
        addDrawableChild(ButtonWidget.builder(Text.literal("保存"), b -> save())
                .dimensions(bx, 4, 70, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("重置默认"), b -> resetDefaults())
                .dimensions(bx + 74, 4, 90, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("返回"), b -> close())
                .dimensions(this.width - 76, 4, 70, 18).build());

        conflictLabels.clear();
    }

    private void save() {
        if (configPath != null) {
            try {
                config.save(configPath);
                setStatus("已保存到 " + configPath);
            } catch (Exception e) {
                setStatus("保存失败: " + e.getMessage());
            }
        }
    }

    private void resetDefaults() {
        this.config.keyBindings = KeyBindings.defaults();
        conflictLabels.clear();
        waitingRowIndex = -1;
        conflictPendingKey = -1;
        conflictPendingRow = -1;
        clearAndInit();
        setStatus("已恢复默认键位");
    }

    private String statusMessage = null;
    private long statusTime = 0;

    private void setStatus(String s) {
        statusMessage = s;
        statusTime = System.currentTimeMillis();
    }

    /** 计算每行键位冲突情况（同键被多个动作占用）。 */
    private void recomputeConflicts() {
        conflictLabels.clear();
        Map<Integer, List<String>> byKey = new LinkedHashMap<>();
        for (ActionRow r : rows) {
            int k = r.getter().getAsInt();
            byKey.computeIfAbsent(k, x -> new ArrayList<>()).add(r.id);
        }
        for (ActionRow r : rows) {
            int k = r.getter().getAsInt();
            List<String> ids = byKey.get(k);
            if (ids != null && ids.size() > 1) {
                conflictLabels.put(r.id, "冲突: " + String.join(", ", ids));
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xFF1E1E1E);
        super.render(context, mouseX, mouseY, delta);

        // 表头
        int lx = 10;
        int kx = this.width / 2;
        context.drawTextWithShadow(this.textRenderer, Text.literal("动作"), lx, TOP_BAR_H - 12, 0xFFAAAAAA);
        context.drawTextWithShadow(this.textRenderer, Text.literal("键位"), kx, TOP_BAR_H - 12, 0xFFAAAAAA);

        recomputeConflicts();

        int y = TOP_BAR_H;
        for (int i = 0; i < rows.size(); i++) {
            ActionRow row = rows.get(i);
            boolean waiting = (i == waitingRowIndex);
            boolean conflict = conflictLabels.containsKey(row.id);
            int rowBg = (mouseX >= 6 && mouseX < this.width - 6 && mouseY >= y && mouseY < y + ROW_H)
                    ? 0xFF333333 : 0xFF262626;
            context.fill(6, y, this.width - 6, y + ROW_H - 1, rowBg);
            // 动作名
            int labelColor = conflict ? 0xFFFF7777 : 0xFFEEEEEE;
            context.drawTextWithShadow(this.textRenderer, Text.literal(row.label), lx, y + 5, labelColor);
            // 键位
            String keyName = keyDisplayName(row.getter().getAsInt());
            String display = waiting ? "> 按下任意键 <" : "[" + keyName + "]";
            int keyColor = waiting ? 0xFFFFFFAA : (conflict ? 0xFFFF5555 : 0xFF55FF55);
            context.drawTextWithShadow(this.textRenderer, Text.literal(display), kx, y + 5, keyColor);
            // 冲突提示
            if (conflict) {
                String cl = conflictLabels.get(row.id);
                context.drawTextWithShadow(this.textRenderer,
                        Text.literal(cl).formatted(Formatting.RED),
                        kx + 120, y + 5, 0xFFFF5555);
            }
            y += ROW_H;
        }

        // 冲突待确认覆盖
        if (conflictPendingKey >= 0 && conflictPendingRow >= 0) {
            int bw = 360;
            int bh = 80;
            int bxp = (this.width - bw) / 2;
            int byp = (this.height - bh) / 2;
            context.fill(bxp, byp, bxp + bw, byp + bh, 0xF0000000);
            context.drawBorder(bxp, byp, bw, bh, 0xFFFF5555);
            ActionRow r = rows.get(conflictPendingRow);
            String msg = "键位 [" + keyDisplayName(conflictPendingKey) + "] 已被以下动作占用，是否覆盖？";
            context.drawTextWithShadow(this.textRenderer, Text.literal(msg),
                    bxp + 8, byp + 8, 0xFFFFAAAA);
            context.drawTextWithShadow(this.textRenderer,
                    Text.literal("目标: " + r.label + "   冲突: " + conflictLabels.getOrDefault(r.id, "")),
                    bxp + 8, byp + 22, 0xFFDDDDDD);
            context.drawTextWithShadow(this.textRenderer,
                    Text.literal("[Enter] 覆盖    [Esc] 取消").formatted(Formatting.YELLOW),
                    bxp + 8, byp + 44, 0xFFFFFFAA);
        }

        // 状态消息
        if (statusMessage != null) {
            long age = System.currentTimeMillis() - statusTime;
            if (age > 4000) {
                statusMessage = null;
            } else {
                context.drawTextWithShadow(this.textRenderer, Text.literal(statusMessage),
                        6, this.height - 14, 0xFF55FF55);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (conflictPendingKey >= 0) {
            return true; // 等待用户按 Enter/Esc
        }
        int y = TOP_BAR_H;
        for (int i = 0; i < rows.size(); i++) {
            if (mouseX >= 6 && mouseX < this.width - 6 && mouseY >= y && mouseY < y + ROW_H) {
                waitingRowIndex = i;
                return true;
            }
            y += ROW_H;
        }
        waitingRowIndex = -1;
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Esc 取消等待或取消冲突确认
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            if (conflictPendingKey >= 0) {
                conflictPendingKey = -1;
                conflictPendingRow = -1;
                return true;
            }
            if (waitingRowIndex >= 0) {
                waitingRowIndex = -1;
                return true;
            }
            close();
            return true;
        }
        // 冲突待确认：Enter 覆盖
        if (conflictPendingKey >= 0) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                    || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
                ActionRow r = rows.get(conflictPendingRow);
                r.setter().accept(conflictPendingKey);
                conflictPendingKey = -1;
                conflictPendingRow = -1;
                recomputeConflicts();
                return true;
            }
            return true; // 其它按键忽略
        }
        // 等待按键绑定
        if (waitingRowIndex >= 0) {
            ActionRow r = rows.get(waitingRowIndex);
            int newKey = keyCode;
            // 检测与其它行冲突
            boolean conflict = false;
            for (int j = 0; j < rows.size(); j++) {
                if (j == waitingRowIndex) {
                    continue;
                }
                if (rows.get(j).getter().getAsInt() == newKey) {
                    conflict = true;
                    break;
                }
            }
            if (conflict) {
                // 进入待确认状态
                conflictPendingKey = newKey;
                conflictPendingRow = waitingRowIndex;
                waitingRowIndex = -1;
                recomputeConflicts();
                return true;
            }
            r.setter().accept(newKey);
            waitingRowIndex = -1;
            recomputeConflicts();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** 把 GLFW 键码转为可读名称。 */
    private static String keyDisplayName(int keyCode) {
        if (keyCode <= 0) {
            return "未绑定";
        }
        try {
            InputUtil.Key key = InputUtil.fromKeyCode(keyCode, 0);
            Text t = key.getLocalizedText();
            if (t != null) {
                String s = t.getString();
                if (s != null && !s.isEmpty()) {
                    return s;
                }
            }
        } catch (Throwable ignored) {
            // 忽略
        }
        // 回退到 GLFW_KEY_NAME 或数字
        try {
            String name = org.lwjgl.glfw.GLFW.glfwGetKeyName(keyCode, 0);
            if (name != null && !name.isEmpty()) {
                return name.toUpperCase(java.util.Locale.ROOT);
            }
        } catch (Throwable ignored) {
            // 忽略
        }
        return "KEY_" + keyCode;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }
}
