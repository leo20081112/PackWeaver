package com.dpe.server;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Paper 服务端插件入口：注册 dpe:msg 插件消息通道与 /datapackeditor (/dpe) 命令。
 */
public final class DatapackEditorPlugin extends JavaPlugin {

    /** 与 mod 端 ClientNetworking 一致的通道名。 */
    public static final String CHANNEL = "dpe:msg";

    private EditorSessionManager sessionManager;
    private DpePluginMessageListener messageListener;

    @Override
    public void onEnable() {
        sessionManager = new EditorSessionManager();
        messageListener = new DpePluginMessageListener(this, sessionManager);

        // 注册插件消息通道（出 + 入）
        getServer().getMessenger().registerOutgoingPluginChannel(this, CHANNEL);
        getServer().getMessenger().registerIncomingPluginChannel(this, CHANNEL, messageListener);

        // 注册命令与 Tab 补全
        DpeCommand dpeCommand = new DpeCommand(this, sessionManager);
        var cmd = getCommand("datapackeditor");
        if (cmd == null) {
            getLogger().warning("未在 plugin.yml 找到 datapackeditor 命令声明，命令不可用。");
        } else {
            cmd.setExecutor(dpeCommand);
            cmd.setTabCompleter(dpeCommand);
        }

        getLogger().info("DatapackEditor 已启用 (通道 " + CHANNEL + ")。");
    }

    @Override
    public void onDisable() {
        // 注销通道（保存状态可在此扩展持久化）
        try {
            if (messageListener != null) {
                getServer().getMessenger().unregisterIncomingPluginChannel(this, CHANNEL, messageListener);
            }
            getServer().getMessenger().unregisterOutgoingPluginChannel(this, CHANNEL);
        } catch (Exception ignored) {
            // 注销失败忽略
        }
        getLogger().info("DatapackEditor 已禁用。");
    }

    public EditorSessionManager sessionManager() {
        return sessionManager;
    }
}
