package com.dpe.common.model;

import java.util.List;

/**
 * Minecraft 函数（.mcfunction），由 id 与命令行列表组成，不可变。
 */
public record McFunction(ResourceLocation id, List<String> commands) {

    public McFunction {
        commands = commands == null ? List.of() : List.copyOf(commands);
    }

    /**
     * 拼接为 .mcfunction 文件内容（每行一条命令）。
     */
    public String toFileContent() {
        StringBuilder sb = new StringBuilder();
        for (String c : commands) {
            sb.append(c).append('\n');
        }
        return sb.toString();
    }
}
