package com.dpe.client;

import com.dpe.common.block.BlockSchemaRegistry;
import com.dpe.common.block.EditorState;
import com.dpe.common.compile.BlockCompiler;
import com.dpe.common.compile.CompileResult;
import com.dpe.common.compile.ValidationError;
import com.dpe.common.protocol.SaveApplyMessage;
import com.dpe.common.reload.ReloadAction;
import com.dpe.common.reload.ReloadCoordinator;
import com.dpe.common.reload.ReloadEnvironment;
import com.dpe.common.reload.ReloadResult;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.server.integrated.IntegratedServer;

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 重载服务：按环境路由重载动作。
 * <ul>
 *   <li>单机：编译+导出到世界 datapacks 目录（解压目录），再触发集成服务器数据包重载（vanilla /reload）。</li>
 *   <li>专用服务端有插件：发送 {@link SaveApplyMessage} 给服务端。</li>
 *   <li>专用服务端无插件：拒绝并返回 denyMessage。</li>
 * </ul>
 */
public final class ReloadService {

    /** 最近一次重载结果，便于 UI 显示。 */
    private static volatile ReloadResult lastResult = null;
    /** 是否曾收到过服务端消息（视为服务端装了插件）。 */
    private static final AtomicBoolean serverPluginPresent = new AtomicBoolean(false);

    private ReloadService() {
    }

    /** 标记客户端已收到服务端消息，视为服务端安装了插件。 */
    public static void markServerPluginPresent() {
        serverPluginPresent.set(true);
    }

    /** 是否检测到服务端插件存在。 */
    public static boolean isServerPluginPresent() {
        return serverPluginPresent.get();
    }

    /** 重置检测状态（断开连接时调用）。 */
    public static void resetServerPluginState() {
        serverPluginPresent.set(false);
    }

    public static ReloadResult lastResult() {
        return lastResult;
    }

    /**
     * 编译并按环境重载。
     * @param state 当前编辑器状态
     * @param mc    MinecraftClient 实例
     * @return 重载结果
     */
    public static ReloadResult reload(EditorState state, MinecraftClient mc) {
        if (state == null || mc == null) {
            ReloadResult r = new ReloadResult(false, "参数为空", 0);
            lastResult = r;
            return r;
        }

        // 1. 先编译，校验
        CompileResult compile = new BlockCompiler().compile(state, BlockSchemaRegistry.DEFAULT);
        if (!compile.success()) {
            String msg = formatErrors(compile.errors());
            ReloadResult r = new ReloadResult(false, "编译失败:\n" + msg, 0);
            lastResult = r;
            return r;
        }

        // 2. 判断环境
        ReloadEnvironment env = detectEnvironment(mc);
        ReloadAction action = ReloadCoordinator.decide(env);

        switch (action) {
            case LOCAL_WRITE_AND_RELOAD -> {
                return doLocalReload(state, mc);
            }
            case SEND_TO_SERVER -> {
                return doSendToServer(state);
            }
            case DENY_WITH_MESSAGE -> {
                ReloadResult r = new ReloadResult(false, ReloadCoordinator.denyMessage(), 0);
                lastResult = r;
                return r;
            }
            default -> {
                ReloadResult r = new ReloadResult(false, "未知重载动作", 0);
                lastResult = r;
                return r;
            }
        }
    }

    /** 判断当前重载环境。 */
    private static ReloadEnvironment detectEnvironment(MinecraftClient mc) {
        ServerInfo info = mc.getCurrentServerEntry();
        if (info == null) {
            // 单机/局域网联机（集成服务器）或离线
            return ReloadEnvironment.SINGLEPLAYER;
        }
        // 远程专用服务端：根据是否曾收到服务端消息区分
        return serverPluginPresent.get()
                ? ReloadEnvironment.DEDICATED_WITH_PLUGIN
                : ReloadEnvironment.DEDICATED_NO_PLUGIN;
    }

    /** 单机：一步导出到世界 datapacks 目录（解压目录），再触发集成服务器数据包重载（vanilla /reload）。 */
    private static ReloadResult doLocalReload(EditorState state, MinecraftClient mc) {
        try {
            // 一步落盘到世界 datapacks 目录（解压目录，便于持续编辑）
            Path exported = OfflineDatapackIo.exportToDatapacksDir(state, BlockSchemaRegistry.DEFAULT, mc);
            // 触发集成服务器数据包重载（vanilla /reload 等价，重载数据包而非资源包）
            IntegratedServer server = mc.getServer();
            if (server != null) {
                // withLevel(2) 满足 /reload 所需权限；withSilent 抑制命令反馈
                server.getCommandManager().executeWithPrefix(
                        server.getCommandSource().withSilent().withLevel(2), "reload");
                lastResult = new ReloadResult(true, "已写入并触发数据包重载", 1);
                return new ReloadResult(true,
                        "已写入并触发数据包重载: " + exported.getFileName(), 1);
            }
            lastResult = new ReloadResult(true, "已写入文件（无集成服务器，未触发重载）", 0);
            return new ReloadResult(true,
                    "已写入: " + exported.getFileName() + "（未触发重载）", 0);
        } catch (IllegalStateException e) {
            ReloadResult r = new ReloadResult(false, "编译失败: " + e.getMessage(), 0);
            lastResult = r;
            return r;
        } catch (IOException e) {
            ReloadResult r = new ReloadResult(false, "写入失败: " + e.getMessage(), 0);
            lastResult = r;
            return r;
        }
    }

    /** 远程有插件：发送 SaveApplyMessage。 */
    private static ReloadResult doSendToServer(EditorState state) {
        if (!ClientNetworking.canSend()) {
            ReloadResult r = new ReloadResult(false, "未连接服务端，无法发送", 0);
            lastResult = r;
            return r;
        }
        String ns = state.getActiveDatapackNamespace();
        ClientNetworking.send(new SaveApplyMessage(ns));
        ReloadResult r = new ReloadResult(true, "已请求服务端重载: " + ns, 0);
        lastResult = r;
        return r;
    }

    /** 格式化校验错误列表。 */
    private static String formatErrors(List<ValidationError> errors) {
        if (errors == null || errors.isEmpty()) {
            return "";
        }
        Map<String, String> seen = new LinkedHashMap<>();
        StringBuilder sb = new StringBuilder();
        for (ValidationError e : errors) {
            String key = (e.blockId() == null ? "?" : e.blockId()) + "|"
                    + (e.field() == null ? "" : e.field());
            if (seen.containsKey(key)) {
                continue;
            }
            seen.put(key, "");
            sb.append('[').append(e.blockId() == null ? "?" : e.blockId()).append(']');
            if (e.field() != null && !e.field().isBlank()) {
                sb.append(' ').append(e.field());
            }
            String msg = e.friendlyMessage();
            if (msg == null || msg.isBlank()) {
                msg = e.message();
            }
            sb.append(": ").append(msg);
            if (e.fixSuggestion() != null && !e.fixSuggestion().isBlank()) {
                sb.append("（建议: ").append(e.fixSuggestion()).append('）');
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }
}
