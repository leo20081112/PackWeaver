package com.dpe.common.block;

import java.util.List;

/**
 * 积木块字段定义，不可变。
 * defaultValue 可为 null；enumValues 仅 ENUM 类型使用，可为 null/空。
 */
public record BlockField(String name,
                         BlockFieldType type,
                         boolean required,
                         String defaultValue,
                         List<String> enumValues) {

    public BlockField {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("field name 不能为空");
        }
        if (type == null) {
            throw new IllegalArgumentException("field type 不能为空");
        }
        enumValues = enumValues == null ? List.of() : List.copyOf(enumValues);
    }

    /** 便捷构造（无默认值、无枚举值）。 */
    public BlockField(String name, BlockFieldType type, boolean required) {
        this(name, type, required, null, List.of());
    }
}
