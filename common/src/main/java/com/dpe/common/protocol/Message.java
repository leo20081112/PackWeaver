package com.dpe.common.protocol;

/**
 * 客户端/服务端协议消息 sealed 接口。
 * 子类为 records，通过 {@link MessageCodec} 用 "type" 字段鉴别编解码。
 */
public sealed interface Message
        permits OpenEditorMessage, EditOpMessage, SyncStateMessage,
        SaveApplyMessage, KeepAliveMessage, ErrorMessage {
}
