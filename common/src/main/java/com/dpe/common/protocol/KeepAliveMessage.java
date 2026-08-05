package com.dpe.common.protocol;

/**
 * 心跳保活消息。
 */
public record KeepAliveMessage(long timestamp) implements Message {
}
