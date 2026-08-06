package com.dpe.client;

import net.minecraft.util.Util;

import java.awt.Desktop;
import java.nio.file.Path;

/**
 * 用系统文件管理器打开数据包文件夹（Task 3）。
 * 优先 java.awt.Desktop，失败回退到 {@link Util#getOperatingSystem()}。
 */
public final class DatapackFolderOpener {

    private DatapackFolderOpener() {
    }

    /** 打开 folder；成功返回 true。 */
    public static boolean open(Path folder) {
        if (folder == null) {
            return false;
        }
        // 1. Desktop.open
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop desktop = Desktop.getDesktop();
                if (desktop.isSupported(Desktop.Action.OPEN)) {
                    desktop.open(folder.toFile());
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // 回退到 Util
        }
        // 2. 回退：Util.getOperatingSystem().open(URI)
        try {
            Util.getOperatingSystem().open(folder.toUri());
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}
