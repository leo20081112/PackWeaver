package com.dpe.common.protocol;

/**
 * 错误消息。
 */
public record ErrorMessage(String code, String message) implements Message {

    public ErrorMessage {
        if (code == null || code.isBlank()) {
            code = "UNKNOWN";
        }
    }
}
