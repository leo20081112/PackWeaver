package com.dpe.common.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TextComponent 单元测试。
 */
class TextComponentTest {

    @Test
    void roundTripPreservesStyleAndClickEvent() {
        TextComponent tc = new TextComponent("hello");
        tc.getStyle().setBold(true);
        tc.getStyle().setColor("red");
        tc.setClickEvent(new ClickEvent("run_command", "/say hi"));

        String json = tc.toJsonString();
        TextComponent parsed = TextComponent.fromJson(json);

        assertEquals("hello", parsed.getText());
        assertEquals(Boolean.TRUE, parsed.getStyle().getBold());
        assertEquals("red", parsed.getStyle().getColor());
        assertNotNull(parsed.getClickEvent());
        assertEquals("run_command", parsed.getClickEvent().action());
        assertEquals("/say hi", parsed.getClickEvent().value());
    }

    @Test
    void roundTripJsonIsStable() {
        TextComponent tc = new TextComponent("msg");
        tc.getStyle().setBold(true);
        tc.getStyle().setColor("red");
        tc.setClickEvent(new ClickEvent("run_command", "/say hi"));

        String json1 = tc.toJsonString();
        String json2 = TextComponent.fromJson(json1).toJsonString();
        assertEquals(json1, json2, "二次序列化应一致");
    }

    @Test
    void validateClickEventRejectsMissingSlash() {
        ClickEvent bad = new ClickEvent("run_command", "say hi");
        String err = TextComponentEditor.validateClickEventCommand(bad);
        assertNotNull(err, "run_command 缺少 '/' 应返回错误");
    }

    @Test
    void validateClickEventAcceptsValidCommand() {
        ClickEvent ok = new ClickEvent("run_command", "/say hi");
        String err = TextComponentEditor.validateClickEventCommand(ok);
        assertNull(err, "合法 run_command 应无错误");
    }

    @Test
    void validateClickEventRejectsBadUrl() {
        ClickEvent bad = new ClickEvent("open_url", "ftp://example.com");
        String err = TextComponentEditor.validateClickEventCommand(bad);
        assertNotNull(err, "open_url 非 http(s) 应返回错误");

        ClickEvent ok = new ClickEvent("open_url", "https://example.com");
        assertNull(TextComponentEditor.validateClickEventCommand(ok));
    }

    @Test
    void suggestCommandRequiresSlash() {
        assertNull(TextComponentEditor.validateClickEventCommand(
                new ClickEvent("suggest_command", "/give @p stone")));
        assertNotNull(TextComponentEditor.validateClickEventCommand(
                new ClickEvent("suggest_command", "give @p stone")));
    }

    @Test
    void extraChildrenRoundTrip() {
        TextComponent root = new TextComponent("root");
        root.getStyle().setColor("green");
        TextComponent child = new TextComponent("child");
        child.getStyle().setItalic(true);
        root.addChild(child);

        String json = root.toJsonString();
        TextComponent parsed = TextComponent.fromJson(json);
        assertEquals(1, parsed.getExtra().size());
        assertEquals("child", parsed.getExtra().get(0).getText());
        assertEquals(Boolean.TRUE, parsed.getExtra().get(0).getStyle().getItalic());
    }
}
