package com.dpe.common.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * 编辑器按键绑定，可序列化（Gson）。
 * common 是纯 Java 库，不依赖 LWJGL，键码用 int 字面量并注释含义（如 75=K）。
 */
public class KeyBindings {

    public int openEditor = 75;      // GLFW_KEY_K = 75
    public int switchMode = 77;      // GLFW_KEY_M = 77
    public int reload = 82;          // GLFW_KEY_R = 82
    public int save = 83;            // GLFW_KEY_S = 83
    public int help = 290;           // GLFW_KEY_F1 = 290
    public int togglePalette = 80;   // GLFW_KEY_P = 80
    /** 保存是否需要配合 Ctrl 键。 */
    public boolean ctrlSave = true;

    public KeyBindings() {
    }

    public KeyBindings(int openEditor, int switchMode, int reload, int save,
                       int help, int togglePalette, boolean ctrlSave) {
        this.openEditor = openEditor;
        this.switchMode = switchMode;
        this.reload = reload;
        this.save = save;
        this.help = help;
        this.togglePalette = togglePalette;
        this.ctrlSave = ctrlSave;
    }

    /** 默认按键绑定。 */
    public static KeyBindings defaults() {
        return new KeyBindings();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 序列化为 JSON 字符串。 */
    public String toJson() {
        return GSON.toJson(this);
    }

    /** 从 JSON 字符串重建；空串返回 defaults。 */
    public static KeyBindings fromJson(String json) {
        if (json == null || json.isBlank()) {
            return defaults();
        }
        KeyBindings kb = GSON.fromJson(json, KeyBindings.class);
        return kb == null ? defaults() : kb;
    }
}
