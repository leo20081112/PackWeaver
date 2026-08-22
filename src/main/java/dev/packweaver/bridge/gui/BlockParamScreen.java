package dev.packweaver.bridge.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import dev.packweaver.bridge.pack.BlockDefs;
import dev.packweaver.bridge.pack.BlockNode;
import dev.packweaver.bridge.pack.CodeGen;
import dev.packweaver.bridge.pack.PackProject;

import java.util.ArrayList;
import java.util.List;

/**
 * 积木参数编辑（规划书第 10 章参数输入 + 第 10.1 代码预览）。
 * 文本参数直接输入；选项参数循环切换；并提供 ▲▼移动 / 删除 / 设为插入点。
 */
public class BlockParamScreen extends Screen {
    private final BlockEditorScreen parent;
    private final PackProject project;
    private final BlockNode node;
    private final BlockDefs.BlockDef def;
    private final List<TextFieldWidget> fields = new ArrayList<>();
    private final List<BlockDefs.Param> textParams = new ArrayList<>();
    private String notice = "";

    public BlockParamScreen(BlockEditorScreen parent, PackProject project, BlockNode node) {
        super(Text.literal("积木参数"));
        this.parent = parent;
        this.project = project;
        this.node = node;
        this.def = BlockDefs.get(node.type);
    }

    @Override
    protected void init() {
        int y = 40;
        for (BlockDefs.Param p : def.params) {
            if (p.name.equals("__template")) {
                continue;
            }
            if (p.kind.equals("options")) {
                String cur = node.p(p.name, p.def);
                int idx = 0;
                for (int i = 0; i < p.options.length; i++) {
                    if (p.options[i].equals(cur)) {
                        idx = i;
                    }
                }
                final int[] sel = {idx};
                final BlockDefs.Param param = p;
                ButtonWidget b = ButtonWidget.builder(Text.literal(p.label + ": " + cur), btn -> {
                            sel[0] = (sel[0] + 1) % param.options.length;
                            node.params.put(param.name, param.options[sel[0]]);
                            btn.setMessage(Text.literal(param.label + ": " + param.options[sel[0]]));
                        })
                        .dimensions(this.width / 2 - 90, y, 180, 18).build();
                addDrawableChild(b);
            } else {
                final BlockDefs.Param param = p;
                TextFieldWidget f = new TextFieldWidget(this.textRenderer,
                        this.width / 2 - 90, y, 180, 16, Text.literal(p.label));
                f.setMaxLength(120);
                f.setText(node.p(p.name, p.def));
                f.setChangedListener(t -> node.params.put(param.name, t));
                fields.add(f);
                textParams.add(p);
                addSelectableChild(f);
                y += 4;
            }
            y += 22;
        }
        if (!fields.isEmpty()) {
            setInitialFocus(fields.get(0));
        }

        int by = Math.max(y + 6, 120);
        if (def.container) {
            addDrawableChild(ButtonWidget.builder(Text.literal("在此积木内继续添加（设为插入点）"), b -> {
                        parent.setInsertTarget(node);
                        back();
                    })
                    .dimensions(this.width / 2 - 90, by, 180, 18).build());
            by += 22;
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("▲ 上移"), b -> {
                    parent.moveSelected(node, -1);
                    back();
                })
                .dimensions(this.width / 2 - 90, by, 86, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("▼ 下移"), b -> {
                    parent.moveSelected(node, 1);
                    back();
                })
                .dimensions(this.width / 2 + 4, by, 86, 18).build());
        by += 22;
        addDrawableChild(ButtonWidget.builder(Text.literal("✕ 删除积木"), b -> {
                    parent.deleteSelected(node);
                    back();
                })
                .dimensions(this.width / 2 - 90, by, 180, 18).build());
        by += 22;
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.packweaver.done"), b -> back())
                .dimensions(this.width / 2 - 90, by, 180, 18).build());
    }

    private void back() {
        assert this.client != null;
        this.client.setScreen(parent);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(this.textRenderer,
                def.label + "（" + def.category + "）", this.width / 2, 18, 0xFFFFFFFF);
        int y = 40;
        for (BlockDefs.Param p : def.params) {
            if (p.name.equals("__template")) {
                continue;
            }
            if (p.kind.equals("options")) {
                // 按钮自绘文字，不重复画 label
            } else {
                context.drawTextWithShadow(this.textRenderer, p.label, this.width / 2 - 90, y - 9, 0xB0BEC5);
                y += 4;
            }
            y += 22;
        }
        for (TextFieldWidget f : fields) {
            f.render(context, mouseX, mouseY, delta);
        }
        String cmd = CodeGen.commandOf(node);
        if (!cmd.isBlank()) {
            context.drawTextWithShadow(this.textRenderer, "代码预览:", 8, this.height - 34, 0x4FC3F7);
            context.drawTextWithShadow(this.textRenderer, cmd, 8, this.height - 23, 0x81C784);
        }
        if (!notice.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, notice, this.width / 2, this.height - 44, 0xFFEF5350);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void tick() {
        for (TextFieldWidget f : fields) {
            f.tick();
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
