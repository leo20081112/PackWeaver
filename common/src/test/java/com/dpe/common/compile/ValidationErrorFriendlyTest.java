package com.dpe.common.compile;

import com.dpe.common.block.BlockSchemaRegistry;
import com.dpe.common.block.EditorBlock;
import com.dpe.common.block.EditorState;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 校验 ValidationError 的 friendlyMessage / fixSuggestion 友好化。
 */
class ValidationErrorFriendlyTest {

    private static final Pattern CJK = Pattern.compile("[\\u4e00-\\u9fff]");

    @Test
    void missingRequiredFieldHasFriendlyMessageAndSuggestion() {
        EditorState state = new EditorState("dp");
        EditorBlock tick = new EditorBlock("b1", "event.tick", 0, 0);
        // action.say_text 缺少必填 text 字段
        EditorBlock say = new EditorBlock("b2", "action.say_text", 0, 0);
        state.addBlock(tick);
        state.addBlock(say);
        state.connect("b1", "b2");

        List<ValidationError> errors = new BlockValidator().validate(state, BlockSchemaRegistry.DEFAULT);
        assertFalse(errors.isEmpty(), "缺必填字段应报错");

        ValidationError err = errors.stream()
                .filter(e -> "b2".equals(e.blockId()) && "text".equals(e.field()))
                .findFirst().orElseThrow(() -> new AssertionError("应报告 b2 的 text 字段缺失: " + errors));

        assertFalse(err.friendlyMessage().isBlank(), "friendlyMessage 不能为空");
        assertFalse(err.fixSuggestion().isBlank(), "fixSuggestion 不能为空");
        assertTrue(CJK.matcher(err.friendlyMessage()).find(),
                "friendlyMessage 应含中文: " + err.friendlyMessage());
        assertTrue(CJK.matcher(err.fixSuggestion()).find(),
                "fixSuggestion 应含中文: " + err.fixSuggestion());
        assertTrue(err.friendlyMessage().contains("必填"), "required 错误应说明必填: " + err.friendlyMessage());
    }

    @Test
    void invalidEnumHasFriendlyMessageAndSuggestion() {
        EditorState state = new EditorState("dp");
        EditorBlock tick = new EditorBlock("b1", "event.tick", 0, 0);
        EditorBlock cond = new EditorBlock("b2", "condition.score_compare", 0, 0,
                java.util.Map.of("objective", "obj", "target", "@p", "op", "INVALID", "value", 5),
                List.of());
        state.addBlock(tick);
        state.addBlock(cond);
        state.connect("b1", "b2");

        List<ValidationError> errors = new BlockValidator().validate(state, BlockSchemaRegistry.DEFAULT);
        ValidationError err = errors.stream().filter(e -> "op".equals(e.field())).findFirst()
                .orElseThrow(() -> new AssertionError("非法 enum 应报错: " + errors));
        assertFalse(err.friendlyMessage().isBlank());
        assertFalse(err.fixSuggestion().isBlank());
        assertTrue(CJK.matcher(err.friendlyMessage()).find(), "enum friendlyMessage 应含中文: " + err.friendlyMessage());
    }

    @Test
    void invalidResourceLocationHasFriendlyMessage() {
        EditorState state = new EditorState("dp");
        EditorBlock tick = new EditorBlock("b1", "event.tick", 0, 0);
        // entity_type 含大写字母非法
        EditorBlock death = new EditorBlock("b2", "event.entity_death", 0, 0,
                java.util.Map.of("entity_type", "Minecraft:Zombie"), List.of());
        state.addBlock(tick);
        state.addBlock(death);
        state.connect("b1", "b2");

        List<ValidationError> errors = new BlockValidator().validate(state, BlockSchemaRegistry.DEFAULT);
        ValidationError err = errors.stream().filter(e -> "entity_type".equals(e.field())).findFirst()
                .orElseThrow(() -> new AssertionError("非法 ResourceLocation 应报错: " + errors));
        assertFalse(err.friendlyMessage().isBlank());
        assertTrue(err.friendlyMessage().contains("命名空间"), "ResourceLocation 错误应说明命名空间: " + err.friendlyMessage());
    }

    @Test
    void oldConstructorRemainsBackwardCompatible() {
        // 旧构造器仍可用，friendly/suggestion 为空串
        ValidationError e = new ValidationError("b1", "f", "msg");
        assertEquals("b1", e.blockId());
        assertEquals("f", e.field());
        assertEquals("msg", e.message());
        assertEquals("", e.friendlyMessage());
        assertEquals("", e.fixSuggestion());

        ValidationError e2 = new ValidationError("b1", "msg");
        assertEquals("", e2.friendlyMessage());
    }
}
