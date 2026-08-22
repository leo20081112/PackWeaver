package dev.packweaver.bridge.command;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import dev.packweaver.bridge.bridge.BridgeServer;
import dev.packweaver.bridge.perf.PerfTracker;

/**
 * /pw 命令系统：
 *   /pw reload                     —— 热重载数据包（第 18 章）
 *   /pw stats                      —— 性能报告（MSPT / TPS）
 *   /pw bridge status              —— 桥接服务器状态
 *   /pw copier give [玩家]         —— 发放坐标复制器
 */
public final class PWCommands {
    private PWCommands() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(build());
        });
    }

    private static LiteralArgumentBuilder<ServerCommandSource> build() {
        return CommandManager.literal("pw")
                .requires(src -> src.hasPermissionLevel(2))
                .then(CommandManager.literal("reload")
                        .executes(ctx -> {
                            ServerCommandSource src = ctx.getSource();
                            src.getServer().getCommandManager().executeWithPrefix(
                                    src.getServer().getCommandSource(), "reload");
                            src.sendFeedback(() -> Text.literal("§a[PW]§7 数据包已重载"), true);
                            return 1;
                        }))
                .then(CommandManager.literal("stats")
                        .executes(ctx -> {
                            ctx.getSource().sendFeedback(() -> Text.literal(String.format(
                                    "§a[PW]§7 MSPT 平均 §f%.1f§7ms | 峰值 §f%.1f§7ms | TPS §f%.1f§7 | 状态 §f%s",
                                    PerfTracker.averageMspt(), PerfTracker.maxMspt(),
                                    PerfTracker.tps(), PerfTracker.status())), false);
                            return 1;
                        }))
                .then(CommandManager.literal("bridge")
                        .then(CommandManager.literal("status")
                                .executes(ctx -> {
                                    BridgeServer bridge = BridgeServer.getInstance();
                                    boolean up = bridge.isRunning();
                                    ctx.getSource().sendFeedback(() -> Text.literal(String.format(
                                            "§a[PW]§7 桥接服务器: %s§7（127.0.0.1:%d，仅本机）",
                                            up ? "§a运行中" : "§c已停止", bridge.getPort())), false);
                                    return up ? 1 : 0;
                                })))
                .then(CommandManager.literal("copier")
                        .then(CommandManager.literal("give")
                                .executes(ctx -> {
                                    var player = ctx.getSource().getPlayerOrThrow();
                                    player.getInventory().insertStack(
                                            new net.minecraft.item.ItemStack(dev.packweaver.bridge.PackWeaverBridge.COORDINATE_COPIER));
                                    ctx.getSource().sendFeedback(() ->
                                            Text.literal("§a[PW]§7 已发放坐标复制器（对方块右键复制坐标，Shift+右键复制 NBT）"), false);
                                    return 1;
                                })));
    }
}
