package com.dpe.common.text;

/**
 * 悬停事件，不可变 record。
 * action 取值：show_text / show_item / show_entity
 * contents 为文本组件（show_text）或物品/实体描述对象。
 */
public record HoverEvent(String action, Object contents) {

    public HoverEvent {
        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("hoverEvent action 不能为空");
        }
    }
}
