package com.dpe.server;

import com.dpe.common.block.BlockSchema;
import com.dpe.common.block.BlockSchemaRegistry;
import com.dpe.common.protocol.Message;
import com.dpe.common.protocol.OpenEditorMessage;
import com.dpe.common.protocol.SyncStateMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * /datapackeditor (/dpe) 命令处理器。
 *
 * <p>子命令：无参 / help / chat / list / add &lt;schemaId&gt; / reload / compile；
 * 其它非子命令参数视为 namespace 并打开编辑器。</p>
 */
public final class DpeCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB_COMMANDS = List.of(
            "chat", "add", "list", "reload", "compile", "help");
    private static final String DEFAULT_NAMESPACE = "dpe";

    private final JavaPlugin plugin;
    private final EditorSessionManager manager;

    public DpeCommand(JavaPlugin plugin, EditorSessionManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("该命令仅限玩家使用。");
            return true;
        }
        if (args.length == 0) {
            openEditor(player, DEFAULT_NAMESPACE);
            return true;
        }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "help" -> sendHelp(player);
            case "chat" -> player.sendMessage(ChatMenu.buildRootMenu(currentOrJoin(player)));
            case "list" -> player.sendMessage(ChatMenu.buildBlockList(currentOrJoin(player)));
            case "add" -> handleAdd(player, args);
            case "compile" -> handleCompile(player);
            case "reload" -> handleReload(player);
            default -> openEditor(player, args[0]);
        }
        return true;
    }

    /** 打开编辑器：加入会话，发送 OpenEditorMessage + SyncStateMessage，并回聊天降级提示。 */
    private void openEditor(Player player, String namespace) {
        EditorSession session = manager.join(player, namespace);
        sendToPlayer(player, new OpenEditorMessage(session.namespace()));
        sendToPlayer(player, new SyncStateMessage(session.state().toJson(), session.revision()));
        player.sendMessage(Component.empty()
                .append(Component.text("已为 mod 客户端打开编辑器 [" + session.namespace() + "]。",
                        NamedTextColor.AQUA))
                .append(Component.newline())
                .append(Component.text("未安装 mod？", NamedTextColor.GRAY))
                .append(Component.text(" [使用聊天菜单]", NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.runCommand("/dpe chat"))));
    }

    /** /dpe add <schemaId>：新增块、bump revision、广播同步、回聊提示。 */
    private void handleAdd(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("用法: /dpe add <schemaId>", NamedTextColor.RED));
            player.sendMessage(ChatMenu.buildAddMenu());
            return;
        }
        String schemaId = args[1];
        BlockSchema schema = manager.registry().get(schemaId);
        if (schema == null) {
            player.sendMessage(Component.text("未知 schemaId: " + schemaId, NamedTextColor.RED));
            return;
        }
        EditorSession session = currentOrJoin(player);
        String blockId = "b" + System.nanoTime();
        manager.addBlock(session.state(), blockId, schemaId);
        session.bumpRevision();
        player.sendMessage(Component.text(
                "已添加 " + schema.label() + " (id=" + blockId + ", schema=" + schemaId + ")",
                NamedTextColor.GREEN));
        manager.broadcastSync(plugin, session.namespace());
    }

    /** /dpe compile：编译预览（不写盘、不重载）。 */
    private void handleCompile(Player player) {
        EditorSession session = currentOrJoin(player);
        var result = manager.compiler().compile(session.state(), manager.registry());
        if (result.success()) {
            Component msg = Component.empty()
                    .append(Component.text("编译成功: ", NamedTextColor.GREEN))
                    .append(Component.text("functions=" + result.mcfunctions().size()
                            + " jsonFiles=" + result.jsonFiles().size(), NamedTextColor.AQUA));
            player.sendMessage(msg);
            for (var e : result.mcfunctions().entrySet()) {
                player.sendMessage(Component.text("  " + e.getKey(), NamedTextColor.YELLOW));
            }
        } else {
            player.sendMessage(Component.text(DatapackCommandUtil.formatErrors(result.errors()), NamedTextColor.RED));
        }
    }

    /** /dpe reload：编译当前 state -> 写入世界 datapacks -> reloadData。 */
    private void handleReload(Player player) {
        EditorSession session = manager.sessionOf(player);
        String ns = session == null ? DEFAULT_NAMESPACE : session.namespace();
        player.sendMessage(Component.text("正在编译并保存 " + ns + " ...", NamedTextColor.YELLOW));
        EditorSessionManager.CompileSaveResult result = manager.compileAndSave(plugin, ns);
        if (result.success()) {
            player.sendMessage(Component.text("成功: " + result.message(), NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("失败: " + result.message(), NamedTextColor.RED));
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.empty()
                .append(Component.text("=== DPE 命令帮助 ===", NamedTextColor.GOLD))
                .append(Component.newline())
                .append(Component.text("/dpe - 打开编辑器（默认 dpe 命名空间）", NamedTextColor.AQUA))
                .append(Component.newline())
                .append(Component.text("/dpe <namespace> - 打开指定命名空间编辑器", NamedTextColor.AQUA))
                .append(Component.newline())
                .append(Component.text("/dpe chat - 聊天菜单（降级 UI）", NamedTextColor.AQUA))
                .append(Component.newline())
                .append(Component.text("/dpe add <schemaId> - 添加积木块", NamedTextColor.AQUA))
                .append(Component.newline())
                .append(Component.text("/dpe list - 列出当前 blocks", NamedTextColor.AQUA))
                .append(Component.newline())
                .append(Component.text("/dpe compile - 编译预览", NamedTextColor.AQUA))
                .append(Component.newline())
                .append(Component.text("/dpe reload - 保存并热重载", NamedTextColor.AQUA)));
    }

    /** 获取玩家当前会话；无则加入默认命名空间会话。 */
    private EditorSession currentOrJoin(Player player) {
        EditorSession session = manager.sessionOf(player);
        if (session == null) {
            session = manager.join(player, DEFAULT_NAMESPACE);
        }
        return session;
    }

    /** 通过 dpe:msg 通道发送一条消息给玩家。 */
    private void sendToPlayer(Player player, Message msg) {
        try {
            player.sendPluginMessage(plugin, "dpe:msg", DpeWire.encode(msg));
        } catch (Exception e) {
            plugin.getLogger().warning("发送插件消息失败给 " + player.getName() + ": " + e.getMessage());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            for (String s : SUB_COMMANDS) {
                if (s.startsWith(prefix)) {
                    out.add(s);
                }
            }
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("add")) {
            String prefix = args[1].toLowerCase();
            for (BlockSchema s : BlockSchemaRegistry.DEFAULT.all()) {
                if (s.id().startsWith(prefix)) {
                    out.add(s.id());
                }
            }
            return out;
        }
        return out;
    }
}
