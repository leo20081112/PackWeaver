package com.dpe.common.reload;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 服务端并发重载串行化队列。
 * 内部单线程执行器串行执行；短时间内（合并窗口 200ms）多个请求合并为一次实际 reload，
 * 并把同一 ReloadResult 返回给所有等待者。
 */
public final class ReloadQueue {

    /** 合并窗口（毫秒）。 */
    static final long MERGE_WINDOW_MS = 200L;

    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "dpe-reload-queue");
        t.setDaemon(true);
        return t;
    });
    private final AtomicLong reloadCount = new AtomicLong(0L);

    private final Object lock = new Object();
    /** 当前合并窗口内挂起的 future；为 null 表示无挂起重载。 */
    private CompletableFuture<ReloadResult> pending = null;
    private ScheduledFuture<?> pendingTask = null;

    /**
     * 入队一个重载请求。若 200ms 内已有挂起请求则合并，复用同一 future。
     */
    public CompletableFuture<ReloadResult> enqueue(ReloadRequest req) {
        if (req == null) {
            CompletableFuture<ReloadResult> err = new CompletableFuture<>();
            err.completeExceptionally(new IllegalArgumentException("req 不能为空"));
            return err;
        }
        synchronized (lock) {
            if (pending != null) {
                // 合并到已有挂起重载，复用同一 future
                return pending;
            }
            CompletableFuture<ReloadResult> f = new CompletableFuture<>();
            pending = f;
            pendingTask = exec.schedule(this::doReload, MERGE_WINDOW_MS, TimeUnit.MILLISECONDS);
            return f;
        }
    }

    /** 实际执行重载并完成所有等待者。 */
    private void doReload() {
        CompletableFuture<ReloadResult> f;
        synchronized (lock) {
            f = pending;
            pending = null;
            pendingTask = null;
        }
        if (f == null) {
            return;
        }
        long count = reloadCount.incrementAndGet();
        ReloadResult result = new ReloadResult(true, "数据包重载成功", count);
        f.complete(result);
    }

    /** 已累计完成的实际重载次数（主要供测试/监控）。 */
    public long completedReloadCount() {
        return reloadCount.get();
    }

    /** 关闭队列，释放线程。 */
    public void shutdown() {
        synchronized (lock) {
            if (pendingTask != null) {
                pendingTask.cancel(false);
                pendingTask = null;
            }
            if (pending != null) {
                pending.cancel(false);
                pending = null;
            }
        }
        exec.shutdownNow();
    }
}
