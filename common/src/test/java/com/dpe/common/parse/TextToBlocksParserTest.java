package com.dpe.common.parse;

import com.dpe.common.block.EditorBlock;
import com.dpe.common.block.EditorState;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TextToBlocksParser 单元测试。
 */
class TextToBlocksParserTest {

    @Test
    void parsesSayCommandToSayTextBlock() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("dpe/internal/tick.mcfunction", "say Hello");

        EditorState state = new TextToBlocksParser().parse("dpe", files);

        assertEquals("dpe", state.getActiveDatapackNamespace());
        EditorBlock say = state.getBlocks().stream()
                .filter(b -> "action.say_text".equals(b.schemaId()))
                .findFirst().orElseThrow(() -> new AssertionError("应生成 action.say_text 积木: " + state.getBlocks()));
        assertEquals("Hello", say.fieldValues().get("text"));
    }

    @Test
    void unknownCommandBecomesRawTextBlock() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("dpe/internal/tick.mcfunction", "weather rain");

        EditorState state = new TextToBlocksParser().parse("dpe", files);

        EditorBlock raw = state.getBlocks().stream()
                .filter(b -> "raw_text".equals(b.schemaId()))
                .findFirst().orElseThrow(() -> new AssertionError("未知命令应生成 raw_text 积木: " + state.getBlocks()));
        assertEquals("weather rain", raw.fieldValues().get("text"));
    }

    @Test
    void parsesTellrawAndFunction() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("dpe/test.mcfunction",
                "tellraw @a {\"text\":\"hi\"}\nfunction dpe:internal/tick");

        EditorState state = new TextToBlocksParser().parse("dpe", files);

        EditorBlock tellraw = state.getBlocks().stream()
                .filter(b -> "action.tellraw".equals(b.schemaId()))
                .findFirst().orElseThrow(() -> new AssertionError("应生成 action.tellraw 积木"));
        assertEquals("@a", tellraw.fieldValues().get("target"));
        assertEquals("{\"text\":\"hi\"}", tellraw.fieldValues().get("text"));

        EditorBlock fn = state.getBlocks().stream()
                .filter(b -> "action.run_function".equals(b.schemaId()))
                .findFirst().orElseThrow(() -> new AssertionError("应生成 action.run_function 积木"));
        assertEquals("dpe:internal/tick", fn.fieldValues().get("function"));
    }

    @Test
    void jsonFileBecomesRawText() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("dpe/tags/functions.json", "{\"values\":[\"dpe:helper\"]}");

        EditorState state = new TextToBlocksParser().parse("dpe", files);
        EditorBlock raw = state.getBlocks().stream()
                .filter(b -> "raw_text".equals(b.schemaId()))
                .findFirst().orElseThrow(() -> new AssertionError("JSON 文件应转为 raw_text 积木"));
        assertTrue(raw.fieldValues().get("text").toString().contains("values"));
    }

    @Test
    void parsedBlocksLiveUnderTickRoot() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("dpe/t.mcfunction", "say A\nsay B");

        EditorState state = new TextToBlocksParser().parse("dpe", files);
        EditorBlock root = state.getById("blk_root");
        assertNotNull(root, "应有 event.tick 根");
        assertEquals("event.tick", root.schemaId());
        long says = state.getBlocks().stream()
                .filter(b -> "action.say_text".equals(b.schemaId())).count();
        assertEquals(2, says);
        // 根应连接两个 say 块
        assertEquals(2, root.childIds().size());
    }
}
