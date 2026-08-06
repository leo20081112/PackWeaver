package com.dpe.common.complete;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * NBT / 记分板补全提供者。
 * 当 commandContext 为 "data" 或 "scoreboard" 时返回常见候选。
 */
public final class NbtScoreboardCompletionProvider implements CompletionProvider {

    /** 常见 NBT 路径 -> 中文说明。 */
    private static final Map<String, String> NBT_PATHS = Map.ofEntries(
            Map.entry("Health", "实体生命值 NBT 路径：存储实体当前血量，如 Health"),
            Map.entry("Pos", "坐标 NBT 路径：实体所在位置 [x,y,z]，如 Pos"),
            Map.entry("Inventory", "背包 NBT 路径：玩家/实体物品栏列表，如 Inventory"),
            Map.entry("ArmorItems", "护甲槽 NBT 路径：四件护甲物品，如 ArmorItems"),
            Map.entry("HandItems", "主副手 NBT 路径：手中物品列表，如 HandItems"),
            Map.entry("AbsorptionAmount", "吸收生命 NBT 路径：黄心额外生命值，如 AbsorptionAmount"),
            Map.entry("Air", "氧气值 NBT 路径：剩余呼吸空气，如 Air"),
            Map.entry("DeathTime", "死亡动画 NBT 路径：死亡倒计时，如 DeathTime"),
            Map.entry("FallFlying", "鞘翅飞行 NBT 路径：是否正在滑翔，如 FallFlying"),
            Map.entry("Fire", "着火时间 NBT 路径：剩余燃烧刻，如 Fire"),
            Map.entry("HurtTime", "受伤免疫 NBT 路径：受击后无敌刻数，如 HurtTime"),
            Map.entry("OnGround", "是否着地 NBT 路径：实体是否接触地面，如 OnGround"),
            Map.entry("Motion", "运动向量 NBT 路径：[dx,dy,dz] 速度，如 Motion"),
            Map.entry("Rotation", "朝向 NBT 路径：[yaw,pitch] 旋转，如 Rotation"),
            Map.entry("UUID", "唯一标识 NBT 路径：实体唯一 ID，如 UUID"),
            Map.entry("CustomName", "自定义名称 NBT 路径：实体显示名（JSON 文本），如 CustomName"),
            Map.entry("PersistenceRequired", "持久保留 NBT 路径：是否不被清除，如 PersistenceRequired")
    );

    @Override
    public List<CompletionCandidate> complete(CompletionContext ctx) {
        List<CompletionCandidate> result = new ArrayList<>();
        if (ctx == null) {
            return result;
        }
        String cc = ctx.commandContext();
        if ("data".equals(cc)) {
            for (var e : NBT_PATHS.entrySet()) {
                result.add(new CompletionCandidate(e.getKey(), e.getKey(), e.getValue(), "nbt"));
            }
        } else if ("scoreboard".equals(cc)) {
            result.add(new CompletionCandidate("objectives", "objectives",
                    "记分板目标：管理计分项，如 scoreboard objectives add obj dummy", "scoreboard"));
            result.add(new CompletionCandidate("<name>", "<name>",
                    "记分板名称：占位的记分项名称，如 scoreboard objectives add <name> dummy", "scoreboard"));
            for (var e : NBT_PATHS.entrySet()) {
                result.add(new CompletionCandidate(e.getKey(), e.getKey(), e.getValue(), "nbt"));
            }
        }
        return result;
    }
}
