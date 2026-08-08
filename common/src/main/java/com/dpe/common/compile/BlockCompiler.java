package com.dpe.common.compile;

import com.dpe.common.block.BlockCategory;
import com.dpe.common.block.BlockSchema;
import com.dpe.common.block.BlockSchemaRegistry;
import com.dpe.common.block.EditorBlock;
import com.dpe.common.block.EditorState;
import com.dpe.common.model.ResourceLocation;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 积木块编译器。先校验，再把事件根块树编译为 mcfunction，
 * 把 tag/advancement 动作编译为 JSON 文件。
 */
public final class BlockCompiler {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final BlockValidator validator = new BlockValidator();

    /**
     * 编译编辑器状态。
     * @return CompileResult；校验失败时 success=false 且无产物。
     */
    public CompileResult compile(EditorState state, BlockSchemaRegistry reg) {
        List<ValidationError> errors = validator.validate(state, reg);
        if (!errors.isEmpty()) {
            return CompileResult.failure(errors);
        }
        String ns = state.getActiveDatapackNamespace();
        Map<ResourceLocation, String> mcfunctions = new LinkedHashMap<>();
        Map<ResourceLocation, String> jsonFiles = new LinkedHashMap<>();

        // 收集所有事件根块并编译
        for (EditorBlock b : state.getBlocks()) {
            BlockSchema schema = reg.get(b.schemaId());
            if (schema != null && schema.category() == BlockCategory.EVENT) {
                compileEvent(state, b, reg, ns, mcfunctions, jsonFiles);
            }
        }
        return new CompileResult(true, mcfunctions, jsonFiles, List.of());
    }

    /** 编译一个事件根块为 mcfunction。 */
    private void compileEvent(EditorState state, EditorBlock event, BlockSchemaRegistry reg,
                              String ns, Map<ResourceLocation, String> mcfunctions,
                              Map<ResourceLocation, String> jsonFiles) {
        String path = eventFunctionPath(event.schemaId());
        ResourceLocation fnId = new ResourceLocation(ns, path);
        List<String> lines = new ArrayList<>();
        lines.add("# 由 PackWeaver 编辑器生成 - 事件: " + event.schemaId());

        // 递归编译子块
        for (String childId : event.childIds()) {
            EditorBlock child = state.getById(childId);
            if (child != null) {
                compileNode(state, child, reg, ns, new ArrayList<>(), lines, jsonFiles);
            }
        }
        mcfunctions.put(fnId, String.join("\n", lines) + "\n");
    }

    /**
     * 递归编译一个节点。ifConditions 为已累积的 execute if 子条件（不含 "if " 前缀）。
     * ACTION：输出（可能被 execute if 包装的）命令行；
     * CONDITION：累积条件并递归子块。
     */
    private void compileNode(EditorState state, EditorBlock b, BlockSchemaRegistry reg, String ns,
                             List<String> ifConditions, List<String> lines,
                             Map<ResourceLocation, String> jsonFiles) {
        BlockSchema schema = reg.get(b.schemaId());
        if (schema == null) {
            return;
        }
        if (schema.category() == BlockCategory.ACTION) {
            compileAction(b, schema, reg, ns, ifConditions, lines, jsonFiles);
        } else if (schema.category() == BlockCategory.CONDITION) {
            String cond = buildCondition(b, schema);
            List<String> nested = new ArrayList<>(ifConditions);
            if (cond != null) {
                nested.add(cond);
            }
            for (String childId : b.childIds()) {
                EditorBlock child = state.getById(childId);
                if (child != null) {
                    compileNode(state, child, reg, ns, nested, lines, jsonFiles);
                }
            }
        }
    }

    /** 编译动作块：raw_text 原样输出；tag_add 产出 JSON；其余产出命令行。 */
    private void compileAction(EditorBlock b, BlockSchema schema, BlockSchemaRegistry reg, String ns,
                               List<String> ifConditions, List<String> lines,
                               Map<ResourceLocation, String> jsonFiles) {
        // raw_text：原样输出 text 字段行，不被 execute if 包装（往返无损）
        if ("raw_text".equals(b.schemaId())) {
            String text = strField(b, "text");
            if (text != null) {
                lines.add(text);
            }
            return;
        }
        if ("tag".equals(schema.produces())) {
            compileTagAction(b, jsonFiles);
            return;
        }
        String cmd = buildActionCommand(b, schema, ns);
        if (cmd == null) {
            return;
        }
        if (ifConditions.isEmpty()) {
            lines.add(cmd);
        } else {
            StringBuilder sb = new StringBuilder("execute");
            for (String c : ifConditions) {
                sb.append(" if ").append(c);
            }
            sb.append(" run ").append(cmd);
            lines.add(sb.toString());
        }
    }

    /** 构造动作命令。 */
    private String buildActionCommand(EditorBlock b, BlockSchema schema, String ns) {
        return switch (b.schemaId()) {
            case "action.run_function" -> {
                String fn = strField(b, "function");
                String fnNs = strField(b, "namespace");
                yield "function " + resolveFunctionId(fn, fnNs, ns);
            }
            case "action.say_text" -> "say " + strField(b, "text");
            case "action.set_block" -> "setblock " + strField(b, "pos") + " " + strField(b, "block");
            case "action.give_item" -> {
                String target = strField(b, "target");
                String item = strField(b, "item");
                String count = strField(b, "count");
                yield count == null || count.isBlank()
                        ? "give " + target + " " + item
                        : "give " + target + " " + item + " " + count;
            }
            case "action.summon" -> "summon " + strField(b, "entity") + " " + strField(b, "pos");
            case "action.tellraw" -> "tellraw " + strField(b, "target") + " " + textToJson(strField(b, "text"));
            default -> "# 未知动作: " + b.schemaId();
        };
    }

    /** action.tag_add：产出/合并 tag JSON 文件。 */
    private void compileTagAction(EditorBlock b, Map<ResourceLocation, String> jsonFiles) {
        String tagRaw = strField(b, "tag");
        String entryRaw = strField(b, "entry");
        if (tagRaw == null || entryRaw == null) {
            return;
        }
        ResourceLocation tagId = ResourceLocation.tryParse(tagRaw);
        if (tagId == null) {
            return;
        }
        // 已存在则追加，否则新建
        JsonObject obj;
        JsonArray values;
        if (jsonFiles.containsKey(tagId)) {
            obj = JsonParser.parseString(jsonFiles.get(tagId)).getAsJsonObject();
            values = obj.has("values") ? obj.getAsJsonArray("values") : new JsonArray();
        } else {
            obj = new JsonObject();
            obj.addProperty("replace", false);
            values = new JsonArray();
            obj.add("values", values);
        }
        if (!values.asList().stream().anyMatch(e -> e.getAsString().equals(entryRaw))) {
            values.add(entryRaw);
        }
        obj.add("values", values);
        jsonFiles.put(tagId, GSON.toJson(obj));
    }

    /** 构造条件子表达式（execute if 后面的部分）。 */
    private String buildCondition(EditorBlock b, BlockSchema schema) {
        return switch (b.schemaId()) {
            case "condition.score_compare" -> {
                String objective = strField(b, "objective");
                String target = strField(b, "target");
                String op = mapOp(strField(b, "op"));
                String value = strField(b, "value");
                yield "score " + target + " " + objective + " " + op + " " + value;
            }
            case "condition.entity_exists" -> {
                String et = strField(b, "entity_type");
                yield "entity @e[type=" + et + "]";
            }
            case "condition.random_chance" -> {
                // 用 scoreboard 表达概率门；需外部维护 #rng dpe_internal 在 0..100
                String v = strField(b, "value");
                int pct = toPercent(v);
                yield "score #rng dpe_internal matches .." + pct;
            }
            default -> null;
        };
    }

    /** 事件 schema id -> 函数路径（event.tick -> internal/tick）。 */
    private String eventFunctionPath(String schemaId) {
        String suffix = schemaId.startsWith("event.") ? schemaId.substring("event.".length()) : schemaId;
        return "internal/" + suffix;
    }

    /** 解析 function 字段为完整 id（带 namespace）。 */
    private String resolveFunctionId(String fn, String fnNs, String defaultNs) {
        if (fn == null || fn.isBlank()) {
            return defaultNs + ":unknown";
        }
        if (fn.contains(":")) {
            return fn;
        }
        String useNs = (fnNs != null && !fnNs.isBlank()) ? fnNs : defaultNs;
        return useNs + ":" + fn;
    }

    /** 把 say_text 类纯文本转为 vanilla 文本组件 JSON。 */
    private String textToJson(String text) {
        if (text == null) {
            return "{\"text\":\"\"}";
        }
        String trimmed = text.trim();
        if (trimmed.startsWith("{")) {
            // 已是 JSON，原样返回
            return text;
        }
        JsonObject o = new JsonObject();
        o.addProperty("text", text);
        return GSON.toJson(o);
    }

    /** 取字段字符串值（null 安全）。 */
    private String strField(EditorBlock b, String name) {
        Object v = b.fieldValues().get(name);
        return v == null ? null : v.toString();
    }

    /** 操作符映射：≥ -> >=, ≤ -> <=。 */
    private String mapOp(String op) {
        if (op == null) {
            return "=";
        }
        return switch (op) {
            case "\u2265" -> ">=";
            case "\u2264" -> "<=";
            default -> op;
        };
    }

    /** 0..1 概率值 -> 0..100 整数。 */
    private int toPercent(String v) {
        if (v == null) {
            return 0;
        }
        try {
            double d = Double.parseDouble(v.trim());
            if (d <= 1.0) {
                return (int) Math.round(d * 100);
            }
            return (int) Math.round(d);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
