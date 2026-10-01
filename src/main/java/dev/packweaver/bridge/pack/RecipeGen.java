package dev.packweaver.bridge.pack;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配方 JSON 生成（规划书第 5.1 章配方设计器的数据层）。
 * 支持 1.20.1：有序合成 / 无序合成 / 熔炼。
 */
public final class RecipeGen {

    /** 归一化物品 ID（无命名空间自动补 minecraft:）。 */
    public static String id(String raw) {
        String s = raw == null ? "" : raw.trim();
        return s.contains(":") ? s : "minecraft:" + s;
    }

    /**
     * 有序合成。grid 为 3x3（行优先，空串=空格）。
     * 自动裁剪空行/空列、合并相同物品为同一 key 字母。
     */
    public static String shaped(String[][] grid, String result, int count) {
        Map<String, String> key = new LinkedHashMap<>();
        char next = 'A';
        StringBuilder[] rows = new StringBuilder[3];
        for (int r = 0; r < 3; r++) {
            rows[r] = new StringBuilder("   ");
        }
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                String item = grid[r][c] == null ? "" : grid[r][c].trim();
                if (item.isEmpty()) {
                    continue;
                }
                String letter = null;
                for (var e : key.entrySet()) {
                    if (e.getValue().equals(id(item))) {
                        letter = e.getKey();
                        break;
                    }
                }
                if (letter == null) {
                    letter = String.valueOf(next++);
                    key.put(letter, id(item));
                }
                rows[r].setCharAt(c, letter.charAt(0));
            }
        }
        // 裁剪空行与空列
        int rowStart = 0, rowEnd = 2, colStart = 0, colEnd = 2;
        while (rowStart <= rowEnd && rows[rowStart].toString().isBlank()) {
            rowStart++;
        }
        while (rowEnd >= rowStart && rows[rowEnd].toString().isBlank()) {
            rowEnd--;
        }
        while (colStart <= colEnd) {
            boolean colEmpty = true;
            for (int r = rowStart; r <= rowEnd; r++) {
                if (rows[r].charAt(colStart) != ' ') {
                    colEmpty = false;
                    break;
                }
            }
            if (!colEmpty) {
                break;
            }
            colStart++;
        }
        while (colEnd >= colStart) {
            boolean colEmpty = true;
            for (int r = rowStart; r <= rowEnd; r++) {
                if (rows[r].charAt(colEnd) != ' ') {
                    colEmpty = false;
                    break;
                }
            }
            if (!colEmpty) {
                break;
            }
            colEnd--;
        }
        if (rowStart > rowEnd || colStart > colEnd) {
            throw new IllegalArgumentException("配方网格为空（至少放置 1 个材料）");
        }

        JsonObject o = new JsonObject();
        o.addProperty("type", "minecraft:crafting_shaped");
        JsonArray pattern = new JsonArray();
        for (int r = rowStart; r <= rowEnd; r++) {
            pattern.add(rows[r].substring(colStart, colEnd + 1));
        }
        o.add("pattern", pattern);
        JsonObject keyObj = new JsonObject();
        for (var e : key.entrySet()) {
            JsonObject item = new JsonObject();
            item.addProperty("item", e.getValue());
            keyObj.add(e.getKey(), item);
        }
        o.add("key", keyObj);
        o.add("result", resultObj(result, count));
        return pretty(o);
    }

    /** 无序合成：取网格中全部非空材料。 */
    public static String shapeless(String[][] grid, String result, int count) {
        JsonArray ingredients = new JsonArray();
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                String item = grid[r][c] == null ? "" : grid[r][c].trim();
                if (!item.isEmpty()) {
                    JsonObject ing = new JsonObject();
                    ing.addProperty("item", id(item));
                    ingredients.add(ing);
                }
            }
        }
        if (ingredients.size() == 0) {
            throw new IllegalArgumentException("配方网格为空（至少放置 1 个材料）");
        }
        JsonObject o = new JsonObject();
        o.addProperty("type", "minecraft:crafting_shapeless");
        o.add("ingredients", ingredients);
        o.add("result", resultObj(result, count));
        return pretty(o);
    }

    /** 熔炼（1.20.1 的 result 为字符串 ID）。 */
    public static String smelting(String ingredient, String result) {
        JsonObject o = new JsonObject();
        o.addProperty("type", "minecraft:smelting");
        JsonObject ing = new JsonObject();
        ing.addProperty("item", id(ingredient));
        o.add("ingredient", ing);
        o.add("result", new JsonPrimitive(id(result)));
        o.addProperty("experience", 0.1);
        o.addProperty("cookingtime", 200);
        return pretty(o);
    }

    private static JsonObject resultObj(String result, int count) {
        if (result == null || result.isBlank()) {
            throw new IllegalArgumentException("缺少产物物品 ID");
        }
        JsonObject r = new JsonObject();
        r.addProperty("item", id(result));
        r.addProperty("count", Math.max(1, count));
        return r;
    }

    private static String pretty(JsonObject o) {
        return new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(o) + "\n";
    }

    private RecipeGen() {
    }
}
