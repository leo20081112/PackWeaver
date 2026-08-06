package com.dpe.common.complete;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本组件字段补全提供者。
 * 当 commandContext 为 "text_component" 或在 JSON 文本字段输入时，
 * 返回 vanilla 文本组件字段候选（含引号与冒号的 insertText），detail 为中文说明。
 */
public final class TextComponentCompletionProvider implements CompletionProvider {

    /** {字段名, 中文说明}。 */
    private static final String[][] FIELDS = {
            {"text", "文本内容：显示的纯文本，如 \"text\":\"你好\""},
            {"translate", "翻译键：根据语言文件显示本地化文本，如 \"translate\":\"item.minecraft.apple\""},
            {"with", "翻译参数：填充 translate 中 %s 占位，如 \"with\":[\"Steve\"]"},
            {"color", "颜色：文本颜色，如 red/gold/aqua 或十六进制 \"#FF0000\""},
            {"bold", "加粗：true 时文本加粗，如 \"bold\":true"},
            {"italic", "斜体：true 时文本倾斜，如 \"italic\":true"},
            {"underlined", "下划线：true 时文本加下划线，如 \"underlined\":true"},
            {"strikethrough", "删除线：true 时文本加删除线，如 \"strikethrough\":true"},
            {"obfuscated", "乱码：true 时文本随机扰动显示，如 \"obfuscated\":true"},
            {"clickEvent", "点击事件：玩家点击文本时触发动作（如 run_command 运行命令）"},
            {"hoverEvent", "悬停事件：鼠标悬停时显示提示（如 show_text 显示文本）"},
            {"insertion", "插入文本：Shift+点击时插入到聊天框，如 \"insertion\":\"/home\""},
            {"font", "字体：使用的字体资源，如 \"font\":\"minecraft:default\""},
            {"selector", "选择器：显示目标实体名，如 \"selector\":\"@p\""},
            {"score", "记分板分数：显示某目标的分数，如 \"score\":{\"name\":\"@p\",\"objective\":\"obj\"}"},
            {"keybind", "按键绑定：显示对应按键的名称，如 \"keybind\":\"key.inventory\""},
            {"extra", "附加子组件：在当前文本后追加更多文本组件，如 \"extra\":[{\"text\":\"!\"}]"}
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
