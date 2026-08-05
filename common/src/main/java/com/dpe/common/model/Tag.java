package com.dpe.common.model;

import java.util.List;

/**
 * 标签文件，不可变。
 * type 取值：blocks / items / entities / functions。
 */
public record Tag(ResourceLocation id, String type, List<ResourceLocation> values, boolean replace) {

    public Tag {
        if (id == null) {
            throw new IllegalArgumentException("tag id 不能为空");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("tag type 不能为空");
        }
        values = values == null ? List.of() : List.copyOf(values);
    }
}
