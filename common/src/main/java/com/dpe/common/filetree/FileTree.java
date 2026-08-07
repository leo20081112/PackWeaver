package com.dpe.common.filetree;

import java.nio.file.Path;
import java.util.List;

/**
 * 文件树快照。root 为 datapacks 目录，datapacks 为顶级数据包节点列表。
 */
public record FileTree(Path root, List<FileNode> datapacks) {

    public FileTree {
        datapacks = datapacks == null ? List.of() : List.copyOf(datapacks);
    }
}
