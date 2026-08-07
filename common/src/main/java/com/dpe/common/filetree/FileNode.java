package com.dpe.common.filetree;

import java.util.ArrayList;
import java.util.List;

/**
 * 文件树节点。普通文件/目录 zipFilePath=null；zip 内条目 zipFilePath=所属 zip 绝对路径。
 * children 可变，便于增删文件时同步树结构。
 */
public record FileNode(String name,
                       String path,
                       boolean directory,
                       boolean zipEntry,
                       String zipFilePath,
                       List<FileNode> children) {

    public FileNode {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name 不能为空");
        }
        if (path == null) {
            path = "";
        }
        children = children == null ? new ArrayList<>() : new ArrayList<>(children);
    }

    /** 叶子节点便捷构造。 */
    public FileNode(String name, String path, boolean directory, boolean zipEntry, String zipFilePath) {
        this(name, path, directory, zipEntry, zipFilePath, List.of());
    }

    public boolean isFile() {
        return !directory;
    }

    public boolean isDirectory() {
        return directory;
    }

    public boolean isZipEntry() {
        return zipEntry;
    }

    /** 是否位于 zip 内（zip 条目）。 */
    public boolean isInZip() {
        return zipFilePath != null;
    }

    /** 是否为 zip 数据包容器节点（.zip 文件本身，非条目）。 */
    public boolean isZipContainer() {
        return directory && !zipEntry && zipFilePath == null
                && name.toLowerCase().endsWith(".zip");
    }

    /** 按名称查找直接子节点。 */
    public FileNode childByName(String childName) {
        if (childName == null) {
            return null;
        }
        for (FileNode c : children) {
            if (childName.equals(c.name())) {
                return c;
            }
        }
        return null;
    }

    /** 递归查找路径匹配的后代节点。 */
    public FileNode findByPath(String targetPath) {
        if (targetPath == null) {
            return null;
        }
        if (targetPath.equals(path)) {
            return this;
        }
        for (FileNode c : children) {
            FileNode found = c.findByPath(targetPath);
            if (found != null) {
                return found;
            }
        }
        return null;
    }
}
