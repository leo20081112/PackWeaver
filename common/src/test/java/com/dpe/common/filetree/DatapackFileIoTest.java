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
 * DatapackFileIo 单元测试：read/write/create/delete/rename，覆盖普通文件与 zip 内条目。
 */
class DatapackFileIoTest {

    @TempDir
    Path tempDir;

    @Test
    void readWriteRoundTripDirectoryDatapack() throws IOException {
        Path datapacks = Files.createDirectory(tempDir.resolve("datapacks"));
        Path dp = Files.createDirectories(datapacks.resolve("mydp"));
        Files.writeString(dp.resolve("pack.mcmeta"), "{}");
        Files.createDirectories(dp.resolve("data/ns/functions"));
        Files.writeString(dp.resolve("data/ns/functions/a.mcfunction"), "say old");

        FileTree tree = DatapackScanner.scan(datapacks);
        FileNode dpNode = tree.datapacks().get(0);
        FileNode fn = findInTree(dpNode, "data", "ns", "functions", "a.mcfunction");
        assertNotNull(fn);

        assertEquals("say old", DatapackFileIo.read(datapacks, fn));
        DatapackFileIo.write(datapacks, fn, "say new");
        assertEquals("say new", DatapackFileIo.read(datapacks, fn));
        assertEquals("say new", Files.readString(dp.resolve("data/ns/functions/a.mcfunction")));
    }

    @Test
    void readWriteRoundTripZipDatapack() throws IOException {
        Path datapacks = Files.createDirectory(tempDir.resolve("datapacks"));
        createZipFixture(datapacks.resolve("otherdp.zip"));

        FileTree tree = DatapackScanner.scan(datapacks);
        FileNode zipNode = tree.datapacks().get(0);
        FileNode fn = findInTree(zipNode, "data", "ns", "functions", "a.mcfunction");
        assertNotNull(fn);
        assertTrue(fn.isZipEntry());

        assertEquals("say zip", DatapackFileIo.read(datapacks, fn));
        DatapackFileIo.write(datapacks, fn, "say updated");
        assertEquals("say updated", DatapackFileIo.read(datapacks, fn));

        // 重新扫描确认已持久化
        FileTree tree2 = DatapackScanner.scan(datapacks);
        FileNode fn2 = findInTree(tree2.datapacks().get(0), "data", "ns", "functions", "a.mcfunction");
        assertNotNull(fn2);
        assertEquals("say updated", DatapackFileIo.read(datapacks, fn2));
    }

    @Test
    void createFileAndDeleteInDirectoryDatapack() throws IOException {
        Path datapacks = Files.createDirectory(tempDir.resolve("datapacks"));
        Path dp = Files.createDirectories(datapacks.resolve("mydp"));
        Files.writeString(dp.resolve("pack.mcmeta"), "{}");
        Files.createDirectories(dp.resolve("data/ns/functions"));

        FileTree tree = DatapackScanner.scan(datapacks);
        FileNode dpNode = tree.datapacks().get(0);
        FileNode funcsDir = findInTree(dpNode, "data", "ns", "functions");
        assertNotNull(funcsDir);

        FileNode created = DatapackFileIo.createFile(datapacks, funcsDir, "new.mcfunction");
        assertNotNull(created);
        assertEquals("new.mcfunction", created.name());
        assertFalse(created.isDirectory());
        assertTrue(Files.exists(dp.resolve("data/ns/functions/new.mcfunction")),
                "文件应已创建");
        assertTrue(funcsDir.children().contains(created), "应加入父节点 children");

        DatapackFileIo.write(datapacks, created, "say created");
        assertEquals("say created", DatapackFileIo.read(datapacks, created));

        DatapackFileIo.delete(datapacks, created);
        assertFalse(Files.exists(dp.resolve("data/ns/functions/new.mcfunction")),
                "文件应已删除");
    }

    @Test
    void createFileAndDeleteInZipDatapack() throws IOException {
        Path datapacks = Files.createDirectory(tempDir.resolve("datapacks"));
        createZipFixture(datapacks.resolve("otherdp.zip"));

        FileTree tree = DatapackScanner.scan(datapacks);
        FileNode zipNode = tree.datapacks().get(0);
        FileNode funcsDir = findInTree(zipNode, "data", "ns", "functions");
        assertNotNull(funcsDir);

        FileNode created = DatapackFileIo.createFile(datapacks, funcsDir, "new.mcfunction");
        assertNotNull(created);
        assertTrue(created.isZipEntry(), "新建 zip 内文件应为 zipEntry");
        assertNotNull(created.zipFilePath());

        DatapackFileIo.write(datapacks, created, "say znew");
        assertEquals("say znew", DatapackFileIo.read(datapacks, created));

        DatapackFileIo.delete(datapacks, created);
        // 重新扫描确认删除
        FileTree tree2 = DatapackScanner.scan(datapacks);
        FileNode gone = findInTree(tree2.datapacks().get(0), "data", "ns", "functions", "new.mcfunction");
        assertNull(gone, "zip 内文件应已删除");
    }

    @Test
    void createDirectoryInDatapack() throws IOException {
        Path datapacks = Files.createDirectory(tempDir.resolve("datapacks"));
        Path dp = Files.createDirectories(datapacks.resolve("mydp"));
        Files.writeString(dp.resolve("pack.mcmeta"), "{}");
        Files.createDirectories(dp.resolve("data"));

        FileTree tree = DatapackScanner.scan(datapacks);
        FileNode dpNode = tree.datapacks().get(0);
        FileNode dataDir = findInTree(dpNode, "data");
        assertNotNull(dataDir);

        FileNode newDir = DatapackFileIo.createDirectory(datapacks, dataDir, "my_ns");
        assertTrue(newDir.isDirectory());
        assertEquals("my_ns", newDir.name());
        assertTrue(Files.isDirectory(dp.resolve("data/my_ns")), "目录应已创建");
        assertTrue(dataDir.children().contains(newDir));
    }

    @Test
    void renameFileInDatapack() throws IOException {
        Path datapacks = Files.createDirectory(tempDir.resolve("datapacks"));
        Path dp = Files.createDirectories(datapacks.resolve("mydp"));
        Files.writeString(dp.resolve("pack.mcmeta"), "{}");
        Files.createDirectories(dp.resolve("data/ns/functions"));
        Files.writeString(dp.resolve("data/ns/functions/a.mcfunction"), "say hi");

        FileTree tree = DatapackScanner.scan(datapacks);
        FileNode dpNode = tree.datapacks().get(0);
        FileNode fn = findInTree(dpNode, "data", "ns", "functions", "a.mcfunction");
        assertNotNull(fn);

        DatapackFileIo.rename(datapacks, fn, "renamed.mcfunction");
        assertFalse(Files.exists(dp.resolve("data/ns/functions/a.mcfunction")),
                "原文件应已重命名");
        assertTrue(Files.exists(dp.resolve("data/ns/functions/renamed.mcfunction")),
                "新文件应存在");
        assertEquals("say hi", Files.readString(dp.resolve("data/ns/functions/renamed.mcfunction")),
                "内容应保留");
    }

    @Test
    void writeCreatesParentDirectories() throws IOException {
        Path datapacks = Files.createDirectory(tempDir.resolve("datapacks"));
        Path dp = Files.createDirectories(datapacks.resolve("mydp"));
        Files.writeString(dp.resolve("pack.mcmeta"), "{}");

        FileTree tree = DatapackScanner.scan(datapacks);
        FileNode dpNode = tree.datapacks().get(0);
        FileNode dataDir = findInTree(dpNode, "data");
        if (dataDir == null) {
            dataDir = DatapackFileIo.createDirectory(datapacks, dpNode, "data");
        }
        FileNode nsDir = DatapackFileIo.createDirectory(datapacks, dataDir, "ns");
        FileNode fnsDir = DatapackFileIo.createDirectory(datapacks, nsDir, "functions");
        FileNode fn = DatapackFileIo.createFile(datapacks, fnsDir, "fresh.mcfunction");

        DatapackFileIo.write(datapacks, fn, "say fresh");
        assertEquals("say fresh", Files.readString(dp.resolve("data/ns/functions/fresh.mcfunction")));
    }

    private void createZipFixture(Path zipPath) throws IOException {
        try (FileSystem fs = FileSystems.newFileSystem(zipPath,
                Map.of("create", "true"), (ClassLoader) null)) {
            Files.createDirectories(fs.getPath("/data/ns/functions"));
            Files.createDirectories(fs.getPath("/data/ns/tags"));
            Files.writeString(fs.getPath("/data/ns/functions/a.mcfunction"), "say zip");
            Files.writeString(fs.getPath("/data/ns/tags/b.json"), "{\"values\":[]}");
            Files.writeString(fs.getPath("/pack.mcmeta"), "{}");
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
