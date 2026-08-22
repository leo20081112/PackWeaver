package dev.packweaver.bridge.pack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 积木 AST 节点（双模式共享数据源，规划书第 1.3.2 章）。
 * type 对应 BlockDefs 中注册的积木类型；params 为字符串参数。
 */
public class BlockNode {
    public String type;
    public Map<String, String> params = new LinkedHashMap<>();
    public List<BlockNode> children = new ArrayList<>();
    public List<BlockNode> elseChildren = new ArrayList<>();
    public boolean collapsed;

    public BlockNode() {
    }

    public BlockNode(String type, Object... kv) {
        this.type = type;
        for (int i = 0; i + 1 < kv.length; i += 2) {
            params.put(String.valueOf(kv[i]), String.valueOf(kv[i + 1]));
        }
    }

    public String p(String key) {
        return params.getOrDefault(key, "");
    }

    public String p(String key, String def) {
        return params.getOrDefault(key, def);
    }
}
