package com.dpe.common.text;

/**
 * 点击事件，不可变 record。
 * action 取值：run_command / suggest_command / open_url / copy_to_clipboard / change_page / open_file
 */
public record ClickEvent(String action, String value) {

    public ClickEvent {
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("clickEvent action 不能为空");
        }
        if (value == null) {
            throw new IllegalArgumentException("clickEvent value 不能为 null");
        }
    }
}
