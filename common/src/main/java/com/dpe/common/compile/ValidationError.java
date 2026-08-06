package com.dpe.common.compile;

/**
 * 校验错误，不可变。blockId/field 可为 null（如 schema 不存在时 field 为 null）。
 * friendlyMessage 为中文原因说明，fixSuggestion 为修复建议（均可为空以保持向后兼容）。
 */
public record ValidationError(String blockId,
                              String field,
                              String message,
                              String friendlyMessage,
                              String fixSuggestion) {

    public ValidationError {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("error message 不能为空");
        }
        if (friendlyMessage == null) {
            friendlyMessage = "";
        }
        if (fixSuggestion == null) {
            fixSuggestion = "";
        }
    }

    /** 兼容旧构造（无 friendly/suggestion）。 */
    public ValidationError(String blockId, String field, String message) {
        this(blockId, field, message, "", "");
    }

    /** 兼容旧构造（无 field、无 friendly/suggestion）。 */
    public ValidationError(String blockId, String message) {
        this(blockId, null, message, "", "");
    }
}
