package com.dpe.server;

import com.dpe.common.protocol.EditOpMessage;
import com.dpe.common.protocol.ErrorMessage;
import com.dpe.common.protocol.KeepAliveMessage;
import com.dpe.common.protocol.Message;
import com.dpe.common.protocol.SaveApplyMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * dpe:msg 通道监听器：解析客户端消息并分发到 {@link EditorSessionManager}。
 *
 * <p>处理 EditOpMessage（应用操作 + 广播同步）、SaveApplyMessage（编译保存 + 重载）、
 * KeepAliveMessage（忽略）；非 Player 来源忽略。</p>
 */
public final class DpePluginMessageListener implements PluginMessageListener {

    private final JavaPlugin plugin;
    private final EditorSessionManager manager;

    public DpePluginMessageListener(JavaPlugin plugin, EditorSessionManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!"dpe:msg".equals(channel) || player == null) {
            return;
        }
        Message msg;
        try {
            msg = DpeWire.decode(message);
        } catch (Exception e) {
            plugin.getLogger().warning("解析 dpe:msg 消息失败 (来自 " + player.getName() + "): " + e.getMessage());
            return;
        }
        if (msg instanceof KeepAliveMessage) {
            return; // 心跳忽略
        }
        if (msg instanceof EditOpMessage edit) {
            // 发送者必须已加入某会话
            if (manager.sessionOf(player) == null) {
                return;
            }
            EditorSession session = manager.applyEdit(edit);
            if (session != null) {
                // 广播给该 namespace 所有编辑者（含发送者，确认同步）
                manager.broadcastSync(plugin, session.namespace());
            }
        } else if (msg instanceof SaveApplyMessage save) {
            handleSaveApply(player, save);
        }
        // OpenEditorMessage / SyncStateMessage / ErrorMessage 来自服务端，客户端->服务端不应发送，忽略
    }

    /** 处理保存应用：编译 -> 写文件 -> reloadData；回 ErrorMessage 或聊天成功提示。 */
    private void handleSaveApply(Player player, SaveApplyMessage save) {
        String ns = save.datapackNamespace();
        if (ns == null || ns.isBlank()) {
            EditorSession session = manager.sessionOf(player);
            ns = session == null ? "dpe" : session.namespace();
        }
        EditorSessionManager.CompileSaveResult result = manager.compileAndSave(plugin, ns);
        if (result.success()) {
            player.sendMessage(Component.text("数据包保存并重载成功: " + result.message(), NamedTextColor.GREEN));
            // 同步最新状态给该 namespace 的所有编辑者
            manager.broadcastSync(plugin, ns);
        } else {
            sendError(player, "compile_failed", result.message());
        }
    }

    /** 发送 ErrorMessage（mod 通道）+ 聊天降级提示。 */
    private void sendError(Player player, String code, String message) {
        try {
            player.sendPluginMessage(plugin, "dpe:msg", DpeWire.encode(new ErrorMessage(code, message)));
        } catch (Exception ignored) {
            // 客户端未注册通道等情况忽略
        }
        player.sendMessage(Component.text("[DPE 错误] " + code + ": " + message, NamedTextColor.RED));
    }
}
