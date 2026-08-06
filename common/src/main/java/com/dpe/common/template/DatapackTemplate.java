package com.dpe.common.template;

import com.dpe.common.block.EditorState;

/**
 * 数据包模板，不可变。preset 为预置好的编辑器状态。
 */
public record DatapackTemplate(String id,
                               String title,
                               String description,
                               EditorState preset) {

    public DatapackTemplate {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("template id 不能为空");
        }
        if (title == null || title.isBlank()) {
            title = id;
        }
        if (description == null) {
            description = "";
        }
        if (preset == null) {
            throw new IllegalArgumentException("template preset 不能为空");
        }
    }
}
