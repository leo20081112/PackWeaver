package com.dpe.common.block;

import java.util.List;

/**
 * 积木块 schema（类型定义），不可变。
 * color 为 hex 如 "#4C97FF"；acceptsChildrenCategories 为子块可属 category 列表，可为空；
 * produces 为产物类型如 "mcfunction"/"tag"/"advancement"，可空。
 */
public record BlockSchema(String id,
                          BlockCategory category,
                          String label,
                          String color,
                          List<BlockField> fields,
                          List<String> acceptsChildrenCategories,
                          String produces) {

    public BlockSchema {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("schema id 不能为空");
        }
        if (category == null) {
            throw new IllegalArgumentException("schema category 不能为空");
        }
        fields = fields == null ? List.of() : List.copyOf(fields);
        acceptsChildrenCategories = acceptsChildrenCategories == null
                ? List.of() : List.copyOf(acceptsChildrenCategories);
    }
}
