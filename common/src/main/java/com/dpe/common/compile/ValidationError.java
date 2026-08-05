package com.dpe.common.compile;

/**
 * 校验错误，不可变。blockId/field 可为 null（如 schema 不存在时 field 为 null）。
 */
public record ValidationError(String blockId, String field, String message) {

    public ValidationError {
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("error message 不能为空");
        }
    }

    /** 便捷构造（无具体字段）。 */
    public ValidationError(String blockId, String message) {
        this(blockId, null, message);
    }
}
