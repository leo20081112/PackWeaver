package dev.packweaver.bridge.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 渐进式解锁（规划书第 11.1 章）：
 * Lv.1 菜鸟（起步）→ Lv.2 学徒（3 个项目）→ Lv.3 工匠（10 个）
 * → Lv.4 专家（25 个）→ Lv.5 大师（50 个，全积木 + 高级分类）。
 * 可用 /pw level unlock 关闭限制。
 */
public final class PWLevel {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("packweaver").resolve("level.json");

    private static JsonObject data;

    private static synchronized JsonObject data() {
        if (data == null) {
            data = new JsonObject();
            data.addProperty("projectsCreated", 0);
            data.addProperty("unlocked", false);
            try {
                if (Files.exists(FILE)) {
                    JsonObject loaded = GSON.fromJson(Files.readString(FILE), JsonObject.class);
                    if (loaded != null) {
                        data = loaded;
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return data;
    }

    private static void persist() {
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, GSON.toJson(data()));
        } catch (IOException ignored) {
        }
    }

    public static void projectCreated() {
        data().addProperty("projectsCreated", projectsCreated() + 1);
        persist();
    }

    public static int projectsCreated() {
        return data().has("projectsCreated") ? data().get("projectsCreated").getAsInt() : 0;
    }

    /** 当前等级 1-5。 */
    public static int level() {
        int n = projectsCreated();
        if (n >= 50) {
            return 5;
        }
        if (n >= 25) {
            return 4;
        }
        if (n >= 10) {
            return 3;
        }
        return n >= 3 ? 2 : 1;
    }

    public static String title() {
        return switch (level()) {
            case 5 -> "Lv.5 大师";
            case 4 -> "Lv.4 专家";
            case 3 -> "Lv.3 工匠";
            case 2 -> "Lv.2 学徒";
            default -> "Lv.1 菜鸟";
        };
    }

    public static boolean unlocked() {
        return data().has("unlocked") && data().get("unlocked").getAsBoolean();
    }

    public static void toggleUnlock() {
        data().addProperty("unlocked", !unlocked());
        persist();
    }

    /** 「高级」「自定义」分类需要 Lv.2（或已解锁全部）。 */
    public static boolean canUseAdvanced() {
        return unlocked() || level() >= 2;
    }

    private PWLevel() {
    }
}
