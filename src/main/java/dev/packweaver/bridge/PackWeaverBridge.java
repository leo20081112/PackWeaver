package dev.packweaver.bridge;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import dev.packweaver.bridge.bridge.BridgeServer;
import dev.packweaver.bridge.command.PWCommands;
import dev.packweaver.bridge.perf.PerfTracker;
import dev.packweaver.bridge.tools.CoordinateCopierItem;

/**
 * PackWeaver Bridge 主入口。
 *
 * 启动策略（v1.5.1 起加固）：各组件独立 try/catch，任何一个失败只记日志
 * 不中断启动；客户端初始化由主入口在 CLIENT 环境下经 {@link ClientBridge}
 * 懒加载调用（不再使用 fabric.mod.json 的 client 入口点，规避新版加载器
 * 对入口类的类加载差异导致的启动崩溃，服务端也绝不会加载客户端类）。
 */
public class PackWeaverBridge implements ModInitializer {
    public static final String MOD_ID = "packweaver";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final int BRIDGE_DEFAULT_PORT = 32005;
    public static final int HTTP_BRIDGE_PORT = 32006;

    public static final Item COORDINATE_COPIER =
            new CoordinateCopierItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC));

    @Override
    public void onInitialize() {
        try {
            Registry.register(Registries.ITEM, new Identifier(MOD_ID, "coordinate_copier"), COORDINATE_COPIER);
        } catch (Throwable t) {
            LOGGER.error("[PackWeaver] 物品注册失败: {}", t.toString());
        }
        try {
            PerfTracker.init();
        } catch (Throwable t) {
            LOGGER.error("[PackWeaver] 性能统计初始化失败: {}", t.toString());
        }
        try {
            PWCommands.register();
        } catch (Throwable t) {
            LOGGER.error("[PackWeaver] /pw 命令注册失败: {}", t.toString());
        }
        try {
            BridgeServer.getInstance().start();
        } catch (Throwable t) {
            LOGGER.error("[PackWeaver] TCP 桥接启动失败: {}", t.toString());
        }
        try {
            new dev.packweaver.bridge.bridge.HttpBridgeServer().start();
        } catch (Throwable t) {
            LOGGER.error("[PackWeaver] HTTP 桥接启动失败: {}", t.toString());
        }
        if (FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT) {
            try {
                // 懒加载：仅客户端会真正加载 ClientBridge/ClientHooks 及其引用的客户端类
                ClientBridge.init();
            } catch (Throwable t) {
                LOGGER.error("[PackWeaver] 客户端功能初始化失败（游戏可正常运行，相关功能不可用）", t);
            }
        }
        LOGGER.info("[PackWeaver] Bridge v{} 初始化完成：TCP {} / HTTP {} / env={}",
                FabricLoader.getInstance().getModContainer(MOD_ID)
                        .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?"),
                BRIDGE_DEFAULT_PORT, HTTP_BRIDGE_PORT,
                FabricLoader.getInstance().getEnvironmentType());
    }

    /** 仅客户端分支内被首次引用，服务端不会加载。 */
    private static final class ClientBridge {
        static void init() {
            dev.packweaver.bridge.client.ClientHooks.init();
        }
    }
}
