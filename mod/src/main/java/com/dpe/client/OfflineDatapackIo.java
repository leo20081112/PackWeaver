package com.dpe.client;

import com.dpe.common.block.BlockSchemaRegistry;
import com.dpe.common.block.EditorState;
import com.dpe.common.compile.BlockCompiler;
import com.dpe.common.compile.CompileResult;
import com.dpe.common.compile.ValidationError;
import com.dpe.common.editor.DatapackExporter;
import com.dpe.common.editor.DatapackReader;
import com.dpe.common.model.Datapack;
import com.dpe.common.model.McFunction;
import com.dpe.common.model.ResourceLocation;
import com.dpe.common.model.Tag;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 离线本地数据包读写：从世界 datapacks 目录加载，或编译状态导出为 zip。
 */
public final class OfflineDatapackIo {

    /** 已知 tag 类型集合（用于从 ResourceLocation 路径首段推断类型）。 */
    private static final Set<String> KNOWN_TAG_TYPES = Set.of(
            "blocks", "items", "entities", "functions", "fluids",
            "block_entities", "worldgen", "damage_type", "enchantments",
            "point_of_interest", "raid", "cat_variants", "painting_variant",
            "banner_pattern", "instrument");

    private OfflineDatapackIo() {
    }

    /**
     * 从当前世界 datapacks 目录或游戏目录 datapacks 加载一个数据包。
     * @return 加载成功的数据包；找不到返回 {@link Optional#empty()}。
     */
    public static Optional<Datapack> loadLocal(MinecraftClient client) {
        if (client == null) {
            return Optional.empty();
        }
        // 优先：当前世界的 datapacks 目录（仅单机集成服务器可用）
        if (client.getServer() != null) {
            Path worldDatapacks = client.getServer().getSavePath(WorldSavePath.DATAPACKS);
            Optional<Datapack> found = readFirstDatapack(worldDatapacks);
            if (found.isPresent()) {
                return found;
            }
        }
        // 回退：游戏运行目录下的 datapacks
        Path gameDatapacks = client.runDirectory.toPath().resolve("datapacks");
        return readFirstDatapack(gameDatapacks);
    }

    /** 在 datapacks 目录下寻找第一个可读的数据包子目录。 */
    private static Optional<Datapack> readFirstDatapack(Path datapacksDir) {
        if (!Files.isDirectory(datapacksDir)) {
            return Optional.empty();
        }
        try (var stream = Files.list(datapacksDir)) {
            for (Path candidate : stream.filter(Files::isDirectory).toList()) {
                if (Files.isRegularFile(candidate.resolve("pack.mcmeta"))) {
                    try {
                        return Optional.of(DatapackReader.readFromDir(candidate));
                    } catch (IOException ignored) {
                        // 跳过无法读取的目录
                    }
                }
            }
        } catch (IOException ignored) {
            // 忽略
        }
        return Optional.empty();
    }

    /**
     * 编译编辑器状态并导出为 zip 到游戏目录 {@code dpe-<ns>.zip}。
     * 作为非单机回退（无世界 datapacks 目录时）使用。
     * @return 导出的 zip 文件路径。
     * @throws IllegalStateException 编译失败时抛出，含校验错误信息。
     * @throws IOException           写入文件失败。
     */
    public static Path export(EditorState state, BlockSchemaRegistry reg) throws IOException {
        CompileResult result = new BlockCompiler().compile(state, reg);
        if (!result.success()) {
            throw new IllegalStateException("编译失败: " + formatErrors(result.errors()));
        }
        Datapack dp = buildDatapack(state.getActiveDatapackNamespace(), result);
        String ns = state.getActiveDatapackNamespace();
        Path target = MinecraftClient.getInstance().runDirectory.toPath()
                .resolve("dpe-" + ns + ".zip");
        try (OutputStream out = Files.newOutputStream(target)) {
            DatapackExporter.exportToZip(dp, out);
        }
        return target;
    }

    /**
     * 编译编辑器状态并导出为解压目录到世界 datapacks 目录 {@code dpe-<ns>}（Task 4）。
     * 用于单机持续编辑：直接落盘到 datapacks 目录，便于数据包重载识别与文件树扫描。
     * @return 导出的数据包目录路径。
     * @throws IllegalStateException 编译失败时抛出。
     * @throws IOException           写入文件失败或非单机世界（worldDatapacksDir 为 null）。
     */
    public static Path exportToDatapacksDir(EditorState state, BlockSchemaRegistry reg,
                                            MinecraftClient mc) throws IOException {
        CompileResult result = new BlockCompiler().compile(state, reg);
        if (!result.success()) {
            throw new IllegalStateException("编译失败: " + formatErrors(result.errors()));
        }
        Datapack dp = buildDatapack(state.getActiveDatapackNamespace(), result);
        String ns = state.getActiveDatapackNamespace();
        Path worldDatapacks = DatapackEditorClient.worldDatapacksDir(mc);
        if (worldDatapacks == null) {
            throw new IOException("非单机世界，无法定位 datapacks 目录");
        }
        Files.createDirectories(worldDatapacks);
        Path target = worldDatapacks.resolve("dpe-" + ns);
        DatapackExporter.exportToDir(dp, target);
        return target;
    }

    /** 把校验错误格式化为多行字符串。 */
    public static String formatErrors(List<ValidationError> errors) {
        if (errors == null || errors.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ValidationError e : errors) {
            sb.append('[').append(e.blockId() == null ? "?" : e.blockId()).append(']');
            if (e.field() != null) {
                sb.append(' ').append(e.field());
            }
            sb.append(": ").append(e.message()).append('\n');
        }
        return sb.toString().trim();
    }

    /** 由编译结果构造 {@link Datapack}。 */
    private static Datapack buildDatapack(String namespace, CompileResult result) {
        List<McFunction> functions = new ArrayList<>();
        for (var entry : result.mcfunctions().entrySet()) {
            ResourceLocation id = entry.getKey();
            List<String> lines = new ArrayList<>();
            for (String line : entry.getValue().split("\n")) {
                if (!line.isEmpty()) {
                    lines.add(line);
                }
            }
            functions.add(new McFunction(id, lines));
        }
        List<Tag> tags = new ArrayList<>();
        for (var entry : result.jsonFiles().entrySet()) {
            Tag tag = buildTag(entry.getKey(), entry.getValue());
            if (tag != null) {
                tags.add(tag);
            }
        }
        return new Datapack(namespace, functions, tags, List.of(), List.of());
    }

    /** 解析 tag JSON 并从路径首段推断 tag 类型。 */
    private static Tag buildTag(ResourceLocation id, String json) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            boolean replace = obj.has("replace") && obj.get("replace").getAsBoolean();
            List<ResourceLocation> values = new ArrayList<>();
            if (obj.has("values") && obj.get("values").isJsonArray()) {
                for (var e : obj.getAsJsonArray("values")) {
                    ResourceLocation v = ResourceLocation.tryParse(e.getAsString());
                    if (v != null) {
                        values.add(v);
                    }
                }
            }
            String path = id.path();
            String type = "functions";
            String newPath = path;
            int slash = path.indexOf('/');
            if (slash > 0) {
                String first = path.substring(0, slash);
                if (KNOWN_TAG_TYPES.contains(first)) {
                    type = first;
                    newPath = path.substring(slash + 1);
                }
            }
            if (newPath.isEmpty()) {
                newPath = "tag";
            }
            return new Tag(new ResourceLocation(id.namespace(), newPath), type, values, replace);
        } catch (Exception ignored) {
            return null;
        }
    }
}
