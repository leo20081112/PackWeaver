package com.dpe.common.protocol;

/**
 * 保存并应用数据包消息。
 */
public record SaveApplyMessage(String datapackNamespace) implements Message {
}
