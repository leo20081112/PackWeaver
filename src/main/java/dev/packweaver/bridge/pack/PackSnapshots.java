package dev.packweaver.bridge.pack;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 版本控制（规划书第 7 章的轻量实现）：
 * 快照 = 项目目录整体打 zip 存入 config/packweaver/backups/<ns>/。
 * 支持保存 / 列表 / 恢复 / 删除，恢复后自动热重载。
 */
public final class PackSnapshots {

    private static Path backupsDir(String ns) {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir()
                .resolve("packweaver").resolve("backups").resolve(ns);
    }

    /** 保存快照，返回快照 id。 */
    public static String save(PackProject project) throws IOException {
        Path dir = PackProject.projectDir(project.namespace);
        if (dir == null || !Files.isDirectory(dir)) {
            throw new IOException("项目目录不存在");
        }
        String id = String.valueOf(System.currentTimeMillis());
        Files.createDirectories(backupsDir(project.namespace));
        Path zip = backupsDir(project.namespace).resolve(id + ".zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip));
             Stream<Path> walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile).forEach(f -> {
                try {
                    String rel = dir.relativize(f).toString().replace('\\', '/');
                    out.putNextEntry(new ZipEntry(rel));
                    out.write(Files.readAllBytes(f));
                    out.closeEntry();
                } catch (IOException ignored) {
                }
            });
        }
        return id;
    }

    public record Snapshot(String id, String time, long bytes) {
    }

    public static List<Snapshot> list(String ns) throws IOException {
        Path dir = backupsDir(ns);
        List<Snapshot> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (var stream = Files.newDirectoryStream(dir, "*.zip")) {
            for (Path p : stream) {
                String id = p.getFileName().toString().replace(".zip", "");
                long t;
                try {
                    t = Long.parseLong(id);
                } catch (NumberFormatException e) {
                    continue;
                }
                out.add(new Snapshot(id, new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                        .format(new java.util.Date(t)), Files.size(p)));
            }
        }
        out.sort(Comparator.comparing(Snapshot::id).reversed());
        return out;
    }

    /** 恢复快照（覆盖项目目录）。 */
    public static void restore(String ns, String id) throws IOException {
        Path zip = backupsDir(ns).resolve(id + ".zip");
        if (!Files.exists(zip)) {
            throw new IOException("快照不存在: " + id);
        }
        Path dir = PackProject.projectDir(ns);
        if (dir == null) {
            throw new IOException("未进入世界");
        }
        // 清空现目录
        if (Files.exists(dir)) {
            try (var walk = Files.walk(dir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try {
                        Files.delete(p);
                    } catch (IOException ignored) {
                    }
                });
            }
        }
        Files.createDirectories(dir);
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                Path target = dir.resolve(entry.getName()).normalize();
                if (!target.startsWith(dir)) {
                    continue; // 防路径穿越
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }
                Files.createDirectories(target.getParent());
                try (OutputStream out = Files.newOutputStream(target)) {
                    in.transferTo(out);
                }
            }
        }
    }

    public static boolean delete(String ns, String id) throws IOException {
        return Files.deleteIfExists(backupsDir(ns).resolve(id + ".zip"));
    }

    private PackSnapshots() {
    }
}
