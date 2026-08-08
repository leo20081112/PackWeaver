package com.dpe.common.complete;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class WikiCommandCache {

    private static final Map<String, WikiCommandInfo> CACHE = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 100;
    private static long lastFetchTime = 0;
    private static final long FETCH_COOLDOWN_MS = 60000;
    private static final String CACHE_PREFIX = "wiki:";

    private WikiCommandCache() {
    }

    public static WikiCommandInfo get(String commandName) {
        return CACHE.get(CACHE_PREFIX + commandName.toLowerCase());
    }

    public static void put(String commandName, WikiCommandInfo info) {
        if (CACHE.size() >= MAX_CACHE_SIZE) {
            evictOldest();
        }
        CACHE.put(CACHE_PREFIX + commandName.toLowerCase(), info);
        lastFetchTime = System.currentTimeMillis();
    }

    public static boolean hasCache(String commandName) {
        return CACHE.containsKey(CACHE_PREFIX + commandName.toLowerCase());
    }

    public static boolean shouldFetchFromWiki() {
        return System.currentTimeMillis() - lastFetchTime > FETCH_COOLDOWN_MS;
    }

    public static void clear() {
        CACHE.clear();
    }

    private static void evictOldest() {
        if (CACHE.isEmpty()) {
            return;
        }
        String firstKey = CACHE.keySet().iterator().next();
        CACHE.remove(firstKey);
    }

    public static int size() {
        return CACHE.size();
    }

    public record WikiCommandInfo(String commandName, String description, String syntax, String example, long fetchTime) {

        public WikiCommandInfo {
            if (commandName == null) {
                commandName = "";
            }
            if (description == null) {
                description = "";
            }
            if (syntax == null) {
                syntax = "";
            }
            if (example == null) {
                example = "";
            }
            if (fetchTime <= 0) {
                fetchTime = System.currentTimeMillis();
            }
        }

        public String toDetail() {
            StringBuilder sb = new StringBuilder();
            if (!description.isEmpty()) {
                sb.append(description);
            }
            if (!syntax.isEmpty()) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append("语法: ").append(syntax);
            }
            return sb.toString();
        }
    }
}
