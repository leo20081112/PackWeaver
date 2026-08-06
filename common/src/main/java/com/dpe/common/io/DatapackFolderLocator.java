package com.dpe.common.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 数据包文件夹定位工具（纯 Java，跨平台）。
 * 给定世界根路径与命名空间，返回 datapacks/&lt;ns&gt; 路径；不存在则创建。
 */
public final class DatapackFolderLocator {

    private DatapackFolderLocator() {
    }

    /**
     * 返回世界根下的 datapacks 目录。
     *
     * @param worldRoot 世界根目录（如 .minecraft/saves/&lt;world&gt; 或服务端 world 目录）
     * @return datapacks 目录 Path；不存在则创建
     */
    public static Path datapacksRoot(Path worldRoot) throws IOException {
        if (worldRoot == null) {
            throw new IllegalArgumentException("worldRoot 不能为空");
        }
        Path root = worldRoot.resolve("datapacks");
        Files.createDirectories(root);
        return root;
    }

    /**
     * 返回 datapacks/&lt;ns&gt; 目录；不存在则创建。
     */
    public static Path namespaceFolder(Path worldRoot, String namespace) throws IOException {
        if (namespace == null || namespace.isBlank()) {
            return datapacksRoot(worldRoot);
        }
        Path folder = datapacksRoot(worldRoot).resolve(namespace);
        Files.createDirectories(folder);
        return folder;
    }
}
