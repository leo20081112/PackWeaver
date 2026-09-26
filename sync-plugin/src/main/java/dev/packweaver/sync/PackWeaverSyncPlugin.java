package dev.packweaver.sync;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * PackWeaver Sync — 服务端同步插件主入口（规划书第 19.1 / 20 章）。
 *
 * 「插件承担 API 调用层，替代 Mod」：在 Paper/Purpur/Spigot 服务端提供与
 * Fabric Mod 相同协议的桥接（HTTP 32006 / TCP 32005），桌面端与外部工具
 * 可部署数据包并热重载，无需在客户端安装任何东西。
 */
public class PackWeaverSyncPlugin extends JavaPlugin {

    private PerfTracker perf;
    private HttpBridge http;
    private TcpBridge tcp;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        perf = new PerfTracker(this);
        perf.init();
        http = new HttpBridge(this);
        http.start();
        tcp = new TcpBridge(this);
        tcp.start();
        PwsCommand cmd = new PwsCommand(this);
        getCommand("pws").setExecutor(cmd);
        getCommand("pws").setTabCompleter(cmd);
        getLogger().info("PackWeaver Sync 已启用：HTTP http://127.0.0.1:"
                + httpPort() + "/pw/ ，TCP 127.0.0.1:" + tcpPort() + "（仅本机回环）");
    }

    @Override
    public void onDisable() {
        if (http != null) {
            http.stop();
        }
        if (tcp != null) {
            tcp.stop();
        }
    }

    public int httpPort() {
        return getConfig().getInt("http-port", 32006);
    }

    public int tcpPort() {
        return getConfig().getInt("tcp-port", 32005);
    }

    public PerfTracker perf() {
        return perf;
    }

    /**
     * 在主线程执行任务并等待结果（桥接线程调用）。
     * 超时返回 null，由调用方转为错误应答。
     */
    public <T> T onMainThread(Callable<T> task, long timeoutMillis) {
        try {
            Future<T> f = Bukkit.getScheduler().callSyncMethod(this, task);
            return f.get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            return null;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
