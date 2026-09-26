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
        try {
            perf = new PerfTracker(this);
            perf.init();
        } catch (Throwable t) {
            getLogger().severe("性能统计初始化失败（不影响桥接）: " + t);
        }
        try {
            http = new HttpBridge(this);
            http.start();
        } catch (Throwable t) {
            getLogger().severe("HTTP 桥接启动失败: " + t);
        }
        try {
            tcp = new TcpBridge(this);
            tcp.start();
        } catch (Throwable t) {
            getLogger().severe("TCP 桥接启动失败: " + t);
        }
        try {
            PwsCommand cmd = new PwsCommand(this);
            if (getCommand("pws") != null) {
                getCommand("pws").setExecutor(cmd);
                getCommand("pws").setTabCompleter(cmd);
            } else {
                getLogger().severe("plugin.yml 中缺少 pws 命令定义（jar 可能损坏或被重组）");
            }
        } catch (Throwable t) {
            getLogger().severe("/pws 命令注册失败: " + t);
        }
        getLogger().info("PackWeaver Sync v" + getDescription().getVersion()
                + " 启用完成：HTTP http://127.0.0.1:" + httpPort() + "/pw/ ，TCP 127.0.0.1:" + tcpPort()
                + "（仅本机回环）。若上方有 severe 报错，请把该行发给开发者。");
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

    public long getMaxDeployMb() {
        return getConfig().getLong("max-deploy-mb", 64L);
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
