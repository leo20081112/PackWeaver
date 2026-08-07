package com.dpe.common.filetree;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

/**
 * 数据包文件读写工具。支持普通文件与 zip 内条目。
 * zip 操作均用 try-with-resources，异常以 IOException 抛出。
 */
public final class DatapackFileIo {

    private DatapackFileIo() {
    }

    /** 读取文件内容；普通文件用 Files.readString，zip 内用 FileSystem 读取。 */
    public static String read(Path datapacksDir, FileNode node) throws IOException {
        requireNode(node);
        if (node.directory()) {
            throw new IOException("无法读取目录: " + node.path());
        }
        if (node.zipFilePath() != null) {
            try (FileSystem fs = openZip(node.zipFilePath())) {
                Path entry = fs.getPath("/" + node.path());
                return Files.readString(entry);
            }
        }
        return Files.readString(datapacksDir.resolve(node.path()));
    }

    /** 写入文件内容；普通文件用 Files.writeString，zip 内用 FileSystem 写回。 */
    public static void write(Path datapacksDir, FileNode node, String content) throws IOException {
        requireNode(node);
        if (node.directory()) {
            throw new IOException("无法写入目录: " + node.path());
        }
        String data = content == null ? "" : content;
        if (node.zipFilePath() != null) {
            try (FileSystem fs = openZip(node.zipFilePath())) {
                Path entry = fs.getPath("/" + node.path());
                if (entry.getParent() != null) {
                    Files.createDirectories(entry.getParent());
                }
                Files.writeString(entry, data);
            }
        } else {
            Path file = datapacksDir.resolve(node.path());
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, data);
        }
    }

    /** 在父目录下新建文件，返回新 FileNode（并加入父节点 children）。 */
    public static FileNode createFile(Path datapacksDir, FileNode parentDir, String name) throws IOException {
        return createNode(datapacksDir, parentDir, name, false);
    }

    /** 在父目录下新建目录，返回新 FileNode（并加入父节点 children）。 */
    public static FileNode createDirectory(Path datapacksDir, FileNode parentDir, String name) throws IOException {
        return createNode(datapacksDir, parentDir, name, true);
    }

    private static FileNode createNode(Path datapacksDir, FileNode parentDir, String name, boolean directory) throws IOException {
        requireNode(parentDir);
        if (!parentDir.directory()) {
            throw new IOException("父节点不是目录: " + parentDir.path());
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name 不能为空");
        }
        FileNode created;
        if (parentDir.zipFilePath() != null) {
            // zip 内目录条目下新建
            created = createZipNode(parentDir.zipFilePath(), parentDir.path(), name, directory);
        } else if (parentDir.isZipContainer()) {
            // zip 容器根下新建
            String zipAbs = datapacksDir.resolve(parentDir.path()).toAbsolutePath().toString();
            created = createZipNode(zipAbs, "", name, directory);
        } else {
            // 普通目录
            Path parent = datapacksDir.resolve(parentDir.path());
            Files.createDirectories(parent);
            Path target = parent.resolve(name);
            if (directory) {
                Files.createDirectory(target);
            } else {
                Files.createFile(target);
            }
            String rel = datapacksDir.relativize(target).toString();
            created = new FileNode(name, rel, directory, false, null, new ArrayList<>());
        }
        parentDir.children().add(created);
        return created;
    }

    private static FileNode createZipNode(String zipAbsPath, String parentEntryPath, String name, boolean directory) throws IOException {
        try (FileSystem fs = openZip(zipAbsPath)) {
            String entryPath = parentEntryPath == null || parentEntryPath.isEmpty()
                    ? name : parentEntryPath + "/" + name;
            Path entry = fs.getPath("/" + entryPath);
            if (directory) {
                Files.createDirectories(entry);
            } else {
                if (entry.getParent() != null) {
                    Files.createDirectories(entry.getParent());
                }
                Files.createFile(entry);
            }
            return new FileNode(name, entryPath, directory, true, zipAbsPath, new ArrayList<>());
        }
    }

    /** 删除文件或空目录；zip 内删除条目。 */
    public static void delete(Path datapacksDir, FileNode node) throws IOException {
        requireNode(node);
        if (node.zipFilePath() != null) {
            try (FileSystem fs = openZip(node.zipFilePath())) {
                Path entry = fs.getPath("/" + node.path());
                Files.deleteIfExists(entry);
            }
        } else {
            Path target = datapacksDir.resolve(node.path());
            Files.deleteIfExists(target);
        }
    }

    /** 重命名文件或目录；zip 内重命名条目。 */
    public static void rename(Path datapacksDir, FileNode node, String newName) throws IOException {
        requireNode(node);
        if (newName == null || newName.isBlank()) {
            throw new IllegalArgumentException("newName 不能为空");
        }
        if (node.zipFilePath() != null) {
            try (FileSystem fs = openZip(node.zipFilePath())) {
                Path entry = fs.getPath("/" + node.path());
                Path parent = entry.getParent();
                Path target = parent == null ? fs.getPath("/" + newName) : parent.resolve(newName);
                Files.move(entry, target);
            }
        } else {
            Path source = datapacksDir.resolve(node.path());
            Path parent = source.getParent();
            Path target = parent == null ? datapacksDir.resolve(newName) : parent.resolve(newName);
            Files.move(source, target);
        }
    }

    private static FileSystem openZip(String zipAbsPath) throws IOException {
        return FileSystems.newFileSystem(Paths.get(zipAbsPath), (ClassLoader) null);
    }

    private static void requireNode(FileNode node) {
        if (node == null) {
            throw new IllegalArgumentException("node 不能为空");
        }
    }
}
