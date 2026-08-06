package com.dpe.common.complete;

import com.dpe.common.model.Datapack;
import com.dpe.common.model.McFunction;
import com.dpe.common.model.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 校验各补全 provider 返回的 detail 非空且含中文。
 */
class CompletionDetailTest {

    private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fff]");

    private static void assertChineseDetail(CompletionCandidate c) {
        assertNotNull(c.detail(), "detail 不能为 null: " + c);
        assertFalse(c.detail().isBlank(), "detail 不能为空: " + c);
        assertTrue(CJK.matcher(c.detail()).find(),
                "detail 应含中文: " + c + " 实际 detail=" + c.detail());
    }

    @Test
    void textComponentProviderDetailsAreChinese() {
        TextComponentCompletionProvider provider = new TextComponentCompletionProvider();
        CompletionContext ctx = new CompletionContext("{\"te", 2, "dp", null, "text_component");
        List<CompletionCandidate> cands = provider.complete(ctx);

        assertFalse(cands.isEmpty());
        for (CompletionCandidate c : cands) {
            assertChineseDetail(c);
        }
        // 抽样校验关键候选
        CompletionCandidate click = cands.stream().filter(c -> "clickEvent".equals(c.label())).findFirst().orElseThrow();
        assertTrue(click.detail().contains("点击事件"), "clickEvent detail 应含中文说明: " + click.detail());
        CompletionCandidate color = cands.stream().filter(c -> "color".equals(c.label())).findFirst().orElseThrow();
        assertTrue(color.detail().contains("颜色"), "color detail 应含中文说明: " + color.detail());
        CompletionCandidate bold = cands.stream().filter(c -> "bold".equals(c.label())).findFirst().orElseThrow();
        assertTrue(bold.detail().contains("加粗"), "bold detail 应含中文说明: " + bold.detail());
    }

    @Test
    void functionProviderDetailsAreChinese() {
        McFunction fn = new McFunction(new ResourceLocation("mydp", "helper/greet"), List.of("say hi"));
        Datapack dp = new Datapack("mydp", List.of(fn), List.of(), List.of(), List.of());

        FunctionCompletionProvider provider = new FunctionCompletionProvider();
        CompletionContext ctx = new CompletionContext("function ", 9, "mydp", dp, "function");
        List<CompletionCandidate> cands = provider.complete(ctx);

        assertFalse(cands.isEmpty());
        for (CompletionCandidate c : cands) {
            assertChineseDetail(c);
        }
        // 函数候选应含「函数」中文说明
        CompletionCandidate fnCand = cands.stream()
                .filter(c -> "mydp:helper/greet".equals(c.label())).findFirst().orElseThrow();
        assertTrue(fnCand.detail().contains("函数"), "函数候选 detail 应含「函数」: " + fnCand.detail());
    }

    @Test
    void nbtProviderDetailsAreChinese() {
        NbtScoreboardCompletionProvider provider = new NbtScoreboardCompletionProvider();
        CompletionContext ctx = new CompletionContext("data get entity @p ", 20, "dp", null, "data");
        List<CompletionCandidate> cands = provider.complete(ctx);

        assertFalse(cands.isEmpty());
        for (CompletionCandidate c : cands) {
            assertChineseDetail(c);
        }
        // Health 候选应含「生命值」中文说明
        CompletionCandidate health = cands.stream().filter(c -> "Health".equals(c.label())).findFirst().orElseThrow();
        assertTrue(health.detail().contains("生命值"), "Health detail 应含中文说明: " + health.detail());
    }

    @Test
    void scoreboardProviderDetailsAreChinese() {
        NbtScoreboardCompletionProvider provider = new NbtScoreboardCompletionProvider();
        CompletionContext ctx = new CompletionContext("scoreboard ", 11, "dp", null, "scoreboard");
        List<CompletionCandidate> cands = provider.complete(ctx);

        assertFalse(cands.isEmpty());
        for (CompletionCandidate c : cands) {
            assertChineseDetail(c);
        }
    }
}
