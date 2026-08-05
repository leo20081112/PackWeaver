package com.dpe.server;

import com.dpe.common.compile.ValidationError;

import java.util.List;

/**
 * 命令辅助工具：格式化校验错误列表等。
 */
final class DatapackCommandUtil {

    private DatapackCommandUtil() {
    }

    /** 将校验错误列表格式化为多行字符串。 */
    static String formatErrors(List<ValidationError> errors) {
        if (errors == null || errors.isEmpty()) {
            return "未知错误";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("编译失败 (").append(errors.size()).append(" 项错误):");
        for (ValidationError e : errors) {
            sb.append("\n- block=").append(e.blockId() == null ? "?" : e.blockId());
            if (e.field() != null && !e.field().isBlank()) {
                sb.append(" field=").append(e.field());
            }
            sb.append(": ").append(e.message());
        }
        return sb.toString();
    }
}
