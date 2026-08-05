package com.dpe.common.complete;

import java.util.ArrayList;
import java.util.List;

/**
 * 补全服务，聚合多个 provider，按 context 路由。
 */
public final class CompletionService implements CompletionProvider {

    private final List<CompletionProvider> providers;

    public CompletionService() {
        this.providers = new ArrayList<>();
        this.providers.add(new FunctionCompletionProvider());
        this.providers.add(new NbtScoreboardCompletionProvider());
        this.providers.add(new TextComponentCompletionProvider());
    }

    public CompletionService(List<CompletionProvider> providers) {
        this.providers = new ArrayList<>(providers);
    }

    /** 注册额外 provider。 */
    public void addProvider(CompletionProvider provider) {
        if (provider != null) {
            providers.add(provider);
        }
    }

    @Override
    public List<CompletionCandidate> complete(CompletionContext ctx) {
        List<CompletionCandidate> result = new ArrayList<>();
        if (ctx == null) {
            return result;
        }
        for (CompletionProvider p : providers) {
            List<CompletionCandidate> cands = p.complete(ctx);
            if (cands != null) {
                result.addAll(cands);
            }
        }
        return result;
    }
}
