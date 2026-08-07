package com.dpe.common.filetree;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 扫描 datapacks 目录，构建文件树。
 * 支持目录型数据包（含 pack.mcmeta）与 zip 型数据包（.zip）。
 * 单个数据包解析失败不影响其它。
 */
public final class DatapackScanner {

    private DatapackScanner() {
    }

    /** 扫描 datapacks 目录，返回文件树。 */
    public static FileTree scan(Path datapacksDir) {
        List<FileNode> datapacks = new ArrayList<>();
        if (datapacksDir == null || !Files.isDirectory(datapacksDir)) {
            return new FileTree(datapacksDir, datapacks);
        }
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(datapacksDir)) {
            for (Path entry : ds) {
                try {
                    if (Files.isDirectory(entry) && Files.isRegularFile(entry.resolve("pack.mcmeta"))) {
                        datapacks.add(scanDirectoryDatapack(entry, datapacksDir));
                    } else if (Files.isRegularFile(entry) && isZipFileName(entry)) {
                        datapacks.add(scanZipDatapack(entry, datapacksDir));
                    }
                    // 其余内容忽略
                } catch (Exception e) {
                    // 单个数据包解析失败不影响其它
                    System.err.println("[DatapackScanner] 跳过数据包 " + entry + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("[DatapackScanner] 遍历目录失败 " + datapacksDir + ": " + e.getMessage());
        }
        return new FileTree(datapacksDir, datapacks);
    }

    private static boolean isZipFileName(Path entry) {
        String name = entry.getFileName().toString().toLowerCase();
        return name.endsWith(".zip");
    }

    /** 目录型数据包：递归列出全部子项。 */
    private static FileNode scanDirectoryDatapack(Path dpDir, Path root) throws IOException {
        String rel = root.relativize(dpDir).toString();
        List<FileNode> children = buildDirChildren(dpDir, root);
        return new FileNode(dpDir.getFileName().toString(), rel, true, false, null, children);
    }

    private static List<FileNode> buildDirChildren(Path dir, Path root) throws IOException {
        List<FileNode> children = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path entry : ds) {
                String name = entry.getFileName().toString();
                String rel = root.relativize(entry).toString();
                boolean isDir = Files.isDirectory(entry);
                List<FileNode> kids = isDir ? buildDirChildren(entry, root) : List.of();
                children.add(new FileNode(name, rel, isDir, false, null, kids));
            }
        }
        return children;
    }

    /** zip 型数据包：打开 zip FileSystem，遍历全部条目，关闭后仅保留路径字符串。 */
    private static FileNode scanZipDatapack(Path zipFile, Path root) throws IOException {
        String rel = root.relativize(zipFile).toString();
        String zipAbs = zipFile.toAbsolutePath().toString();
        List<FileNode> children;
        try (FileSystem fs = FileSystems.newFileSystem(zipFile, (ClassLoader) null)) {
            Path zipRoot = fs.getPath("/");
            children = buildZipChildren(zipRoot, zipAbs);
        }
        return new FileNode(zipFile.getFileName().toString(), rel, true, false, null, children);
    }

    private static List<FileNode> buildZipChildren(Path dir, String zipAbsPath) throws IOException {
        List<FileNode> children = new ArrayList<>();
        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir)) {
            for (Path entry : ds) {
                String name = entry.getFileName().toString();
                String entryPath = stripLeadingSlash(entry.toString());
                boolean isDir = Files.isDirectory(entry);
                List<FileNode> kids = isDir ? buildZipChildren(entry, zipAbsPath) : List.of();
                children.add(new FileNode(name, entryPath, isDir, true, zipAbsPath, kids));
            }
        }
        return children;
    }

    private static String stripLeadingSlash(String p) {
        while (p.startsWith("/")) {
            p = p.substring(1);
        }
        return p;
    }
}
