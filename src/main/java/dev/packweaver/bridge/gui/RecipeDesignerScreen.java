package dev.packweaver.bridge.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import dev.packweaver.bridge.pack.PackProject;
import dev.packweaver.bridge.pack.RecipeGen;

import java.io.IOException;

/**
 * 配方设计器（规划书第 5.1 章）：3x3 网格填材料 → 生成 1.20.1 配方 JSON
 * 写入项目 data/&lt;ns&gt;/recipes/&lt;名称&gt;.json，reload 后即生效。
 * 类型：有序合成 / 无序合成 / 熔炼。
 */
public class RecipeDesignerScreen extends Screen {
    private final PackProject project;
    private final Screen back;
    private final TextFieldWidget[][] grid = new TextFieldWidget[3][3];
    private TextFieldWidget resultField;
    private TextFieldWidget countField;
    private TextFieldWidget nameField;
    private ButtonWidget typeButton;
    private int typeIndex;
    private String message = "";
    private static final String[] TYPES = {"有序合成", "无序合成", "熔炼"};

    public RecipeDesignerScreen(PackProject project, Screen back) {
        super(Text.literal("配方设计器 - " + project.namespace));
        this.project = project;
        this.back = back;
    }

    @Override
    protected void init() {
        int gx = this.width / 2 - 110;
        int gy = 40;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                grid[r][c] = new TextFieldWidget(this.textRenderer, gx + c * 76, gy + r * 22, 72, 16,
                        Text.literal("材料" + r + c));
                grid[r][c].setMaxLength(60);
                addSelectableChild(grid[r][c]);
            }
        }
        resultField = new TextFieldWidget(this.textRenderer, this.width / 2 + 4, gy, 150, 16,
                Text.literal("产物"));
        resultField.setMaxLength(60);
        countField = new TextFieldWidget(this.textRenderer, this.width / 2 + 4, gy + 30, 60, 16,
                Text.literal("数量"));
        countField.setMaxLength(3);
        countField.setText("1");
        nameField = new TextFieldWidget(this.textRenderer, this.width / 2 + 4, gy + 60, 150, 16,
                Text.literal("配方名"));
        nameField.setMaxLength(40);
        nameField.setText("my_recipe");
        addSelectableChild(resultField);
        addSelectableChild(countField);
        addSelectableChild(nameField);

        typeButton = ButtonWidget.builder(Text.literal("类型: 有序合成"), b -> {
                    typeIndex = (typeIndex + 1) % TYPES.length;
                    typeButton.setMessage(Text.literal("类型: " + TYPES[typeIndex]));
                })
                .dimensions(this.width / 2 + 4, gy + 90, 150, 18).build();
        addDrawableChild(typeButton);

        addDrawableChild(ButtonWidget.builder(Text.literal("💾 生成配方"), b -> generate())
                .dimensions(this.width / 2 - 110, gy + 74, 226, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.packweaver.done"), b -> {
                    assert this.client != null;
                    this.client.setScreen(back);
                })
                .dimensions(this.width / 2 - 60, this.height - 28, 120, 18).build());
    }

    private void generate() {
        String[][] g = new String[3][3];
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                g[r][c] = grid[r][c].getText();
            }
        }
        String name = nameField.getText().trim().toLowerCase().replace(' ', '_');
        if (!name.matches("[a-z0-9_/]+")) {
            message = "§c配方名只能小写字母/数字/下划线";
            return;
        }
        try {
            String json = switch (TYPES[typeIndex]) {
                case "无序合成" -> RecipeGen.shapeless(g,
                        resultField.getText(), parseInt(countField.getText(), 1));
                case "熔炼" -> RecipeGen.smelting(g[0][0], resultField.getText());
                default -> RecipeGen.shaped(g, resultField.getText(), parseInt(countField.getText(), 1));
            };
            project.files.put(project.namespace + "/recipes/" + name + ".json", json);
            project.save();
            message = "§a已生成 " + name + ".json（reload 后生效）";
        } catch (IllegalArgumentException e) {
            message = "§c" + e.getMessage();
        } catch (IOException e) {
            message = "§c保存失败: " + e.getMessage();
        }
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 10, 0xFFFFFF);
        // 3x3 网格底色
        int gx = this.width / 2 - 110;
        int gy = 40;
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                context.fill(gx + c * 76 - 2, gy + r * 22 - 2, gx + c * 76 + 74, gy + r * 22 + 18, 0x40333A45);
                grid[r][c].render(context, mouseX, mouseY, delta);
            }
        }
        context.drawTextWithShadow(this.textRenderer, "材料（可留空）", gx, gy - 11, 0xB0BEC5);
        context.drawTextWithShadow(this.textRenderer, "产物 ID", this.width / 2 + 4, gy - 11, 0xB0BEC5);
        resultField.render(context, mouseX, mouseY, delta);
        context.drawTextWithShadow(this.textRenderer, "数量", this.width / 2 + 4, gy + 22, 0xB0BEC5);
        countField.render(context, mouseX, mouseY, delta);
        context.drawTextWithShadow(this.textRenderer, "配方名", this.width / 2 + 4, gy + 52, 0xB0BEC5);
        nameField.render(context, mouseX, mouseY, delta);
        if (!message.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, message, this.width / 2, this.height - 46,
                    message.startsWith("§a") ? 0xFF66BB6A : 0xFFEF5350);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void tick() {
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                grid[r][c].tick();
            }
        }
        resultField.tick();
        countField.tick();
        nameField.tick();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
