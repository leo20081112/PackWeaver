package dev.packweaver.bridge.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 正则测试器（规划书第 14.6 章）：
 * 上方正则输入、中间测试文本、下方高亮匹配结果列表 + 常用模板。
 */
public class RegexScreen extends Screen {
    private TextFieldWidget regexField;
    private TextFieldWidget textField;
    private Pattern pattern;
    private String error = "";
    private final List<int[]> spans = new ArrayList<>(); // [start, end]

    public RegexScreen() {
        super(Text.literal("正则测试器"));
    }

    @Override
    protected void init() {
        regexField = new TextFieldWidget(this.textRenderer, 20, 34, this.width - 40, 16,
                Text.literal("正则"));
        regexField.setMaxLength(200);
        regexField.setText("(\\d+)\\.(\\d+)");
        textField = new TextFieldWidget(this.textRenderer, 20, 68, this.width - 40, 16,
                Text.literal("文本"));
        textField.setMaxLength(200);
        textField.setText("坐标 100.5 64.0 200.25，血量 20.0");
        addSelectableChild(regexField);
        addSelectableChild(textField);
        regexField.setChangedListener(t -> compile());
        textField.setChangedListener(t -> compile());
        setInitialFocus(textField);

        String[][] presets = {
                {"坐标", "[-\\d.]+ [-\\d.]+ [-\\d.]+"},
                {"选择器参数", "\\[\\w+="},
                {"物品ID", "minecraft:[a-z_]+"},
                {"NBT路径", "\\w+(\\[\\d+])?"},
                {"数字", "-?\\d+(\\.\\d+)?"}};
        int px = 20;
        for (String[] p : presets) {
            ButtonWidget b = ButtonWidget.builder(Text.literal(p[0]), btn -> {
                        regexField.setText(p[1]);
                        compile();
                    })
                    .dimensions(px, 92, 78, 16).build();
            addDrawableChild(b);
            px += 82;
        }
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.packweaver.done"), b -> close())
                .dimensions(this.width - 80, 6, 60, 18).build());
        compile();
    }

    private void compile() {
        spans.clear();
        error = "";
        try {
            pattern = Pattern.compile(regexField.getText());
            Matcher m = pattern.matcher(textField.getText());
            while (m.find() && spans.size() < 200) {
                spans.add(new int[]{m.start(), m.end()});
            }
        } catch (PatternSyntaxException e) {
            pattern = null;
            error = "正则无效: " + e.getDescription();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, "正则", 20, 26, 0xB0BEC5);
        regexField.render(context, mouseX, mouseY, delta);
        context.drawTextWithShadow(this.textRenderer, "测试文本", 20, 60, 0xB0BEC5);
        textField.render(context, mouseX, mouseY, delta);
        context.drawTextWithShadow(this.textRenderer, "常用模板", 20, 86, 0xB0BEC5);

        // 高亮测试文本：匹配段绿底
        int y = 118;
        String text = textField.getText();
        int x = 20;
        if (pattern != null && !spans.isEmpty()) {
            int cursor = 0;
            for (int[] span : spans) {
                if (span[0] > cursor) {
                    context.drawTextWithShadow(this.textRenderer, text.substring(cursor, span[0]), x, y, 0xFFE0E0E0);
                    x += this.textRenderer.getWidth(text.substring(cursor, span[0]));
                }
                String hit = text.substring(span[0], span[1]);
                context.fill(x - 1, y - 2, x + this.textRenderer.getWidth(hit) + 1, y + 10, 0xFF1B5E20);
                context.drawTextWithShadow(this.textRenderer, hit, x, y, 0xFFA5D6A7);
                x += this.textRenderer.getWidth(hit);
                cursor = span[1];
                if (x > this.width - 40) {
                    x = 20;
                    y += 13;
                }
            }
            if (cursor < text.length()) {
                context.drawTextWithShadow(this.textRenderer, text.substring(cursor), x, y, 0xFFE0E0E0);
            }
            y += 16;
        }

        context.drawTextWithShadow(this.textRenderer,
                pattern == null ? "§c" + error
                        : "匹配 " + spans.size() + " 处", 20, this.height - 40,
                pattern == null ? 0xFFEF5350 : 0xFF66BB6A);
        // 分组明细（前 6 个）
        if (pattern != null) {
            int groups = pattern.matcher("").groupCount();
            if (groups > 0) {
                int ry = this.height - 26;
                int shown = 0;
                for (int[] span : spans) {
                    if (shown++ >= 6 || ry > this.height - 8) {
                        break;
                    }
                    Matcher m = pattern.matcher(text);
                    m.region(span[0], span[1]);
                    if (m.find()) {
                        StringBuilder sb = new StringBuilder("组: ");
                        for (int g = 1; g <= groups; g++) {
                            sb.append('$').append(g).append("='").append(m.group(g)).append("' ");
                        }
                        context.drawTextWithShadow(this.textRenderer, sb.toString(), 20, ry, 0xFF90A4AE);
                    }
                }
            }
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void tick() {
        regexField.tick();
        textField.tick();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
