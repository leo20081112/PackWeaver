package com.dpe.common.parse;

import com.dpe.common.block.BlockSchemaRegistry;
import com.dpe.common.block.EditorBlock;
import com.dpe.common.block.EditorState;
import com.dpe.common.compile.BlockCompiler;
import com.dpe.common.compile.CompileResult;
import com.dpe.common.model.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TextToBlocksParser 单元测试。
 */
class TextToBlocksParserTest {

    private final TextToBlocksParser parser = new TextToBlocksParser();
    private final BlockCompiler compiler = new BlockCompiler();

    // ---------- 原有用例 ----------

    @Test
    void parsesSayCommandToSayTextBlock() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("dpe/internal/tick.mcfunction", "say Hello");

        EditorState state = parser.parse("dpe", files);

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

        EditorState state = parser.parse("dpe", files);

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

        EditorState state = parser.parse("dpe", files);

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

        EditorState state = parser.parse("dpe", files);
        EditorBlock raw = state.getBlocks().stream()
                .filter(b -> "raw_text".equals(b.schemaId()))
                .findFirst().orElseThrow(() -> new AssertionError("JSON 文件应转为 raw_text 积木"));
        assertTrue(raw.fieldValues().get("text").toString().contains("values"));
    }

    @Test
    void parsedBlocksLiveUnderTickRoot() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("dpe/t.mcfunction", "say A\nsay B");

        EditorState state = parser.parse("dpe", files);
        EditorBlock root = state.getById("blk_root");
        assertNotNull(root, "应有 event.tick 根");
        assertEquals("event.tick", root.schemaId());
        long says = state.getBlocks().stream()
                .filter(b -> "action.say_text".equals(b.schemaId())).count();
        assertEquals(2, says);
        // 根应连接两个 say 块
        assertEquals(2, root.childIds().size());
    }

    // ---------- 新增：setblock/give/summon 解析 ----------

    @Test
    void parsesSetblockCommand() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("dpe/internal/tick.mcfunction", "setblock 0 0 0 minecraft:stone");

        EditorState state = parser.parse("dpe", files);
        EditorBlock sb = state.getBlocks().stream()
                .filter(b -> "action.set_block".equals(b.schemaId()))
                .findFirst().orElseThrow(() -> new AssertionError("应生成 action.set_block 积木"));
        assertEquals("0 0 0", sb.fieldValues().get("pos"));
        assertEquals("minecraft:stone", sb.fieldValues().get("block"));
    }

    @Test
    void parsesGiveCommandWithCount() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("dpe/internal/tick.mcfunction", "give @p minecraft:apple 3");

        EditorState state = parser.parse("dpe", files);
        EditorBlock give = state.getBlocks().stream()
                .filter(b -> "action.give_item".equals(b.schemaId()))
                .findFirst().orElseThrow(() -> new AssertionError("应生成 action.give_item 积木"));
        assertEquals("@p", give.fieldValues().get("target"));
        assertEquals("minecraft:apple", give.fieldValues().get("item"));
        assertEquals("3", give.fieldValues().get("count"));
    }

    @Test
    void parsesGiveCommandWithoutCount() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("dpe/internal/tick.mcfunction", "give @p minecraft:stick");

        EditorState state = parser.parse("dpe", files);
        EditorBlock give = state.getBlocks().stream()
                .filter(b -> "action.give_item".equals(b.schemaId()))
                .findFirst().orElseThrow(() -> new AssertionError("应生成 action.give_item 积木"));
        assertEquals("@p", give.fieldValues().get("target"));
        assertEquals("minecraft:stick", give.fieldValues().get("item"));
        // 无 count 时不应包含该字段（编译时回退到默认无 count）
        assertFalse(give.fieldValues().containsKey("count"));
    }

    @Test
    void parsesSummonCommand() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("dpe/internal/tick.mcfunction", "summon minecraft:zombie 1 2 3");

        EditorState state = parser.parse("dpe", files);
        EditorBlock sum = state.getBlocks().stream()
                .filter(b -> "action.summon".equals(b.schemaId()))
                .findFirst().orElseThrow(() -> new AssertionError("应生成 action.summon 积木"));
        assertEquals("minecraft:zombie", sum.fieldValues().get("entity"));
        assertEquals("1 2 3", sum.fieldValues().get("pos"));
    }

    // ---------- execute if 解析 ----------

    @Test
    void parsesExecuteIfScoreAsConditionWithChild() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("dpe/internal/tick.mcfunction", "execute if score @a foo >= 5 run say Hi");

        EditorState state = parser.parse("dpe", files);
        EditorBlock cond = state.getBlocks().stream()
                .filter(b -> "condition.score_compare".equals(b.schemaId()))
                .findFirst().orElseThrow(() -> new AssertionError("应生成 condition.score_compare 积木"));
        assertEquals("@a", cond.fieldValues().get("target"));
        assertEquals("foo", cond.fieldValues().get("objective"));
        assertEquals("\u2265", cond.fieldValues().get("op"), ">= 应映射为 ≥");
        assertEquals("5", cond.fieldValues().get("value"));
        // 应包含一个 action.say_text 子块
        assertEquals(1, cond.childIds().size());
        EditorBlock child = state.getById(cond.childIds().get(0));
        assertNotNull(child);
        assertEquals("action.say_text", child.schemaId());
        assertEquals("Hi", child.fieldValues().get("text"));
    }

    @Test
    void parsesExecuteIfEntityAsConditionWithChild() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("dpe/internal/tick.mcfunction",
                "execute if entity @e[type=minecraft:zombie] run say Z");

        EditorState state = parser.parse("dpe", files);
        EditorBlock cond = state.getBlocks().stream()
                .filter(b -> "condition.entity_exists".equals(b.schemaId()))
                .findFirst().orElseThrow(() -> new AssertionError("应生成 condition.entity_exists 积木"));
        assertEquals("minecraft:zombie", cond.fieldValues().get("entity_type"));
        assertEquals(1, cond.childIds().size());
        EditorBlock child = state.getById(cond.childIds().get(0));
        assertNotNull(child);
        assertEquals("action.say_text", child.schemaId());
        assertEquals("Z", child.fieldValues().get("text"));
    }

    // ---------- 往返无损 ----------

    @Test
    void setblockRoundTripPreservesText() {
        assertRoundTrip("setblock 0 0 0 minecraft:stone");
    }

    @Test
    void giveWithCountRoundTripPreservesText() {
        assertRoundTrip("give @p minecraft:apple 3");
    }

    @Test
    void giveWithoutCountRoundTripPreservesText() {
        assertRoundTrip("give @p minecraft:stick");
    }

    @Test
    void summonRoundTripPreservesText() {
        assertRoundTrip("summon minecraft:zombie 1 2 3");
    }

    @Test
    void rawTextRoundTripPreservesText() {
        // 未知命令保留为 raw_text，编译后原样输出
        assertRoundTrip("weather rain");
    }

    @Test
    void executeIfScoreRoundTripPreservesText() {
        assertRoundTrip("execute if score @a foo >= 5 run say Hi");
    }

    @Test
    void executeIfEntityRoundTripPreservesText() {
        assertRoundTrip("execute if entity @e[type=minecraft:zombie] run say Z");
    }

    /** parse -> compile 后内容应一致（忽略 dpe 生成注释行与空行）。 */
    private void assertRoundTrip(String mcfunctionContent) {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("dpe/internal/tick.mcfunction", mcfunctionContent);

        EditorState state = parser.parse("dpe", files);
        CompileResult result = compiler.compile(state, BlockSchemaRegistry.DEFAULT);
        assertTrue(result.success(), "编译应成功: " + result.errors());
        String compiled = result.mcfunctions().get(new ResourceLocation("dpe", "internal/tick"));
        assertNotNull(compiled, "应生成 internal/tick 函数");
        assertEquals(filterSignificant(mcfunctionContent), filterSignificant(compiled),
                "往返后内容应一致（忽略注释/空行）");
    }

    /** 过滤掉空行与 dpe 生成注释行，保留有效命令行。 */
    private List<String> filterSignificant(String content) {
        List<String> out = new ArrayList<>();
        for (String l : content.split("\n", -1)) {
            String t = l.trim();
            if (t.isEmpty() || t.startsWith("# 由 PackWeaver 编辑器生成")) {
                continue;
            }
            out.add(t);
        }
        return out;
    }

    // ---------- dpe 注释跳过 ----------

    @Test
    void dpeGeneratedCommentIsSkipped() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("dpe/internal/tick.mcfunction",
                "# 由 PackWeaver 编辑器生成 - 事件: event.tick\nsay Hello");

        EditorState state = parser.parse("dpe", files);
        long raws = state.getBlocks().stream()
                .filter(b -> "raw_text".equals(b.schemaId())).count();
        assertEquals(0, raws, "dpe 生成注释不应生成 raw_text 积木");
        long says = state.getBlocks().stream()
                .filter(b -> "action.say_text".equals(b.schemaId())).count();
        assertEquals(1, says);
    }

    // ---------- 网格布局 ----------

    @Test
    void newBlocksAreNotAtOrigin() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("dpe/internal/tick.mcfunction", "say A\nsay B");

        EditorState state = parser.parse("dpe", files);
        for (EditorBlock b : state.getBlocks()) {
            if (b.schemaId().startsWith("event.")) {
                continue;
            }
            assertFalse(b.x() == 0 && b.y() == 0,
                    "新建积木不应位于 (0,0): " + b.id() + " @(" + b.x() + "," + b.y() + ")");
        }
    }

    // ---------- 文件路径路由 ----------

    @Test
    void internalLoadPathRoutesToLoadRoot() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("dpe/internal/load.mcfunction", "say onLoad");

        EditorState state = parser.parse("dpe", files);
        EditorBlock loadRoot = state.getBlocks().stream()
                .filter(b -> "event.load".equals(b.schemaId()))
                .findFirst().orElseThrow(() -> new AssertionError("应有 event.load 根"));
        assertTrue(loadRoot.childIds().size() >= 1, "load 根应有子块");
    }

    @Test
    void tickPathRoutesToTickRoot() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("dpe/internal/tick.mcfunction", "say tick");

        EditorState state = parser.parse("dpe", files);
        EditorBlock tickRoot = state.getBlocks().stream()
                .filter(b -> "event.tick".equals(b.schemaId()))
                .findFirst().orElseThrow(() -> new AssertionError("应有 event.tick 根"));
        assertTrue(tickRoot.childIds().size() >= 1, "tick 根应有子块");
    }

    @Test
    void playerJoinPathRoutesToPlayerJoinRoot() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("dpe/internal/on_player_join.mcfunction", "say welcome");

        EditorState state = parser.parse("dpe", files);
        EditorBlock root = state.getBlocks().stream()
                .filter(b -> "event.player_join".equals(b.schemaId()))
                .findFirst().orElseThrow(() -> new AssertionError("应有 event.player_join 根"));
        assertTrue(root.childIds().size() >= 1, "player_join 根应有子块");
    }

    @Test
    void entityDeathPathRoutesToEntityDeathRoot() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("dpe/internal/entity_death.mcfunction", "say death");

        EditorState state = parser.parse("dpe", files);
        EditorBlock root = state.getBlocks().stream()
                .filter(b -> "event.entity_death".equals(b.schemaId()))
                .findFirst().orElseThrow(() -> new AssertionError("应有 event.entity_death 根"));
        assertTrue(root.childIds().size() >= 1, "entity_death 根应有子块");
    }

    @Test
    void multipleEventRootsStackVertically() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("dpe/internal/tick.mcfunction", "say tick");
        files.put("dpe/internal/load.mcfunction", "say load");

        EditorState state = parser.parse("dpe", files);
        EditorBlock tick = state.getBlocks().stream()
                .filter(b -> "event.tick".equals(b.schemaId())).findFirst().orElseThrow();
        EditorBlock load = state.getBlocks().stream()
                .filter(b -> "event.load".equals(b.schemaId())).findFirst().orElseThrow();
        assertNotEquals(tick.y(), load.y(), 0.001, "两个事件根不应重叠在同一 y 坐标");
    }

    @Test
    void parseTwiceMergePreservesCoordinates() {
        Map<String, String> files = new LinkedHashMap<>();
        files.put("dpe/internal/tick.mcfunction", "say Hello");

        EditorState stateA = parser.parse("dpe", files);
        EditorBlock sayA = stateBlockBySchema(stateA, "action.say_text");
        assertNotNull(sayA);
        double ax = sayA.x();
        double ay = sayA.y();
        assertNotEquals(0, ax + ay, 0.001, "首次解析的子块应在网格上而非原点");

        // 用相同文本再次解析（合并入 stateA），坐标应保留
        EditorState stateB = parser.parse("dpe", files, stateA);
        assertSame(stateA, stateB);
        EditorBlock sayB = stateBlockBySchema(stateA, "action.say_text");
        assertEquals(ax, sayB.x(), 0.001, "合并后 x 应保留");
        assertEquals(ay, sayB.y(), 0.001, "合并后 y 应保留");
    }

    // ---------- 合并解析：保留已有积木坐标 ----------

    @Test
    void mergeParseKeepsExistingBlockCoordinates() {
        // 已有状态：tick 根 + 一个 say_text 积木位于 (50, 70)
        EditorState existing = new EditorState("dpe");
        EditorBlock tick = new EditorBlock("blk_root", "event.tick", 0, 0);
        EditorBlock say = new EditorBlock("existing_say", "action.say_text", 50, 70,
                Map.of("text", "Hello"), List.of());
        existing.addBlock(tick);
        existing.addBlock(say);
        existing.connect("blk_root", "existing_say");

        Map<String, String> files = new LinkedHashMap<>();
        files.put("dpe/internal/tick.mcfunction", "say Hello");

        EditorState merged = parser.parse("dpe", files, existing);
        assertSame(existing, merged, "应复用同一 EditorState 引用");

        EditorBlock matched = stateBlockBySchema(existing, "action.say_text");
        assertNotNull(matched);
        assertEquals("existing_say", matched.id(), "应复用已有积木 id");
        assertEquals(50, matched.x(), 0.001, "已有积木 x 应保留");
        assertEquals(70, matched.y(), 0.001, "已有积木 y 应保留");
        assertEquals("Hello", matched.fieldValues().get("text"));
    }

    @Test
    void mergeParseKeepsUnmatchedExistingBlocks() {
        // 已有状态：tick 根 + 一个 say_text("Manual") 手动布置的积木
        EditorState existing = new EditorState("dpe");
        EditorBlock tick = new EditorBlock("blk_root", "event.tick", 0, 0);
        EditorBlock manual = new EditorBlock("manual_blk", "action.say_text", 200, 300,
                Map.of("text", "Manual"), List.of());
        existing.addBlock(tick);
        existing.addBlock(manual);
        existing.connect("blk_root", "manual_blk");

        Map<String, String> files = new LinkedHashMap<>();
        files.put("dpe/internal/tick.mcfunction", "say Other");

        parser.parse("dpe", files, existing);
        // 文本中未出现的已有积木应保留
        EditorBlock stillThere = existing.getById("manual_blk");
        assertNotNull(stillThere, "未匹配的已有积木应保留");
        assertEquals("Manual", stillThere.fieldValues().get("text"));
        assertEquals(200, stillThere.x(), 0.001);
        assertEquals(300, stillThere.y(), 0.001);
    }

    private EditorBlock stateBlockBySchema(EditorState state, String schemaId) {
        return state.getBlocks().stream()
                .filter(b -> schemaId.equals(b.schemaId()))
                .findFirst().orElse(null);
    }
}
