package com.dpe.common.complete;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本组件字段补全提供者。
 * 当 commandContext 为 "text_component" 或在 JSON 文本字段输入时，
 * 返回 vanilla 文本组件字段候选（含引号与冒号的 insertText）。
 */
public final class TextComponentCompletionProvider implements CompletionProvider {

    private static final String[][] FIELDS = {
            // {name, detail 示例}
            {"text", "\"文本内容\""},
            {"translate", "\"translation.key\""},
            {"with", "[]"},
            {"color", "\"red\" / \"#FF0000\""},
            {"bold", "true"},
            {"italic", "true"},
            {"underlined", "true"},
            {"strikethrough", "true"},
            {"obfuscated", "true"},
            {"clickEvent", "{\"action\":\"run_command\",\"value\":\"/say hi\"}"},
            {"hoverEvent", "{\"action\":\"show_text\",\"contents\":\"...\"}"},
            {"insertion", "\"可插入文本\""},
            {"font", "\"minecraft:default\""},
            {"selector", "\"@p\""},
            {"score", "{\"name\":\"@p\",\"objective\":\"obj\"}"},
            {"keybind", "\"key.inventory\""},
            {"extra", "[]"}
    };

    @Override
    public List<CompletionCandidate> complete(CompletionContext ctx) {
        List<CompletionCandidate> result = new ArrayList<>();
        if (ctx == null) {
            return result;
        }
        boolean match = "text_component".equals(ctx.commandContext())
                || (ctx.text() != null && ctx.text().trim().startsWith("{"));
        if (!match) {
            return result;
        }
        for (String[] f : FIELDS) {
            String name = f[0];
            String detail = f[1];
            String insertText = "\"" + name + "\":";
            result.add(new CompletionCandidate(name, insertText, detail, "field"));
        }
        return result;
    }
}
