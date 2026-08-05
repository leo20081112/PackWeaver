package com.dpe.common.editor;

import com.dpe.common.model.Datapack;
import com.dpe.common.model.McFunction;
import com.dpe.common.model.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DatapackExporter 单元测试。
 */
class DatapackExporterTest {

    @Test
    void exportToZipContainsPackMcmetaAndFunction() throws IOException {
        McFunction fn = new McFunction(new ResourceLocation("mydp", "tick"),
                List.of("say hello", "give @p stone"));
        Datapack dp = new Datapack("mydp", List.of(fn), List.of(), List.of(), List.of());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DatapackExporter.exportToZip(dp, baos);
        byte[] zipBytes = baos.toByteArray();

        boolean foundMcmeta = false;
        boolean foundFunction = false;
        boolean foundPackFormat = false;
        String functionContent = null;

        try (ZipInputStream zis = new ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                byte[] data = zis.readAllBytes();
                if (name.equals("pack.mcmeta")) {
                    foundMcmeta = true;
                    String content = new String(data, StandardCharsets.UTF_8);
                    if (content.contains("\"pack_format\": 48")) {
                        foundPackFormat = true;
                    }
                }
                if (name.equals("data/mydp/functions/tick.mcfunction")) {
                    foundFunction = true;
                    functionContent = new String(data, StandardCharsets.UTF_8);
                }
            }
        }

        assertTrue(foundMcmeta, "zip 应包含 pack.mcmeta");
        assertTrue(foundPackFormat, "pack.mcmeta 应含 pack_format 48");
        assertTrue(foundFunction, "zip 应包含函数文件 data/mydp/functions/tick.mcfunction");
        assertNotNull(functionContent);
        assertTrue(functionContent.contains("say hello"), "函数文件内容应含命令");
    }

    @Test
    void exportToDirWritesFiles(@TempDir Path tempDir) throws IOException {
        McFunction fn = new McFunction(new ResourceLocation("mydp", "helper/run"),
                List.of("say hi"));
        Datapack dp = new Datapack("mydp", List.of(fn), List.of(), List.of(), List.of());

        DatapackExporter.exportToDir(dp, tempDir);

        assertTrue(java.nio.file.Files.exists(tempDir.resolve("pack.mcmeta")));
        Path funcFile = tempDir.resolve("data/mydp/functions/helper/run.mcfunction");
        assertTrue(java.nio.file.Files.exists(funcFile));
        String content = java.nio.file.Files.readString(funcFile);
        assertTrue(content.contains("say hi"));
    }

    @Test
    void roundTripDirExportAndRead(@TempDir Path tempDir) throws IOException {
        McFunction fn = new McFunction(new ResourceLocation("rt", "main"),
                List.of("say roundtrip"));
        Datapack dp = new Datapack("rt", List.of(fn), List.of(), List.of(), List.of());

        DatapackExporter.exportToDir(dp, tempDir);
        Datapack read = DatapackReader.readFromDir(tempDir);

        assertEquals(1, read.functions().size());
        assertEquals("rt:main", read.functions().get(0).id().toString());
        assertTrue(read.functions().get(0).commands().contains("say roundtrip"));
    }
}
