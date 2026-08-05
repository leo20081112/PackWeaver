package com.dpe.common.text;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * 文本样式，可变。color/font 为 String，其余为 Boolean（null 表示未设置，使用默认）。
 * toJson 省略 null 与 false 默认值。
 */
public final class TextStyle {

    private String color;
    private Boolean bold;
    private Boolean italic;
    private Boolean underlined;
    private Boolean strikethrough;
    private Boolean obfuscated;
    private String font;

    public TextStyle() {
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public Boolean getBold() {
        return bold;
    }

    public void setBold(Boolean bold) {
        this.bold = bold;
    }

    public Boolean getItalic() {
        return italic;
    }

    public void setItalic(Boolean italic) {
        this.italic = italic;
    }

    public Boolean getUnderlined() {
        return underlined;
    }

    public void setUnderlined(Boolean underlined) {
        this.underlined = underlined;
    }

    public Boolean getStrikethrough() {
        return strikethrough;
    }

    public void setStrikethrough(Boolean strikethrough) {
        this.strikethrough = strikethrough;
    }

    public Boolean getObfuscated() {
        return obfuscated;
    }

    public void setObfuscated(Boolean obfuscated) {
        this.obfuscated = obfuscated;
    }

    public String getFont() {
        return font;
    }

    public void setFont(String font) {
        this.font = font;
    }

    /** 序列化到 JsonObject（仅写入非 null、非 false 的字段）。 */
    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        if (color != null) {
            o.addProperty("color", color);
        }
        if (Boolean.TRUE.equals(bold)) {
            o.addProperty("bold", true);
        }
        if (Boolean.TRUE.equals(italic)) {
            o.addProperty("italic", true);
        }
        if (Boolean.TRUE.equals(underlined)) {
            o.addProperty("underlined", true);
        }
        if (Boolean.TRUE.equals(strikethrough)) {
            o.addProperty("strikethrough", true);
        }
        if (Boolean.TRUE.equals(obfuscated)) {
            o.addProperty("obfuscated", true);
        }
        if (font != null) {
            o.addProperty("font", font);
        }
        return o;
    }

    /** 从 JsonObject 读取样式。 */
    public static TextStyle fromJson(JsonObject o) {
        TextStyle s = new TextStyle();
        if (o == null) {
            return s;
        }
        if (o.has("color") && !o.get("color").isJsonNull()) {
            s.color = o.get("color").getAsString();
        }
        if (o.has("bold") && !o.get("bold").isJsonNull()) {
            s.bold = o.get("bold").getAsBoolean();
        }
        if (o.has("italic") && !o.get("italic").isJsonNull()) {
            s.italic = o.get("italic").getAsBoolean();
        }
        if (o.has("underlined") && !o.get("underlined").isJsonNull()) {
            s.underlined = o.get("underlined").getAsBoolean();
        }
        if (o.has("strikethrough") && !o.get("strikethrough").isJsonNull()) {
            s.strikethrough = o.get("strikethrough").getAsBoolean();
        }
        if (o.has("obfuscated") && !o.get("obfuscated").isJsonNull()) {
            s.obfuscated = o.get("obfuscated").getAsBoolean();
        }
        if (o.has("font") && !o.get("font").isJsonNull()) {
            s.font = o.get("font").getAsString();
        }
        return s;
    }

    /** 拷贝。 */
    public TextStyle copy() {
        TextStyle s = new TextStyle();
        s.color = color;
        s.bold = bold;
        s.italic = italic;
        s.underlined = underlined;
        s.strikethrough = strikethrough;
        s.obfuscated = obfuscated;
        s.font = font;
        return s;
    }

    /** 便捷解析 JSON 字符串。 */
    public static TextStyle fromJsonString(String json) {
        return fromJson(JsonParser.parseString(json).getAsJsonObject());
    }
}
