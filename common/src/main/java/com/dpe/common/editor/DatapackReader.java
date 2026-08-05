package com.dpe.common.editor;

import com.dpe.common.model.Advancement;
import com.dpe.common.model.Datapack;
import com.dpe.common.model.LootTable;
import com.dpe.common.model.McFunction;
import com.dpe.common.model.ResourceLocation;
import com.dpe.common.model.Tag;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 数据包读取器，从目录读取 vanilla 数据包结构。
 */
public final class DatapackReader {

    private DatapackReader() {
    }

    /** 从目录读取数据包。 */
    public static Datapack readFromDir(Path dir) throws IOException {
        String namespace = readNamespace(dir);
        List<McFunction> functions = new ArrayList<>();
        List<Tag> tags = new ArrayList<>();
        List<Advancement> advancements = new ArrayList<>();
        List<LootTable> lootTables = new ArrayList<>();

        Path dataDir = dir.resolve("data");
        if (Files.isDirectory(dataDir)) {
            try (Stream<Path> nsDirs = Files.list(dataDir)) {
                List<Path> nsList = nsDirs.filter(Files::isDirectory).toList();
                for (Path nsDir : nsList) {
                    String ns = nsDir.getFileName().toString();
                    readFunctions(nsDir, ns, functions);
                    readTags(nsDir, ns, tags);
                    readAdvancements(nsDir, ns, advancements);
                    readLootTables(nsDir, ns, lootTables);
                }
            }
        }
        return new Datapack(namespace, functions, tags, advancements, lootTables);
    }

    /** 从 pack.mcmeta 读 description 推断 namespace，失败用 data 下首个子目录名。 */
    private static String readNamespace(Path dir) throws IOException {
        Path meta = dir.resolve("pack.mcmeta");
        if (Files.isRegularFile(meta)) {
            try {
                JsonObject root = JsonParser.parseString(Files.readString(meta, StandardCharsets.UTF_8)).getAsJsonObject();
                if (root.has("pack") && root.getAsJsonObject("pack").has("description")) {
                    String desc = root.getAsJsonObject("pack").get("description").getAsString();
                    if (desc != null && !desc.isBlank()) {
                        return desc;
                    }
                }
            } catch (Exception ignored) {
                // 解析失败则走默认
            }
        }
        Path dataDir = dir.resolve("data");
        if (Files.isDirectory(dataDir)) {
            try (Stream<Path> s = Files.list(dataDir)) {
                Path first = s.filter(Files::isDirectory).findFirst().orElse(null);
                if (first != null) {
                    return first.getFileName().toString();
                }
            }
        }
        return "datapack";
    }

    private static void readFunctions(Path nsDir, String ns, List<McFunction> out) throws IOException {
        Path funcDir = nsDir.resolve("functions");
        if (!Files.isDirectory(funcDir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(funcDir)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".mcfunction"))
                    .forEach(p -> {
                        try {
                            String rel = funcDir.relativize(p).toString().replace('\\', '/');
                            String path = rel.substring(0, rel.length() - ".mcfunction".length());
                            List<String> cmds = Files.readAllLines(p, StandardCharsets.UTF_8);
                            out.add(new McFunction(new ResourceLocation(ns, path), cmds));
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        }
    }

    private static void readTags(Path nsDir, String ns, List<Tag> out) throws IOException {
        Path tagsDir = nsDir.resolve("tags");
        if (!Files.isDirectory(tagsDir)) {
            return;
        }
        try (Stream<Path> typeDirs = Files.list(tagsDir)) {
            for (Path typeDir : typeDirs.filter(Files::isDirectory).toList()) {
                String type = typeDir.getFileName().toString();
                try (Stream<Path> walk = Files.walk(typeDir)) {
                    walk.filter(Files::isRegularFile)
                            .filter(p -> p.toString().endsWith(".json"))
                            .forEach(p -> {
                                try {
                                    String rel = typeDir.relativize(p).toString().replace('\\', '/');
                                    String path = rel.substring(0, rel.length() - ".json".length());
                                    JsonObject obj = JsonParser.parseString(
                                            Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
                                    boolean replace = obj.has("replace") && obj.get("replace").getAsBoolean();
                                    List<ResourceLocation> values = new ArrayList<>();
                                    if (obj.has("values") && obj.get("values").isJsonArray()) {
                                        for (var e : obj.getAsJsonArray("values")) {
                                            values.add(ResourceLocation.parse(e.getAsString()));
                                        }
                                    }
                                    out.add(new Tag(new ResourceLocation(ns, path), type, values, replace));
                                } catch (IOException e) {
                                    throw new UncheckedIOException(e);
                                }
                            });
                }
            }
        }
    }

    private static void readAdvancements(Path nsDir, String ns, List<Advancement> out) throws IOException {
        Path advDir = nsDir.resolve("advancements");
        if (!Files.isDirectory(advDir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(advDir)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json"))
                    .forEach(p -> {
                        try {
                            String rel = advDir.relativize(p).toString().replace('\\', '/');
                            String path = rel.substring(0, rel.length() - ".json".length());
                            JsonObject obj = JsonParser.parseString(
                                    Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
                            out.add(new Advancement(new ResourceLocation(ns, path), obj));
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        }
    }

    private static void readLootTables(Path nsDir, String ns, List<LootTable> out) throws IOException {
        Path ltDir = nsDir.resolve("loot_tables");
        if (!Files.isDirectory(ltDir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(ltDir)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".json"))
                    .forEach(p -> {
                        try {
                            String rel = ltDir.relativize(p).toString().replace('\\', '/');
                            String path = rel.substring(0, rel.length() - ".json".length());
                            JsonObject obj = JsonParser.parseString(
                                    Files.readString(p, StandardCharsets.UTF_8)).getAsJsonObject();
                            out.add(new LootTable(new ResourceLocation(ns, path), obj));
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        }
    }
}
