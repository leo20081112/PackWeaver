package com.dpe.common.block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 积木块 schema 注册表，单例。
 * 通过 {@link #DEFAULT} 获取默认实例（静态块预注册一批内置 schema）。
 * 也可 new 出独立实例用于自定义注册。
 */
public final class BlockSchemaRegistry {

    /** 默认全局实例，已预注册内置 schema。 */
    public static final BlockSchemaRegistry DEFAULT = new BlockSchemaRegistry();

    private final Map<String, BlockSchema> schemas = new LinkedHashMap<>();

    public BlockSchemaRegistry() {
    }

    static {
        // 在默认实例上预注册内置 schema
        DEFAULT.registerBuiltinSchemas();
    }

    /** 注册一个 schema（同 id 覆盖）。 */
    public void register(BlockSchema schema) {
        if (schema == null) {
            throw new IllegalArgumentException("schema 不能为空");
        }
        schemas.put(schema.id(), schema);
    }

    /** 按 id 获取 schema，不存在返回 null。 */
    public BlockSchema get(String id) {
        return schemas.get(id);
    }

    /** 全部 schema（不可变视图）。 */
    public List<BlockSchema> all() {
        return Collections.unmodifiableList(new ArrayList<>(schemas.values()));
    }

    /** 按大类筛选。 */
    public List<BlockSchema> byCategory(BlockCategory category) {
        List<BlockSchema> result = new ArrayList<>();
        for (BlockSchema s : schemas.values()) {
            if (s.category() == category) {
                result.add(s);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** 是否包含某 id。 */
    public boolean contains(String id) {
        return schemas.containsKey(id);
    }

    /** 预注册内置 schema（事件 / 条件 / 动作）。 */
    private void registerBuiltinSchemas() {
        List<String> eventAccepts = List.of("CONDITION", "ACTION");
        List<String> conditionAccepts = List.of("CONDITION", "ACTION");

        // ---------- 事件类 ----------
        register(new BlockSchema("event.tick", BlockCategory.EVENT, "每刻触发", "#4C97FF",
                List.of(), eventAccepts, "mcfunction"));
        register(new BlockSchema("event.load", BlockCategory.EVENT, "数据包加载", "#4C97FF",
                List.of(), eventAccepts, "mcfunction"));
        register(new BlockSchema("event.player_join", BlockCategory.EVENT, "玩家加入", "#4C97FF",
                List.of(), eventAccepts, "mcfunction"));
        register(new BlockSchema("event.entity_death", BlockCategory.EVENT, "实体死亡", "#4C97FF",
                List.of(new BlockField("entity_type", BlockFieldType.RESOURCE_LOCATION, true, "minecraft:zombie", List.of())),
                eventAccepts, "mcfunction"));

        // ---------- 条件类 ----------
        register(new BlockSchema("condition.score_compare", BlockCategory.CONDITION, "记分板比较", "#59C059",
                List.of(
                        new BlockField("objective", BlockFieldType.STRING, true, null, List.of()),
                        new BlockField("target", BlockFieldType.STRING, true, null, List.of()),
                        new BlockField("op", BlockFieldType.ENUM, true, "=", List.of(">", "<", "=", "\u2265", "\u2264")),
                        new BlockField("value", BlockFieldType.NUMBER, true, null, List.of())
                ),
                conditionAccepts, null));
        register(new BlockSchema("condition.entity_exists", BlockCategory.CONDITION, "实体存在", "#59C059",
                List.of(new BlockField("entity_type", BlockFieldType.RESOURCE_LOCATION, true, "minecraft:zombie", List.of())),
                conditionAccepts, null));
        register(new BlockSchema("condition.random_chance", BlockCategory.CONDITION, "随机概率", "#59C059",
                List.of(new BlockField("value", BlockFieldType.NUMBER, true, null, List.of())),
                conditionAccepts, null));

        // ---------- 动作类 ----------
        register(new BlockSchema("action.run_function", BlockCategory.ACTION, "运行函数", "#FF8C1A",
                List.of(
                        new BlockField("function", BlockFieldType.RESOURCE_LOCATION, true, null, List.of()),
                        new BlockField("namespace", BlockFieldType.STRING, false, null, List.of())
                ),
                List.of(), "mcfunction"));
        register(new BlockSchema("action.say_text", BlockCategory.ACTION, "说话文本", "#FF8C1A",
                List.of(new BlockField("text", BlockFieldType.TEXT_COMPONENT, true, null, List.of())),
                List.of(), "mcfunction"));
        register(new BlockSchema("action.set_block", BlockCategory.ACTION, "放置方块", "#FF8C1A",
                List.of(
                        new BlockField("pos", BlockFieldType.STRING, true, null, List.of()),
                        new BlockField("block", BlockFieldType.RESOURCE_LOCATION, true, null, List.of())
                ),
                List.of(), "mcfunction"));
        register(new BlockSchema("action.give_item", BlockCategory.ACTION, "给予物品", "#FF8C1A",
                List.of(
                        new BlockField("target", BlockFieldType.STRING, true, "@p", List.of()),
                        new BlockField("item", BlockFieldType.RESOURCE_LOCATION, true, null, List.of()),
                        new BlockField("count", BlockFieldType.NUMBER, false, "1", List.of())
                ),
                List.of(), "mcfunction"));
        register(new BlockSchema("action.summon", BlockCategory.ACTION, "召唤实体", "#FF8C1A",
                List.of(
                        new BlockField("entity", BlockFieldType.RESOURCE_LOCATION, true, null, List.of()),
                        new BlockField("pos", BlockFieldType.STRING, true, null, List.of())
                ),
                List.of(), "mcfunction"));
        register(new BlockSchema("action.tag_add", BlockCategory.ACTION, "添加标签项", "#FF8C1A",
                List.of(
                        new BlockField("tag", BlockFieldType.RESOURCE_LOCATION, true, null, List.of()),
                        new BlockField("entry", BlockFieldType.RESOURCE_LOCATION, true, null, List.of())
                ),
                List.of(), "tag"));
        register(new BlockSchema("action.tellraw", BlockCategory.ACTION, "原始消息", "#FF8C1A",
                List.of(
                        new BlockField("target", BlockFieldType.STRING, true, "@a", List.of()),
                        new BlockField("text", BlockFieldType.TEXT_COMPONENT, true, null, List.of())
                ),
                List.of(), "mcfunction"));
    }
}
