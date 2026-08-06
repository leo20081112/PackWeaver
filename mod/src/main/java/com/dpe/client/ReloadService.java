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

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 重载服务（Task 9 mod 部分）：按环境路由重载动作。
 * <ul>
 *   <li>单机：本地编译+导出+触发 {@link MinecraftClient#reloadResources()}。</li>
 *   <li>专用服务端有插件：发送 {@link SaveApplyMessage} 给服务端。</li>
 *   <li>专用服务端无插件：拒绝并返回 denyMessage。</li>
 * </ul>
 * 简化判断：客户端是否曾收到过任意服务端消息（{@link ClientNetworking#serverReachable()}）
 * 用于区分 DEDICATED_WITH_PLUGIN / DEDICATED_NO_PLUGIN。
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

    /** 单机：导出 zip 到世界 datapacks 目录，再触发资源重载。 */
    private static ReloadResult doLocalReload(EditorState state, MinecraftClient mc) {
        try {
            // OfflineDatapackIo.export 写到游戏目录 dpe-<ns>.zip；
            // 单机环境下也写入世界 datapacks 目录使重载能识别。
            Path exported = OfflineDatapackIo.export(state, BlockSchemaRegistry.DEFAULT);
            // 尝试同步到世界 datapacks 目录
            try {
                Path worldDatapacks = DatapackEditorClient.worldDatapacksDir(mc);
                if (worldDatapacks != null) {
                    java.nio.file.Files.createDirectories(worldDatapacks);
                    Path target = worldDatapacks.resolve(exported.getFileName());
                    java.nio.file.Files.copy(exported, target,
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException ignored) {
                // 同步失败不影响主流程
            }
            // 触发客户端/集成服务器资源重载（含 datapack）
            CompletableFuture<Void> fut = mc.reloadResources();
            if (fut != null) {
                fut.whenComplete((v, ex) -> {
                    if (ex != null) {
                        lastResult = new ReloadResult(false, "重载异常: " + ex.getMessage(), 0);
                    } else {
                        lastResult = new ReloadResult(true, "已写入并重载", 1);
                    }
                });
            } else {
                lastResult = new ReloadResult(true, "已写入文件（重载未触发）", 0);
            }
            return new ReloadResult(true, "已写入并请求重载: " + exported.getFileName(), 1);
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
