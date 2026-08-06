package com.dpe.common.manual;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 手册搜索器：按关键词模糊匹配（title/keywords/description 包含 query，不区分大小写）排序。
 */
public final class ManualSearcher {

    private static final int DEFAULT_LIMIT = 20;

    private final List<ManualEntry> entries;

    public ManualSearcher() {
        this(BuiltinManual.all());
    }

    public ManualSearcher(List<ManualEntry> entries) {
        this.entries = entries == null ? List.of() : entries;
    }

    /**
     * 按关键词搜索；命中越多、title 命中越靠前。limit 默认 20。
     */
    public List<ManualEntry> search(String query, int limit) {
        if (query == null || query.isBlank()) {
            return entries.stream().limit(limit <= 0 ? DEFAULT_LIMIT : limit).toList();
        }
        String q = query.trim().toLowerCase(Locale.ROOT);
        List<Scored> scored = new ArrayList<>();
        for (ManualEntry e : entries) {
            int score = score(e, q);
            if (score > 0) {
                scored.add(new Scored(e, score));
            }
        }
        scored.sort((a, b) -> Integer.compare(b.score, a.score));
        int cap = limit <= 0 ? DEFAULT_LIMIT : limit;
        List<ManualEntry> result = new ArrayList<>();
        for (Scored s : scored) {
            if (result.size() >= cap) {
                break;
            }
            result.add(s.entry);
        }
        return result;
    }

    /** limit 默认 20 的便捷重载。 */
    public List<ManualEntry> search(String query) {
        return search(query, DEFAULT_LIMIT);
    }

    /** 按大类筛选。 */
    public List<ManualEntry> byCategory(ManualCategory category) {
        if (category == null) {
            return List.of();
        }
        List<ManualEntry> result = new ArrayList<>();
        for (ManualEntry e : entries) {
            if (e.category() == category) {
                result.add(e);
            }
        }
        return result;
    }

    private static int score(ManualEntry e, String q) {
        String title = e.title() == null ? "" : e.title().toLowerCase(Locale.ROOT);
        String desc = e.description() == null ? "" : e.description().toLowerCase(Locale.ROOT);
        int score = 0;
        if (title.equals(q)) {
            score += 100;
        } else if (title.contains(q)) {
            score += 50;
        }
        if (desc.contains(q)) {
            score += 20;
        }
        for (String k : e.keywords()) {
            if (k == null) {
                continue;
            }
            String kl = k.toLowerCase(Locale.ROOT);
            if (kl.equals(q)) {
                score += 40;
            } else if (kl.contains(q)) {
                score += 15;
            }
        }
        // id 命中
        if (e.id() != null && e.id().toLowerCase(Locale.ROOT).contains(q)) {
            score += 30;
        }
        return score;
    }

    private record Scored(ManualEntry entry, int score) {
    }
}
