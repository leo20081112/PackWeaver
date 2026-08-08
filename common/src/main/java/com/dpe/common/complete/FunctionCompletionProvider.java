package com.dpe.common.complete;

import com.dpe.common.model.Datapack;
import com.dpe.common.model.McFunction;

import java.util.ArrayList;
import java.util.List;

/**
 * 函数补全提供者。
 * 当 commandContext 为 "function" 或文本以 "function " 开头时，
 * 返回 datapack 中所有 McFunction 的 id + minecraft 命名空间占位。
 */
public final class FunctionCompletionProvider implements CompletionProvider {

    private static final String[] COMMON_COMMANDS = {
            "tellraw", "title", "bossbar", "schedule", "function", "execute",
            "setblock", "fill", "clone", "give", "summon", "kill", "clear",
            "effect", "enchant", "xp", "experience", "scoreboard", "team",
            "advancement", "recipe", "loot", "data", "tag", "tell", "wmsg",
            "msg", "say", "me", "title", "playsound", "stopsound"
    };

    @Override
    public List<CompletionCandidate> complete(CompletionContext ctx) {
        List<CompletionCandidate> result = new ArrayList<>();
        if (ctx == null) {
            return result;
        }
        boolean match = "function".equals(ctx.commandContext())
                || (ctx.text() != null && ctx.text().startsWith("function "));
        if (!match) {
            return result;
        }

        String currentWord = extractCurrentWord(ctx.text(), ctx.cursor());

        for (String cmd : COMMON_COMMANDS) {
            if (cmd.startsWith(currentWord.toLowerCase()) || currentWord.isEmpty()) {
                WikiCommandCache.WikiCommandInfo wikiInfo = WikiCommandCache.get(cmd);
                String detail = wikiInfo != null ? wikiInfo.toDetail() : "Minecraft 原版命令";
                result.add(new CompletionCandidate(cmd, cmd, detail, "function"));
            }
        }

        Datapack dp = ctx.datapack();
        if (dp != null) {
            for (McFunction fn : dp.functions()) {
                String id = fn.id().toString();
                result.add(new CompletionCandidate(id, id,
                        "自定义函数：调用命名空间下的函数，如 function " + id, "function"));
            }
        }

        result.add(new CompletionCandidate("minecraft:", "minecraft:",
                "命名空间：原版 minecraft 函数，如 function minecraft:internal/tick", "function"));

        result.sort((a, b) -> {
            String aName = a.label().toLowerCase();
            String bName = b.label().toLowerCase();
            boolean aStarts = aName.startsWith(currentWord.toLowerCase());
            boolean bStarts = bName.startsWith(currentWord.toLowerCase());
            if (aStarts && !bStarts) return -1;
            if (!aStarts && bStarts) return 1;
            return aName.compareTo(bName);
        });

        return result;
    }

    private String extractCurrentWord(String text, int cursor) {
        if (text == null || cursor <= 0) {
            return "";
        }
        int start = cursor - 1;
        while (start >= 0 && !Character.isWhitespace(text.charAt(start))) {
            start--;
        }
        return text.substring(start + 1, cursor);
    }
}
