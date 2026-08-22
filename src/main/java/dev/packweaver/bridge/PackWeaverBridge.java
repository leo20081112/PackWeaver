package dev.packweaver.bridge;

import net.fabricmc.api.ModInitializer;
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
 * 负责注册物品、命令、性能追踪与桌面端桥接服务器（对应规划书第 18/20 章）。
 */
public class PackWeaverBridge implements ModInitializer {
    public static final String MOD_ID = "packweaver";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final int BRIDGE_DEFAULT_PORT = 32005;

    public static final Item COORDINATE_COPIER =
            new CoordinateCopierItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC));

    @Override
    public void onInitialize() {
        Registry.register(Registries.ITEM, new Identifier(MOD_ID, "coordinate_copier"), COORDINATE_COPIER);

        PerfTracker.init();
        PWCommands.register();
        BridgeServer.getInstance().start();

        LOGGER.info("[PackWeaver] Bridge 已初始化，TCP 桥接端口 {}（仅本机回环）", BRIDGE_DEFAULT_PORT);
    }
}
