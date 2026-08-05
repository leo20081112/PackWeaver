package com.dpe.common.protocol;

/**
 * 打开编辑器消息。
 */
public record OpenEditorMessage(String datapackNamespace) implements Message {
}
