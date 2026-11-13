package dev.lowspec.rendercore;

import java.util.concurrent.atomic.LongAdder;

/** Aggregate timing data for vanilla LightingProvider.doLightUpdates calls. */
public final class LightingMetrics {
    private static final LongAdder CALLS = new LongAdder();
    private static final LongAdder NANOS = new LongAdder();
    private static final LongAdder REPORTED_UPDATES = new LongAdder();

    private LightingMetrics() { }

    public static void record(long elapsedNanos, int reportedUpdates) {
        CALLS.increment();
        NANOS.add(Math.max(0L, elapsedNanos));
        REPORTED_UPDATES.add(Math.max(0, reportedUpdates));
    }

    public static Snapshot snapshot() {
        long calls = CALLS.sum();
        long nanos = NANOS.sum();
        long updates = REPORTED_UPDATES.sum();
        return new Snapshot(calls, nanos, updates, calls == 0 ? 0.0 : (double) nanos / calls / 1_000_000.0);
    }

    public static void reset() {
        CALLS.reset();
        NANOS.reset();
        REPORTED_UPDATES.reset();
    }

    public record Snapshot(long calls, long totalNanos, long reportedUpdates, double averageMillisPerCall) { }
}
