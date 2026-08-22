package dev.packweaver.bridge.perf;

import com.google.gson.JsonObject;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * 轻量性能追踪器（规划书第 8.3 / 14.7 / 18 章性能分析器）。
 * 通过测量服务器 tick 间隔估算 MSPT 与 TPS，供 /pw stats、
 * F12 叠加层与桥接 stats 指令使用。
 */
public final class PerfTracker {
    private static volatile long lastTickNanos = -1;
    private static final double[] msptHistory = new double[100];
    private static int index;
    private static int samples;

    private PerfTracker() {
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long now = System.nanoTime();
            if (lastTickNanos > 0) {
                double mspt = (now - lastTickNanos) / 1_000_000.0;
                synchronized (msptHistory) {
                    msptHistory[index] = mspt;
                    index = (index + 1) % msptHistory.length;
                    if (samples < msptHistory.length) {
                        samples++;
                    }
                }
            }
            lastTickNanos = now;
        });
    }

    /** 最近若干 tick 的平均毫秒数（MSPT）。 */
    public static double averageMspt() {
        synchronized (msptHistory) {
            if (samples == 0) {
                return 0;
            }
            double sum = 0;
            for (int i = 0; i < samples; i++) {
                sum += msptHistory[i];
            }
            return sum / samples;
        }
    }

    public static double maxMspt() {
        synchronized (msptHistory) {
            double max = 0;
            for (int i = 0; i < samples; i++) {
                max = Math.max(max, msptHistory[i]);
            }
            return max;
        }
    }

    /** 估算 TPS（每秒 tick 数，上限 20）。 */
    public static double tps() {
        double mspt = averageMspt();
        if (mspt <= 0) {
            return 20.0;
        }
        return Math.min(20.0, 1000.0 / mspt);
    }

    /** 性能状态：good（<40ms）/ warn（<50ms）/ bad。 */
    public static String status() {
        double mspt = averageMspt();
        if (mspt < 40) {
            return "good";
        }
        return mspt < 50 ? "warn" : "bad";
    }

    public static JsonObject report() {
        JsonObject o = new JsonObject();
        o.addProperty("mspt_avg", Math.round(averageMspt() * 100.0) / 100.0);
        o.addProperty("mspt_max", Math.round(maxMspt() * 100.0) / 100.0);
        o.addProperty("tps", Math.round(tps() * 100.0) / 100.0);
        o.addProperty("status", status());
        return o;
    }
}
