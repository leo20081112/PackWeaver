package com.dpe.common.parse;

import com.dpe.common.block.EditorBlock;
import com.dpe.common.block.EditorState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 把原版 .mcfunction / .json 文本解析为积木编辑器状态。
 * 识别 say/tellraw/function/setblock/give/summon/execute if 等命令为对应积木；
 * 其余行/JSON 文件保留为 raw_text 积木（往返无损）。
 * 支持合并解析：传入已有 EditorState 时按签名匹配复用积木坐标。
 */
public final class TextToBlocksParser {

    private static final String RAW_TEXT_SCHEMA = "raw_text";
    /** dpe 编辑器生成的注释行前缀，解析时跳过以避免往返污染。 */
    private static final String DPE_COMMENT_PREFIX = "# 由 PackWeaver 编辑器生成";

    /**
     * 解析多个文件（新建状态）。
     * @param namespace 数据包命名空间
     * @param files     文件路径 -> 内容
     * @return 编辑器状态，activeDatapackNamespace=namespace
     */
    public EditorState parse(String namespace, Map<String, String> files) {
        EditorState state = new EditorState(namespace == null || namespace.isBlank() ? "minecraft" : namespace);
        return parse(namespace, files, state);
    }

    /**
     * 解析多个文件并合并入已有状态。
     * <p>existingState 非空时复用其引用：按 (schemaId + 关键字段) 匹配已有积木，
     * 命中则保留其 x/y 仅更新字段；未命中则新建并按网格布局放置（避开占用格子）。
     * 文本中不再出现的已有积木予以保留（不删除）。</p>
     */
    public EditorState parse(String namespace, Map<String, String> files, EditorState existingState) {
        EditorState state = existingState != null ? existingState
                : new EditorState(namespace == null || namespace.isBlank() ? "minecraft" : namespace);
        if (namespace != null && !namespace.isBlank()) {
            state.setActiveDatapackNamespace(namespace);
        }
        if (files == null || files.isEmpty()) {
            return state;
        }
        boolean merge = existingState != null;
        ParseContext ctx = new ParseContext(state, merge, startingCounter(state));
        for (Map.Entry<String, String> e : files.entrySet()) {
            String path = e.getKey();
            String content = e.getValue() == null ? "" : e.getValue();
            EditorBlock root = findOrCreateRoot(ctx, rootSchemaForPath(path));
            if (path != null && path.endsWith(".mcfunction")) {
                parseMcFunction(content, ctx, root, path);
            } else if (!content.isBlank()) {
                // JSON 或其它文件：整体作为 raw_text 保留
                Map<String, Object> fv = fields("text", content, "source", path == null ? "" : path);
                placeBlock(RAW_TEXT_SCHEMA, fv, ctx, root, 0);
            }
        }
        return state;
    }

    private void parseMcFunction(String content, ParseContext ctx, EditorBlock root, String source) {
        String[] lines = content.split("\n", -1);
        for (String raw : lines) {
            String line = raw.stripTrailing();
            if (line.isBlank()) {
                continue;
            }
            parseLine(line.trim(), ctx, root, 0, source);
        }
    }

    /**
     * 解析单行，生成积木并连接到 parent。
     * 返回该行对应的积木（execute if 返回条件父块）；dpe 注释行返回 null。
     */
    private EditorBlock parseLine(String line, ParseContext ctx, EditorBlock parent, int depth, String source) {
        // 跳过 dpe 生成注释，避免往返污染
        if (line.startsWith(DPE_COMMENT_PREFIX)) {
            return null;
        }

        // say <text>
        if (line.startsWith("say ") || line.equals("say")) {
            String text = line.equals("say") ? "" : line.substring("say ".length());
            return placeBlock("action.say_text", fields("text", text), ctx, parent, depth);
        }
        // tellraw <target> <json>
        if (line.startsWith("tellraw ")) {
            String rest = line.substring("tellraw ".length()).trim();
            int sp = rest.indexOf(' ');
            String target = sp < 0 ? rest : rest.substring(0, sp);
            String text = sp < 0 ? "" : rest.substring(sp + 1).trim();
            return placeBlock("action.tellraw",
                    fields("target", target, "text", text), ctx, parent, depth);
        }
        // function <ns:path>
        if (line.startsWith("function ")) {
            String fn = line.substring("function ".length()).trim();
            return placeBlock("action.run_function", fields("function", fn), ctx, parent, depth);
        }
        // setblock <pos> <block>
        if (line.startsWith("setblock ")) {
            String rest = line.substring("setblock ".length()).trim();
            int last = rest.lastIndexOf(' ');
            if (last > 0) {
                String pos = rest.substring(0, last);
                String block = rest.substring(last + 1);
                return placeBlock("action.set_block",
                        fields("pos", pos, "block", block), ctx, parent, depth);
            }
            // 仅一个 token：作为 raw_text
        }
        // give <target> <item> [count]
        if (line.startsWith("give ")) {
            String rest = line.substring("give ".length()).trim();
            String[] tokens = rest.split("\\s+");
            if (tokens.length >= 2) {
                Map<String, Object> fv = fields("target", tokens[0], "item", tokens[1]);
                if (tokens.length >= 3) {
                    fv.put("count", tokens[2]);
                }
                return placeBlock("action.give_item", fv, ctx, parent, depth);
            }
        }
        // summon <entity> <pos>
        if (line.startsWith("summon ")) {
            String rest = line.substring("summon ".length()).trim();
            int sp = rest.indexOf(' ');
            if (sp > 0) {
                String entity = rest.substring(0, sp);
                String pos = rest.substring(sp + 1).trim();
                return placeBlock("action.summon",
                        fields("entity", entity, "pos", pos), ctx, parent, depth);
            }
        }
        // execute if score <target> <objective> <op> <value> run <cmd>
        if (line.startsWith("execute if score ")) {
            String rest = line.substring("execute if score ".length());
            int runIdx = rest.indexOf(" run ");
            String condPart = runIdx < 0 ? rest : rest.substring(0, runIdx);
            String cmd = runIdx < 0 ? null : rest.substring(runIdx + " run ".length()).trim();
            String[] tokens = condPart.trim().split("\\s+");
            if (tokens.length >= 4) {
                Map<String, Object> fv = fields(
                        "objective", tokens[1],
                        "target", tokens[0],
                        "op", mapOpParse(tokens[2]),
                        "value", tokens[3]);
                EditorBlock cond = placeBlock("condition.score_compare", fv, ctx, parent, depth);
                if (cmd != null && !cmd.isEmpty()) {
                    parseLine(cmd, ctx, cond, depth + 1, source);
                }
                return cond;
            }
        }
        // execute if entity @e[type=<et>] run <cmd>
        if (line.startsWith("execute if entity ")) {
            String rest = line.substring("execute if entity ".length());
            int runIdx = rest.indexOf(" run ");
            String sel = runIdx < 0 ? rest : rest.substring(0, runIdx);
            String cmd = runIdx < 0 ? null : rest.substring(runIdx + " run ".length()).trim();
            String et = extractEntityType(sel);
            if (et != null) {
                EditorBlock cond = placeBlock("condition.entity_exists",
                        fields("entity_type", et), ctx, parent, depth);
                if (cmd != null && !cmd.isEmpty()) {
                    parseLine(cmd, ctx, cond, depth + 1, source);
                }
                return cond;
            }
        }

        // 其余命令/注释：raw_text 保留原文
        Map<String, Object> fv = fields("text", line);
        fv.put("source", source == null ? "" : source);
        return placeBlock(RAW_TEXT_SCHEMA, fv, ctx, parent, depth);
    }

    /** 放置积木：合并模式优先匹配已有积木（保留 x/y），否则新建并按网格布局放置。 */
    private EditorBlock placeBlock(String schemaId, Map<String, Object> fieldValues,
                                   ParseContext ctx, EditorBlock parent, int depth) {
        if (ctx.merge) {
            String sig = signature(schemaId, fieldValues);
            for (EditorBlock eb : ctx.state.getBlocks()) {
                if (eb.schemaId().startsWith("event.")) {
                    continue;
                }
                if (ctx.usedExistingIds.contains(eb.id())) {
                    continue;
                }
                if (sig.equals(signature(eb.schemaId(), eb.fieldValues()))) {
                    EditorBlock updated = new EditorBlock(eb.id(), eb.schemaId(),
                            eb.x(), eb.y(), fieldValues, new ArrayList<>(eb.childIds()), null, eb.collapsed());
                    ctx.state.addBlock(updated);
                    ctx.usedExistingIds.add(eb.id());
                    if (parent != null) {
                        ctx.state.connect(parent.id(), eb.id());
                    }
                    return updated;
                }
            }
        }
        String id = nextId(ctx.counter);
        double[] pos = gridPos(ctx, depth);
        EditorBlock nb = new EditorBlock(id, schemaId, pos[0], pos[1],
                new LinkedHashMap<>(fieldValues), new ArrayList<>(), null, false);
        ctx.state.addBlock(nb);
        if (parent != null) {
            ctx.state.connect(parent.id(), id);
        }
        return nb;
    }

    /** 查找或创建事件根块（同 schemaId 复用）。新根按 (0, 已有事件根数*180) 堆叠。 */
    private EditorBlock findOrCreateRoot(ParseContext ctx, String schemaId) {
        for (EditorBlock b : ctx.state.getBlocks()) {
            if (b.schemaId().equals(schemaId)) {
                return b;
            }
        }
        String id;
        if (schemaId.equals("event.tick") && ctx.state.getById("blk_root") == null) {
            id = "blk_root";
        } else if (schemaId.equals("event.load") && ctx.state.getById("blk_root_load") == null) {
            id = "blk_root_load";
        } else {
            id = nextId(ctx.counter);
        }
        long existingRoots = ctx.state.getBlocks().stream()
                .filter(b -> b.schemaId().startsWith("event.")).count();
        EditorBlock root = new EditorBlock(id, schemaId, 0, existingRoots * 180.0);
        ctx.state.addBlock(root);
        return root;
    }

    /** 文件路径 -> 事件根 schemaId。按 /load /player_join /entity_death 路由，其余默认 event.tick。 */
    private static String rootSchemaForPath(String path) {
        if (path == null) {
            return "event.tick";
        }
        String p = path.toLowerCase();
        if (p.contains("/load")) {
            return "event.load";
        }
        if (p.contains("/on_player_join") || p.contains("/player_join")) {
            return "event.player_join";
        }
        if (p.contains("/on_entity_death") || p.contains("/entity_death")) {
            return "event.entity_death";
        }
        // /tick 或无法识别 → event.tick
        return "event.tick";
    }

    /** 网格布局：(col*180, 90+depth*90)，跳过已被占用的格子。 */
    private static double[] gridPos(ParseContext ctx, int depth) {
        double y = 90.0 + depth * 90.0;
        int col = ctx.layoutCol;
        while (occupied(ctx, col * 180.0, y)) {
            col++;
        }
        ctx.layoutCol = col + 1;
        return new double[]{col * 180.0, y};
    }

    private static boolean occupied(ParseContext ctx, double x, double y) {
        for (EditorBlock b : ctx.state.getBlocks()) {
            if (b.x() == x && b.y() == y) {
                return true;
            }
        }
        return false;
    }

    /** 计算已有状态中 blk_<n> 的最大 n，作为 id 计数器起点（避免合并时 id 冲突）。 */
    private static int startingCounter(EditorState state) {
        int max = 0;
        for (EditorBlock b : state.getBlocks()) {
            String id = b.id();
            if (id.startsWith("blk_")) {
                try {
                    int n = Integer.parseInt(id.substring("blk_".length()));
                    if (n > max) {
                        max = n;
                    }
                } catch (NumberFormatException ignored) {
                    // blk_root 等非数字 id 忽略
                }
            }
        }
        return max;
    }

    /** 积木签名：schemaId + 关键字段，用于合并匹配。 */
    private static String signature(String schemaId, Map<String, Object> fv) {
        StringBuilder sb = new StringBuilder(schemaId).append('|');
        switch (schemaId) {
            case "action.say_text" -> appendKV(sb, "text", fv);
            case "action.tellraw" -> {
                appendKV(sb, "target", fv);
                appendKV(sb, "text", fv);
            }
            case "action.run_function" -> appendKV(sb, "function", fv);
            case "action.set_block" -> {
                appendKV(sb, "pos", fv);
                appendKV(sb, "block", fv);
            }
            case "action.give_item" -> {
                appendKV(sb, "target", fv);
                appendKV(sb, "item", fv);
                appendKV(sb, "count", fv);
            }
            case "action.summon" -> {
                appendKV(sb, "entity", fv);
                appendKV(sb, "pos", fv);
            }
            case "raw_text" -> appendKV(sb, "text", fv);
            case "condition.score_compare" -> {
                appendKV(sb, "objective", fv);
                appendKV(sb, "target", fv);
                appendKV(sb, "op", fv);
                appendKV(sb, "value", fv);
            }
            case "condition.entity_exists" -> appendKV(sb, "entity_type", fv);
            default -> sb.append(fv);
        }
        return sb.toString();
    }

    private static void appendKV(StringBuilder sb, String key, Map<String, Object> fv) {
        Object v = fv.get(key);
        sb.append(key).append('=').append(v == null ? "" : v.toString()).append('|');
    }

    /** 从 @e[type=...] 选择器提取实体类型。 */
    private static String extractEntityType(String selector) {
        int idx = selector.indexOf("type=");
        if (idx < 0) {
            return null;
        }
        int start = idx + "type=".length();
        int end = start;
        while (end < selector.length() && selector.charAt(end) != ',' && selector.charAt(end) != ']') {
            end++;
        }
        return selector.substring(start, end);
    }

    /** 解析时操作符映射：>= -> ≥, <= -> ≤（与编译器 mapOp 互逆）。 */
    private static String mapOpParse(String op) {
        return switch (op) {
            case ">=" -> "\u2265";
            case "<=" -> "\u2264";
            default -> op;
        };
    }

    private static String nextId(AtomicInteger counter) {
        return "blk_" + counter.incrementAndGet();
    }

    private static Map<String, Object> fields(String... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    /** 解析过程上下文。 */
    private static final class ParseContext {
        final EditorState state;
        final boolean merge;
        final AtomicInteger counter;
        final Set<String> usedExistingIds = new HashSet<>();
        int layoutCol = 0;

        ParseContext(EditorState state, boolean merge, int startCounter) {
            this.state = state;
            this.merge = merge;
            this.counter = new AtomicInteger(startCounter);
        }
    }
}
