package com.dpe.common.compile;

import com.dpe.common.block.BlockSchemaRegistry;
import com.dpe.common.block.EditorBlock;
import com.dpe.common.block.EditorState;
import com.dpe.common.model.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BlockCompiler 单元测试。
 */
class BlockCompilerTest {

    @Test
    void compilesEventTickWithSayTextChild() {
        EditorState state = new EditorState("testdp");
        EditorBlock tick = new EditorBlock("b1", "event.tick", 0, 0);
        EditorBlock say = new EditorBlock("b2", "action.say_text", 100, 0,
                Map.of("text", "Hello"), List.of());
        state.addBlock(tick);
        state.addBlock(say);
        state.connect("b1", "b2");

        BlockCompiler compiler = new BlockCompiler();
        CompileResult result = compiler.compile(state, BlockSchemaRegistry.DEFAULT);

        assertTrue(result.success(), "编译应成功");
        assertTrue(result.errors().isEmpty(), "不应有错误");

        ResourceLocation expectedFn = new ResourceLocation("testdp", "internal/tick");
        assertTrue(result.mcfunctions().containsKey(expectedFn),
                "应生成函数 " + expectedFn + "，实际: " + result.mcfunctions().keySet());

        String content = result.mcfunctions().get(expectedFn);
        assertTrue(content.contains("say Hello"),
                "函数体应含 'say Hello'，实际: " + content);
    }

    @Test
    void functionNamespaceUsesActiveDatapackNamespace() {
        EditorState state = new EditorState("myns");
        EditorBlock tick = new EditorBlock("b1", "event.tick", 0, 0);
        EditorBlock say = new EditorBlock("b2", "action.say_text", 0, 0,
                Map.of("text", "Hi"), List.of());
        state.addBlock(tick);
        state.addBlock(say);
        state.connect("b1", "b2");

        CompileResult result = new BlockCompiler().compile(state, BlockSchemaRegistry.DEFAULT);
        assertTrue(result.success());
        ResourceLocation fnId = result.mcfunctions().keySet().iterator().next();
        assertEquals("myns", fnId.namespace());
        assertEquals("internal/tick", fnId.path());
    }

    @Test
    void conditionWrapsActionWithExecuteIf() {
        EditorState state = new EditorState("dp");
        EditorBlock tick = new EditorBlock("b1", "event.tick", 0, 0);
        EditorBlock cond = new EditorBlock("b2", "condition.random_chance", 0, 0,
                Map.of("value", 0.5), List.of());
        EditorBlock say = new EditorBlock("b3", "action.say_text", 0, 0,
                Map.of("text", "Lucky"), List.of());
        state.addBlock(tick);
        state.addBlock(cond);
        state.addBlock(say);
        state.connect("b1", "b2");
        state.connect("b2", "b3");

        CompileResult result = new BlockCompiler().compile(state, BlockSchemaRegistry.DEFAULT);
        assertTrue(result.success());
        String content = result.mcfunctions().values().iterator().next();
        assertTrue(content.contains("execute if") && content.contains("run say Lucky"),
                "条件应包装为 execute if ... run，实际: " + content);
    }

    @Test
    void tagAddProducesJsonFile() {
        EditorState state = new EditorState("dp");
        EditorBlock tick = new EditorBlock("b1", "event.tick", 0, 0);
        EditorBlock tagAdd = new EditorBlock("b2", "action.tag_add", 0, 0,
                Map.of("tag", "dp:my_funcs", "entry", "dp:helper"), List.of());
        state.addBlock(tick);
        state.addBlock(tagAdd);
        state.connect("b1", "b2");

        CompileResult result = new BlockCompiler().compile(state, BlockSchemaRegistry.DEFAULT);
        assertTrue(result.success());
        assertFalse(result.jsonFiles().isEmpty(), "应生成 tag JSON 文件");
        String json = result.jsonFiles().get(new ResourceLocation("dp", "my_funcs"));
        assertNotNull(json);
        assertTrue(json.contains("dp:helper"), "tag JSON 应含 entry，实际: " + json);
    }

    @Test
    void rawTextBlockCompilesToOriginalLine() {
        EditorState state = new EditorState("dp");
        EditorBlock tick = new EditorBlock("b1", "event.tick", 0, 0);
        // raw_text 保留原文本（含注释行）
        EditorBlock raw = new EditorBlock("b2", "raw_text", 0, 0,
                Map.of("text", "# this is a comment"), List.of());
        state.addBlock(tick);
        state.addBlock(raw);
        state.connect("b1", "b2");

        CompileResult result = new BlockCompiler().compile(state, BlockSchemaRegistry.DEFAULT);
        assertTrue(result.success(), "raw_text 应编译成功: " + result.errors());
        String content = result.mcfunctions().values().iterator().next();
        assertTrue(content.contains("# this is a comment"),
                "raw_text 应原样输出原文行，实际: " + content);
    }

    @Test
    void rawTextNotWrappedByExecuteIf() {
        // raw_text 即使作为条件子块也不应被 execute if 包装
        EditorState state = new EditorState("dp");
        EditorBlock tick = new EditorBlock("b1", "event.tick", 0, 0);
        EditorBlock cond = new EditorBlock("b2", "condition.score_compare", 0, 0,
                Map.of("objective", "obj", "target", "@p", "op", "\u2265", "value", 5), List.of());
        EditorBlock raw = new EditorBlock("b3", "raw_text", 0, 0,
                Map.of("text", "weather rain"), List.of());
        state.addBlock(tick);
        state.addBlock(cond);
        state.addBlock(raw);
        state.connect("b1", "b2");
        state.connect("b2", "b3");

        CompileResult result = new BlockCompiler().compile(state, BlockSchemaRegistry.DEFAULT);
        assertTrue(result.success());
        String content = result.mcfunctions().values().iterator().next();
        assertTrue(content.contains("weather rain"), "应包含 raw_text 原文");
        assertFalse(content.contains("execute if") && content.contains("run weather rain"),
                "raw_text 不应被 execute if 包装，实际: " + content);
    }
}
