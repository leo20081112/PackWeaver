package dev.packweaver.bridge.tools;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import dev.packweaver.bridge.PackWeaverBridge;

import java.util.List;

/**
 * 坐标复制器（规划书第 14.3 / 14.4 章）。
 *
 * - 右键方块：复制方块坐标 "X Y Z" 到剪贴板
 * - Shift+右键方块：复制方块完整 NBT 数据
 *
 * 剪贴板等客户端专属逻辑被隔离在 {@link ClientClipboard} 中，
 * 保证专用服务器不会加载客户端类。
 */
public class CoordinateCopierItem extends Item {

    public CoordinateCopierItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        World world = context.getWorld();
        BlockPos pos = context.getBlockPos();
        if (world.isClient) {
            String payload;
            if (context.getPlayer() != null && context.getPlayer().isSneaking()) {
                var blockEntity = world.getBlockEntity(pos);
                payload = blockEntity != null
                        ? NbtHelper.toPrettyPrintedText(blockEntity.createNbtWithIdentifyingData()).getString()
                        : world.getBlockState(pos).toString();
            } else {
                payload = ClientClipboard.positionPayload(pos);
            }
            ClientClipboard.copy(payload);
            if (context.getPlayer() != null) {
                context.getPlayer().sendMessage(
                        Text.literal("§a[PW]§7 已复制: §f").append(payload), true);
            }
        }
        return ActionResult.success(true);
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("item.packweaver.coordinate_copier.tooltip.1").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.packweaver.coordinate_copier.tooltip.2").formatted(Formatting.DARK_GRAY));
    }

    /** 仅在客户端被加载（world.isClient 分支内首次引用）。 */
    private static final class ClientClipboard {
        /** 坐标格式（规划书 14.3）：普通=基础 "X Y Z"；按住 Ctrl=JSON 格式。 */
        static String positionPayload(BlockPos pos) {
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            boolean ctrl = net.minecraft.client.util.InputUtil.isKeyPressed(
                    client.getWindow().getHandle(), org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL)
                    || net.minecraft.client.util.InputUtil.isKeyPressed(
                    client.getWindow().getHandle(), org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_CONTROL);
            if (ctrl) {
                return String.format("{\"x\":%d,\"y\":%d,\"z\":%d}", pos.getX(), pos.getY(), pos.getZ());
            }
            return String.format("%d %d %d", pos.getX(), pos.getY(), pos.getZ());
        }

        static void copy(String text) {
            net.minecraft.client.MinecraftClient client = net.minecraft.client.MinecraftClient.getInstance();
            client.execute(() -> client.keyboard.setClipboard(text));
            PackWeaverBridge.LOGGER.info("[Copier] 已复制到剪贴板（{} 字符）", text.length());
        }
    }
}
