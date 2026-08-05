package com.dpe.server;

import com.dpe.common.block.BlockField;
import com.dpe.common.block.BlockSchema;
import com.dpe.common.block.BlockSchemaRegistry;
import com.dpe.common.block.EditorBlock;
import com.dpe.common.block.EditorState;
import com.dpe.common.compile.BlockCompiler;
import com.dpe.common.compile.CompileResult;
import com.dpe.common.editor.DatapackExporter;
import com.dpe.common.model.Datapack;
import com.dpe.common.model.McFunction;
import com.dpe.common.model.ResourceLocation;
import com.dpe.common.model.Tag;
import com.dpe.common.protocol.EditOpMessage;
import com.dpe.common.protocol.SyncStateMessage;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理命名空间 -> {@link EditorSession}、玩家 -> 命名空间映射，
 * 处理编辑操作、广播同步、编译并保存数据包。
 */
public final class EditorSessionManager {

    /** 编译保存结果。 */
    public record CompileSaveResult(boolean success, String message) {
    }

    private final Map<String, EditorSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerNamespace = new ConcurrentHashMap<>();
    private final BlockSchemaRegistry registry = BlockSchemaRegistry.DEFAULT;
    private final BlockCompiler compiler = new BlockCompiler();

    public EditorSessionManager() {
    }

    public BlockSchemaRegistry registry() {
        return registry;
    }

    public BlockCompiler compiler() {
        return compiler;
    }

    /** 获取或创建命名空间的会话。 */
    public EditorSession getOrCreate(String namespace) {
        return sessions.computeIfAbsent(safeNamespace(namespace), EditorSession::new);
    }

    /** 玩家加入命名空间会话。 */
    public EditorSession join(Player player, String namespace) {
        EditorSession session = getOrCreate(namespace);
        session.editors().add(player.getUniqueId());
        playerNamespace.put(player.getUniqueId(), session.namespace());
        return session;
    }

    /** 玩家离开当前会话。 */
    public void leave(Player player) {
        UUID id = player.getUniqueId();
        String ns = playerNamespace.remove(id);
        if (ns != null) {
            EditorSession session = sessions.get(ns);
            if (session != null) {
                session.editors().remove(id);
            }
        }
    }

    /** 获取玩家当前所在会话，可能为 null。 */
    public EditorSession sessionOf(Player player) {
        String ns = playerNamespace.get(player.getUniqueId());
        return ns == null ? null : sessions.get(ns);
    }

    /**
     * 应用一条编辑操作到发送者所在会话；返回会话用于广播，无会话或未知 op 返回 null。
     */
    public EditorSession applyEdit(EditOpMessage op) {
        if (op == null) {
            return null;
        }
        EditorSession session = null;
        if (op.playerId() != null) {
            try {
                UUID id = UUID.fromString(op.playerId());
                String ns = playerNamespace.get(id);
                if (ns != null) {
                    session = sessions.get(ns);
                }
            } catch (IllegalArgumentException ignored) {
                // 非法 playerId，忽略
            }
        }
        if (session == null) {
            return null;
        }
        applyOpToState(session.state(), op);
        session.bumpRevision();
        return session;
    }

    /** 根据操作类型修改 state。 */
    private void applyOpToState(EditorState state, EditOpMessage op) {
        switch (op.op()) {
            case "add" -> {
                String schemaId = op.value() == null ? null : op.value().toString();
                if (schemaId == null || schemaId.isBlank()) {
                    return;
                }
                addBlock(state, op.blockId(), schemaId);
            }
            case "remove" -> {
                if (op.blockId() != null) {
                    state.removeBlock(op.blockId());
                }
            }
            case "move" -> {
                double[] xy = parseXY(op.value());
                if (xy != null && op.blockId() != null) {
                    state.moveBlock(op.blockId(), xy[0], xy[1]);
                }
            }
            case "connect" -> {
                String childId = op.value() == null ? null : op.value().toString();
                if (childId != null && op.blockId() != null) {
                    state.connect(op.blockId(), childId);
                }
            }
            case "disconnect" -> {
                String childId = op.value() == null ? null : op.value().toString();
                if (childId != null && op.blockId() != null) {
                    state.disconnect(op.blockId(), childId);
                }
            }
            case "field", "text" -> {
                EditorBlock b = op.blockId() == null ? null : state.getById(op.blockId());
                if (b != null && op.field() != null) {
                    b.fieldValues().put(op.field(), op.value());
                }
            }
            default -> {
                // 未知 op 忽略
            }
        }
    }

    /** 新增一个块（带默认字段值）；blockId 为空则自动生成。 */
    public void addBlock(EditorState state, String blockId, String schemaId) {
        BlockSchema schema = registry.get(schemaId);
        if (schema == null) {
            return;
        }
        String id = (blockId == null || blockId.isBlank())
                ? ("b" + System.nanoTime())
                : blockId;
        EditorBlock block = new EditorBlock(id, schemaId, 40.0, 40.0);
        for (BlockField f : schema.fields()) {
            if (f.defaultValue() != null) {
                block.fieldValues().put(f.name(), f.defaultValue());
            }
        }
        state.addBlock(block);
    }

    /** 从 value 解析 [x, y]；支持 List [x,y] 或 Map {x,y}。 */
    private static double[] parseXY(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List<?> list && list.size() >= 2) {
            try {
                return new double[]{toDouble(list.get(0)), toDouble(list.get(1))};
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        if (value instanceof Map<?, ?> map) {
            if (map.containsKey("x") && map.containsKey("y")) {
                try {
                    return new double[]{toDouble(map.get("x")), toDouble(map.get("y"))};
                } catch (RuntimeException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private static double toDouble(Object o) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        return Double.parseDouble(o.toString());
    }

    /** 向某命名空间的所有编辑者广播 SyncStateMessage。 */
    public void broadcastSync(JavaPlugin plugin, String namespace) {
        EditorSession session = sessions.get(namespace);
        if (session == null) {
            return;
        }
        SyncStateMessage msg = new SyncStateMessage(session.state().toJson(), session.revision());
        byte[] bytes = DpeWire.encode(msg);
        for (UUID id : session.editors()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) {
                try {
                    p.sendPluginMessage(plugin, "dpe:msg", bytes);
                } catch (Exception ignored) {
                    // 客户端未注册通道等情况忽略
                }
            }
        }
    }

    /**
     * 编译并保存数据包到世界 datapacks 目录，然后 reloadData。
     * @return 成功时 success=true 且 message 为提示；失败时 success=false 且 message 为错误列表。
     */
    public CompileSaveResult compileAndSave(JavaPlugin plugin, String namespace) {
        String ns = safeNamespace(namespace);
        EditorSession session = getOrCreate(ns);
        CompileResult result = compiler.compile(session.state(), registry);
        if (!result.success()) {
            return new CompileSaveResult(false, DatapackCommandUtil.formatErrors(result.errors()));
        }
        Datapack dp = buildDatapack(ns, result);
        List<World> worlds = Bukkit.getWorlds();
        if (worlds.isEmpty()) {
            return new CompileSaveResult(false, "无可用世界目录");
        }
        Path datapacksDir = worlds.get(0).getWorldFolder().toPath().resolve("datapacks");
        Path target = datapacksDir.resolve(ns);
        try {
            DatapackExporter.exportToDir(dp, target);
        } catch (Exception e) {
            plugin.getLogger().warning("导出数据包失败: " + e.getMessage());
            return new CompileSaveResult(false, "导出失败: " + e.getMessage());
        }
        reloadDataSafe(plugin);
        return new CompileSaveResult(true,
                "数据包 " + ns + " 已保存并重载 (functions=" + dp.functions().size()
                        + ", tags=" + dp.tags().size() + ")");
    }

    /** 组装 Datapack 对象（mcfunctions + 解析 tag JSON）。 */
    private Datapack buildDatapack(String namespace, CompileResult result) {
        List<McFunction> functions = new ArrayList<>();
        for (Map.Entry<ResourceLocation, String> e : result.mcfunctions().entrySet()) {
            List<String> cmds = new ArrayList<>();
            for (String line : e.getValue().split("\n")) {
                if (!line.isBlank()) {
                    cmds.add(line);
                }
            }
            functions.add(new McFunction(e.getKey(), cmds));
        }
        List<Tag> tags = new ArrayList<>();
        for (Map.Entry<ResourceLocation, String> e : result.jsonFiles().entrySet()) {
            Tag tag = parseTag(e.getKey(), e.getValue());
            if (tag != null) {
                tags.add(tag);
            }
        }
        return new Datapack(namespace, functions, tags, List.of(), List.of());
    }

    /** 将编译器产出的 tag JSON 字符串解析为 Tag 对象，并推断 type（从路径前缀）。 */
    private static Tag parseTag(ResourceLocation rawId, String json) {
        try {
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            boolean replace = obj.has("replace") && obj.get("replace").getAsBoolean();
            List<ResourceLocation> values = new ArrayList<>();
            if (obj.has("values") && obj.get("values").isJsonArray()) {
                for (var v : obj.getAsJsonArray("values")) {
                    ResourceLocation parsed = ResourceLocation.tryParse(v.getAsString());
                    if (parsed != null) {
                        values.add(parsed);
                    }
                }
            }
            String type = "functions";
            String path = rawId.path();
            int slash = path.indexOf('/');
            if (slash > 0) {
                String first = path.substring(0, slash);
                switch (first) {
                    case "blocks", "items", "entities", "functions" -> {
                        type = first;
                        path = path.substring(slash + 1);
                    }
                    default -> {
                    }
                }
            }
            if (path.isBlank()) {
                return null;
            }
            ResourceLocation tagId = new ResourceLocation(rawId.namespace(), path);
            return new Tag(tagId, type, values, replace);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /** 安全热重载：优先 reloadData，失败回退 dispatchCommand reload。 */
    private void reloadDataSafe(JavaPlugin plugin) {
        try {
            Bukkit.getServer().reloadData();
        } catch (Throwable t) {
            plugin.getLogger().warning("reloadData 失败，回退 reload 命令: " + t.getMessage());
            try {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "reload");
            } catch (Exception ignored) {
                // reload 也失败则忽略
            }
        }
    }

    private static String safeNamespace(String ns) {
        if (ns == null || ns.isBlank()) {
            return "dpe";
        }
        return ns;
    }
}
