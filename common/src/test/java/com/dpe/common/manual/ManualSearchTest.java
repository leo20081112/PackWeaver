package com.dpe.common.manual;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ManualSearcher 单元测试。
 */
class ManualSearchTest {

    @Test
    void builtinHasAtLeastFortyEntries() {
        assertTrue(BuiltinManual.all().size() >= 40, "内置条目应不少于 40 条: " + BuiltinManual.all().size());
    }

    @Test
    void searchClickEventHitsTextComponentEntry() {
        ManualSearcher searcher = new ManualSearcher();
        List<ManualEntry> result = searcher.search("点击事件");
        assertFalse(result.isEmpty(), "搜索「点击事件」应命中条目");
        assertTrue(result.stream().anyMatch(e -> e.category() == ManualCategory.TEXT_COMPONENT
                        && "clickEvent".equals(e.title())),
                "应命中 clickEvent 文本组件条目: " + result);
    }

    @Test
    void searchSayHitsSayCommand() {
        ManualSearcher searcher = new ManualSearcher();
        List<ManualEntry> result = searcher.search("say");
        assertFalse(result.isEmpty(), "搜索「say」应命中条目");
        assertTrue(result.stream().anyMatch(e -> "say".equalsIgnoreCase(e.title())
                        && e.category() == ManualCategory.COMMAND),
                "应命中 say 命令条目: " + result);
    }

    @Test
    void byCategoryCommandIsNonEmpty() {
        ManualSearcher searcher = new ManualSearcher();
        List<ManualEntry> cmds = searcher.byCategory(ManualCategory.COMMAND);
        assertFalse(cmds.isEmpty(), "COMMAND 类目应非空");
        assertTrue(cmds.stream().allMatch(e -> e.category() == ManualCategory.COMMAND));
    }

    @Test
    void byIdReturnsEntry() {
        ManualEntry e = BuiltinManual.byId("cmd/say");
        assertNotNull(e, "byId 应返回 say 命令条目");
        assertEquals("say", e.title());
        assertNull(BuiltinManual.byId("does/not/exist"), "不存在的 id 应返回 null");
    }

    @Test
    void searchRespectsLimit() {
        ManualSearcher searcher = new ManualSearcher();
        List<ManualEntry> result = searcher.search("e", 5);
        assertTrue(result.size() <= 5, "结果数应不超过 limit");
    }
}
