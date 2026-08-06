package com.dpe.common.parse;

import com.dpe.common.block.EditorBlock;
import com.dpe.common.block.EditorState;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 把原版 .mcfunction / .json 文本解析为积木编辑器状态。
 * say/tellraw/function 识别为对应动作积木；其余行/JSON 文件保留为 raw_text 积木。
 * EditorBlock.schemaId 允许任意字符串，raw_text 不需在注册表中。
 */
public final class TextToBlocksParser {

    private static final String RAW_TEXT_SCHEMA = "raw_text";

    /**
     * 解析多个文件。
     * @param namespace 数据包命名空间
     * @param files     文件路径 -> 内容
     * @return 编辑器状态，activeDatapackNamespace=namespace
     */
    public EditorState parse(String namespace, Map<String, String> files) {
        EditorState state = new EditorState(namespace == null || namespace.isBlank() ? "minecraft" : namespace);
        if (files == null || files.isEmpty()) {
            return state;
        }

        // 单一 event.tick 根，承载所有解析出的积木
        EditorBlock root = new EditorBlock("blk_root", "event.tick", 0, 0);
        state.addBlock(root);

        AtomicInteger counter = new AtomicInteger(0);
        for (Map.Entry<String, String> e : files.entrySet()) {
            String path = e.getKey();
            String content = e.getValue() == null ? "" : e.getValue();
            if (path != null && path.endsWith(".mcfunction")) {
                parseMcFunction(content, state, root, counter);
            } else {
                // JSON 或其它文件：整体作为 raw_text 保留
                if (!content.isBlank()) {
                    String id = nextId(counter);
                    EditorBlock raw = new EditorBlock(id, RAW_TEXT_SCHEMA, 0, 0,
                            fields("text", content, "source", path == null ? "" : path), List.of());
                    state.addBlock(raw);
                    state.connect(root.id(), id);
                }
            }
        }
        return state;
    }

    private void parseMcFunction(String content, EditorState state, EditorBlock root, AtomicInteger counter) {
        String[] lines = content.split("\n", -1);
        for (String raw : lines) {
            String line = raw.stripTrailing();
            if (line.isBlank()) {
                continue;
            }
            String trimmed = line.trim();
            EditorBlock block = parseLine(trimmed, counter);
            if (block != null) {
                state.addBlock(block);
                state.connect(root.id(), block.id());
            }
        }
    }

    private EditorBlock parseLine(String line, AtomicInteger counter) {
        // say <text>
        if (line.startsWith("say ")) {
            String text = line.substring("say ".length());
            return new EditorBlock(nextId(counter), "action.say_text", 0, 0,
                    fields("text", text), List.of());
        }
        if (line.equals("say")) {
            return new EditorBlock(nextId(counter), "action.say_text", 0, 0,
                    fields("text", ""), List.of());
        }
        // tellraw <target> <json>
        if (line.startsWith("tellraw ")) {
            String rest = line.substring("tellraw ".length()).trim();
            int sp = rest.indexOf(' ');
            String target = sp < 0 ? rest : rest.substring(0, sp);
            String text = sp < 0 ? "" : rest.substring(sp + 1).trim();
            return new EditorBlock(nextId(counter), "action.tellraw", 0, 0,
                    fields("target", target, "text", text), List.of());
        }
        // function <ns:path>
        if (line.startsWith("function ")) {
            String fn = line.substring("function ".length()).trim();
            return new EditorBlock(nextId(counter), "action.run_function", 0, 0,
                    fields("function", fn), List.of());
        }
        // 其它命令/注释：raw_text 保留原文
        return new EditorBlock(nextId(counter), RAW_TEXT_SCHEMA, 0, 0,
                fields("text", line), List.of());
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
}
