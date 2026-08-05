package com.dpe.client;

import com.dpe.common.text.ClickEvent;
import com.dpe.common.text.HoverEvent;
import com.dpe.common.text.TextComponent;
import com.dpe.common.text.TextComponentEditor;
import com.dpe.common.text.TextStyle;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * 文本组件可视化编辑器子屏幕：样式 / clickEvent / hoverEvent / JSON 双向同步 + 实时预览。
 */
public class TextComponentScreen extends Screen {

    private static final List<Formatting> COLOR_FORMATTINGS = List.of(
            Formatting.BLACK, Formatting.DARK_BLUE, Formatting.DARK_GREEN, Formatting.DARK_AQUA,
            Formatting.DARK_RED, Formatting.DARK_PURPLE, Formatting.GOLD, Formatting.GRAY,
            Formatting.DARK_GRAY, Formatting.BLUE, Formatting.GREEN, Formatting.AQUA,
            Formatting.RED, Formatting.LIGHT_PURPLE, Formatting.YELLOW, Formatting.WHITE);

    private static final List<String> CLICK_ACTIONS = List.of(
            "(none)", "run_command", "suggest_command", "open_url",
            "copy_to_clipboard", "change_page", "open_file");
    private static final List<String> HOVER_ACTIONS = List.of(
            "(none)", "show_text", "show_item", "show_entity");

    private final TextComponentEditor editor;
    private final Consumer<String> callback;
    private final Screen parent;

    private TextFieldWidget jsonField;
    private TextFieldWidget clickValueField;
    private TextFieldWidget hoverValueField;
    private TextFieldWidget textField;

    private int clickActionIndex = 0;
    private int hoverActionIndex = 0;
    private String validationError = null;
    private boolean syncing = false;

    public TextComponentScreen(String initialJson, Consumer<String> callback, Screen parent) {
        super(Text.literal("Text Component Editor"));
        this.callback = callback;
        this.parent = parent;
        this.editor = new TextComponentEditor();
        if (initialJson != null && !initialJson.isBlank()) {
            try {
                editor.fromJson(initialJson);
            } catch (Exception ignored) {
                // 解析失败用空组件
            }
        }
    }

    @Override
    protected void init() {
        syncing = true;
        try {
            int leftX = 10;
            int topY = 30;
            int btnW = 60;
            int btnH = 16;
            int gap = 2;

            // 16 色按钮 (4 列 x 4 行)
            for (int i = 0; i < COLOR_FORMATTINGS.size(); i++) {
                Formatting f = COLOR_FORMATTINGS.get(i);
                int col = i % 4;
                int row = i / 4;
                int bx = leftX + col * (btnW + gap);
                int by = topY + row * (btnH + gap);
                addDrawableChild(ButtonWidget.builder(
                                Text.literal(f.getName()).formatted(f),
                                b -> applyColor(f.getName()))
                        .dimensions(bx, by, btnW, btnH)
                        .build());
            }

            // 样式切换按钮
            int styleY = topY + 4 * (btnH + gap) + 4;
            addDrawableChild(ButtonWidget.builder(Text.literal("B"), b -> toggleStyle("bold"))
                    .dimensions(leftX, styleY, btnW, btnH).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("I"), b -> toggleStyle("italic"))
                    .dimensions(leftX + (btnW + gap), styleY, btnW, btnH).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("U"), b -> toggleStyle("underlined"))
                    .dimensions(leftX + 2 * (btnW + gap), styleY, btnW, btnH).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("S"), b -> toggleStyle("strikethrough"))
                    .dimensions(leftX + 3 * (btnW + gap), styleY, btnW, btnH).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("O"), b -> toggleStyle("obfuscated"))
                    .dimensions(leftX, styleY + btnH + gap, 2 * btnW + gap, btnH).build());

            // 文本内容输入
            int rightX = Math.max(280, this.width / 2);
            int fieldW = this.width - rightX - 10;
            textField = new TextFieldWidget(this.textRenderer, rightX, topY, fieldW, 18, Text.literal("text"));
            textField.setMaxLength(4096);
            textField.setText(safeText(editor.current().getText()));
            textField.setPlaceholder(Text.literal("text..."));
            textField.setChangedListener(s -> {
                if (!syncing) {
                    editor.current().setText(s);
                    syncJsonFromEditor();
                }
            });
            addDrawableChild(textField);

            // JSON 文本框（双向同步）
            int jsonY = topY + 24;
            jsonField = new TextFieldWidget(this.textRenderer, rightX, jsonY, fieldW, 60, Text.literal("json"));
            jsonField.setMaxLength(32767);
            jsonField.setDrawsBackground(true);
            jsonField.setText(editor.toJson());
            jsonField.setPlaceholder(Text.literal("{\"text\":\"...\"}"));
            jsonField.setChangedListener(s -> {
                if (!syncing) {
                    try {
                        editor.fromJson(s);
                        validationError = null;
                    } catch (Exception e) {
                        validationError = "JSON 解析错误: " + e.getMessage();
                    }
                }
            });
            addDrawableChild(jsonField);

            // clickEvent 动作按钮 + 值输入
            int clickY = jsonY + 68;
            addDrawableChild(ButtonWidget.builder(
                            Text.literal("Click: " + CLICK_ACTIONS.get(clickActionIndex)),
                            b -> cycleClickAction())
                    .dimensions(rightX, clickY, fieldW, 18).build());
            clickValueField = new TextFieldWidget(this.textRenderer, rightX, clickY + 20, fieldW, 18, Text.literal("value"));
            clickValueField.setMaxLength(4096);
            ClickEvent ce = editor.current().getClickEvent();
            clickValueField.setText(ce == null ? "" : ce.value());
            clickValueField.setPlaceholder(Text.literal("click value..."));
            addDrawableChild(clickValueField);

            // hoverEvent 动作按钮 + 值输入
            int hoverY = clickY + 42;
            addDrawableChild(ButtonWidget.builder(
                            Text.literal("Hover: " + HOVER_ACTIONS.get(hoverActionIndex)),
                            b -> cycleHoverAction())
                    .dimensions(rightX, hoverY, fieldW, 18).build());
            hoverValueField = new TextFieldWidget(this.textRenderer, rightX, hoverY + 20, fieldW, 18, Text.literal("value"));
            hoverValueField.setMaxLength(4096);
            HoverEvent he = editor.current().getHoverEvent();
            hoverValueField.setText(he == null ? "" : String.valueOf(he.contents()));
            hoverValueField.setPlaceholder(Text.literal("hover value..."));
            addDrawableChild(hoverValueField);

            // Done / Cancel
            int bottomY = this.height - 24;
            addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> finish(true))
                    .dimensions(this.width / 2 - 110, bottomY, 100, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Cancel"), b -> finish(false))
                    .dimensions(this.width / 2 + 10, bottomY, 100, 20).build());
        } finally {
            syncing = false;
        }
    }

    private String safeText(String s) {
        return s == null ? "" : s;
    }

    private void applyColor(String colorName) {
        TextStyle ts = new TextStyle();
        ts.setColor(colorName);
        editor.applyStyle(ts);
        syncJsonFromEditor();
    }

    private void toggleStyle(String attr) {
        TextStyle cur = editor.current().getStyle();
        TextStyle ts = new TextStyle();
        switch (attr) {
            case "bold" -> ts.setBold(!Boolean.TRUE.equals(cur.getBold()));
            case "italic" -> ts.setItalic(!Boolean.TRUE.equals(cur.getItalic()));
            case "underlined" -> ts.setUnderlined(!Boolean.TRUE.equals(cur.getUnderlined()));
            case "strikethrough" -> ts.setStrikethrough(!Boolean.TRUE.equals(cur.getStrikethrough()));
            case "obfuscated" -> ts.setObfuscated(!Boolean.TRUE.equals(cur.getObfuscated()));
        }
        editor.applyStyle(ts);
        syncJsonFromEditor();
    }

    private void cycleClickAction() {
        clickActionIndex = (clickActionIndex + 1) % CLICK_ACTIONS.size();
        applyClickEvent();
        rebuildButtons();
    }

    private void cycleHoverAction() {
        hoverActionIndex = (hoverActionIndex + 1) % HOVER_ACTIONS.size();
        applyHoverEvent();
        rebuildButtons();
    }

    private void applyClickEvent() {
        String action = CLICK_ACTIONS.get(clickActionIndex);
        if ("(none)".equals(action)) {
            editor.setClickEvent(null);
        } else {
            String v = clickValueField.getText();
            editor.setClickEvent(new ClickEvent(action, v == null ? "" : v));
        }
        syncJsonFromEditor();
    }

    private void applyHoverEvent() {
        String action = HOVER_ACTIONS.get(hoverActionIndex);
        if ("(none)".equals(action)) {
            editor.setHoverEvent(null);
        } else {
            String v = hoverValueField.getText();
            editor.setHoverEvent(new HoverEvent(action, v == null ? "" : v));
        }
        syncJsonFromEditor();
    }

    private void syncJsonFromEditor() {
        if (jsonField == null) {
            return;
        }
        syncing = true;
        try {
            jsonField.setText(editor.toJson());
        } finally {
            syncing = false;
        }
    }

    private void rebuildButtons() {
        clearAndInit();
    }

    private void finish(boolean save) {
        if (save) {
            // 同步 click/hover 值
            applyClickEvent();
            applyHoverEvent();
            // 校验 clickEvent 命令
            ClickEvent ce = editor.current().getClickEvent();
            String err = TextComponentEditor.validateClickEventCommand(ce);
            if (err != null) {
                validationError = err;
                return;
            }
            String json = editor.toJson();
            Consumer<String> cb = callback;
            if (cb != null) {
                cb.accept(json);
            }
        }
        if (this.client != null) {
            this.client.setScreen(parent);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, 0xFFFFFF);

        // 左侧分组标题
        context.drawTextWithShadow(this.textRenderer, Text.literal("Color"), 10, 20, 0xCCCCCC);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Style"), 10,
                30 + 4 * 18, 0xCCCCCC);
        context.drawTextWithShadow(this.textRenderer, Text.literal("Preview:"),
                Math.max(280, this.width / 2), this.height - 80, 0xCCCCCC);

        // 实时预览：渲染当前 TextComponent（含样式）
        Text preview = toMcText(editor.getRoot());
        context.drawTextWithShadow(this.textRenderer, preview,
                Math.max(280, this.width / 2), this.height - 60, 0xFFFFFF);

        // 校验错误提示
        if (validationError != null) {
            context.drawTextWithShadow(this.textRenderer,
                    Text.literal("Error: " + validationError).formatted(Formatting.RED),
                    10, this.height - 30, 0xFF5555);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /** 把 common TextComponent 转为 MC Text 以便用 DrawContext 渲染（含样式）。 */
    private static Text toMcText(com.dpe.common.text.TextComponent tc) {
        if (tc == null) {
            return Text.literal("");
        }
        MutableText mt;
        String text = tc.getText();
        if (tc.getTranslate() != null && !tc.getTranslate().isEmpty()) {
            mt = Text.translatable(tc.getTranslate());
        } else if (text != null && !text.isEmpty()) {
            mt = Text.literal(text);
        } else if (tc.getSelector() != null && !tc.getSelector().isEmpty()) {
            mt = Text.literal("@" + tc.getSelector());
        } else {
            mt = Text.literal("");
        }
        mt.setStyle(toMcStyle(tc.getStyle()));
        for (com.dpe.common.text.TextComponent child : tc.getExtra()) {
            mt.append(toMcText(child));
        }
        return mt;
    }

    /** common TextStyle → MC Style。 */
    private static Style toMcStyle(TextStyle ts) {
        if (ts == null) {
            return Style.EMPTY;
        }
        Style s = Style.EMPTY;
        if (ts.getColor() != null) {
            TextColor color = resolveColor(ts.getColor());
            if (color != null) {
                s = s.withColor(color);
            }
        }
        if (Boolean.TRUE.equals(ts.getBold())) {
            s = s.withBold(true);
        }
        if (Boolean.TRUE.equals(ts.getItalic())) {
            s = s.withItalic(true);
        }
        if (Boolean.TRUE.equals(ts.getUnderlined())) {
            s = s.withUnderline(true);
        }
        if (Boolean.TRUE.equals(ts.getStrikethrough())) {
            s = s.withStrikethrough(true);
        }
        if (Boolean.TRUE.equals(ts.getObfuscated())) {
            s = s.withObfuscated(true);
        }
        return s;
    }

    /** 解析颜色字符串：hex (#RRGGBB) 或 vanilla 颜色名。 */
    private static TextColor resolveColor(String color) {
        if (color == null || color.isEmpty()) {
            return null;
        }
        String c = color.trim();
        if (c.startsWith("#")) {
            try {
                int rgb = Integer.parseInt(c.substring(1), 16);
                return TextColor.fromRgb(rgb);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        Formatting f = Formatting.byName(c.toLowerCase(Locale.ROOT));
        if (f != null && f.isColor()) {
            return TextColor.fromFormatting(f);
        }
        return null;
    }
}
