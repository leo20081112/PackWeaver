package com.dpe.common.complete;

import java.util.ArrayList;
import java.util.List;

/**
 * NBT / 记分板补全提供者。
 * 当 commandContext 为 "data" 或 "scoreboard" 时返回常见候选。
 */
public final class NbtScoreboardCompletionProvider implements CompletionProvider {

    private static final List<String> NBT_PATHS = List.of(
            "Health", "Pos", "Inventory", "ArmorItems", "HandItems",
            "AbsorptionAmount", "Air", "DeathTime", "FallFlying",
            "Fire", "HurtTime", "OnGround", "Motion", "Rotation",
            "UUID", "CustomName", "PersistenceRequired"
    );

    @Override
    public List<CompletionCandidate> complete(CompletionContext ctx) {
        List<CompletionCandidate> result = new ArrayList<>();
        if (ctx == null) {
            return result;
        }
        String cc = ctx.commandContext();
        if ("data".equals(cc)) {
            for (String p : NBT_PATHS) {
                result.add(new CompletionCandidate(p, p, "NBT 路径 " + p, "nbt"));
            }
        } else if ("scoreboard".equals(cc)) {
            result.add(new CompletionCandidate("objectives", "objectives",
                    "记分板目标列表", "scoreboard"));
            result.add(new CompletionCandidate("<name>", "<name>",
                    "记分板名称占位", "scoreboard"));
            for (String p : NBT_PATHS) {
                result.add(new CompletionCandidate(p, p, "NBT 路径 " + p, "nbt"));
            }
        }
        return result;
    }
}
