package com.dpe.client;

import com.dpe.common.model.Project;
import com.dpe.common.template.BuiltinTemplates;
import com.dpe.common.template.DatapackTemplate;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ProjectWizardScreen extends Screen {
    private TextFieldWidget nameField;
    private TextFieldWidget namespaceField;
    private TextFieldWidget descriptionField;
    private ButtonWidget createButton;
    private ButtonWidget cancelButton;
    private Project selectedProject;
    private boolean isCreatingNew = true;
    private String errorMessage = null;
    private int scrollOffset = 0;
    private static final int PROJECT_ITEM_HEIGHT = 30;
    private static final int MAX_VISIBLE_PROJECTS = 6;
    private static final int LIST_HEIGHT = MAX_VISIBLE_PROJECTS * PROJECT_ITEM_HEIGHT;
    
    private static final int TEMPLATE_ITEM_HEIGHT = 40;
    private static final int MAX_VISIBLE_TEMPLATES = 5;
    private int templateScrollOffset = 0;
    private String selectedTemplateId = "blank";
    private static final int TEMPLATE_LIST_HEIGHT = MAX_VISIBLE_TEMPLATES * TEMPLATE_ITEM_HEIGHT;
    
    private static final List<TemplateOption> TEMPLATE_OPTIONS = buildTemplateOptions();
    
    private final List<LabelInfo> labels = new ArrayList<>();
    private final List<LabelInfo> hintTexts = new ArrayList<>();
    private String headerText = "";
    
    private static class TemplateOption {
        final String id;
        final String name;
        final String description;
        
        TemplateOption(String id, String name, String description) {
            this.id = id;
            this.name = name;
            this.description = description;
        }
    }
    
    private static class LabelInfo {
        final String text;
        final int x;
        final int y;
        final int color;
        
        LabelInfo(String text, int x, int y, int color) {
            this.text = text;
            this.x = x;
            this.y = y;
            this.color = color;
        }
    }
    
    private static List<TemplateOption> buildTemplateOptions() {
        List<TemplateOption> options = new ArrayList<>();
        options.add(new TemplateOption("blank", "空白项目", "从零开始，不包含任何积木"));
        for (DatapackTemplate t : BuiltinTemplates.all()) {
            options.add(new TemplateOption(t.id(), t.title(), t.description()));
        }
        return options;
    }

    public ProjectWizardScreen() {
        super(Text.literal("Project Wizard"));
    }

    @Override
    protected void init() {
        labels.clear();
        hintTexts.clear();
        
        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int panelWidth = 380;
        int panelHeight = isCreatingNew ? 480 : 400;
        int panelX = centerX - panelWidth / 2;
        int panelY = centerY - panelHeight / 2;

        if (isCreatingNew) {
            initCreateMode(panelX, panelY, panelWidth, panelHeight);
        } else {
            initSelectMode(panelX, panelY, panelWidth, panelHeight);
        }
    }

    private void initCreateMode(int panelX, int panelY, int panelWidth, int panelHeight) {
        int labelWidth = 80;
        int fieldWidth = 260;
        int fieldX = panelX + labelWidth + 10;

        int currentY = panelY + 30;

        labels.add(new LabelInfo("项目名称:", panelX + 10, currentY, 0xFFFFFF));
        this.nameField = new TextFieldWidget(
                this.textRenderer, fieldX, currentY, fieldWidth, 20,
                Text.literal("项目名称")
        );
        this.nameField.setMaxLength(64);
        this.nameField.setFocused(true);
        this.nameField.setEditableColor(0xFFFFFF);
        this.nameField.setUneditableColor(0x707070);
        this.nameField.setTextPredicate(this::validateName);
        this.addSelectableChild(this.nameField);
        currentY += 35;

        labels.add(new LabelInfo("命名空间:", panelX + 10, currentY, 0xFFFFFF));
        this.namespaceField = new TextFieldWidget(
                this.textRenderer, fieldX, currentY, fieldWidth, 20,
                Text.literal("命名空间")
        );
        this.namespaceField.setMaxLength(32);
        this.namespaceField.setEditableColor(0xFFFFFF);
        this.namespaceField.setUneditableColor(0x707070);
        this.namespaceField.setTextPredicate(this::validateNamespaceInput);
        this.addSelectableChild(this.namespaceField);
        hintTexts.add(new LabelInfo("小写字母、数字、下划线", fieldX, currentY + 22, 0x808080));
        currentY += 45;

        labels.add(new LabelInfo("描述:", panelX + 10, currentY, 0xFFFFFF));
        this.descriptionField = new TextFieldWidget(
                this.textRenderer, fieldX, currentY, fieldWidth, 20,
                Text.literal("描述")
        );
        this.descriptionField.setMaxLength(256);
        this.descriptionField.setEditableColor(0xFFFFFF);
        this.descriptionField.setUneditableColor(0x707070);
        this.addSelectableChild(this.descriptionField);
        currentY += 45;

        labels.add(new LabelInfo("选择模板:", panelX + 10, currentY, 0xFFFFFF));
        currentY += 20;
        
        int templateListTop = currentY;
        int templateListBottom = templateListTop + TEMPLATE_LIST_HEIGHT;
        
        int visibleCount = Math.min(TEMPLATE_OPTIONS.size() - templateScrollOffset, MAX_VISIBLE_TEMPLATES);
        for (int i = 0; i < visibleCount; i++) {
            int idx = templateScrollOffset + i;
            if (idx >= TEMPLATE_OPTIONS.size()) break;
            
            TemplateOption opt = TEMPLATE_OPTIONS.get(idx);
            int itemY = templateListTop + i * TEMPLATE_ITEM_HEIGHT;
            int itemX = panelX + 10;
            int itemWidth = panelWidth - 60;
            
            int finalI = i;
            ButtonWidget templateBtn = ButtonWidget.builder(Text.literal(""), btn -> {
                selectedTemplateId = TEMPLATE_OPTIONS.get(templateScrollOffset + finalI).id;
                init();
            }).dimensions(itemX, itemY, itemWidth, TEMPLATE_ITEM_HEIGHT - 2).build();
            this.addDrawableChild(templateBtn);
        }
        
        if (TEMPLATE_OPTIONS.size() > MAX_VISIBLE_TEMPLATES) {
            ButtonWidget scrollUpBtn = ButtonWidget.builder(Text.literal("^"), btn -> {
                if (templateScrollOffset > 0) templateScrollOffset--;
                init();
            }).dimensions(panelX + panelWidth - 45, templateListTop, 35, 15).build();
            
            ButtonWidget scrollDownBtn = ButtonWidget.builder(Text.literal("v"), btn -> {
                if (templateScrollOffset < TEMPLATE_OPTIONS.size() - MAX_VISIBLE_TEMPLATES) templateScrollOffset++;
                init();
            }).dimensions(panelX + panelWidth - 45, templateListBottom - 15, 35, 15).build();
            
            this.addDrawableChild(scrollUpBtn);
            this.addDrawableChild(scrollDownBtn);
        }
        
        currentY = templateListBottom + 10;

        if (errorMessage != null) {
            hintTexts.add(new LabelInfo(errorMessage, fieldX, currentY, 0xFF5555));
            currentY += 20;
        }

        int buttonWidth = 100;
        int buttonSpacing = 15;
        int totalButtonsWidth = buttonWidth * 2 + buttonSpacing;
        int buttonStartX = panelX + panelWidth / 2 - totalButtonsWidth / 2;

        this.createButton = ButtonWidget.builder(Text.literal("创建"), btn -> onCreateProject())
                .dimensions(buttonStartX, panelY + panelHeight - 50, buttonWidth, 20)
                .build();
        this.cancelButton = ButtonWidget.builder(Text.literal("返回"), btn -> switchToSelectMode())
                .dimensions(buttonStartX + buttonWidth + buttonSpacing, panelY + panelHeight - 50, buttonWidth, 20)
                .build();

        this.addDrawableChild(createButton);
        this.addDrawableChild(cancelButton);
    }

    private void initSelectMode(int panelX, int panelY, int panelWidth, int panelHeight) {
        List<Project> projects = DatapackEditorClient.config().projects;

        headerText = "Select Project";

        int listTop = panelY + 50;
        int listBottom = listTop + LIST_HEIGHT;

        if (projects != null && !projects.isEmpty()) {
            int visibleCount = Math.min(projects.size() - scrollOffset, MAX_VISIBLE_PROJECTS);
            for (int i = 0; i < visibleCount; i++) {
                int projectIndex = scrollOffset + i;
                if (projectIndex >= projects.size()) break;

                Project project = projects.get(projectIndex);
                int itemY = listTop + i * PROJECT_ITEM_HEIGHT;
                int itemX = panelX + 10;
                int itemWidth = panelWidth - 20;

                ButtonWidget projectButton = ButtonWidget.builder(Text.literal(project.name() + " (" + project.namespace() + ")"), btn -> {
                    this.selectedProject = project;
                }).dimensions(itemX, itemY, itemWidth, PROJECT_ITEM_HEIGHT - 5).build();

                this.addDrawableChild(projectButton);
            }

            if (projects.size() > MAX_VISIBLE_PROJECTS) {
                ButtonWidget scrollUpBtn = ButtonWidget.builder(Text.literal("^"), btn -> {
                    if (scrollOffset > 0) scrollOffset--;
                }).dimensions(panelX + panelWidth - 30, listTop, 20, 15).build();

                ButtonWidget scrollDownBtn = ButtonWidget.builder(Text.literal("v"), btn -> {
                    if (scrollOffset < projects.size() - MAX_VISIBLE_PROJECTS) scrollOffset++;
                }).dimensions(panelX + panelWidth - 30, listBottom - 15, 20, 15).build();

                this.addDrawableChild(scrollUpBtn);
                this.addDrawableChild(scrollDownBtn);
            }
        } else {
            headerText = "No projects found";
        }

        int buttonY = panelY + panelHeight - 50;
        int buttonWidth = 120;
        int buttonSpacing = 20;
        int totalWidth = buttonWidth * 2 + buttonSpacing;
        int startX = panelX + panelWidth / 2 - totalWidth / 2;

        ButtonWidget newProjectBtn = ButtonWidget.builder(Text.literal("New Project"), btn -> switchToCreateMode())
                .dimensions(startX, buttonY, buttonWidth, 20)
                .build();

        ButtonWidget openBtn = ButtonWidget.builder(Text.literal("Open"), btn -> onOpenProject())
                .dimensions(startX + buttonWidth + buttonSpacing, buttonY, buttonWidth, 20)
                .build();

        openBtn.active = selectedProject != null;

        this.addDrawableChild(newProjectBtn);
        this.addDrawableChild(openBtn);
    }

    private void switchToCreateMode() {
        isCreatingNew = true;
        children().clear();
        init();
    }

    private void switchToSelectMode() {
        isCreatingNew = false;
        selectedProject = null;
        scrollOffset = 0;
        children().clear();
        init();
    }

    private boolean validateName(String name) {
        return name != null && !name.isBlank() && name.length() <= 64;
    }

    private boolean validateNamespaceInput(String input) {
        if (input == null || input.isBlank()) {
            return true;
        }
        return input.matches("^[a-z][a-z0-9_]*$");
    }

    private boolean validateNamespace(String namespace) {
        if (namespace == null || namespace.isBlank()) {
            return false;
        }
        return namespace.matches("^[a-z][a-z0-9_]*$");
    }

    private void onCreateProject() {
        String name = nameField.getText().trim();
        String namespace = namespaceField.getText().trim();
        String description = descriptionField.getText().trim();

        if (name.isBlank()) {
            errorMessage = "项目名称不能为空";
            switchToCreateMode();
            return;
        }

        if (!validateNamespace(namespace)) {
            errorMessage = "命名空间格式无效";
            switchToCreateMode();
            return;
        }

        boolean namespaceExists = DatapackEditorClient.config().projects.stream()
                .anyMatch(p -> p.namespace().equals(namespace));
        if (namespaceExists) {
            errorMessage = "命名空间已存在";
            switchToCreateMode();
            return;
        }

        long now = System.currentTimeMillis() / 1000;
        Project project = new Project(
                UUID.randomUUID().toString(),
                name,
                namespace,
                description,
                now,
                now
        );

        if (createProjectStructure(project)) {
            DatapackEditorClient.config().addProject(project);
            DatapackEditorClient.saveConfig();
            openEditorWithProject(project, selectedTemplateId);
        } else {
            errorMessage = "创建项目文件失败";
            switchToCreateMode();
        }
    }

    private boolean createProjectStructure(Project project) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Path projectDir = DatapackEditorClient.getProjectDirectory(project.id());

        if (projectDir == null) {
            return false;
        }

        try {
            Files.createDirectories(projectDir);

            Path mcmetaFile = projectDir.resolve("pack.mcmeta");
            if (!Files.exists(mcmetaFile)) {
                String mcmetaJson = "{\"pack\":{\"pack_format\":15,\"description\":\"" + escapeJson(project.description()) + "\"}}";
                Files.writeString(mcmetaFile, mcmetaJson);
            }

            Path functionsDir = projectDir.resolve("data").resolve(project.namespace()).resolve("functions").resolve("internal");
            Files.createDirectories(functionsDir);

            Path tickFile = functionsDir.resolve("tick.mcfunction");
            if (!Files.exists(tickFile)) {
                Files.writeString(tickFile, "# Tick function\n# Add your commands here\n");
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
    }

    private void onOpenProject() {
        if (selectedProject != null) {
            DatapackEditorClient.config().setCurrentProject(selectedProject.id());
            DatapackEditorClient.saveConfig();
            openEditorWithProject(selectedProject, null);
        }
    }

    private void openEditorWithProject(Project project, String templateId) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) {
            this.close();
            if (templateId != null && !templateId.equals("blank")) {
                DatapackEditorClient.openEditorWithProjectAndTemplate(project, templateId);
            } else {
                DatapackEditorClient.openEditorWithProject(project);
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context, mouseX, mouseY, delta);

        int centerX = this.width / 2;
        int panelWidth = 380;
        int panelHeight = isCreatingNew ? 480 : 400;
        int panelX = centerX - panelWidth / 2;
        int panelY = this.height / 2 - panelHeight / 2;

        context.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, 0xCC1a1a2e);

        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("项目管理器"), centerX, panelY + 10, 0xFFFFFF);

        if (!isCreatingNew && !headerText.isEmpty()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(headerText), centerX, panelY + 20, 
                    headerText.equals("No projects found") ? 0x808080 : 0xFFFFFF);
        }

        for (LabelInfo label : labels) {
            context.drawTextWithShadow(this.textRenderer, Text.literal(label.text), label.x, label.y, label.color);
        }

        for (LabelInfo hint : hintTexts) {
            context.drawTextWithShadow(this.textRenderer, Text.literal(hint.text), hint.x, hint.y, hint.color);
        }

        if (isCreatingNew) {
            renderTemplateList(context, panelX, panelY, panelWidth);
        }

        super.render(context, mouseX, mouseY, delta);
    }
    
    private void renderTemplateList(DrawContext context, int panelX, int panelY, int panelWidth) {
        int currentY = panelY + 110;
        int templateListTop = currentY;
        int templateListBottom = templateListTop + TEMPLATE_LIST_HEIGHT;
        
        int visibleCount = Math.min(TEMPLATE_OPTIONS.size() - templateScrollOffset, MAX_VISIBLE_TEMPLATES);
        for (int i = 0; i < visibleCount; i++) {
            int idx = templateScrollOffset + i;
            if (idx >= TEMPLATE_OPTIONS.size()) break;
            
            TemplateOption opt = TEMPLATE_OPTIONS.get(idx);
            int itemY = templateListTop + i * TEMPLATE_ITEM_HEIGHT;
            int itemX = panelX + 10;
            int itemWidth = panelWidth - 60;
            
            boolean isSelected = selectedTemplateId.equals(opt.id);
            int bgColor = isSelected ? 0xFF094771 : 0xFF2D2D2D;
            context.fill(itemX, itemY, itemX + itemWidth, itemY + TEMPLATE_ITEM_HEIGHT - 2, bgColor);
            
            context.drawTextWithShadow(this.textRenderer, Text.literal(opt.name), itemX + 5, itemY + 4, 
                    isSelected ? 0xFFFFFF : 0xCCCCCC);
            String truncatedDesc = opt.description.length() > 40 ? opt.description.substring(0, 40) + "..." : opt.description;
            context.drawTextWithShadow(this.textRenderer, Text.literal(truncatedDesc), itemX + 5, itemY + 18, 0x888888);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (nameField != null && nameField.isFocused()) {
            return nameField.keyPressed(keyCode, scanCode, modifiers);
        }
        if (namespaceField != null && namespaceField.isFocused()) {
            return namespaceField.keyPressed(keyCode, scanCode, modifiers);
        }
        if (descriptionField != null && descriptionField.isFocused()) {
            return descriptionField.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        if (nameField != null && nameField.isFocused()) {
            return nameField.charTyped(chr, modifiers);
        }
        if (namespaceField != null && namespaceField.isFocused()) {
            return namespaceField.charTyped(chr, modifiers);
        }
        if (descriptionField != null && descriptionField.isFocused()) {
            return descriptionField.charTyped(chr, modifiers);
        }
        return super.charTyped(chr, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (nameField != null) {
            nameField.mouseClicked(mouseX, mouseY, button);
        }
        if (namespaceField != null) {
            namespaceField.mouseClicked(mouseX, mouseY, button);
        }
        if (descriptionField != null) {
            descriptionField.mouseClicked(mouseX, mouseY, button);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
