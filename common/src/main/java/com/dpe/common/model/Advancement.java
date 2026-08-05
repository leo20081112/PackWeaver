package com.dpe.common.model;

import com.google.gson.JsonObject;

/**
 * 进度文件（advancement），原始 JSON 由 Gson JsonObject 持有，不可变封装。
 */
public record Advancement(ResourceLocation id, JsonObject raw) {

    public Advancement {
        if (id == null) {
            throw new IllegalArgumentException("advancement id 不能为空");
        }
        // JsonObject 可变，深拷贝一份以保持不可变外观
        raw = raw == null ? new JsonObject() : raw.deepCopy();
    }

    public JsonObject getRaw() {
        return raw.deepCopy();
    }
}
