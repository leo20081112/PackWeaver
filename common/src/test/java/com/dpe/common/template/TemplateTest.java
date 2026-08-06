package com.dpe.common.template;

import com.dpe.common.block.EditorBlock;
import com.dpe.common.block.EditorState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BuiltinTemplates 单元测试。
 */
class TemplateTest {

    @Test
    void allReturnsThreeTemplates() {
        List<DatapackTemplate> all = BuiltinTemplates.all();
        assertEquals(3, all.size(), "应内置 3 个模板: " + all);
    }

    @Test
    void tickNotifyByIdHasPresetWithBlocks() {
        DatapackTemplate t = BuiltinTemplates.byId("tick_notify");
        assertNotNull(t, "byId(tick_notify) 不应为 null");
        assertEquals("每刻通知", t.title());
        EditorState preset = t.preset();
        assertNotNull(preset);
        assertFalse(preset.getBlocks().isEmpty(), "preset 应含积木");

        // 应含 event.tick 根与 action.tellraw 子块，且 tellraw text="Tick!"
        EditorBlock tick = preset.getById("b1");
        assertNotNull(tick);
        assertEquals("event.tick", tick.schemaId());
        EditorBlock tellraw = preset.getById("b2");
        assertNotNull(tellraw);
        assertEquals("action.tellraw", tellraw.schemaId());
        assertEquals("Tick!", tellraw.fieldValues().get("text"));
        assertTrue(tick.childIds().contains("b2"), "event.tick 应连接 tellraw");
    }

    @Test
    void killRewardWiresConditionBetweenEventAndAction() {
        DatapackTemplate t = BuiltinTemplates.byId("kill_reward");
        assertNotNull(t);
        EditorState preset = t.preset();
        EditorBlock death = preset.getById("b1");
        assertEquals("event.entity_death", death.schemaId());
        assertEquals("minecraft:zombie", death.fieldValues().get("entity_type"));

        EditorBlock cond = preset.getById("b2");
        assertEquals("condition.score_compare", cond.schemaId());

        EditorBlock give = preset.getById("b3");
        assertEquals("action.give_item", give.schemaId());
        assertEquals("minecraft:diamond", give.fieldValues().get("item"));

        assertTrue(death.childIds().contains("b2"));
        assertTrue(cond.childIds().contains("b3"));
    }

    @Test
    void byIdUnknownReturnsNull() {
        assertNull(BuiltinTemplates.byId("does_not_exist"));
    }

    @Test
    void presetNamespaceIsDpe() {
        for (DatapackTemplate t : BuiltinTemplates.all()) {
            assertEquals("dpe", t.preset().getActiveDatapackNamespace(),
                    "模板 preset 命名空间应为 dpe: " + t.id());
        }
    }
}
