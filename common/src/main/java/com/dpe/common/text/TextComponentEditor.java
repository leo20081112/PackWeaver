package com.dpe.common.text;

/**
 * 文本组件可视化编辑器，持有 root 树与选中索引。
 * 选中索引：0 表示 root，1..n 表示 root.extra 的第 n 个子节点。
 */
public final class TextComponentEditor {

    private TextComponent root;
    private int selected = 0;

    public TextComponentEditor() {
        this.root = new TextComponent("");
    }

    public TextComponentEditor(TextComponent root) {
        this.root = root == null ? new TextComponent("") : root;
    }

    public TextComponent getRoot() {
        return root;
    }

    public void setRoot(TextComponent root) {
        this.root = root == null ? new TextComponent("") : root;
        this.selected = 0;
    }

    public int getSelected() {
        return selected;
    }

    /** 选中节点：0=root，1..n=extra 子节点。越界抛异常。 */
    public void select(int index) {
        if (index < 0) {
            throw new IllegalArgumentException("selected index 不能为负");
        }
        if (index > root.getExtra().size()) {
            throw new IllegalArgumentException("selected index 越界: " + index);
        }
        this.selected = index;
    }

    /** 当前选中的节点。 */
    public TextComponent current() {
        if (selected == 0) {
            return root;
        }
        return root.getExtra().get(selected - 1);
    }

    /** 应用样式到当前选中节点（合并：仅覆盖非 null 字段）。 */
    public void applyStyle(TextStyle style) {
        TextStyle target = current().getStyle();
        if (style == null) {
            return;
        }
        if (style.getColor() != null) {
            target.setColor(style.getColor());
        }
        if (style.getBold() != null) {
            target.setBold(style.getBold());
        }
        if (style.getItalic() != null) {
            target.setItalic(style.getItalic());
        }
        if (style.getUnderlined() != null) {
            target.setUnderlined(style.getUnderlined());
        }
        if (style.getStrikethrough() != null) {
            target.setStrikethrough(style.getStrikethrough());
        }
        if (style.getObfuscated() != null) {
            target.setObfuscated(style.getObfuscated());
        }
        if (style.getFont() != null) {
            target.setFont(style.getFont());
        }
    }

    /** 添加文本子节点并选中它。 */
    public TextComponent addText(String text) {
        TextComponent child = new TextComponent(text);
        root.addChild(child);
        selected = root.getExtra().size();
        return child;
    }

    /** 设置当前选中节点的点击事件。 */
    public void setClickEvent(ClickEvent event) {
        current().setClickEvent(event);
    }

    /** 设置当前选中节点的悬停事件。 */
    public void setHoverEvent(HoverEvent event) {
        current().setHoverEvent(event);
    }

    /** 序列化整个树。 */
    public String toJson() {
        return root.toJsonString();
    }

    /** 从 JSON 重建树（双向同步核心）。 */
    public void fromJson(String json) {
        this.root = TextComponent.fromJson(json);
        this.selected = 0;
    }

    /**
     * 校验点击事件命令合法性。
     * run_command / suggest_command 必须以 / 开头；
     * open_url 必须 http(s):// 开头。
     * @return 错误信息；合法返回 null。
     */
    public static String validateClickEventCommand(ClickEvent event) {
        if (event == null) {
            return null;
        }
        String action = event.action();
        String value = event.value();
        switch (action) {
            case "run_command", "suggest_command" -> {
                if (value == null || !value.startsWith("/")) {
                    return action + " 的 value 必须以 '/' 开头: " + value;
                }
            }
            case "open_url" -> {
                if (value == null || !(value.startsWith("http://") || value.startsWith("https://"))) {
                    return "open_url 的 value 必须以 http:// 或 https:// 开头: " + value;
                }
            }
            case "copy_to_clipboard", "change_page", "open_file" -> {
                // 无特定格式约束
            }
            default -> {
                return "未知 clickEvent action: " + action;
            }
        }
        return null;
    }
}
