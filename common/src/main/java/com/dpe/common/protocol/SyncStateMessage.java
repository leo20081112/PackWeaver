package com.dpe.common.protocol;

/**
 * 同步编辑器状态消息。editorStateJson 为 {@link com.dpe.common.block.EditorState#toJson()} 的结果。
 */
public record SyncStateMessage(String editorStateJson, long revision) implements Message {
}
