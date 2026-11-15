package dev.lowspec.rendercore;

import net.minecraft.client.gl.GlTimer;
import java.util.ArrayDeque;
import java.util.Iterator;

/**
 * Samples GPU execution time for RenderDispatcher with Minecraft's own timer
 * query abstraction. Results are polled asynchronously; this class never
 * waits for the GPU. It yields whenever Minecraft or another mod owns GlTimer.
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
    private static long totalNanos;
    private static boolean querySupported = true;

    private EntityGpuMetrics() { }

    private static boolean enabled() {
        return LowSpecRenderCoreClient.CONFIG != null
                && LowSpecRenderCoreClient.CONFIG.diagnosticsEnabled;
    }

    public static boolean beginDispatch() {
        if (!enabled() || !querySupported) return false;
        pollAvailable();
        if (pending.size() >= MAX_PENDING_QUERIES) return false;

        GlTimer timer = GlTimer.getInstance();
        // GlTimer is shared with Minecraft's GPU-utilization profiling. Never
        // nest queries or interfere with a query owned by vanilla/another mod.
        if (timer.isRunning()) return false;
        try {
            timer.beginProfile();
            return timer.isRunning();
        } catch (RuntimeException exception) {
            querySupported = false;
            LowSpecRenderCoreClient.LOGGER.debug("Aether GPU timer queries unavailable; CPU profiling remains active.", exception);
            return false;
        }
    }

    public static void endDispatch(boolean started) {
        if (!started) return;
        try {
            GlTimer.Query query = GlTimer.getInstance().endProfile();
            if (query != null) pending.addLast(query);
        } catch (RuntimeException exception) {
            querySupported = false;
            LowSpecRenderCoreClient.LOGGER.debug("Aether GPU timer query could not be closed; CPU profiling remains active.", exception);
        }
    }

    public static void pollAvailable() {
        if (!querySupported || pending.isEmpty()) return;
        Iterator<GlTimer.Query> iterator = pending.iterator();
        while (iterator.hasNext()) {
            GlTimer.Query query = iterator.next();
            try {
                if (!query.isResultAvailable()) continue;
                long elapsed = query.queryResult();
                query.close();
                iterator.remove();
                if (elapsed > 0L) record(elapsed);
            } catch (RuntimeException exception) {
                try { query.close(); } catch (RuntimeException ignored) { }
                iterator.remove();
                LowSpecRenderCoreClient.LOGGER.debug("Aether discarded an invalid GPU timer result.", exception);
            }
        }
    }

    private static void record(long elapsedNanos) {
        lastNanos = elapsedNanos;
        totalNanos += elapsedNanos;
        samplesNanos[cursor] = elapsedNanos;
        cursor = (cursor + 1) % WINDOW;
        if (sampleCount < WINDOW) sampleCount++;
    }

    public static Snapshot snapshot() {
        pollAvailable();
        long total = 0L;
        for (int i = 0; i < sampleCount; i++) total += samplesNanos[i];
        return new Snapshot(sampleCount,
                sampleCount == 0 ? 0.0 : total / (double) sampleCount / 1_000_000.0,
                lastNanos / 1_000_000.0,
                querySupported);
    }

    public record Snapshot(int samples, double averageMillis, double lastMillis,
                           boolean querySupported) { }
}
