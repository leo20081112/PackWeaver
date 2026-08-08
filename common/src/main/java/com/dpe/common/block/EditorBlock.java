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
                          List<String> childIds,
                          String customName,
                          boolean collapsed) {

    public EditorBlock {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("block id 不能为空");
        }
        if (schemaId == null || schemaId.isBlank()) {
            throw new IllegalArgumentException("schemaId 不能为空");
        }
        fieldValues = fieldValues == null ? new HashMap<>() : new HashMap<>(fieldValues);
        childIds = childIds == null ? new ArrayList<>() : new ArrayList<>(childIds);
        customName = customName == null ? null : customName.trim();
    }

    /** 便捷构造（无字段、无子块、无 customName、无折叠）。 */
    public EditorBlock(String id, String schemaId, double x, double y) {
        this(id, schemaId, x, y, new HashMap<>(), new ArrayList<>(), null, false);
    }

    /** 便捷构造（无 customName、无折叠）。 */
    public EditorBlock(String id, String schemaId, double x, double y, Map<String, Object> fieldValues, List<String> childIds) {
        this(id, schemaId, x, y, fieldValues, childIds, null, false);
    }

    /** 深拷贝。 */
    public EditorBlock copy() {
        return new EditorBlock(id, schemaId, x, y, new HashMap<>(fieldValues), new ArrayList<>(childIds), customName, collapsed);
    }

    /** 获取显示名称：优先返回 customName，否则返回 null。 */
    public String displayName() {
        return customName;
    }
}
