package com.dpe.server;

import com.dpe.common.block.BlockCategory;
import com.dpe.common.block.BlockSchema;
import com.dpe.common.block.BlockSchemaRegistry;
import com.dpe.common.block.EditorBlock;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

import java.util.Collection;

/**
 * 聊天菜单（Adventure {@link Component}）：可点击的命令菜单，
 * 作为未安装 mod 客户端的降级 UI。
 */
final class ChatMenu {

    private ChatMenu() {
    }

    /** 根菜单：列出主要操作。 */
    static Component buildRootMenu(EditorSession session) {
        String ns = session == null ? "dpe" : session.namespace();
        long rev = session == null ? 0L : session.revision();
        Component header = Component.text("=== DPE 编辑器 [" + ns + "] (rev=" + rev + ") ===",
                NamedTextColor.GOLD, TextDecoration.BOLD);
        return Component.empty()
                .append(header).append(Component.newline())
                .append(clickable("[打开 mod 编辑器]", "/dpe " + ns, NamedTextColor.AQUA)).append(Component.newline())
                .append(clickable("[添加积木块]", "/dpe chat", NamedTextColor.GREEN)).append(Component.newline())
                .append(clickable("[列出当前 blocks]", "/dpe list", NamedTextColor.YELLOW)).append(Component.newline())
                .append(clickable("[编译预览]", "/dpe compile", NamedTextColor.LIGHT_PURPLE)).append(Component.newline())
                .append(clickable("🔄 [重载]", "/dpe reload", NamedTextColor.RED)).append(Component.newline())
                .append(clickable("📘 [手册]", "/dpe wiki", NamedTextColor.BLUE)).append(Component.newline())
                .append(clickable("📋 [模板]", "/dpe template list", NamedTextColor.DARK_AQUA)).append(Component.newline())
                .append(clickable("📂 [文件夹]", "/dpe folder", NamedTextColor.DARK_GREEN)).append(Component.newline())
                .append(clickable("[帮助]", "/dpe help", NamedTextColor.GRAY));
    }

    /** 添加菜单：按 BlockSchemaRegistry.all() 列出可添加的块（按 category 分组）。 */
    static Component buildAddMenu() {
        Component root = Component.empty()
                .append(Component.text("=== 添加积木块 ===", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.newline());
        for (BlockCategory cat : BlockCategory.values()) {
            root = root.append(Component.text("[" + cat.name() + "]", NamedTextColor.DARK_AQUA))
                    .append(Component.newline());
            for (BlockSchema schema : BlockSchemaRegistry.DEFAULT.byCategory(cat)) {
                root = root.append(clickable("  " + schema.label() + " (" + schema.id() + ")",
                        "/dpe add " + schema.id(), NamedTextColor.GREEN)).append(Component.newline());
            }
        }
        root = root.append(clickable("[返回]", "/dpe chat", NamedTextColor.GRAY));
        return root;
    }

    /** 块列表：列出当前 state 的所有 block（id/schemaId）。 */
    static Component buildBlockList(EditorSession session) {
        Collection<EditorBlock> blocks = session == null ? java.util.List.of() : session.state().getBlocks();
        long rev = session == null ? 0L : session.revision();
        Component root = Component.empty()
                .append(Component.text("=== 当前 blocks (rev=" + rev + ") ===",
                        NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.newline());
        if (blocks.isEmpty()) {
            root = root.append(Component.text("(空)", NamedTextColor.GRAY)).append(Component.newline());
        } else {
            for (EditorBlock b : blocks) {
                root = root.append(Component.text("- " + b.id() + " [" + b.schemaId() + "]",
                                NamedTextColor.YELLOW))
                        .append(Component.newline());
            }
        }
        root = root.append(clickable("[返回]", "/dpe chat", NamedTextColor.GRAY));
        return root;
    }

    /** 构造可点击文本（run_command）。 */
    private static Component clickable(String label, String command, NamedTextColor color) {
        return Component.text(label, color).clickEvent(ClickEvent.runCommand(command));
    }
}
