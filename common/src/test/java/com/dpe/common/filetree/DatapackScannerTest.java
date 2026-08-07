package com.dpe.common.filetree;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DatapackScanner 单元测试：目录型与 zip 型数据包扫描。
 */
class DatapackScannerTest {

    @TempDir
    Path tempDir;

    @Test
    void scansDirectoryAndZipDatapacks() throws IOException {
        Path datapacks = Files.createDirectory(tempDir.resolve("datapacks"));
        // 目录型数据包
        Path dirDp = Files.createDirectories(datapacks.resolve("mydp"));
        Files.writeString(dirDp.resolve("pack.mcmeta"),
                "{\"pack\":{\"pack_format\":1,\"description\":\"d\"}}");
        Files.createDirectories(dirDp.resolve("data/ns/functions"));
        Files.createDirectories(dirDp.resolve("data/ns/tags"));
        Files.writeString(dirDp.resolve("data/ns/functions/a.mcfunction"), "say hi");
        Files.writeString(dirDp.resolve("data/ns/tags/b.json"), "{\"values\":[]}");
        // zip 型数据包
        createZipFixture(datapacks.resolve("otherdp.zip"));

        FileTree tree = DatapackScanner.scan(datapacks);

        assertEquals(2, tree.datapacks().size(), "应有 2 个数据包");

        // 目录型
        FileNode dirNode = tree.datapacks().stream()
                .filter(n -> n.name().equals("mydp"))
                .findFirst().orElseThrow(() -> new AssertionError("应有 mydp 数据包"));
        assertTrue(dirNode.isDirectory());
        assertFalse(dirNode.isZipEntry());
        assertNull(dirNode.zipFilePath(), "目录型数据包 zipFilePath 应为 null");

        FileNode aFn = findInTree(dirNode, "data", "ns", "functions", "a.mcfunction");
        assertNotNull(aFn, "应找到 a.mcfunction");
        assertEquals("a.mcfunction", aFn.name());
        assertFalse(aFn.isZipEntry());
        FileNode bTag = findInTree(dirNode, "data", "ns", "tags", "b.json");
        assertNotNull(bTag, "应找到 b.json");
        // data 目录节点
        FileNode dataDir = dirNode.childByName("data");
        assertNotNull(dataDir);
        assertTrue(dataDir.isDirectory());

        // zip 型
        FileNode zipNode = tree.datapacks().stream()
                .filter(n -> n.name().equals("otherdp.zip"))
                .findFirst().orElseThrow(() -> new AssertionError("应有 otherdp.zip 数据包"));
        assertTrue(zipNode.isDirectory(), "zip 容器应为目录节点");
        assertTrue(zipNode.isZipContainer(), "应识别为 zip 容器");

        FileNode zipFn = findInTree(zipNode, "data", "ns", "functions", "a.mcfunction");
        assertNotNull(zipFn, "zip 内应找到 a.mcfunction");
        assertTrue(zipFn.isZipEntry(), "zip 内文件应为 zipEntry");
        assertNotNull(zipFn.zipFilePath(), "zip 条目 zipFilePath 不应为 null");
        assertTrue(zipFn.zipFilePath().endsWith("otherdp.zip"));

        FileNode zipTag = findInTree(zipNode, "data", "ns", "tags", "b.json");
        assertNotNull(zipTag, "zip 内应找到 b.json");
    }

    @Test
    void nonDatapackContentIsIgnored() throws IOException {
        Path datapacks = Files.createDirectory(tempDir.resolve("datapacks"));
        Files.createDirectory(datapacks.resolve("notadp"));
        Files.writeString(datapacks.resolve("random.txt"), "x");

        FileTree tree = DatapackScanner.scan(datapacks);
        assertTrue(tree.datapacks().isEmpty(), "非数据包内容应被忽略");
    }

    @Test
    void missingDirectoryReturnsEmptyTree() {
        FileTree tree = DatapackScanner.scan(tempDir.resolve("does-not-exist"));
        assertNotNull(tree);
        assertTrue(tree.datapacks().isEmpty(), "不存在的目录应返回空树");
    }

    @Test
    void singleBrokenZipDoesNotBreakOthers() throws IOException {
        Path datapacks = Files.createDirectory(tempDir.resolve("datapacks"));
        // 正常目录型数据包
        Path dirDp = Files.createDirectories(datapacks.resolve("good"));
        Files.writeString(dirDp.resolve("pack.mcmeta"), "{}");
        Files.createDirectories(dirDp.resolve("data/ns"));
        Files.writeString(dirDp.resolve("data/ns/a.mcfunction"), "say ok");
        // 损坏 zip（非 zip 内容）
        Files.writeString(datapacks.resolve("broken.zip"), "not a zip");

        FileTree tree = DatapackScanner.scan(datapacks);
        assertEquals(1, tree.datapacks().size(), "损坏 zip 应被跳过，仅保留正常数据包");
        assertEquals("good", tree.datapacks().get(0).name());
    }

    private void createZipFixture(Path zipPath) throws IOException {
        try (FileSystem fs = FileSystems.newFileSystem(zipPath,
                Map.of("create", "true"), (ClassLoader) null)) {
            Files.createDirectories(fs.getPath("/data/ns/functions"));
            Files.createDirectories(fs.getPath("/data/ns/tags"));
            Files.writeString(fs.getPath("/data/ns/functions/a.mcfunction"), "say zip");
            Files.writeString(fs.getPath("/data/ns/tags/b.json"), "{\"values\":[]}");
            Files.writeString(fs.getPath("/pack.mcmeta"), "{\"pack\":{\"pack_format\":1}}");
        }
    }

    /** 按路径段逐层查找子节点。 */
    private FileNode findInTree(FileNode root, String... parts) {
        FileNode cur = root;
        for (String p : parts) {
            cur = cur.childByName(p);
            if (cur == null) {
                return null;
            }
        }
        return cur;
    }
}
