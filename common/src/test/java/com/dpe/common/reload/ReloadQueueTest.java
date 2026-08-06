package com.dpe.common.reload;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ReloadQueue 单元测试。
 */
class ReloadQueueTest {

    private ReloadQueue queue;

    @AfterEach
    void tearDown() {
        if (queue != null) {
            queue.shutdown();
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void concurrentRequestsAreMergedIntoOneReload() throws Exception {
        queue = new ReloadQueue();
        int n = 5;
        Thread[] threads = new Thread[n];
        CompletableFuture<ReloadResult>[] futures = new CompletableFuture[n];
        CountDownLatch ready = new CountDownLatch(n);
        CountDownLatch start = new CountDownLatch(1);

        for (int i = 0; i < n; i++) {
            final int idx = i;
            threads[i] = new Thread(() -> {
                ready.countDown();
                try {
                    start.await();
                } catch (InterruptedException e) {
                    return;
                }
                futures[idx] = queue.enqueue(new ReloadRequest("p" + idx, "dpe"));
            });
            threads[i].start();
        }

        assertTrue(ready.await(2, TimeUnit.SECONDS), "所有线程应就绪");
        start.countDown(); // 同时触发所有 enqueue，确保落在 200ms 合并窗口内
        for (Thread t : threads) {
            t.join(2000);
        }

        CompletableFuture<Void> all = CompletableFuture.allOf(futures);
        all.get(5, TimeUnit.SECONDS);

        for (CompletableFuture<ReloadResult> f : futures) {
            assertNotNull(f);
            assertTrue(f.isDone(), "所有 future 应完成");
            assertFalse(f.isCompletedExceptionally(), "不应异常完成");
        }
        ReloadResult shared = futures[0].get();
        assertTrue(shared.success(), "重载应成功: " + shared);
        assertEquals(1L, shared.reloadCount(), "5 个并发请求应合并为 1 次实际重载: " + shared);

        for (CompletableFuture<ReloadResult> f : futures) {
            assertSame(shared, f.get(), "合并窗口内的请求应拿到同一 ReloadResult");
        }
        assertEquals(1L, queue.completedReloadCount());
    }

    @Test
    void requestsBeyondMergeWindowTriggerSeparateReload() throws Exception {
        queue = new ReloadQueue();
        ReloadResult r1 = queue.enqueue(new ReloadRequest("p1", "dpe")).get(5, TimeUnit.SECONDS);
        // 等待合并窗口过后再发起第二个
        Thread.sleep(ReloadQueue.MERGE_WINDOW_MS + 150);
        ReloadResult r2 = queue.enqueue(new ReloadRequest("p2", "dpe")).get(5, TimeUnit.SECONDS);

        assertTrue(r1.success());
        assertTrue(r2.success());
        assertEquals(1L, r1.reloadCount(), "首次重载计数应为 1");
        assertEquals(2L, r2.reloadCount(), "第二次重载计数应为 2");
        assertNotSame(r1, r2, "不同窗口的请求应拿到不同结果");
        assertEquals(2L, queue.completedReloadCount());
    }

    @Test
    void nullRequestCompletesExceptionally() {
        queue = new ReloadQueue();
        CompletableFuture<ReloadResult> f = queue.enqueue(null);
        assertTrue(f.isCompletedExceptionally());
    }
}
