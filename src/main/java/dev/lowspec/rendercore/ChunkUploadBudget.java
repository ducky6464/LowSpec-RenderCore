package dev.lowspec.rendercore;

/** Chooses a conservative per-frame upload cap when the client tick is already overloaded. */
public final class ChunkUploadBudget {
    private static volatile int lastEffectiveLimit = 8;

    private ChunkUploadBudget() { }

    public static int effectiveLimit(int configuredLimit) {
        int base = Math.max(1, Math.min(64, configuredLimit));
        double tickMillis = LowSpecRenderCoreClient.getLastClientTickMillis();
        int limit = base;
        if (tickMillis >= 40.0) {
            limit = Math.max(1, base / 4);
        } else if (tickMillis >= 30.0) {
            limit = Math.max(1, base / 2);
        } else if (tickMillis >= 20.0) {
            limit = Math.max(1, (base * 3) / 4);
        }
        RenderFrameMetrics.Snapshot render = RenderFrameMetrics.snapshot();
        if (render.samples() >= 10) {
            if (render.p95RenderMillis() >= 50.0) {
                limit = Math.min(limit, Math.max(1, base / 4));
            } else if (render.p95RenderMillis() >= 35.0) {
                limit = Math.min(limit, Math.max(1, base / 2));
            }
        }
        lastEffectiveLimit = limit;
        return limit;
    }

    public static int getLastEffectiveLimit() {
        return lastEffectiveLimit;
    }
}
