package com.dpe.common.text;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Minecraft 文本组件，可变树节点。
 * 序列化为 vanilla 格式：style 平铺到顶层，children 放 extra。
 */
public final class TextComponent {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private String text;
    private String translate;
    private List<TextComponent> with;
    private String selector;
    private JsonObject score;
    private String keybind;
    private List<TextComponent> extra;
    private String insertion;
    private ClickEvent clickEvent;
    private HoverEvent hoverEvent;
    private String font;
    private TextStyle style = new TextStyle();

    public TextComponent() {
    }

    public TextComponent(String text) {
        this.text = text;
    }

    // ---------- getters / setters ----------
    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getTranslate() {
        return translate;
    }

    public void setTranslate(String translate) {
        this.translate = translate;
    }

    public List<TextComponent> getWith() {
        if (with == null) {
            with = new ArrayList<>();
        }
        return with;
    }

    public String getSelector() {
        return selector;
    }

    public void setSelector(String selector) {
        this.selector = selector;
    }

    public JsonObject getScore() {
        return score;
    }

    public void setScore(JsonObject score) {
        this.score = score;
    }

    public String getKeybind() {
        return keybind;
    }

    public void setKeybind(String keybind) {
        this.keybind = keybind;
    }

    public List<TextComponent> getExtra() {
        if (extra == null) {
            extra = new ArrayList<>();
        }
        return extra;
    }

    public String getInsertion() {
        return insertion;
    }

    public void setInsertion(String insertion) {
        this.insertion = insertion;
    }

    public ClickEvent getClickEvent() {
        return clickEvent;
    }

    public void setClickEvent(ClickEvent clickEvent) {
        this.clickEvent = clickEvent;
    }

    public HoverEvent getHoverEvent() {
        return hoverEvent;
    }

    public void setHoverEvent(HoverEvent hoverEvent) {
        this.hoverEvent = hoverEvent;
    }

    public String getFont() {
        return font;
    }

    public void setFont(String font) {
        this.font = font;
    }

    public TextStyle getStyle() {
        return style;
    }

    public void setStyle(TextStyle style) {
        this.style = style == null ? new TextStyle() : style;
    }

    /** 添加子节点。 */
    public TextComponent addChild(TextComponent child) {
        getExtra().add(child);
        return this;
    }

    /** 序列化为 vanilla 格式 JsonObject（style 平铺到顶层，children 放 extra）。 */
    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        if (text != null) {
            o.addProperty("text", text);
        }
        if (translate != null) {
            o.addProperty("translate", translate);
        }
        if (with != null && !with.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (TextComponent c : with) {
                arr.add(c.toJson());
            }
            o.add("with", arr);
        }
        if (selector != null) {
            o.addProperty("selector", selector);
        }
        if (score != null) {
            o.add("score", score);
        }
        if (keybind != null) {
            o.addProperty("keybind", keybind);
        }
        if (insertion != null) {
            o.addProperty("insertion", insertion);
        }
        if (font != null) {
            o.addProperty("font", font);
        }
        // style 平铺
        JsonObject styleJson = style.toJson();
        for (var entry : styleJson.entrySet()) {
            o.add(entry.getKey(), entry.getValue());
        }
        if (clickEvent != null) {
            JsonObject ce = new JsonObject();
            ce.addProperty("action", clickEvent.action());
            ce.addProperty("value", clickEvent.value());
            o.add("clickEvent", ce);
        }
        if (hoverEvent != null) {
            JsonObject he = new JsonObject();
            he.addProperty("action", hoverEvent.action());
            he.add("contents", GSON.toJsonTree(hoverEvent.contents()));
            o.add("hoverEvent", he);
        }
        if (extra != null && !extra.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (TextComponent c : extra) {
                arr.add(c.toJson());
            }
            o.add("extra", arr);
        }
        return o;
    }

    /** 序列化为 JSON 字符串。 */
    public String toJsonString() {
        return GSON.toJson(toJson());
    }

    /** 从 JSON 字符串重建树。 */
    public static TextComponent fromJson(String json) {
        return fromJson(JsonParser.parseString(json).getAsJsonObject());
    }

    /** 从 JsonObject 重建。 */
    public static TextComponent fromJson(JsonObject o) {
        TextComponent c = new TextComponent();
        if (o == null) {
            return c;
        }
        if (o.has("text") && !o.get("text").isJsonNull()) {
            c.text = o.get("text").getAsString();
        }
        if (o.has("translate") && !o.get("translate").isJsonNull()) {
            c.translate = o.get("translate").getAsString();
        }
        if (o.has("with") && o.get("with").isJsonArray()) {
            c.with = new ArrayList<>();
            for (JsonElement e : o.getAsJsonArray("with")) {
                c.with.add(fromJson(e.getAsJsonObject()));
            }
        }
        if (o.has("selector") && !o.get("selector").isJsonNull()) {
            c.selector = o.get("selector").getAsString();
        }
        if (o.has("score") && o.get("score").isJsonObject()) {
            c.score = o.getAsJsonObject("score");
        }
        if (o.has("keybind") && !o.get("keybind").isJsonNull()) {
            c.keybind = o.get("keybind").getAsString();
        }
        if (o.has("insertion") && !o.get("insertion").isJsonNull()) {
            c.insertion = o.get("insertion").getAsString();
        }
        if (o.has("font") && !o.get("font").isJsonNull()) {
            c.font = o.get("font").getAsString();
        }
        // style 从平铺字段读
        c.style = TextStyle.fromJson(o);
        if (o.has("clickEvent") && o.get("clickEvent").isJsonObject()) {
            JsonObject ce = o.getAsJsonObject("clickEvent");
            c.clickEvent = new ClickEvent(ce.get("action").getAsString(), ce.get("value").getAsString());
        }
        if (o.has("hoverEvent") && o.get("hoverEvent").isJsonObject()) {
            JsonObject he = o.getAsJsonObject("hoverEvent");
            Object contents = he.has("contents") ? GSON.fromJson(he.get("contents"), Object.class) : null;
            c.hoverEvent = new HoverEvent(he.get("action").getAsString(), contents);
        }
        if (o.has("extra") && o.get("extra").isJsonArray()) {
            c.extra = new ArrayList<>();
            for (JsonElement e : o.getAsJsonArray("extra")) {
                c.extra.add(fromJson(e.getAsJsonObject()));
            }
        }
        return c;
    }

    /** 深拷贝。 */
    public TextComponent copy() {
        TextComponent c = new TextComponent(text);
        c.translate = translate;
        if (with != null) {
            c.with = new ArrayList<>();
            for (TextComponent t : with) {
                c.with.add(t.copy());
            }
        }
        c.selector = selector;
        c.score = score == null ? null : score.deepCopy();
        c.keybind = keybind;
        if (extra != null) {
            c.extra = new ArrayList<>();
            for (TextComponent t : extra) {
                c.extra.add(t.copy());
            }
        }
        c.insertion = insertion;
        c.clickEvent = clickEvent;
        c.hoverEvent = hoverEvent;
        c.font = font;
        c.style = style.copy();
        return c;
    }
}
