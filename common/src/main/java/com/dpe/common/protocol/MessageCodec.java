package com.dpe.common.protocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Message 编解码器，使用 "type" 字段鉴别子类。
 */
public final class MessageCodec {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static final String TYPE_OPEN_EDITOR = "open_editor";
    public static final String TYPE_EDIT_OP = "edit_op";
    public static final String TYPE_SYNC_STATE = "sync_state";
    public static final String TYPE_SAVE_APPLY = "save_apply";
    public static final String TYPE_KEEP_ALIVE = "keep_alive";
    public static final String TYPE_ERROR = "error";

    private MessageCodec() {
    }

    /** 序列化为 JSON 字符串（含 type 鉴别字段）。 */
    public static String toJson(Message msg) {
        JsonObject o = GSON.toJsonTree(msg).getAsJsonObject();
        o.addProperty("type", typeOf(msg));
        return GSON.toJson(o);
    }

    /** 从 JSON 字符串解析为 Message。 */
    public static Message fromJson(String json) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        String type = o.get("type").getAsString();
        return switch (type) {
            case TYPE_OPEN_EDITOR -> new OpenEditorMessage(getStr(o, "datapackNamespace"));
            case TYPE_EDIT_OP -> new EditOpMessage(
                    getStr(o, "op"),
                    getStr(o, "blockId"),
                    getStr(o, "field"),
                    getObject(o, "value"),
                    getStr(o, "playerId"));
            case TYPE_SYNC_STATE -> new SyncStateMessage(getStr(o, "editorStateJson"),
                    o.has("revision") ? o.get("revision").getAsLong() : 0L);
            case TYPE_SAVE_APPLY -> new SaveApplyMessage(getStr(o, "datapackNamespace"));
            case TYPE_KEEP_ALIVE -> new KeepAliveMessage(
                    o.has("timestamp") ? o.get("timestamp").getAsLong() : 0L);
            case TYPE_ERROR -> new ErrorMessage(getStr(o, "code"), getStr(o, "message"));
            default -> throw new IllegalArgumentException("未知消息 type: " + type);
        };
    }

    private static String typeOf(Message msg) {
        if (msg instanceof OpenEditorMessage) {
            return TYPE_OPEN_EDITOR;
        } else if (msg instanceof EditOpMessage) {
            return TYPE_EDIT_OP;
        } else if (msg instanceof SyncStateMessage) {
            return TYPE_SYNC_STATE;
        } else if (msg instanceof SaveApplyMessage) {
            return TYPE_SAVE_APPLY;
        } else if (msg instanceof KeepAliveMessage) {
            return TYPE_KEEP_ALIVE;
        } else if (msg instanceof ErrorMessage) {
            return TYPE_ERROR;
        }
        throw new IllegalArgumentException("未知 Message 类型: " + msg.getClass());
    }

    private static String getStr(JsonObject o, String key) {
        if (o.has(key) && !o.get(key).isJsonNull()) {
            return o.get(key).getAsString();
        }
        return null;
    }

    /** JsonElement -> 普通 Java 对象（String/Number/Boolean/JsonObject/JsonArray）。 */
    private static Object getObject(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) {
            return null;
        }
        JsonElement e = o.get(key);
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
