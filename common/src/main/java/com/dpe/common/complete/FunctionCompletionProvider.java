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
        Datapack dp = ctx.datapack();
        if (dp != null) {
            for (McFunction fn : dp.functions()) {
                String id = fn.id().toString();
                result.add(new CompletionCandidate(id, id, "函数 " + id, "function"));
            }
        }
        // minecraft 命名空间占位
        result.add(new CompletionCandidate("minecraft:", "minecraft:",
                "minecraft 命名空间", "function"));
        return result;
    }
}
