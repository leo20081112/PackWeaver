package com.dpe.common.template;

import com.dpe.common.block.BlockSchemaRegistry;
import com.dpe.common.block.EditorBlock;
import com.dpe.common.block.EditorState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置数据包模板。namespace 统一设为 "dpe"。
 */
public final class BuiltinTemplates {

    private static final String NS = "dpe";
    private static final List<DatapackTemplate> TEMPLATES = buildTemplates();

    private BuiltinTemplates() {
    }

    /** 全部模板（不可变）。 */
    public static List<DatapackTemplate> all() {
        return TEMPLATES;
    }

    /** 按 id 查找；不存在返回 null。 */
    public static DatapackTemplate byId(String id) {
        if (id == null) {
            return null;
        }
        for (DatapackTemplate t : TEMPLATES) {
            if (t.id().equals(id)) {
                return t;
            }
        }
        return null;
    }

    private static List<DatapackTemplate> buildTemplates() {
        return List.of(
                tickNotify(),
                playerJoinWelcome(),
                killReward()
        );
    }

    /** 每刻通知：event.tick + action.tellraw(text="Tick!")。 */
    private static DatapackTemplate tickNotify() {
        EditorState state = new EditorState(NS);
        ensureSchema("event.tick");
        ensureSchema("action.tellraw");
        EditorBlock tick = new EditorBlock("b1", "event.tick", 0, 0);
        EditorBlock tellraw = new EditorBlock("b2", "action.tellraw", 200, 0,
                fields("target", "@a", "text", "Tick!"), List.of());
        state.addBlock(tick);
        state.addBlock(tellraw);
        state.connect("b1", "b2");
        return new DatapackTemplate("tick_notify", "每刻通知",
                "每游戏刻向所有玩家发送一条 tellraw 通知。", state);
    }

    /** 玩家加入欢迎：event.player_join + action.tellraw(text="欢迎来到服务器")。 */
    private static DatapackTemplate playerJoinWelcome() {
        EditorState state = new EditorState(NS);
        ensureSchema("event.player_join");
        ensureSchema("action.tellraw");
        EditorBlock join = new EditorBlock("b1", "event.player_join", 0, 0);
        EditorBlock tellraw = new EditorBlock("b2", "action.tellraw", 200, 0,
                fields("target", "@a", "text", "欢迎来到服务器"), List.of());
        state.addBlock(join);
        state.addBlock(tellraw);
        state.connect("b1", "b2");
        return new DatapackTemplate("player_join_welcome", "玩家加入欢迎",
                "玩家加入服务器时向所有人广播欢迎消息。", state);
    }

    /** 击杀计数奖励：event.entity_death(zombie) + condition.score_compare + action.give_item(diamond)。 */
    private static DatapackTemplate killReward() {
        EditorState state = new EditorState(NS);
        ensureSchema("event.entity_death");
        ensureSchema("condition.score_compare");
        ensureSchema("action.give_item");
        EditorBlock death = new EditorBlock("b1", "event.entity_death", 0, 0,
                fields("entity_type", "minecraft:zombie"), List.of());
        EditorBlock cond = new EditorBlock("b2", "condition.score_compare", 200, 0,
                fields("objective", "kills", "target", "@p", "op", ">=", "value", "3"), List.of());
        EditorBlock give = new EditorBlock("b3", "action.give_item", 400, 0,
                fields("target", "@p", "item", "minecraft:diamond", "count", "1"), List.of());
        state.addBlock(death);
        state.addBlock(cond);
        state.addBlock(give);
        state.connect("b1", "b2");
        state.connect("b2", "b3");
        return new DatapackTemplate("kill_reward", "击杀计数奖励",
                "当玩家击杀 3 只僵尸后给予一颗钻石作为奖励。", state);
    }

    /** 确认 schema 存在（构建 EditorBlock 本身不依赖 registry，这里仅做存在性校验）。 */
    private static void ensureSchema(String id) {
        if (BlockSchemaRegistry.DEFAULT.get(id) == null) {
            throw new IllegalStateException("内置模板依赖未知 schema: " + id);
        }
    }

    /** 构造字段 map（保持插入顺序）。 */
    private static Map<String, Object> fields(String... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }
}
