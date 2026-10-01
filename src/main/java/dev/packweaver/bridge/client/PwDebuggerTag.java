package dev.packweaver.bridge.client;

import net.minecraft.client.MinecraftClient;

/**
 * 调试输出标签（规划书 16.6 单人调试）：
 * 携带 pw_debugger 标签的玩家才会收到日志断点/执行轨迹输出。
 */
public final class PwDebuggerTag {

    public static boolean has() {
        var p = MinecraftClient.getInstance().player;
        return p != null && p.getCommandTags().contains("pw_debugger");
    }

    public static void toggle() {
        var p = MinecraftClient.getInstance().player;
        if (p == null) {
            return;
        }
        String cmd = has() ? "tag @s remove pw_debugger" : "tag @s add pw_debugger";
        var server = MinecraftClient.getInstance().getServer();
        if (server != null) {
            server.getCommandManager().executeWithPrefix(
                    server.getCommandSource().withSilent(), cmd);
        }
    }

    private PwDebuggerTag() {
    }
}
