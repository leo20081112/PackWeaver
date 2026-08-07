package com.dpe.client;

import com.dpe.common.block.BlockField;
import com.dpe.common.block.BlockFieldType;
import com.dpe.common.block.BlockSchema;
import com.dpe.common.block.BlockSchemaRegistry;
import com.dpe.common.block.EditorBlock;
import com.dpe.common.block.EditorState;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;

/**
 * NBT 复制服务（Task 9）：从游戏世界抓取坐标/主手物品/准星目标的 NBT 数据，
 * 填入当前选中积木的对应字段，并把原始 NBT 写入系统剪贴板便于粘贴到命令。
 * <ul>
 *   <li>{@link #copyCoordinatesInto} —— 玩家脚下或准星方块坐标 → pos 字段。</li>
 *   <li>{@link #copyHeldItemInto} —— 主手物品 id → item/block/entity 字段；物品 NBT 到剪贴板。</li>
 *   <li>{@link #copyTargetNbtInto} —— 准星方块/实体 id → block/entity 字段（方块同时填 pos）；目标 NBT 到剪贴板。</li>
 * </ul>
 * 1.21.1 物品/实体 NBT 通过数据组件 API（{@code encodeAllowingEmpty} / {@code writeNbt}）获取。
 */
public final class NbtCopyService {

    private NbtCopyService() {
    }

    /** 复制坐标到选中积木的 pos 类字段；返回中文状态文案。 */
    public static String copyCoordinatesInto(EditorState state, String selectedId,
                                             BlockSchemaRegistry reg, MinecraftClient mc) {
        EditorBlock b = state == null ? null : state.getById(selectedId);
        if (b == null) {
            return "请先选中一个积木";
        }
        String coords = copyCoordinates(mc);
        if (coords == null) {
            return "无法获取坐标（无玩家）";
        }
        String field = pickField(b, reg, "pos", "position", "coord", "坐标");
        if (field == null) {
            return "当前积木无可填入坐标的字段";
        }
        b.fieldValues().put(field, coords);
        return "已复制坐标 → " + field + " = " + coords;
    }

    /** 复制主手物品 id 到选中积木的 item/block/entity 字段；物品 NBT 写入剪贴板。 */
    public static String copyHeldItemInto(EditorState state, String selectedId,
                                          BlockSchemaRegistry reg, MinecraftClient mc) {
        EditorBlock b = state == null ? null : state.getById(selectedId);
        if (b == null) {
            return "请先选中一个积木";
        }
        String id = copyHeldItem(mc);
        if (id == null) {
            return "主手无物品";
        }
        // 物品 NBT（含数据组件）写入剪贴板，便于粘贴到 /give 等命令
        String nbt = copyHeldItemNbt(mc);
        copyToClipboard(mc, nbt);
        String field = pickField(b, reg, "item", "block", "entity", "id");
        if (field == null) {
            return "物品 " + id + "（NBT 已复制到剪贴板），但积木无可填字段";
        }
        b.fieldValues().put(field, id);
        return "已复制物品 → " + field + " = " + id + "（NBT 已到剪贴板）";
    }

    /** 复制准星目标（方块/实体）id 到选中积木对应字段；目标 NBT 写入剪贴板。 */
    public static String copyTargetNbtInto(EditorState state, String selectedId,
                                           BlockSchemaRegistry reg, MinecraftClient mc) {
        EditorBlock b = state == null ? null : state.getById(selectedId);
        if (b == null) {
            return "请先选中一个积木";
        }
        TargetInfo info = copyTarget(mc);
        if (info == null) {
            return "准星未命中方块/实体";
        }
        copyToClipboard(mc, info.nbt());
        if (info.kind() == TargetKind.BLOCK) {
            String blockField = pickField(b, reg, "block", "block_ref", "id");
            String posField = pickField(b, reg, "pos", "position", "coord", "坐标");
            StringBuilder msg = new StringBuilder();
            if (blockField != null) {
                b.fieldValues().put(blockField, info.id());
                msg.append("方块 → ").append(blockField).append(" = ").append(info.id());
            }
            if (posField != null && info.coords() != null) {
                b.fieldValues().put(posField, info.coords());
                if (msg.length() > 0) {
                    msg.append("; ");
                }
                msg.append("坐标 → ").append(posField).append(" = ").append(info.coords());
            }
            if (msg.length() == 0) {
                return "命中方块 " + info.id() + "（NBT 已到剪贴板），但积木无可填字段";
            }
            return msg.toString();
        }
        // ENTITY
        String entityField = pickField(b, reg, "entity", "entity_type", "type", "id");
        if (entityField == null) {
            return "命中实体 " + info.id() + "（NBT 已到剪贴板），但积木无可填字段";
        }
        b.fieldValues().put(entityField, info.id());
        return "已复制实体 → " + entityField + " = " + info.id() + "（NBT 已到剪贴板）";
    }

    // ---------- 原始数据抓取 ----------

    /** 坐标字符串 "x y z"：优先准星方块坐标，否则玩家脚下方块坐标；无玩家返回 null。 */
    public static String copyCoordinates(MinecraftClient mc) {
        if (mc == null || mc.player == null) {
            return null;
        }
        BlockPos pos = null;
        if (mc.crosshairTarget != null
                && mc.crosshairTarget.getType() == HitResult.Type.BLOCK
                && mc.crosshairTarget instanceof BlockHitResult bhr) {
            pos = bhr.getBlockPos();
        }
        if (pos == null) {
            pos = mc.player.getBlockPos();
        }
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    /** 主手物品 id（如 "minecraft:diamond_sword"）；空手返回 null。 */
    public static String copyHeldItem(MinecraftClient mc) {
        if (mc == null || mc.player == null) {
            return null;
        }
        ItemStack stack = mc.player.getMainHandStack();
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        return Registries.ITEM.getId(stack.getItem()).toString();
    }

    /** 主手物品的 NBT/数据组件字符串（用于剪贴板）；空手返回 null。 */
    public static String copyHeldItemNbt(MinecraftClient mc) {
        if (mc == null || mc.player == null) {
            return null;
        }
        ItemStack stack = mc.player.getMainHandStack();
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        // 1.21.1 物品 NBT 为数据组件；toString 含 id、数量与组件快照，便于粘贴参考
        return stack.toString();
    }

    /** 准星目标信息（方块/实体 id + 坐标 + NBT）；未命中返回 null。 */
    private static TargetInfo copyTarget(MinecraftClient mc) {
        if (mc == null || mc.crosshairTarget == null || mc.world == null) {
            return null;
        }
        HitResult hit = mc.crosshairTarget;
        if (hit.getType() == HitResult.Type.BLOCK && hit instanceof BlockHitResult bhr) {
            BlockPos pos = bhr.getBlockPos();
            BlockState state = mc.world.getBlockState(pos);
            String id = Registries.BLOCK.getId(state.getBlock()).toString();
            String coords = pos.getX() + " " + pos.getY() + " " + pos.getZ();
            // 方块状态字符串作为辅助 NBT（方块本身无实体 NBT）
            String nbt;
            try {
                nbt = state.toString();
            } catch (Throwable ignored) {
                nbt = null;
            }
            return new TargetInfo(TargetKind.BLOCK, id, coords, nbt);
        }
        if (hit.getType() == HitResult.Type.ENTITY && hit instanceof EntityHitResult ehr) {
            Entity ent = ehr.getEntity();
            if (ent == null) {
                return null;
            }
            String id = Registries.ENTITY_TYPE.getId(ent.getType()).toString();
            String nbt = null;
            try {
                NbtCompound compound = new NbtCompound();
                ent.writeNbt(compound);
                nbt = compound.toString();
            } catch (Throwable ignored) {
                // 忽略 NBT 编码失败
            }
            return new TargetInfo(TargetKind.ENTITY, id, null, nbt);
        }
        return null;
    }

    // ---------- 工具 ----------

    /** 在选中积木的 schema 字段中按偏好顺序选一个可填字段（先精确、再包含、最后取首个字符串类字段）。 */
    private static String pickField(EditorBlock b, BlockSchemaRegistry reg, String... preferences) {
        BlockSchema schema = reg.get(b.schemaId());
        if (schema == null || schema.fields().isEmpty()) {
            return null;
        }
        for (String pref : preferences) {
            for (BlockField f : schema.fields()) {
                if (pref != null && pref.equalsIgnoreCase(f.name())) {
                    return f.name();
                }
            }
        }
        for (String pref : preferences) {
            if (pref == null) {
                continue;
            }
            String low = pref.toLowerCase();
            for (BlockField f : schema.fields()) {
                if (f.name() != null && f.name().toLowerCase().contains(low)) {
                    return f.name();
                }
            }
        }
        for (BlockField f : schema.fields()) {
            if (f.type() == BlockFieldType.STRING
                    || f.type() == BlockFieldType.RESOURCE_LOCATION
                    || f.type() == BlockFieldType.BLOCK_REF) {
                return f.name();
            }
        }
        return null;
    }

    /** 写入系统剪贴板（空值跳过，异常忽略）。 */
    private static void copyToClipboard(MinecraftClient mc, String value) {
        if (value == null || mc == null || mc.keyboard == null) {
            return;
        }
        try {
            mc.keyboard.setClipboard(value);
        } catch (Throwable ignored) {
            // 沙箱无剪贴板时忽略
        }
    }

    private enum TargetKind {
        BLOCK,
        ENTITY
    }

    private record TargetInfo(TargetKind kind, String id, String coords, String nbt) {
    }
}
