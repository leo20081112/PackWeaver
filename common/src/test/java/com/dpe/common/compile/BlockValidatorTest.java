package com.dpe.common.compile;

import com.dpe.common.block.BlockSchemaRegistry;
import com.dpe.common.block.EditorBlock;
import com.dpe.common.block.EditorState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BlockValidator 单元测试。
 */
class BlockValidatorTest {

    @Test
    void missingRequiredFieldProducesError() {
        EditorState state = new EditorState("dp");
        EditorBlock tick = new EditorBlock("b1", "event.tick", 0, 0);
        // action.say_text 缺少必填 text 字段
        EditorBlock say = new EditorBlock("b2", "action.say_text", 0, 0);
        state.addBlock(tick);
        state.addBlock(say);
        state.connect("b1", "b2");

        BlockValidator validator = new BlockValidator();
        List<ValidationError> errors = validator.validate(state, BlockSchemaRegistry.DEFAULT);

        assertFalse(errors.isEmpty(), "缺必填字段应报错");
        assertTrue(errors.stream().anyMatch(e -> "b2".equals(e.blockId()) && "text".equals(e.field())),
                "应报告 b2 的 text 字段缺失，实际: " + errors);
    }

    @Test
    void missingRequiredFieldFailsCompile() {
        EditorState state = new EditorState("dp");
        EditorBlock tick = new EditorBlock("b1", "event.tick", 0, 0);
        EditorBlock say = new EditorBlock("b2", "action.say_text", 0, 0);
        state.addBlock(tick);
        state.addBlock(say);
        state.connect("b1", "b2");

        CompileResult result = new BlockCompiler().compile(state, BlockSchemaRegistry.DEFAULT);

        assertFalse(result.success(), "校验失败时编译应失败");
        assertFalse(result.errors().isEmpty(), "应有错误");
        assertTrue(result.mcfunctions().isEmpty(), "失败时不应有产物");
    }

    @Test
    void validStateProducesNoErrors() {
        EditorState state = new EditorState("dp");
        EditorBlock tick = new EditorBlock("b1", "event.tick", 0, 0);
        EditorBlock say = new EditorBlock("b2", "action.say_text", 0, 0,
                java.util.Map.of("text", "Hi"), List.of());
        state.addBlock(tick);
        state.addBlock(say);
        state.connect("b1", "b2");

        List<ValidationError> errors = new BlockValidator().validate(state, BlockSchemaRegistry.DEFAULT);
        assertTrue(errors.isEmpty(), "合法状态不应有错误，实际: " + errors);
    }

    @Test
    void unknownSchemaProducesError() {
        EditorState state = new EditorState("dp");
        state.addBlock(new EditorBlock("b1", "nonexistent.schema", 0, 0));

        List<ValidationError> errors = new BlockValidator().validate(state, BlockSchemaRegistry.DEFAULT);
        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> "b1".equals(e.blockId())));
    }

    @Test
    void invalidEnumValueProducesError() {
        EditorState state = new EditorState("dp");
        EditorBlock tick = new EditorBlock("b1", "event.tick", 0, 0);
        EditorBlock cond = new EditorBlock("b2", "condition.score_compare", 0, 0,
                java.util.Map.of("objective", "obj", "target", "@p", "op", "INVALID", "value", 5),
                List.of());
        state.addBlock(tick);
        state.addBlock(cond);
        state.connect("b1", "b2");

        List<ValidationError> errors = new BlockValidator().validate(state, BlockSchemaRegistry.DEFAULT);
        assertTrue(errors.stream().anyMatch(e -> "op".equals(e.field())),
                "非法 enum 值应报错，实际: " + errors);
    }
}
