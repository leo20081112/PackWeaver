package com.dpe.common.block;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 编辑器状态，可变。持有积木块 map、缩放、平移、当前数据包命名空间。
 * 支持与 JSON 双向同步（{@link #toJson()} / {@link #fromJson(String)}）。
 */
public final class EditorState {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Map<String, EditorBlock> blocksById = new LinkedHashMap<>();
    private double zoom = 1.0;
    private double panX = 0.0;
    private double panY = 0.0;
    private String activeDatapackNamespace = "minecraft";
    private String projectId = null;

    public EditorState() {
    }

    public EditorState(String activeDatapackNamespace) {
        this.activeDatapackNamespace = activeDatapackNamespace == null || activeDatapackNamespace.isBlank()
                ? "minecraft" : activeDatapackNamespace;
    }

    /** 添加块（同 id 覆盖）。 */
    public void addBlock(EditorBlock block) {
        if (block == null) {
            throw new IllegalArgumentException("block 不能为空");
        }
        blocksById.put(block.id(), block);
    }

    /** 移除块；同时从所有父块的 childIds 中清除引用。 */
    public EditorBlock removeBlock(String id) {
        EditorBlock removed = blocksById.remove(id);
        if (removed != null) {
            for (EditorBlock b : blocksById.values()) {
                b.childIds().remove(id);
            }
        }
        return removed;
    }

    /** 移动块到新坐标。 */
    public boolean moveBlock(String id, double x, double y) {
        EditorBlock b = blocksById.get(id);
        if (b == null) {
            return false;
        }
        blocksById.put(id, new EditorBlock(b.id(), b.schemaId(), x, y, b.fieldValues(), b.childIds(), b.customName(), b.collapsed()));
        return true;
    }

    /** 更新块的自定义名称。 */
    public boolean setCustomName(String id, String customName) {
        EditorBlock b = blocksById.get(id);
        if (b == null) {
            return false;
        }
        blocksById.put(id, new EditorBlock(b.id(), b.schemaId(), b.x(), b.y(), b.fieldValues(), b.childIds(), customName, b.collapsed()));
        return true;
    }
    
    /** 设置块的折叠状态。 */
    public boolean setCollapsed(String id, boolean collapsed) {
        EditorBlock b = blocksById.get(id);
        if (b == null) {
            return false;
        }
        blocksById.put(id, new EditorBlock(b.id(), b.schemaId(), b.x(), b.y(), b.fieldValues(), b.childIds(), b.customName(), collapsed));
        return true;
    }

    /** 按 customName 精确查找积木。 */
    public EditorBlock getByCustomName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        for (EditorBlock b : blocksById.values()) {
            if (name.equals(b.customName())) {
                return b;
            }
        }
        return null;
    }

    /** 按 customName 模糊匹配查找积木（包含关系）。 */
    public List<EditorBlock> getBlocksByCustomName(String name) {
        List<EditorBlock> result = new ArrayList<>();
        if (name == null || name.isBlank()) {
            return result;
        }
        String lower = name.toLowerCase();
        for (EditorBlock b : blocksById.values()) {
            if (b.customName() != null && b.customName().toLowerCase().contains(lower)) {
                result.add(b);
            }
        }
        return result;
    }

    /** 连接父子块（父块 childIds 加入子块 id）。 */
    public boolean connect(String parentId, String childId) {
        EditorBlock parent = blocksById.get(parentId);
        if (parent == null || !blocksById.containsKey(childId) || parentId.equals(childId)) {
            return false;
        }
        if (!parent.childIds().contains(childId)) {
            parent.childIds().add(childId);
        }
        return true;
    }

    /** 断开父子连接。 */
    public boolean disconnect(String parentId, String childId) {
        EditorBlock parent = blocksById.get(parentId);
        if (parent == null) {
            return false;
        }
        return parent.childIds().remove(childId);
    }

    /** 全部块（可变视图，按插入顺序）。 */
    public Collection<EditorBlock> getBlocks() {
        return blocksById.values();
    }

    public EditorBlock getById(String id) {
        return blocksById.get(id);
    }

    public double getZoom() {
        return zoom;
    }

    public void setZoom(double zoom) {
        this.zoom = zoom;
    }

    public double getPanX() {
        return panX;
    }

    public double getPanY() {
        return panY;
    }

    public void setPan(double panX, double panY) {
        this.panX = panX;
        this.panY = panY;
    }

    public String getActiveDatapackNamespace() {
        return activeDatapackNamespace;
    }

    public void setActiveDatapackNamespace(String ns) {
        this.activeDatapackNamespace = ns == null || ns.isBlank() ? "minecraft" : ns;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    /** 序列化为 JSON 字符串。 */
    public String toJson() {
        JsonObject root = new JsonObject();
        JsonArray arr = new JsonArray();
        for (EditorBlock b : blocksById.values()) {
            JsonObject bo = new JsonObject();
            bo.addProperty("id", b.id());
            bo.addProperty("schemaId", b.schemaId());
            bo.addProperty("x", b.x());
            bo.addProperty("y", b.y());
            bo.add("fieldValues", GSON.toJsonTree(b.fieldValues()));
            JsonArray kids = new JsonArray();
            for (String c : b.childIds()) {
                kids.add(c);
            }
            bo.add("childIds", kids);
            if (b.customName() != null && !b.customName().isBlank()) {
                bo.addProperty("customName", b.customName());
            }
            if (b.collapsed()) {
                bo.addProperty("collapsed", true);
            }
            arr.add(bo);
        }
        root.add("blocks", arr);
        root.addProperty("zoom", zoom);
        root.addProperty("panX", panX);
        root.addProperty("panY", panY);
        root.addProperty("activeDatapackNamespace", activeDatapackNamespace);
        if (projectId != null) {
            root.addProperty("projectId", projectId);
        }
        return GSON.toJson(root);
    }

    /** 从 JSON 字符串重建状态。 */
    @SuppressWarnings("unchecked")
    public static EditorState fromJson(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        EditorState state = new EditorState();
        if (root.has("activeDatapackNamespace")) {
            state.activeDatapackNamespace = root.get("activeDatapackNamespace").getAsString();
        }
        if (root.has("projectId")) {
            state.projectId = root.get("projectId").getAsString();
        }
        if (root.has("zoom")) {
            state.zoom = root.get("zoom").getAsDouble();
        }
        if (root.has("panX")) {
            state.panX = root.get("panX").getAsDouble();
        }
        if (root.has("panY")) {
            state.panY = root.get("panY").getAsDouble();
        }
        if (root.has("blocks")) {
            for (JsonElement e : root.getAsJsonArray("blocks")) {
                JsonObject bo = e.getAsJsonObject();
                String id = bo.get("id").getAsString();
                String schemaId = bo.get("schemaId").getAsString();
                double x = bo.get("x").getAsDouble();
                double y = bo.get("y").getAsDouble();
                Map<String, Object> fv = new LinkedHashMap<>();
                if (bo.has("fieldValues") && bo.get("fieldValues").isJsonObject()) {
                    JsonObject fvo = bo.getAsJsonObject("fieldValues");
                    for (Map.Entry<String, JsonElement> en : fvo.entrySet()) {
                        fv.put(en.getKey(), jsonElementToObject(en.getValue()));
                    }
                }
                List<String> kids = new ArrayList<>();
                if (bo.has("childIds")) {
                    for (JsonElement ce : bo.getAsJsonArray("childIds")) {
                        kids.add(ce.getAsString());
                    }
                }
                String customName = null;
                if (bo.has("customName") && !bo.get("customName").isJsonNull()) {
                    customName = bo.get("customName").getAsString();
                }
                boolean collapsed = false;
                if (bo.has("collapsed") && !bo.get("collapsed").isJsonNull()) {
                    collapsed = bo.get("collapsed").getAsBoolean();
                }
                state.blocksById.put(id, new EditorBlock(id, schemaId, x, y, fv, kids, customName, collapsed));
            }
        }
        return state;
    }

    /** Gson JsonElement → 普通 Java 对象。 */
    private static Object jsonElementToObject(JsonElement e) {
        if (e == null || e.isJsonNull()) {
            return null;
        }
        if (e.isJsonPrimitive()) {
            var p = e.getAsJsonPrimitive();
            if (p.isBoolean()) {
                return p.getAsBoolean();
            }
            if (p.isNumber()) {
                return p.getAsNumber();
            }
            return p.getAsString();
        }
        if (e.isJsonObject()) {
            return e.getAsJsonObject();
        }
        if (e.isJsonArray()) {
            return e.getAsJsonArray();
        }
        return e.toString();
    }
}
