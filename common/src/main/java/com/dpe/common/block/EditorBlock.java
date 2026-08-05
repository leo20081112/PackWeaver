package com.dpe.common.block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 编辑器画布上的一个积木块实例。
 * record 本身引用不可变，但 fieldValues 与 childIds 内部用可变集合，
 * 以便 EditorState 直接增删子块、改字段。
 */
public record EditorBlock(String id,
                          String schemaId,
                          double x,
                          double y,
                          Map<String, Object> fieldValues,
                          List<String> childIds) {

    public EditorBlock {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("block id 不能为空");
        }
        if (schemaId == null || schemaId.isBlank()) {
            throw new IllegalArgumentException("schemaId 不能为空");
        }
        fieldValues = fieldValues == null ? new HashMap<>() : new HashMap<>(fieldValues);
        childIds = childIds == null ? new ArrayList<>() : new ArrayList<>(childIds);
    }

    /** 便捷构造（无字段、无子块）。 */
    public EditorBlock(String id, String schemaId, double x, double y) {
        this(id, schemaId, x, y, new HashMap<>(), new ArrayList<>());
    }

    /** 深拷贝。 */
    public EditorBlock copy() {
        return new EditorBlock(id, schemaId, x, y, new HashMap<>(fieldValues), new ArrayList<>(childIds));
    }
}
