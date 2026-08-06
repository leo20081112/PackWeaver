package com.dpe.common.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 用户配置，可序列化（Gson）。
 */
public class UserConfig {

    /** 默认编辑模式："blocks"（积木）或 "ide"（代码）。 */
    public String defaultMode = "blocks";
    /** 是否显示新手引导。 */
    public boolean showOnboarding = true;
    /** 字体缩放倍率。 */
    public double fontSize = 1.0;
    /** 按键绑定。 */
    public KeyBindings keyBindings = KeyBindings.defaults();
    /** 小窗模式：窗口 X 坐标（-1 表示居中）。 */
    public int windowX = -1;
    /** 小窗模式：窗口 Y 坐标（-1 表示居中）。 */
    public int windowY = -1;
    /** 小窗模式：窗口宽度（-1 表示默认 80% 屏宽）。 */
    public int windowWidth = -1;
    /** 小窗模式：窗口高度（-1 表示默认 80% 屏高）。 */
    public int windowHeight = -1;
    /** 是否全屏（true 时忽略 windowX/Y/width/height）。 */
    public boolean fullscreen = false;
    /** 是否曾在独立窗口打开（记录偏好）。 */
    public boolean detachedWindowOpen = false;

    public UserConfig() {
    }

    /** 默认配置。 */
    public static UserConfig defaults() {
        return new UserConfig();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** 序列化为 JSON 字符串。 */
    public String toJson() {
        return GSON.toJson(this);
    }

    /** 从 JSON 字符串重建；空串返回 defaults。 */
    public static UserConfig fromJson(String json) {
        if (json == null || json.isBlank()) {
            return defaults();
        }
        UserConfig c = GSON.fromJson(json, UserConfig.class);
        if (c == null) {
            return defaults();
        }
        if (c.keyBindings == null) {
            c.keyBindings = KeyBindings.defaults();
        }
        return c;
    }

    /** 从文件加载；文件不存在或读取失败返回 defaults。 */
    public static UserConfig load(Path path) {
        if (path == null || !Files.exists(path)) {
            return defaults();
        }
        try {
            return fromJson(Files.readString(path));
        } catch (IOException e) {
            return defaults();
        }
    }

    /** 保存到文件。 */
    public void save(Path path) throws IOException {
        if (path == null) {
            throw new IllegalArgumentException("path 不能为空");
        }
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, toJson());
    }
}
