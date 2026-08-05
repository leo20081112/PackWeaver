package com.dpe.common.complete;

import com.dpe.common.model.Datapack;
import com.dpe.common.model.McFunction;
import com.dpe.common.model.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 自动补全单元测试。
 */
class CompletionTest {

    @Test
    void textComponentProviderReturnsClickEventAndColor() {
        TextComponentCompletionProvider provider = new TextComponentCompletionProvider();
        CompletionContext ctx = new CompletionContext("{\"te", 2, "dp", null, "text_component");
        List<CompletionCandidate> cands = provider.complete(ctx);

        assertFalse(cands.isEmpty(), "应返回候选");
        assertTrue(cands.stream().anyMatch(c -> "clickEvent".equals(c.label())),
                "应包含 clickEvent 候选: " + cands);
        assertTrue(cands.stream().anyMatch(c -> "color".equals(c.label())),
                "应包含 color 候选: " + cands);
        // insertText 应含引号与冒号
        CompletionCandidate click = cands.stream().filter(c -> "clickEvent".equals(c.label())).findFirst().orElseThrow();
        assertEquals("\"clickEvent\":", click.insertText());
    }

    @Test
    void functionProviderReturnsDatapackFunctions() {
        McFunction fn = new McFunction(new ResourceLocation("mydp", "helper/greet"), List.of("say hi"));
        Datapack dp = new Datapack("mydp", List.of(fn), List.of(), List.of(), List.of());

        FunctionCompletionProvider provider = new FunctionCompletionProvider();
        CompletionContext ctx = new CompletionContext("function ", 9, "mydp", dp, "function");
        List<CompletionCandidate> cands = provider.complete(ctx);

        assertTrue(cands.stream().anyMatch(c -> "mydp:helper/greet".equals(c.label())),
                "应返回数据包中的函数 id: " + cands);
        assertTrue(cands.stream().anyMatch(c -> "minecraft:".equals(c.label())),
                "应包含 minecraft 命名空间占位: " + cands);
    }

    @Test
    void functionProviderIgnoresNonFunctionContext() {
        FunctionCompletionProvider provider = new FunctionCompletionProvider();
        CompletionContext ctx = new CompletionContext("say ", 4, "dp", null, "text_component");
        List<CompletionCandidate> cands = provider.complete(ctx);
        assertTrue(cands.isEmpty(), "非 function 上下文应返回空");
    }

    @Test
    void nbtProviderReturnsCommonPaths() {
        NbtScoreboardCompletionProvider provider = new NbtScoreboardCompletionProvider();
        CompletionContext ctx = new CompletionContext("data get entity @p ", 20, "dp", null, "data");
        List<CompletionCandidate> cands = provider.complete(ctx);
        assertTrue(cands.stream().anyMatch(c -> "Health".equals(c.label())));
        assertTrue(cands.stream().anyMatch(c -> "Inventory".equals(c.label())));
    }

    @Test
    void completionServiceAggregatesAll() {
        CompletionService service = new CompletionService();
        CompletionContext ctx = new CompletionContext("{", 1, "dp", null, "text_component");
        List<CompletionCandidate> cands = service.complete(ctx);
        assertFalse(cands.isEmpty());
    }
}
