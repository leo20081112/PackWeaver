package com.dpe.common.model;

import com.google.gson.JsonObject;

/**
 * 战利品表（loot table），原始 JSON 由 Gson JsonObject 持有，不可变封装。
 */
public record LootTable(ResourceLocation id, JsonObject raw) {

    public LootTable {
        if (id == null) {
            throw new IllegalArgumentException("loot table id 不能为空");
        }
        raw = raw == null ? new JsonObject() : raw.deepCopy();
    }

    public JsonObject getRaw() {
        return raw.deepCopy();
    }
}
