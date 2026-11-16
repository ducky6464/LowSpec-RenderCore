package dev.lowspec.rendercore;

import net.minecraft.client.gl.GlTimer;

import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * Samples GPU timer-query duration around RenderDispatcher.render() with
 * Minecraft's own timer abstraction. This is only the GPU work executed inside
 * that call's query scope; Minecraft may defer other work to later render
 * passes, so this must not be treated as total per-entity GPU cost. Results
 * are polled asynchronously; this class never waits for the GPU.
 *
 * All methods are called on the render thread.
 */
public final class EntityGpuMetrics {
    private static final int WINDOW = 120;
    private static final int MAX_PENDING_QUERIES = 8;
    private static final long[] samplesNanos = new long[WINDOW];
    private static final ArrayDeque<GlTimer.Query> pending = new ArrayDeque<>();
    private static int cursor;
    private static int sampleCount;
    private static long lastNanos;
    private static long completedQueries;
    private static long discardedQueries;
    private static long busySkips;
    private static long queueFullSkips;
    private static int pendingHighWater;
    private static boolean querySupported = true;

    private EntityGpuMetrics() { }

    private static boolean enabled() {
        return LowSpecRenderCoreClient.CONFIG != null
                && LowSpecRenderCoreClient.CONFIG.diagnosticsEnabled;
    }

    public static boolean beginDispatch() {
        if (!enabled() || !querySupported) return false;
        pollAvailable();
        if (pending.size() >= MAX_PENDING_QUERIES) {
            queueFullSkips++;
            return false;
        }

        GlTimer timer = GlTimer.getInstance();
        // GlTimer is shared with Minecraft's GPU profiler. Never nest a query
        // or end a query that belongs to vanilla or another mod.
        if (timer.isRunning()) {
            busySkips++;
            return false;
        }
        try {
            timer.beginProfile();
            if (!timer.isRunning()) {
                querySupported = false;
                LowSpecRenderCoreClient.LOGGER.debug(
                        "Minecraft GPU timer did not start; Aether GPU timing is disabled.");
                return false;
            }
            return true;
        } catch (RuntimeException exception) {
            querySupported = false;
            LowSpecRenderCoreClient.LOGGER.debug(
                    "Aether GPU timer queries unavailable; CPU profiling remains active.", exception);
            return false;
        }
    }

    public static void endDispatch(boolean started) {
        if (!started) return;
        try {
            GlTimer.Query query = GlTimer.getInstance().endProfile();
            if (query != null) {
                pending.addLast(query);
                pendingHighWater = Math.max(pendingHighWater, pending.size());
            } else {
                discardedQueries++;
            }
        } catch (RuntimeException exception) {
            querySupported = false;
            discardedQueries++;
            LowSpecRenderCoreClient.LOGGER.debug(
                    "Aether GPU timer query could not be closed; CPU profiling remains active.", exception);
        }
    }

    /** Polls only results already available; it never blocks for a query. */
    public static void pollAvailable() {
        if (pending.isEmpty()) return;
        Iterator<GlTimer.Query> iterator = pending.iterator();
        while (iterator.hasNext()) {
            GlTimer.Query query = iterator.next();
            try {
                if (!query.isResultAvailable()) continue;
                long elapsed = query.queryResult();
                query.close();
                iterator.remove();
                if (elapsed > 0L) {
                    record(elapsed);
                    completedQueries++;
                } else {
                    discardedQueries++;
                }
            } catch (RuntimeException exception) {
                try { query.close(); } catch (RuntimeException ignored) { }
                iterator.remove();
                discardedQueries++;
                LowSpecRenderCoreClient.LOGGER.debug(
                        "Aether discarded an invalid GPU timer result.", exception);
            }
        }
    }

    private static void record(long elapsedNanos) {
        lastNanos = elapsedNanos;
        samplesNanos[cursor] = elapsedNanos;
        cursor = (cursor + 1) % WINDOW;
        if (sampleCount < WINDOW) sampleCount++;
    }

    /** Releases outstanding query objects during client shutdown. */
    public static void close() {
        while (!pending.isEmpty()) {
            GlTimer.Query query = pending.removeFirst();
            try {
                query.close();
            } catch (RuntimeException exception) {
                LowSpecRenderCoreClient.LOGGER.debug(
                        "Aether could not release a pending GPU timer query.", exception);
            }
        }
    }

    public static Snapshot snapshot() {
        pollAvailable();
        long total = 0L;
        for (int i = 0; i < sampleCount; i++) total += samplesNanos[i];
        return new Snapshot(sampleCount,
                sampleCount == 0 ? 0.0 : total / (double) sampleCount / 1_000_000.0,
                lastNanos / 1_000_000.0,
                querySupported, pending.size(), pendingHighWater,
                completedQueries, discardedQueries, busySkips, queueFullSkips);
    }

    public record Snapshot(int samples, double averageMillis, double lastMillis,
                           boolean querySupported, int pendingQueries, int pendingHighWater,
                           long completedQueries, long discardedQueries,
                           long busySkips, long queueFullSkips) { }
}
