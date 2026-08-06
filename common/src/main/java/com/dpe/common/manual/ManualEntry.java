package com.dpe.common.manual;

import java.util.List;

/**
 * 手册条目，不可变。description 为中文说明。
 */
public record ManualEntry(String id,
                          ManualCategory category,
                          String title,
                          String description,
                          String example,
                          List<String> keywords) {

    public ManualEntry {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("entry id 不能为空");
        }
        if (category == null) {
            throw new IllegalArgumentException("entry category 不能为空");
        }
        if (title == null || title.isBlank()) {
            title = id;
        }
        if (description == null) {
            description = "";
        }
        if (example == null) {
            example = "";
        }
        keywords = keywords == null ? List.of() : List.copyOf(keywords);
    }
}
