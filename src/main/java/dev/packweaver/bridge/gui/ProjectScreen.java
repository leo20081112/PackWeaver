package dev.packweaver.bridge.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import dev.packweaver.bridge.pack.PackProject;
import dev.packweaver.bridge.pack.Templates;

import java.util.List;

/**
 * 项目管理界面（规划书第 2.4 / 12 章）：新建项目（命名空间校验 + 模板选择）、打开、删除。
 */
public class ProjectScreen extends Screen {
    private TextFieldWidget nameField;
    private TextFieldWidget nsField;
    private int templateIndex;
    private ButtonWidget tplButton;
    private String message = "";
    private int messageColor = 0xFFE0E0E0;

    public ProjectScreen() {
        super(Text.translatable("screen.packweaver.projects"));
    }

    @Override
    protected void init() {
        nameField = new TextFieldWidget(this.textRenderer, this.width / 2 - 130, 40, 120, 16, Text.literal("项目名"));
        nsField = new TextFieldWidget(this.textRenderer, this.width / 2 + 10, 40, 120, 16, Text.literal("命名空间"));
        nameField.setMaxLength(32);
        nsField.setMaxLength(32);
        nameField.setText("我的项目");
        nsField.setText("my_pack");
        addSelectableChild(nameField);
        addSelectableChild(nsField);
        setInitialFocus(nameField);

        addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> {
                    templateIndex = (templateIndex + Templates.ALL.size() - 1) % Templates.ALL.size();
                    updateTemplateButton(tplButton);
                })
                .dimensions(this.width / 2 - 155, 64, 20, 18).build());
        tplButton = ButtonWidget.builder(Text.literal(""), b -> {
                    templateIndex = (templateIndex + 1) % Templates.ALL.size();
                    updateTemplateButton(tplButton);
                })
                .dimensions(this.width / 2 - 133, 64, 226, 18).build();
        updateTemplateButton(tplButton);
        addDrawableChild(tplButton);
        addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> {
                    templateIndex = (templateIndex + 1) % Templates.ALL.size();
                    updateTemplateButton(tplButton);
                })
                .dimensions(this.width / 2 + 95, 64, 20, 18).build());

        addDrawableChild(ButtonWidget.builder(Text.literal("创建项目"), b -> create())
                .dimensions(this.width / 2 - 130, 88, 120, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("刷新列表"), b -> message = "")
                .dimensions(this.width / 2 + 10, 88, 120, 20).build());

        List<String> projects = PackProject.listProjects();
        int y = 130;
        for (String ns : projects) {
            final String project = ns;
            addDrawableChild(ButtonWidget.builder(Text.literal("积木模式"), b ->
                            openProject(project, false))
                    .dimensions(this.width / 2 - 140, y, 60, 18).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("IDE 模式"), b ->
                            openProject(project, true))
                    .dimensions(this.width / 2 - 76, y, 60, 18).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("删除"), b -> {
                        try {
                            PackProject.load(project).delete();
                            message = "已删除 " + project;
                            messageColor = 0xFFFFA726;
                            this.clearAndInit();
                        } catch (Exception ex) {
                            message = "删除失败: " + ex.getMessage();
                            messageColor = 0xFFEF5350;
                        }
                    })
                    .dimensions(this.width / 2 + 90, y, 50, 18).build());
            y += 22;
        }
        addDrawableChild(ButtonWidget.builder(Text.translatable("screen.packweaver.done"), b -> close())
                .dimensions(this.width / 2 - 50, this.height - 26, 100, 20).build());
    }

    private void updateTemplateButton(ButtonWidget b) {
        Templates.Tpl t = Templates.ALL.get(templateIndex);
        b.setMessage(Text.literal("模板: " + t.name() + " " + "*".repeat(t.stars())));
    }

    private void create() {
        String ns = nsField.getText().trim();
        if (!ns.matches("[a-z][a-z0-9_]*")) {
            message = "命名空间只能小写英文/数字/下划线，且不能以数字开头";
            messageColor = 0xFFEF5350;
            return;
        }
        try {
            PackProject p = new PackProject();
            p.name = nameField.getText().trim();
            p.namespace = ns;
            Templates.apply(p, Templates.ALL.get(templateIndex).id());
            p.save();
            message = "项目 " + ns + " 创建成功（模板: " + Templates.ALL.get(templateIndex).name() + "）";
            messageColor = 0xFF66BB6A;
            this.clearAndInit();
        } catch (Exception ex) {
            message = "创建失败: " + ex.getMessage();
            messageColor = 0xFFEF5350;
        }
    }

    private void openProject(String ns, boolean ideMode) {
        try {
            PackProject p = PackProject.load(ns);
            assert this.client != null;
            if (ideMode) {
                this.client.setScreen(new CodeEditorScreen(p, "tick"));
            } else {
                this.client.setScreen(new BlockEditorScreen(p));
            }
        } catch (Exception ex) {
            message = "打开失败: " + ex.getMessage();
            messageColor = 0xFFEF5350;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 15, 0xFFFFFF);
        context.drawTextWithShadow(this.textRenderer, "项目名", this.width / 2 - 130, 30, 0xA0A0A0);
        context.drawTextWithShadow(this.textRenderer, "命名空间", this.width / 2 + 10, 30, 0xA0A0A0);
        nameField.render(context, mouseX, mouseY, delta);
        nsField.render(context, mouseX, mouseY, delta);
        context.drawTextWithShadow(this.textRenderer, Templates.ALL.get(templateIndex).learns(),
                this.width / 2 - 133, 110, 0x80CBC4);
        List<String> projects = PackProject.listProjects();
        if (projects.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, "（暂无项目，先创建一个）",
                    this.width / 2, 140, 0x808080);
        } else {
            for (int i = 0; i < projects.size(); i++) {
                context.drawTextWithShadow(this.textRenderer, "● " + projects.get(i),
                        this.width / 2 - 200, 134 + i * 22, 0xFFAB91);
            }
        }
        if (!message.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, message, this.width / 2, this.height - 48, messageColor);
        }
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
