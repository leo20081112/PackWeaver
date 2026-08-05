package com.dpe.server;

import com.dpe.common.block.EditorBlock;
import com.dpe.common.block.EditorState;

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
}
