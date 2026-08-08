package com.dpe.common.complete;

/**
 * 补全候选项，不可变。
 * kind 取值：function / nbt / scoreboard / field / value / keyword / snippet
 */
public record CompletionCandidate(String label, String insertText, String detail, String kind) {

    public CompletionCandidate {
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("label 不能为空");
        }
        if (insertText == null) {
            insertText = label;
        }
        if (kind == null) {
            kind = "value";
        }
    }

    /** 便捷构造（detail 可空）。 */
    public CompletionCandidate(String label, String insertText, String kind) {
        this(label, insertText, null, kind);
    }

    /** 获取类型描述。 */
    public String getTypeDescription() {
        return switch (kind) {
            case "function" -> "函数";
            case "nbt" -> "NBT标签";
            case "scoreboard" -> "记分板";
            case "field" -> "字段";
            case "keyword" -> "关键字";
            case "snippet" -> "代码片段";
            default -> "值";
        };
    }
}
