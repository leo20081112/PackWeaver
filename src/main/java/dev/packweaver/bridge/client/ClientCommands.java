package dev.packweaver.bridge.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.packweaver.bridge.PackWeaverBridge;
import dev.packweaver.bridge.gui.BlockEditorScreen;
import dev.packweaver.bridge.gui.CodeEditorScreen;
import dev.packweaver.bridge.gui.DebugScreen;
import dev.packweaver.bridge.gui.DiagScreen;
import dev.packweaver.bridge.gui.ProjectScreen;
import dev.packweaver.bridge.gui.WikiScreen;
import dev.packweaver.bridge.pack.BlockDefs;
import dev.packweaver.bridge.pack.PackProject;
import dev.packweaver.bridge.pack.Templates;

import java.nio.file.Files;
import java.nio.file.Path;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

/**
 * 客户端 /pw 命令：打开游戏内各界面（规划书第 2.3 章菜单体系）。
 *
 * /pw project            项目管理（新建/模板/打开）
 * /pw edit [ns]          积木（技术）模式
 * /pw code <ns> [函数]   IDE 代码模式
 * /pw diag [ns]          诊断报告
 * /pw wiki               命令 Wiki + 拆解
 * /pw debug              调试监视
 * /pw blocks reload      重载自定义积木（config/packweaver/blocks/*.json）
 */
public final class ClientCommands {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                literal("pw")
                        .then(literal("project").executes(ctx -> open(ctx.getSource(), new ProjectScreen())))
                        .then(literal("edit")
                                .executes(ctx -> {
                                    String ns = soleProject(ctx.getSource());
                                    return ns == null ? 0 : openEditor(ctx.getSource(), ns);
                                })
                                .then(argument("ns", StringArgumentType.word())
                                        .executes(ctx -> openEditor(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "ns")))))
                        .then(literal("code")
                                .then(argument("ns", StringArgumentType.word())
                                        .executes(ctx -> openCode(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "ns"), "tick"))))
                        .then(literal("diag")
                                .executes(ctx -> {
                                    String ns = soleProject(ctx.getSource());
                                    return ns == null ? 0 : diag(ctx.getSource(), ns);
                                })
                                .then(argument("ns", StringArgumentType.word())
                                        .executes(ctx -> diag(ctx.getSource(),
                                                StringArgumentType.getString(ctx, "ns")))))
                        .then(literal("wiki").executes(ctx -> open(ctx.getSource(), new WikiScreen())))
                        .then(literal("debug").executes(ctx -> open(ctx.getSource(), new DebugScreen())))
                        .then(literal("blocks")
                                .then(literal("reload").executes(ctx -> {
                                    int n = loadCustomBlocks();
                                    ctx.getSource().sendFeedback(Text.literal("已加载 " + n + " 个自定义积木"));
                                    return n;
                                })))
                        .then(literal("templates").executes(ctx -> {
                            ctx.getSource().sendFeedback(Text.literal(Templates.describe()));
                            return Templates.ALL.size();
                        }))));
    }

    private static int open(FabricClientCommandSource source, net.minecraft.client.gui.screen.Screen screen) {
        MinecraftClient client = source.getClient();
        client.execute(() -> client.setScreen(screen));
        return 1;
    }

    private static String soleProject(FabricClientCommandSource source) {
        var projects = PackProject.listProjects();
        if (projects.isEmpty()) {
            source.sendError(Text.literal("没有项目，先用 /pw project 创建"));
            return null;
        }
        if (projects.size() > 1) {
            source.sendError(Text.literal("存在多个项目: " + String.join(", ", projects) + "，请用 /pw edit ns <命名空间>"));
            return null;
        }
        return projects.get(0);
    }

    private static int openEditor(FabricClientCommandSource source, String ns) {
        try {
            PackProject p = PackProject.load(ns);
            return open(source, new BlockEditorScreen(p));
        } catch (Exception e) {
            source.sendError(Text.literal("打开失败: " + e.getMessage()));
            return 0;
        }
    }

    private static int openCode(FabricClientCommandSource source, String ns, String fn) {
        try {
            PackProject p = PackProject.load(ns);
            return open(source, new CodeEditorScreen(p, fn));
        } catch (Exception e) {
            source.sendError(Text.literal("打开失败: " + e.getMessage()));
            return 0;
        }
    }

    private static int diag(FabricClientCommandSource source, String ns) {
        try {
            PackProject p = PackProject.load(ns);
            return open(source, new DiagScreen(p, null));
        } catch (Exception e) {
            source.sendError(Text.literal("打开失败: " + e.getMessage()));
            return 0;
        }
    }

    /** 加载 config/packweaver/blocks/*.json 自定义积木（规划书第 19 章）。 */
    public static int loadCustomBlocks() {
        Path dir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
                .resolve("packweaver").resolve("blocks");
        int count = 0;
        if (!Files.isDirectory(dir)) {
            return 0;
        }
        try (var stream = Files.newDirectoryStream(dir, "*.json")) {
            for (Path f : stream) {
                try {
                    JsonElement el = PackProject.gson().fromJson(Files.readString(f), JsonElement.class);
                    JsonArray arr = el.isJsonArray() ? el.getAsJsonArray() : new JsonArray();
                    if (el.isJsonObject()) {
                        arr.add(el.getAsJsonObject());
                    }
                    for (JsonElement item : arr) {
                        JsonObject o = item.getAsJsonObject();
                        String type = o.has("type") ? o.get("type").getAsString() : null;
                        String name = o.has("name") ? o.get("name").getAsString() : type;
                        String command = o.has("command") ? o.get("command").getAsString() : "";
                        if (type == null || type.isBlank() || command.isBlank()) {
                            continue;
                        }
                        BlockDefs.registerCustom("custom_" + type.replaceAll("[^a-z0-9_]", ""), name, command);
                        count++;
                    }
                } catch (Exception ex) {
                    PackWeaverBridge.LOGGER.warn("自定义积木文件 {} 解析失败: {}", f.getFileName(), ex.getMessage());
                }
            }
        } catch (Exception e) {
            PackWeaverBridge.LOGGER.warn("读取自定义积木目录失败: {}", e.getMessage());
        }
        return count;
    }

    private ClientCommands() {
    }
}
