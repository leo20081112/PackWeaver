package com.dpe.server;

import com.dpe.common.block.EditorBlock;
import com.dpe.common.block.EditorState;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 单个数据包命名空间的编辑会话：可变 {@link EditorState} + 修订号 + 在线编辑者集合。
 */
public final class EditorSession {

    private final String namespace;
    private final EditorState state;
    private long revision = 0L;
    private final Set<UUID> editors = new HashSet<>();

    public EditorSession(String namespace) {
        this.namespace = namespace;
        this.state = new EditorState(namespace);
        // 预置示例 event.tick 根块便于演示
        this.state.addBlock(new EditorBlock("root", "event.tick", 0.0, 0.0));
    }

    public String namespace() {
        return namespace;
    }

    public EditorState state() {
        return state;
    }

    public long revision() {
        return revision;
    }

    public Set<UUID> editors() {
        return editors;
    }

    /** 递增修订号并返回新值。 */
    public long bumpRevision() {
        return ++revision;
    }

    /**
     * 用给定 state 的内容替换当前会话状态（保留 state 对象引用，
     * 命名空间保持为当前会话的命名空间）。用于模板加载。
     */
    public void replaceState(EditorState from) {
        if (from == null) {
            return;
        }
        // 清空现有块
        java.util.List<String> ids = new ArrayList<>();
        for (EditorBlock b : state.getBlocks()) {
            ids.add(b.id());
        }
        for (String id : ids) {
            state.removeBlock(id);
        }
        // 复制 preset 的块（深拷贝，避免与模板共享可变集合）
        for (EditorBlock b : from.getBlocks()) {
            state.addBlock(b.copy());
        }
        state.setZoom(from.getZoom());
        state.setPan(from.getPanX(), from.getPanY());
        // 保留会话自身命名空间
        state.setActiveDatapackNamespace(namespace);
    }
}
