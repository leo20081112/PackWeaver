package com.dpe.common.complete;

import com.dpe.common.model.Datapack;

/**
 * 补全上下文，不可变。
 * commandContext 如 "function"/"data"/"scoreboard"/"text_component"。
 */
public record CompletionContext(String text,
                                int cursor,
                                String namespace,
                                Datapack datapack,
                                String commandContext) {
}
