package com.dpe.server;

import com.dpe.common.block.BlockSchema;
import com.dpe.common.block.BlockSchemaRegistry;
import com.dpe.common.manual.BuiltinManual;
import com.dpe.common.manual.ManualEntry;
import com.dpe.common.manual.ManualSearcher;
import com.dpe.common.protocol.Message;
import com.dpe.common.protocol.OpenEditorMessage;
import com.dpe.common.protocol.SyncStateMessage;
import com.dpe.common.reload.ReloadResult;
import com.dpe.common.template.BuiltinTemplates;
import com.dpe.common.template.DatapackTemplate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
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
 * <p>子命令：无参 / help / chat / list / add &lt;schemaId&gt; / compile / reload /
 * wiki [关键词] [页] / template [list|&lt;id&gt;]；
 * 其它非子命令参数视为 namespace 并打开编辑器。</p>
 */
public final class DpeCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB_COMMANDS = List.of(
            "chat", "add", "list", "reload", "compile", "wiki", "template", "help");
    private static final String DEFAULT_NAMESPACE = "dpe";
    private static final int WIKI_PAGE_SIZE = 8;

    private final JavaPlugin plugin;
    private final EditorSessionManager manager;
    private final ManualSearcher manualSearcher = new ManualSearcher();

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
            case "wiki" -> handleWiki(player, args);
            case "template" -> handleTemplate(player, args);
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

    /** /dpe reload：编译当前 state -> 写盘 -> 经 ReloadQueue 串行化 reloadData。 */
    private void handleReload(Player player) {
        EditorSession session = manager.sessionOf(player);
        String ns = session == null ? DEFAULT_NAMESPACE : session.namespace();
        player.sendMessage(Component.text("正在编译并保存 " + ns + " ...", NamedTextColor.YELLOW));
        manager.reload(player, ns).thenAccept(rr -> sendReloadResult(player, rr));
    }

    /** 把 ReloadResult 发给玩家（成功/失败不同颜色）。 */
    private void sendReloadResult(Player player, ReloadResult rr) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (rr.success()) {
                player.sendMessage(Component.text(rr.message(), NamedTextColor.GREEN));
            } else {
                player.sendMessage(Component.text("编译失败：" + rr.message(), NamedTextColor.RED));
            }
        });
    }

    /** /dpe wiki [关键词] [页]：搜索手册；无关键词列出全部；命中条目可点击查看详情。 */
    private void handleWiki(Player player, String[] args) {
        if (args.length < 2) {
            // 列出全部条目第一页
            sendWikiPage(player, "", 1);
            return;
        }
        String second = args[1];
        // 第二参数可能是 id（查详情）或搜索关键词
        ManualEntry detail = BuiltinManual.byId(second);
        if (detail != null && args.length == 2) {
            sendWikiDetail(player, detail);
            return;
        }
        // 解析页码：args[1]=关键词，args[2]=页码
        String query = second;
        int page = 1;
        if (args.length >= 3) {
            try {
                page = Integer.parseInt(args[2]);
            } catch (NumberFormatException ignored) {
                page = 1;
            }
            if (page < 1) {
                page = 1;
            }
        }
        sendWikiPage(player, query, page);
    }

    /** 发送一页搜索结果（每页 WIKI_PAGE_SIZE 条，可点击查看详情 + 翻页）。 */
    private void sendWikiPage(Player player, String query, int page) {
        List<ManualEntry> all = manualSearcher.search(query, 100);
        int total = all.size();
        int totalPages = Math.max(1, (total + WIKI_PAGE_SIZE - 1) / WIKI_PAGE_SIZE);
        if (page > totalPages) {
            page = totalPages;
        }
        int from = (page - 1) * WIKI_PAGE_SIZE;
        int to = Math.min(from + WIKI_PAGE_SIZE, total);

        Component root = Component.empty()
                .append(Component.text("=== 手册搜索 ===", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.newline());
        if (query == null || query.isBlank()) {
            root = root.append(Component.text("（无关键词，列出全部）", NamedTextColor.GRAY))
                    .append(Component.newline());
        } else {
            root = root.append(Component.text("关键词: " + query + "  命中 " + total + " 条",
                            NamedTextColor.AQUA))
                    .append(Component.newline());
        }
        if (total == 0) {
            root = root.append(Component.text("（无匹配条目）", NamedTextColor.GRAY))
                    .append(Component.newline());
        } else {
            for (int i = from; i < to; i++) {
                ManualEntry e = all.get(i);
                Component line = Component.empty()
                        .append(Component.text("[" + (i + 1) + "] ", NamedTextColor.YELLOW))
                        .append(Component.text(e.title(), NamedTextColor.GREEN)
                                .clickEvent(ClickEvent.suggestCommand("/dpe wiki " + e.id()))
                                .hoverEvent(HoverEvent.showText(Component.text(
                                        e.category().name() + " | " + e.description()))))
                        .append(Component.text("  (" + e.id() + ")", NamedTextColor.DARK_GRAY));
                root = root.append(line).append(Component.newline());
            }
        }
        // 翻页
        Component pager = Component.empty();
        if (page > 1) {
            pager = pager.append(Component.text("[上一页]", NamedTextColor.AQUA)
                    .clickEvent(ClickEvent.runCommand(
                            "/dpe wiki " + (query == null ? "" : query) + " " + (page - 1))));
            pager = pager.append(Component.space());
        }
        pager = pager.append(Component.text("第 " + page + "/" + totalPages + " 页", NamedTextColor.GRAY));
        if (page < totalPages) {
            pager = pager.append(Component.space())
                    .append(Component.text("[下一页]", NamedTextColor.AQUA)
                            .clickEvent(ClickEvent.runCommand(
                                    "/dpe wiki " + (query == null ? "" : query) + " " + (page + 1))));
        }
        root = root.append(pager).append(Component.newline())
                .append(clickable("[返回菜单]", "/dpe chat", NamedTextColor.GRAY));
        player.sendMessage(root);
    }

    /** 发送单条手册详情：title / description / example。 */
    private void sendWikiDetail(Player player, ManualEntry e) {
        Component root = Component.empty()
                .append(Component.text("=== " + e.title() + " ===", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.newline())
                .append(Component.text("分类: " + e.category().name(), NamedTextColor.AQUA))
                .append(Component.newline())
                .append(Component.text("ID: " + e.id(), NamedTextColor.DARK_GRAY))
                .append(Component.newline())
                .append(Component.text("说明: " + e.description(), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("示例: ", NamedTextColor.YELLOW))
                .append(Component.text(e.example(), NamedTextColor.GREEN)
                        .clickEvent(ClickEvent.suggestCommand(e.example())))
                .append(Component.newline())
                .append(clickable("[返回菜单]", "/dpe chat", NamedTextColor.GRAY));
        player.sendMessage(root);
    }

    /** /dpe template [list|<id>]：列出模板或加载模板到当前会话。 */
    private void handleTemplate(Player player, String[] args) {
        if (args.length < 2 || args[1].equalsIgnoreCase("list")) {
            sendTemplateList(player);
            return;
        }
        String id = args[1];
        DatapackTemplate tpl = BuiltinTemplates.byId(id);
        if (tpl == null) {
            player.sendMessage(Component.text("未知模板 id: " + id, NamedTextColor.RED));
            sendTemplateList(player);
            return;
        }
        EditorSession session = currentOrJoin(player);
        session.replaceState(tpl.preset());
        session.bumpRevision();
        manager.broadcastSync(plugin, session.namespace());
        player.sendMessage(Component.text("已加载模板：" + tpl.title(), NamedTextColor.GREEN));
    }

    /** 发送模板列表：id + title + description，可点击加载。 */
    private void sendTemplateList(Player player) {
        Component root = Component.empty()
                .append(Component.text("=== 数据包模板 ===", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.newline());
        for (DatapackTemplate t : BuiltinTemplates.all()) {
            Component line = Component.empty()
                    .append(Component.text("- " + t.title(), NamedTextColor.GREEN)
                            .clickEvent(ClickEvent.runCommand("/dpe template " + t.id()))
                            .hoverEvent(HoverEvent.showText(Component.text(t.description()))))
                    .append(Component.text("  (" + t.id() + ")", NamedTextColor.DARK_GRAY))
                    .append(Component.newline())
                    .append(Component.text("    " + t.description(), NamedTextColor.GRAY))
                    .append(Component.newline());
            root = root.append(line);
        }
        root = root.append(clickable("[返回菜单]", "/dpe chat", NamedTextColor.GRAY));
        player.sendMessage(root);
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
                .append(Component.text("/dpe reload - 保存并热重载", NamedTextColor.AQUA))
                .append(Component.newline())
                .append(Component.text("/dpe wiki [关键词] [页] - 查询手册", NamedTextColor.AQUA))
                .append(Component.newline())
                .append(Component.text("/dpe template [list|<id>] - 列出/加载模板", NamedTextColor.AQUA)));
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

    /** 构造可点击文本（run_command）。 */
    private static Component clickable(String label, String command, NamedTextColor color) {
        return Component.text(label, color).clickEvent(ClickEvent.runCommand(command));
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
        if (args.length == 2) {
            String prefix = args[1].toLowerCase();
            switch (args[0].toLowerCase()) {
                case "add" -> {
                    for (BlockSchema s : BlockSchemaRegistry.DEFAULT.all()) {
                        if (s.id().startsWith(prefix)) {
                            out.add(s.id());
                        }
                    }
                }
                case "template" -> {
                    out.add("list");
                    for (DatapackTemplate t : BuiltinTemplates.all()) {
                        if (t.id().startsWith(prefix)) {
                            out.add(t.id());
                        }
                    }
                }
                case "wiki" -> {
                    // 补全手册 id
                    for (ManualEntry e : BuiltinManual.all()) {
                        if (e.id().startsWith(prefix)) {
                            out.add(e.id());
                        }
                    }
                }
                default -> {
                    // 无补全
                }
            }
            return out;
        }
        return out;
    }
}
