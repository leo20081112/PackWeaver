package com.dpe.common.protocol;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MessageCodec 单元测试。
 */
class MessageCodecTest {

    @Test
    void openEditorRoundTrip() {
        OpenEditorMessage msg = new OpenEditorMessage("mydp");
        String json = MessageCodec.toJson(msg);
        assertTrue(json.contains("\"type\": \"open_editor\""), "type 字段应正确: " + json);

        Message parsed = MessageCodec.fromJson(json);
        assertInstanceOf(OpenEditorMessage.class, parsed);
        assertEquals("mydp", ((OpenEditorMessage) parsed).datapackNamespace());
    }

    @Test
    void editOpRoundTrip() {
        EditOpMessage msg = new EditOpMessage("field", "b1", "text", "Hello", "player1");
        String json = MessageCodec.toJson(msg);
        assertTrue(json.contains("\"type\": \"edit_op\""));

        Message parsed = MessageCodec.fromJson(json);
        assertInstanceOf(EditOpMessage.class, parsed);
        EditOpMessage e = (EditOpMessage) parsed;
        assertEquals("field", e.op());
        assertEquals("b1", e.blockId());
        assertEquals("text", e.field());
        assertEquals("Hello", e.value());
        assertEquals("player1", e.playerId());
    }

    @Test
    void syncStateRoundTrip() {
        SyncStateMessage msg = new SyncStateMessage("{\"blocks\":[]}", 42L);
        String json = MessageCodec.toJson(msg);
        assertTrue(json.contains("\"type\": \"sync_state\""));

        Message parsed = MessageCodec.fromJson(json);
        assertInstanceOf(SyncStateMessage.class, parsed);
        SyncStateMessage s = (SyncStateMessage) parsed;
        assertEquals("{\"blocks\":[]}", s.editorStateJson());
        assertEquals(42L, s.revision());
    }

    @Test
    void saveApplyRoundTrip() {
        SaveApplyMessage msg = new SaveApplyMessage("mydp");
        String json = MessageCodec.toJson(msg);
        assertTrue(json.contains("\"type\": \"save_apply\""));

        Message parsed = MessageCodec.fromJson(json);
        assertInstanceOf(SaveApplyMessage.class, parsed);
        assertEquals("mydp", ((SaveApplyMessage) parsed).datapackNamespace());
    }

    @Test
    void keepAliveRoundTrip() {
        KeepAliveMessage msg = new KeepAliveMessage(1234567890L);
        String json = MessageCodec.toJson(msg);
        assertTrue(json.contains("\"type\": \"keep_alive\""));

        Message parsed = MessageCodec.fromJson(json);
        assertInstanceOf(KeepAliveMessage.class, parsed);
        assertEquals(1234567890L, ((KeepAliveMessage) parsed).timestamp());
    }

    @Test
    void errorRoundTrip() {
        ErrorMessage msg = new ErrorMessage("ERR_BAD", "出错了");
        String json = MessageCodec.toJson(msg);
        assertTrue(json.contains("\"type\": \"error\""));

        Message parsed = MessageCodec.fromJson(json);
        assertInstanceOf(ErrorMessage.class, parsed);
        ErrorMessage e = (ErrorMessage) parsed;
        assertEquals("ERR_BAD", e.code());
        assertEquals("出错了", e.message());
    }

    @Test
    void unknownTypeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> MessageCodec.fromJson("{\"type\":\"unknown\"}"));
    }
}
