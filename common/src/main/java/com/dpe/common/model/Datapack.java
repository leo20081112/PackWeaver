package com.dpe.common.model;

import java.util.List;

/**
 * 数据包聚合根，不可变。
 * 包含 namespace、函数、标签、进度、战利品表。
 */
public record Datapack(String namespace,
                       List<McFunction> functions,
                       List<Tag> tags,
                       List<Advancement> advancements,
                       List<LootTable> lootTables) {

    public Datapack {
        if (namespace == null || namespace.isBlank()) {
            throw new IllegalArgumentException("datapack namespace 不能为空");
        }
        functions = functions == null ? List.of() : List.copyOf(functions);
        tags = tags == null ? List.of() : List.copyOf(tags);
        advancements = advancements == null ? List.of() : List.copyOf(advancements);
        lootTables = lootTables == null ? List.of() : List.copyOf(lootTables);
    }
}
