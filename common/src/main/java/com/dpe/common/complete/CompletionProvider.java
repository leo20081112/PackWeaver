package com.dpe.common.complete;

import java.util.List;

/**
 * 补全提供者接口。
 */
public interface CompletionProvider {
    /**
     * 根据上下文返回补全候选。
     * @return 候选列表（可为空，不可为 null）
     */
    List<CompletionCandidate> complete(CompletionContext ctx);
}
