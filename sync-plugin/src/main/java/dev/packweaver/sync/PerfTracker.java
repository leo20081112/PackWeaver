package dev.packweaver.sync;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * 轻量性能追踪：每 tick 记录耗时，估算 MSPT / TPS（规划书第 8.3 / 14.7 章）。
 * 纯 Bukkit 实现，Spigot/Paper/Folia 均可用。
 */
public final class PerfTracker {
    private final JavaPlugin plugin;
    private long lastNanos = -1;
    private final double[] mspt = new double[100];
    private int index;
    private int samples;

    public PerfTracker(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.nanoTime();
            if (lastNanos > 0) {
                synchronized (mspt) {
                    mspt[index] = (now - lastNanos) / 1_000_000.0;
                    index = (index + 1) % mspt.length;
                    if (samples < mspt.length) {
                        samples++;
                    }
                }
            }
            lastNanos = now;
        }, 1L, 1L);
    }

    public double averageMspt() {
        synchronized (mspt) {
            if (samples == 0) {
                return 0;
            }
            double sum = 0;
            for (int i = 0; i < samples; i++) {
                sum += mspt[i];
            }
            return sum / samples;
        }
    }

    public double maxMspt() {
        synchronized (mspt) {
            double max = 0;
            for (int i = 0; i < samples; i++) {
                max = Math.max(max, mspt[i]);
            }
            return max;
        }
    }

    public double tps() {
        double m = averageMspt();
        return m <= 0 ? 20.0 : Math.min(20.0, 1000.0 / m);
    }

    public String status() {
        double m = averageMspt();
        return m < 40 ? "good" : m < 50 ? "warn" : "bad";
    }
}
