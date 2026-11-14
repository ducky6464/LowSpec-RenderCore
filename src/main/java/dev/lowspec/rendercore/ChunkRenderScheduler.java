package dev.lowspec.rendercore;

import dev.lowspec.rendercore.mixin.WorldRendererInvoker;
import net.minecraft.client.render.WorldRenderer;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Paces and coalesces chunk rebuild requests without intentionally dropping them. */
public final class ChunkRenderScheduler {
    private static final int MAX_PENDING = 16_384;
    private static final LinkedHashMap<SectionKey, Boolean> IMPORTANT = new LinkedHashMap<>();
    private static final LinkedHashMap<SectionKey, Boolean> NORMAL = new LinkedHashMap<>();
    private static Object currentWorld;
    private static final ThreadLocal<Boolean> REPLAYING = ThreadLocal.withInitial(() -> false);

    private ChunkRenderScheduler() {}

    /** Reduces the rebuild batch when the current client tick is already CPU-heavy. */
    public static int adaptiveBudget(int configuredBudget, long elapsedTickNanos) {
        if (configuredBudget <= 16) return Math.max(0, configuredBudget);
        double elapsedMillis = Math.max(0L, elapsedTickNanos) / 1_000_000.0;
        int budget = configuredBudget;
        if (elapsedMillis >= 40.0) {
            budget = 16;
        } else if (elapsedMillis > 20.0) {
            double ratio = (40.0 - elapsedMillis) / 20.0;
            budget = Math.max(16, Math.min(configuredBudget,
                    (int) Math.ceil(16.0 + (configuredBudget - 16.0) * ratio)));
        }

        // When chunk diagnostics are enabled, worker-thread mesh cost also informs
        // the next batch. Expensive section builds mean fewer new jobs should be queued.
        if (LowSpecRenderCoreClient.CONFIG != null
                && LowSpecRenderCoreClient.CONFIG.chunkDiagnosticsEnabled) {
            ChunkMeshMetrics.Snapshot mesh = ChunkMeshMetrics.snapshot();
            if (mesh.samples() >= 10) {
                if (mesh.p95Millis() >= 30.0) {
                    budget = Math.min(budget, Math.max(16, configuredBudget / 4));
                } else if (mesh.p95Millis() >= 20.0) {
                    budget = Math.min(budget, Math.max(16, configuredBudget / 2));
                } else if (mesh.p95Millis() >= 12.0) {
                    budget = Math.min(budget, Math.max(16, (configuredBudget * 3) / 4));
                }
            }
        }
        return Math.max(16, Math.min(configuredBudget, budget));
    }

    public static synchronized boolean enqueue(int x, int y, int z, boolean important) {
        SectionKey key = new SectionKey(x, y, z);
        if (IMPORTANT.containsKey(key) || NORMAL.containsKey(key)) {
            if (important) {
                NORMAL.remove(key);
                IMPORTANT.putIfAbsent(key, true);
            }
            return true;
        }
        if (IMPORTANT.size() + NORMAL.size() >= MAX_PENDING) return false;
        (important ? IMPORTANT : NORMAL).put(key, important);
        return true;
    }

    public static void drain(WorldRenderer renderer, Object world, int budget, long tickStartNanos) {
        synchronized (ChunkRenderScheduler.class) {
            if (world != currentWorld) {
                IMPORTANT.clear();
                NORMAL.clear();
                currentWorld = world;
            }
        }
        if (world == null || budget <= 0) return;
        WorldRendererInvoker invoker = (WorldRendererInvoker) renderer;
        REPLAYING.set(true);
        try {
            for (int remaining = budget; remaining > 0; remaining--) {
                Map.Entry<SectionKey, Boolean> entry = pollNext();
                if (entry == null) break;
                // Keep ordinary rebuild work from extending an already overloaded tick.
                // Important rebuilds still run so urgent visual updates are not stranded.
                if (!entry.getValue() && tickStartNanos != 0L
                        && System.nanoTime() - tickStartNanos >= 40_000_000L) {
                    requeue(entry);
                    break;
                }
                SectionKey key = entry.getKey();
                try {
                    invoker.lowspec$invokeScheduleChunkRender(key.x(), key.y(), key.z(), entry.getValue());
                } catch (RuntimeException | Error failure) {
                    synchronized (ChunkRenderScheduler.class) {
                        LinkedHashMap<SectionKey, Boolean> target = entry.getValue() ? IMPORTANT : NORMAL;
                        target.putIfAbsent(key, entry.getValue());
                    }
                    throw failure;
                }
            }
        } finally {
            REPLAYING.remove();
        }
    }

    private static synchronized void requeue(Map.Entry<SectionKey, Boolean> entry) {
        LinkedHashMap<SectionKey, Boolean> target = entry.getValue() ? IMPORTANT : NORMAL;
        target.putIfAbsent(entry.getKey(), entry.getValue());
    }

    private static synchronized Map.Entry<SectionKey, Boolean> pollNext() {
        Iterator<Map.Entry<SectionKey, Boolean>> it = IMPORTANT.entrySet().iterator();
        if (it.hasNext()) {
            Map.Entry<SectionKey, Boolean> entry = it.next();
            Map.Entry<SectionKey, Boolean> copy = Map.entry(entry.getKey(), true);
            it.remove();
            return copy;
        }
        it = NORMAL.entrySet().iterator();
        if (it.hasNext()) {
            Map.Entry<SectionKey, Boolean> entry = it.next();
            Map.Entry<SectionKey, Boolean> copy = Map.entry(entry.getKey(), false);
            it.remove();
            return copy;
        }
        return null;
    }

    public static boolean isReplaying() { return REPLAYING.get(); }
    public static synchronized int pendingCount() { return IMPORTANT.size() + NORMAL.size(); }

    public static synchronized void clear() {
        IMPORTANT.clear();
        NORMAL.clear();
        currentWorld = null;
    }

    private record SectionKey(int x, int y, int z) {}
}
