package dev.packweaver.sync;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;

/**
 * /pws 命令：reload（数据包热重载）/ stats（性能）/ bridge（桥接状态）。
 */
public final class PwsCommand implements CommandExecutor, TabCompleter {
    private final PackWeaverSyncPlugin plugin;

    public PwsCommand(PackWeaverSyncPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§a[PWS]§7 用法: /pws <reload|stats|bridge>");
            return true;
        }
        switch (args[0]) {
            case "reload" -> {
                sender.sendMessage("§a[PWS]§7 正在重载数据包…");
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "minecraft:reload");
                sender.sendMessage("§a[PWS]§7 完成（数据包热重载，未重启服务器）");
            }
            case "stats" -> {
                sender.sendMessage(String.format("§a[PWS]§7 MSPT 平均 §f%.1f§7ms | 峰值 §f%.1f§7ms | TPS §f%.1f§7 | %s",
                        plugin.perf().averageMspt(), plugin.perf().maxMspt(),
                        plugin.perf().tps(), plugin.perf().status()));
            }
            case "bridge" -> {
                sender.sendMessage("§a[PWS]§7 HTTP http://127.0.0.1:" + plugin.httpPort()
                        + "/pw/ §7| TCP 127.0.0.1:" + plugin.tcpPort() + "§7（仅本机回环）");
            }
            default -> sender.sendMessage("§c[PWS]§7 未知子命令: " + args[0]);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 1) {
            return List.of("reload", "stats", "bridge").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        return List.of();
    }
}
